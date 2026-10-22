import sys as _s;_s.path.insert(0,"/home/claude/work");from authhelp import login
import asyncio, sys, http.server, threading, functools, json
from playwright.async_api import async_playwright
ROOT=sys.argv[1];STATE=sys.argv[2];SEQ=json.loads(sys.argv[3]);REPS=int(sys.argv[4]);SNAP=sys.argv[5] if len(sys.argv)>5 else ''
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",8799),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
async def main():
  async with async_playwright() as p:
    ctx=await p.chromium.launch_persistent_context(STATE,viewport={"width":390,"height":844},args=['--enable-precise-memory-info']);pg=ctx.pages[0]
    await pg.goto("http://127.0.0.1:8799/index.html");await login(pg);await pg.wait_for_timeout(1500)
    cdp=await ctx.new_cdp_session(pg);await cdp.send('Performance.enable')
    async def m(label):
      await cdp.send('HeapProfiler.collectGarbage');await pg.wait_for_timeout(300)
      x={k['name']:k['value'] for k in (await cdp.send('Performance.getMetrics'))['metrics']}
      print(f"{label:30s} heap {x['JSHeapUsedSize']/1048576:6.2f}  nodes {int(x['Nodes']):6d}  listeners {int(x['JSEventListeners']):5d}",flush=True)
    await m('start')
    for rep in range(REPS):
      for js in SEQ:
        await pg.evaluate(f"async()=>{{{js}}}");await pg.wait_for_timeout(1200)
      await m(f"rep {rep}")
    if SNAP:
      chunks=[];cdp.on('HeapProfiler.addHeapSnapshotChunk',lambda e:chunks.append(e['chunk']))
      await cdp.send('HeapProfiler.takeHeapSnapshot',{'reportProgress':False});open(SNAP,'w').write(''.join(chunks));print('snapshot saved')
    await ctx.close()
asyncio.run(main())
