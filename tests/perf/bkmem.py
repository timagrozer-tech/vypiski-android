import sys as _s;_s.path.insert(0,"/home/claude/work");from authhelp import login
import asyncio, sys, http.server, threading, functools, time
from playwright.async_api import async_playwright
ROOT=sys.argv[1];STATE=sys.argv[2];ORIG=sys.argv[3]=='1'
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",8799),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
async def main():
  async with async_playwright() as p:
    ctx=await p.chromium.launch_persistent_context(STATE,viewport={"width":390,"height":844},accept_downloads=True,args=['--enable-precise-memory-info']);pg=ctx.pages[0]
    await pg.goto("http://127.0.0.1:8799/index.html");await login(pg);await pg.wait_for_timeout(1500)
    print(await pg.evaluate("async()=>{const e=await navigator.storage.estimate();return {usageMB:Math.round(e.usage/1048576),orders:await VypiskiRepository.count('orders')}}"))
    await pg.evaluate("()=>{window.__peak=0;window.__iv=setInterval(()=>{const u=performance.memory.usedJSHeapSize;if(u>__peak)__peak=u},50)}")
    t=time.time()
    async with pg.expect_download(timeout=600000) as d:
      r=await pg.evaluate(f"async()=>{{try{{const x=await VypiskiBackup.create('full','Очень-длинная-фраза-2026!',{{originals:{str(ORIG).lower()}}});return x.bytes}}catch(e){{return 'ERR '+e.message}}}}")
    dl=await d.value;print('backup bytes',r,'time',round(time.time()-t,1),'s','peak heap MB',await pg.evaluate("()=>Math.round(__peak/1048576)"))
    await ctx.close()
asyncio.run(main())
