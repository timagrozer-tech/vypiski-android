import sys as _s;_s.path.insert(0,"/home/claude/work");from authhelp import login
import asyncio, sys, http.server, threading, functools
from playwright.async_api import async_playwright
ROOT=sys.argv[1];STATE=sys.argv[2];WHAT=sys.argv[3]
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",8799),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
JS={
'text':"""async()=>{const w=(f,t=60000)=>new Promise((res,rej)=>{const t0=Date.now();const k=()=>{if(f())return res();if(Date.now()-t0>t)return rej('timeout');setTimeout(k,100)};k()});
 VypiskiShell.go('search');await w(()=>document.querySelector('.sx-mode'));if(!document.querySelector('#dfs .df-q'))[...document.querySelectorAll('.sx-mode button')].find(b=>/Текст/.test(b.textContent)).click();
 await w(()=>document.querySelector('#dfs .df-q'));const q=document.querySelector('#dfs .df-q');q.value='отпуск';q.dispatchEvent(new Event('input'));document.querySelector('#dfs .df-go').click();
 await w(()=>/Результатов/.test(document.querySelector('#dfs')?.textContent||''));VypiskiShell.go('home');await new Promise(r=>setTimeout(r,300))}""",
'bulk':"""async()=>{const w=(f,t=120000)=>new Promise((res,rej)=>{const t0=Date.now();const k=()=>{if(f())return res();if(Date.now()-t0>t)return rej('timeout');setTimeout(k,100)};k()});
 localStorage.setItem('vypiski.bulk.f',JSON.stringify({cats:['Отпуск'],years:['2017'],months:[],by:'event',part:50}));VypiskiBulk.open();await w(()=>document.querySelector('#bk .bk-row:not([hidden])'));
 [...document.querySelectorAll('#bk button')].find(b=>/Найти события/.test(b.textContent)).click();await w(()=>document.querySelector('#bk .bk-job'));VypiskiShell.go('home');await new Promise(r=>setTimeout(r,300))}""",
'orderdoc':"""async()=>{const w=(f,t=60000)=>new Promise((res,rej)=>{const t0=Date.now();const k=()=>{if(f())return res();if(Date.now()-t0>t)return rej('timeout');setTimeout(k,100)};k()});
 VypiskiShell.go('orders');await new Promise(r=>setTimeout(r,800));const c=document.querySelector('#content .card, #content [data-order-id], #content .ord, #content .u2-ord, #content button.df-card');if(c)c.click();await new Promise(r=>setTimeout(r,900));VypiskiShell.go('home');await new Promise(r=>setTimeout(r,300))}""",
'events':"""async()=>{const w=(f,t=60000)=>new Promise((res,rej)=>{const t0=Date.now();const k=()=>{if(f())return res();if(Date.now()-t0>t)return rej('timeout');setTimeout(k,100)};k()});
 VypiskiSearchEvents.open('Иванов Иван');await w(()=>{const s=document.querySelector('#sx');return s&&(s.querySelector('.sx-person')||/ничего не найдено/.test(s.textContent))});VypiskiShell.go('home');await new Promise(r=>setTimeout(r,300))}""",
}
async def main():
  async with async_playwright() as p:
    ctx=await p.chromium.launch_persistent_context(STATE,viewport={"width":390,"height":844});pg=ctx.pages[0]
    await pg.goto("http://127.0.0.1:8799/index.html");await login(pg);await pg.wait_for_timeout(1500)
    cdp=await ctx.new_cdp_session(pg);await cdp.send('Performance.enable')
    async def m(label):
      await cdp.send('HeapProfiler.collectGarbage');await pg.wait_for_timeout(300)
      x={k['name']:k['value'] for k in (await cdp.send('Performance.getMetrics'))['metrics']}
      print(f"{label:12s} heap {x['JSHeapUsedSize']/1048576:6.2f} nodes {int(x['Nodes']):6d} listeners {int(x['JSEventListeners']):5d}",flush=True)
    await m('start')
    for i in range(5):
      await pg.evaluate(JS[WHAT]);await m(f"{WHAT} {i}")
    await ctx.close()
asyncio.run(main())
