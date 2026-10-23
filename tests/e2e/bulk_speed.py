"""12.79.17: speed of bulk preparation on a long order (300 points, synthetic): each excerpt is prepared in well under a second."""
import sys as _s,os;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
import asyncio,sys,glob,http.server,threading,functools,subprocess
from playwright.async_api import async_playwright
ROOT=sys.argv[1];FIX='/tmp/vypiski-test-out/bigcase'
_h=os.path.dirname(os.path.abspath(__file__));_mk=[p for p in [os.path.join(_h,'mkbig.py'),os.path.join(_h,'..','fixtures','big','mkbig.py')] if os.path.exists(p)][0]
subprocess.run([sys.executable,_mk,FIX],check=True)
h=functools.partial(http.server.SimpleHTTPRequestHandler,directory=ROOT);h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h);threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d) if d else ''))
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();pg=await b.new_page();errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
    await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await pg.wait_for_timeout(1500);await login(pg)
    await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()");await pg.wait_for_timeout(500)
    await pg.set_input_files("#wordFiles",sorted(glob.glob(FIX+'/*.docx')));await pg.wait_for_timeout(1500)
    await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
    for _ in range(200):
      await pg.wait_for_timeout(300)
      if not await pg.evaluate("()=>VypiskiImportJob.state().running"):break
    r=await pg.evaluate("""async()=>{const rows=(await VypiskiRegisterView.scan(()=>true)).filter(r=>!r.mention);const pick=rows.filter((_,i)=>i%8===0).slice(0,40);const t=performance.now();let ok=0,bad=[];
      for(const r of pick){const a=await VypiskiBulk.prepareJob(VypiskiBulk.group([r])[0]);if(!window._f){window._f=a.fragments.map(f=>[f.role,String(f.text).slice(0,60)])}if(a.ok&&a.fragments.length===4&&!(a.others||[]).length)ok++;else bad.push([r.ref,a.why,a.fragments?.length])}
      return {f:window._f,rows:rows.length,n:pick.length,ok,bad:bad.slice(0,3),ms:(performance.now()-t)/pick.length}}""")
    check('Таблица событий: 300 человек',r['rows']==300,r['rows'])
    check('Выписки собраны верно (§, пункт, основание; без чужих)',r['ok']==r['n'],r)
    check('Подготовка одной выписки быстрее 150 мс',r['ms']<150,round(r['ms']))
    check('Без ошибок',not errs,errs[:3])
    await b.close()
  print(f'{sum(res)}/{len(res)} passed');sys.exit(0 if all(res) else 1)
asyncio.run(main())
