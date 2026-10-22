"""12.77.12: switching sections quickly (including old sections from «Диагностика») on a slowed-down processor gives no errors. Args: ASSETS [CPU_SLOWDOWN]."""
import os, sys as _s;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
FIX=os.environ.get("VYP_FIX","fixtures-out")
import asyncio, http.server, threading, functools, sys, glob, itertools, random
from playwright.async_api import async_playwright
ROOT=sys.argv[1];RATE=int(sys.argv[2]) if len(sys.argv)>2 else 6
files=sorted(glob.glob(FIX+'/xlcases/*.docx'))+sorted(glob.glob(FIX+'/orders/*.docx'))+glob.glob(FIX+'/wfcases/Приказ_158_2025.docx')
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
NEW={'home':"VypiskiShell.go('home')",'orders':"VypiskiShell.go('orders')",'search':"VypiskiShell.go('search')",'events':"VypiskiSearchEvents.open('Иванов')",'excel':"VypiskiRegisterView.open()",
 'create':"VypiskiShell.go('create')",'bulk':"VypiskiBulk.open()",'ready':"VypiskiShell.go('ready')",'templates':"VypiskiShell.go('templates')",'backup':"VypiskiShell.go('backup')",
 'diag':"VypiskiShell.go('diag')",'queue':"VypiskiWorkflow.queuePage()",'journal':"VypiskiWorkflow.journalPage()",'import':"VypiskiShell.go('import')",
 'settings':"document.querySelector(`#appNav [data-sec=settings], #appMore [data-sec=settings]`).click()"}
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();pg=await b.new_page(viewport={"width":417,"height":940},has_touch=True)
    await pg.add_init_script("addEventListener('unhandledrejection',e=>console.error('UNHANDLED '+(e.reason&&(e.reason.stack||e.reason.message)||e.reason)))")
    errs=[];cur={'k':''};pg.on("console",lambda m:errs.append((cur['k'],m.text[:260])) if m.type=='error' else None);pg.on("pageerror",lambda e:errs.append((cur['k'],'pageerror '+str(e)[:260])))
    pg.on("dialog",lambda d:asyncio.ensure_future(d.dismiss()))
    await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await login(pg)
    await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()");await pg.wait_for_timeout(400)
    await pg.set_input_files("#wordFiles",files);await pg.wait_for_timeout(1500)
    await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
    while await pg.evaluate("()=>VypiskiImportJob.state().running"):await pg.wait_for_timeout(300)
    await pg.evaluate("async()=>{await VypiskiShell.go('diag')}");await pg.wait_for_timeout(800)
    tabs=await pg.evaluate("()=>[...document.querySelectorAll('[data-tab]')].map(b=>b.dataset.tab)")
    LEG={'legacy:'+t:f"(VypiskiShell.go('diag'),new Promise(r=>setTimeout(r,400))).then(()=>{{const b=document.querySelector('[data-tab=\"{t}\"]');b&&b.click()}})" for t in tabs}
    cdp=await pg.context.new_cdp_session(pg);await cdp.send('Emulation.setCPUThrottlingRate',{'rate':RATE})
    keys=list(NEW)
    pairs=[(a,c) for a in keys+list(LEG) for c in keys if a!=c]
    random.seed(3);random.shuffle(pairs)
    for a,c in pairs:
      cur['k']=f'{a} -> {c}'
      ja=NEW.get(a) or LEG[a];jc=NEW[c]
      d=random.choice([5,30,120])
      try:await pg.evaluate(f"async()=>{{try{{const p=Promise.resolve().then(()=>{ja});await new Promise(r=>setTimeout(r,{d+ (450 if a.startswith('legacy') else 0)}));await Promise.resolve().then(()=>{jc});await new Promise(r=>setTimeout(r,700));}}catch(e){{console.error('THROWN '+e.message)}}}}")
      except Exception as e:errs.append((cur['k'],'evaluate '+str(e)[:200]))
      await pg.keyboard.press('Escape');await pg.evaluate("()=>{document.getElementById('wfNotes')?.remove()}")
    seen=set();print('pairs',len(pairs))
    for k,e in errs:
      sig=e.split('\n')[0][:120]
      if sig in seen:continue
      seen.add(sig);print(k,'|',e.replace('\n',' / ')[:400])
    print('unique errors',len(seen));print(f"{1 if not seen else 0}/1")
    await b.close()
asyncio.run(main())
