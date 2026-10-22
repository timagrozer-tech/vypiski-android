import sys as _s;_s.path.insert(0,"/home/claude/work");from authhelp import login,PASSWORD
import asyncio, sys, http.server, threading, functools, time, json, random
from playwright.async_api import async_playwright
ROOT=sys.argv[1];STATE=sys.argv[2];CYCLES=int(sys.argv[3]);RATE=float(sys.argv[4]) if len(sys.argv)>4 else 2
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",8799),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
random.seed(7)
import os
PER=os.environ.get('PER')=='1'
NAMES=None
async def main():
  global NAMES
  async with async_playwright() as p:
    ctx=await p.chromium.launch_persistent_context(STATE,viewport={"width":390,"height":844},accept_downloads=True,args=['--enable-precise-memory-info','--js-flags=--expose-gc']);pg=ctx.pages[0]
    await ctx.add_init_script(path='/tmp/claude-0/soak/inventory.js')
    async def WS(sel,timeout=30000,state='visible'):
      await pg.locator(sel).first.wait_for(state=state,timeout=timeout)
    errs=[];pg.on("pageerror",lambda e:errs.append('pageerror: '+str(e)[:300]));pg.on("console",lambda m:errs.append('console.'+m.type+': '+m.text[:300]) if m.type=='error' else None)
    await pg.goto("http://127.0.0.1:8799/index.html");await login(pg);await pg.wait_for_timeout(1500)
    cdp=await ctx.new_cdp_session(pg);await cdp.send('Emulation.setCPUThrottlingRate',{'rate':RATE});await cdp.send('Performance.enable')
    async def metrics():
      await cdp.send('HeapProfiler.collectGarbage');await pg.wait_for_timeout(300)
      m={x['name']:x['value'] for x in (await cdp.send('Performance.getMetrics'))['metrics']}
      return {k:round(m[k]/1048576,1) if 'Heap' in k else int(m[k]) for k in ['JSHeapUsedSize','Nodes','JSEventListeners','Documents','Frames','LayoutObjects']}
    NAMES=await pg.evaluate("async()=>{const r=await VypiskiRegisterView.scan(()=>false);return []}")
    NAMES=await pg.evaluate("async()=>{const out=new Set();await VypiskiRegisterView.scan(r=>{if(out.size<300&&r.full&&/\\s/.test(r.full))out.add(r.full);return false});return [...out]}")
    print('names',len(NAMES),flush=True)
    base=await metrics();print('start',base,flush=True);hist=[]
    async def step(name,coro):
      t=time.time()
      try:await asyncio.wait_for(coro,180)
      except Exception as e:errs.append(f'STEP {name} failed: {type(e).__name__} {str(e)[:200]}')
      dt=round(time.time()-t,1)
      if PER:m=await metrics();print(f'   after {name}: heap {m["JSHeapUsedSize"]} nodes {m["Nodes"]} listeners {m["JSEventListeners"]} docs {m["Documents"]}',flush=True)
      return dt
    for c in range(CYCLES):
      T={}
      T['home']=await step('home',pg.evaluate("()=>VypiskiShell.go('home')"))
      async def orders():
        await pg.evaluate("()=>VypiskiShell.go('orders')");await pg.wait_for_timeout(800)
        for i in range(4):await pg.mouse.wheel(0,1200);await pg.wait_for_timeout(120)
        o=pg.locator('#content .card, #content [data-order-id], #content .ord').first
        if await o.count():
          try:await o.click(timeout=3000);await pg.wait_for_timeout(800)
          except Exception:pass
      T['orders']=await step('orders',orders())
      async def text():
        await pg.evaluate("()=>{localStorage.setItem('vypiski.sx.mode2',JSON.stringify('text'))}");await pg.evaluate("()=>VypiskiShell.go('search')");await WS('.sx-mode')
        if not await pg.locator('#dfs .df-q').count():await pg.click(".sx-mode button:has-text('Текст приказов')")
        await WS('#dfs .df-q')
        q=random.choice(NAMES) if c%2==0 else random.choice(['отпуск','командировка','"основной отпуск"','поощрение','приказ'])
        await pg.fill('#dfs .df-q',q);await pg.click('#dfs .df-go');await pg.wait_for_function("()=>/Результатов|ничего не найдено/i.test(document.querySelector('#dfs').textContent)",timeout=120000)
        for i in range(3):await pg.mouse.wheel(0,1500);await pg.wait_for_timeout(100)
      T['text']=await step('text',text())
      async def ev():
        await pg.evaluate(f"()=>VypiskiSearchEvents.open({json.dumps(random.choice(NAMES))})");await pg.wait_for_function("()=>{const s=document.querySelector('#sx');return !!(s&&(s.querySelector('.sx-person')||/ничего не найдено/.test(s.textContent)))}",timeout=120000)
      T['events']=await step('events',ev())
      async def wiz():
        await pg.evaluate(f"async()=>{{const n={json.dumps(random.choice(NAMES))};const rows=await VypiskiRegisterView.scan(r=>r.full===n);const r=rows[0];if(r)VypiskiWizard.open({{orderId:r.orderId,query:n,mode:'fio'}})}}");await WS('#wz',timeout=30000);await pg.wait_for_timeout(1000)
        for i in range(7):
          b=pg.locator("#wz button:has-text('Далее')")
          if await b.count() and await b.first.is_enabled():await b.first.click();await pg.wait_for_timeout(500)
          else:break
      T['wizard']=await step('wizard',wiz())
      async def bulk():
        await pg.evaluate("()=>{localStorage.setItem('vypiski.bulk.f',JSON.stringify({cats:['Отпуск'],years:['"+str(2016+c%3)+"'],months:[],by:'event',part:50}))}")
        await pg.evaluate("()=>VypiskiBulk.open()");await WS('#bk .bk-row:not([hidden])')
        await pg.click("#bk button:has-text('Найти события')");await WS('#bk .bk-job, #bk .bk-card .bk-note:has-text(\"Ничего не найдено\")',timeout=120000)
        if c%4==3 and await pg.locator('#bk .bk-job').count():
          await pg.click("#bk button:has-text('Снять всех')")
          boxes=pg.locator('#bk .bk-job input')
          for i in range(min(15,await boxes.count())):await boxes.nth(i).check()
          await pg.click("#bk button:has-text('Подготовить выписки')");await WS("#bk .bk-conf:not([hidden])",timeout=120000)
          await pg.check('#bk .bk-conf input')
          async with pg.expect_download(timeout=120000) as d:await pg.click("#bk button:has-text('Создать и скачать ZIP')")
          await (await d.value).delete()
          await WS('#bk .bk-res',timeout=60000)
      T['bulk']=await step('bulk',bulk())
      async def misc():
        for js in ["VypiskiShell.go('ready')","VypiskiShell.go('templates')","VypiskiShell.go('backup')","VypiskiShell.go('diag')","VypiskiWorkflow.queuePage()","VypiskiWorkflow.journalPage()","VypiskiRegisterView.open()"]:
          await pg.evaluate(f"()=>{js}");await pg.wait_for_timeout(700)
        await pg.evaluate("()=>document.querySelector(`#appNav [data-sec=settings], #appMore [data-sec=settings]`).click()");await pg.wait_for_timeout(600)
        await pg.evaluate("()=>VypiskiWorkflow.openNotes()");await pg.wait_for_timeout(300);await pg.evaluate("()=>document.getElementById('wfNotes')?.remove()")
        if c%5==4:
          await pg.evaluate("()=>VypiskiAuth.lock()");await WS('#vAuth input');await pg.locator('#vAuth input').last.fill(PASSWORD);await pg.click('#vAuth button[type=submit]');await WS('#vAuth',state='detached')
      T['misc']=await step('misc',misc())
      m=await metrics();inv=await pg.evaluate("()=>({mo:[...__inv.mo.values()].reduce((a,b)=>a+b,0),iv:[...__inv.iv.values()].reduce((a,b)=>a+b,0),ls:[...__inv.ls.values()].reduce((a,b)=>a+b,0)})")
      hist.append(m);print(f"cycle {c+1}: {m} inv {inv} times {T} errs {len(errs)}",flush=True)
    if os.environ.get('SNAP'):
      chunks=[];cdp.on('HeapProfiler.addHeapSnapshotChunk',lambda e:chunks.append(e['chunk']))
      await cdp.send('HeapProfiler.takeHeapSnapshot',{'reportProgress':False});open(os.environ['SNAP'],'w').write(''.join(chunks));print('snapshot saved',flush=True)
    json.dump({'hist':hist,'errs':errs},open('/tmp/claude-0/soak/result.json','w'),ensure_ascii=False)
    inv=await pg.evaluate("()=>({mo:[...__inv.mo.entries()].sort((a,b)=>b[1]-a[1]).slice(0,25),iv:[...__inv.iv.entries()].sort((a,b)=>b[1]-a[1]).slice(0,15),ls:[...__inv.ls.entries()].sort((a,b)=>b[1]-a[1]).slice(0,25)})")
    json.dump(inv,open('/tmp/claude-0/soak/inv.json','w'),ensure_ascii=False,indent=1)
    print('ERRORS:');[print(' ',e) for e in sorted(set(errs))[:60]]
    await ctx.close()
asyncio.run(main())
