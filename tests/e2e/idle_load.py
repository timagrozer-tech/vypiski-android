"""12.77.3: no screen may keep working by itself (no endless re-render loops). Usage: idle_load.py <page dir> <browser profile dir>"""
import os, sys as _s;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
import asyncio, sys, http.server, threading, functools
from playwright.async_api import async_playwright
ROOT=sys.argv[1];STATE=sys.argv[2]
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
async def main():
  async with async_playwright() as p:
    ctx=await p.chromium.launch_persistent_context(STATE,viewport={"width":390,"height":844});pg=ctx.pages[0]
    await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await login(pg);await pg.wait_for_timeout(2000)
    fails=[];cdp=await ctx.new_cdp_session(pg);await cdp.send('Performance.enable')
    async def busy():return {x['name']:x['value'] for x in (await cdp.send('Performance.getMetrics'))['metrics']}['TaskDuration']
    await pg.evaluate("()=>{window.__m=0;new MutationObserver(r=>{__m+=r.length}).observe(document.body,{subtree:true,childList:true,attributes:true,characterData:true})}")
    for name,js in [('home',"VypiskiShell.go('home')"),('orders',"VypiskiShell.go('orders')"),('import',"VypiskiShell.go('import')"),('search text',"VypiskiShell.go('search')"),('ready',"VypiskiShell.go('ready')"),('templates',"VypiskiShell.go('templates')"),('backup',"VypiskiShell.go('backup')"),('diag',"VypiskiShell.go('diag')"),('settings',"document.querySelector(`#appNav [data-sec=settings], #appMore [data-sec=settings]`).click()"),('excel (by click)',"VypiskiRegisterView.open()"),('people search (built)',"VypiskiSearchEvents.open('')"),('queue',"VypiskiWorkflow.queuePage()"),('journal',"VypiskiWorkflow.journalPage()"),('help',"VypiskiWorkflow.helpPage()"),('home again',"VypiskiShell.go('home')")]:
      await pg.evaluate(f"()=>{{{js}}}");await pg.wait_for_timeout(6000)
      await pg.evaluate("()=>{__m=0}");b0=await busy();await pg.wait_for_timeout(5000);b1=await busy()
      n=await pg.evaluate('()=>__m');ok=n<20 and b1-b0<0.5;fails.append(not ok);print(("PASS " if ok else "FAIL ")+f"{name:24s} idle: mutations {n:6d}  busy {b1-b0:.2f}s/5s")
    await ctx.close();print(f"{fails.count(False)}/{len(fails)}")
asyncio.run(main())
