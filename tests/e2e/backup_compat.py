"""12.77.11: streamed backups — old copies restore in the new version and new copies in the old one. Args: OLD_ASSETS NEW_ASSETS."""
import os, sys as _s;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
FIX=os.environ.get("VYP_FIX","fixtures-out")
os.makedirs('/tmp/vypiski-test-out',exist_ok=True)
import asyncio, sys, glob, http.server, threading, functools, os
from playwright.async_api import async_playwright
OLD=sys.argv[1];NEW=sys.argv[2]
files=sorted(glob.glob(FIX+'/xlcases/*.docx'))+sorted(glob.glob(FIX+'/orders/*.docx'))
def serve(root):
  h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=root); h.log_message=lambda *a:None
  s=http.server.ThreadingHTTPServer(("127.0.0.1",0),h); threading.Thread(target=s.serve_forever,daemon=True).start();return s.server_address[1]
po,pn=serve(OLD),serve(NEW)
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d)[:300] if d else ''))
PH='Очень-длинная-фраза-2026!'
async def make(p,port,out):
  b=await p.chromium.launch();ctx=await b.new_context(accept_downloads=True);pg=await ctx.new_page()
  await pg.goto(f"http://127.0.0.1:{port}/index.html");await login(pg)
  await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()");await pg.wait_for_timeout(400)
  await pg.set_input_files("#wordFiles",files);await pg.wait_for_timeout(1500)
  await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
  while await pg.evaluate("()=>VypiskiImportJob.state().running"):await pg.wait_for_timeout(300)
  async with pg.expect_download() as d:await pg.evaluate(f"()=>VypiskiBackup.create('full','{PH}')")
  dl=await d.value;await dl.save_as(out);n=await pg.evaluate("()=>VypiskiRepository.count('orders')");await b.close();return n
async def restore(p,port,path):
  b=await p.chromium.launch();pg=await b.new_page();errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
  await pg.goto(f"http://127.0.0.1:{port}/index.html");await login(pg)
  r=await pg.evaluate("""async([b64,ph])=>{const u=Uint8Array.from(atob(b64),c=>c.charCodeAt(0));const f=new File([u],'x.vypbak');try{const o=await VypiskiBackup.open(f,ph);const pl=await VypiskiBackup.plan(o.payload);const n=await VypiskiBackup.apply(pl);return {ok:true,n,orders:await VypiskiRepository.count('orders'),sections:Object.keys(o.payload.data)}}catch(e){return {ok:false,e:e.message}}}""",[__import__('base64').b64encode(open(path,'rb').read()).decode(),PH])
  await b.close();return r,errs
async def main():
  async with async_playwright() as p:
    n_old=await make(p,po,'/tmp/vypiski-test-out/old.vypbak');n_new=await make(p,pn,'/tmp/vypiski-test-out/new.vypbak')
    r,e=await restore(p,pn,'/tmp/vypiski-test-out/old.vypbak');check('Копия прежней версии восстанавливается новой',r.get('ok') and r['orders']==n_old,r)
    r,e=await restore(p,pn,'/tmp/vypiski-test-out/new.vypbak');check('Новая потоковая копия проходит проверку целостности и восстанавливается',r.get('ok') and r['orders']==n_new,r)
    check('В новой копии есть согласование выписок','vypiski-workflow-v1/approvals' in r.get('sections',[]),r.get('sections'))
    r,e=await restore(p,po,'/tmp/vypiski-test-out/new.vypbak');check('Новая копия читается и прежней версией',r.get('ok') and r['orders']==n_new,r)
  print(f"{sum(res)}/{len(res)}")
asyncio.run(main())
