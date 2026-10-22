"""12.79.2: parsing in a worker with a time limit — a slow order is skipped, listed in Diagnostics and can be retried; normal orders parse the same."""
import os, sys as _s;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
FIX=os.environ.get("VYP_FIX","fixtures-out")
import asyncio, sys, http.server, threading, functools, glob
from playwright.async_api import async_playwright
ROOT=sys.argv[1]
files=sorted(glob.glob(FIX+'/wfcases/*.docx'))[:6]
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d)[:300] if d else ''))
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();ctx=await b.new_context(viewport={"width":417,"height":900});pg=await ctx.new_page();errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
    await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await login(pg)
    await pg.evaluate("()=>localStorage.setItem('vypiski.xl.preparse','off')")
    await pg.evaluate("()=>VypiskiShell.go('import')");await pg.wait_for_timeout(500)
    await pg.set_input_files("#wordFiles",files);await pg.wait_for_timeout(1500)
    await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
    while await pg.evaluate("()=>VypiskiImportJob.state().running"):await pg.wait_for_timeout(300)
    # same rows as the page parser
    eq=await pg.evaluate("""async()=>{const os=await VypiskiRepository.all('orders');let same=0,diff=0;for(const o of os){const a=JSON.stringify(VypiskiRegister.extract(o)),b=JSON.stringify(await VypiskiRegisterView.extract(o));a===b?same++:diff++}return [same,diff]}""")
    check('Разбор в фоновом потоке даёт те же строки',eq[1]==0 and eq[0]>=6,eq)
    # a slow order: time limit 1 ms for the big ones
    await pg.evaluate("()=>{window.__vypXlLimit=1}")
    n=await pg.evaluate("async()=>(await VypiskiRegisterView.scan(()=>true)).length")
    sl=await pg.evaluate("()=>VypiskiRegisterView.slowList()")
    check('Долгий разбор остановлен, приказы в списке пропущенных',len(sl)>=1 and all('дольше' in x['why'] for x in sl),sl)
    await pg.evaluate("()=>{delete window.__vypXlLimit}")
    await pg.evaluate("()=>VypiskiShell.go('diag')");await pg.wait_for_timeout(1500)
    t=await pg.inner_text('#content');check('Список в Диагностике','Не разобраны для Excel' in t,t[:100])
    await pg.click("#content button:has-text('Попробовать разобрать снова')");await pg.wait_for_timeout(300)
    n2=await pg.evaluate("async()=>(await VypiskiRegisterView.scan(()=>true)).length")
    check('После «Попробовать снова» разобраны все',n2>n and not await pg.evaluate("()=>VypiskiRegisterView.slowList().length"),[n,n2])
    # a page crash in the middle of parsing: the marker makes the next start skip that order
    oid=await pg.evaluate("async()=>(await VypiskiRepository.all('orders'))[0].id")
    await pg.evaluate("id=>localStorage.setItem('vypiski.xl.parsing',JSON.stringify({id,sha:'zz',n:'77',d:'2025-01-01',kb:1,np:1}))",oid)
    await pg.reload();await login(pg);await pg.wait_for_timeout(2500)
    sk=await pg.evaluate("()=>JSON.parse(localStorage.getItem('vypiski.xl.skip')||'{}')");check('После сбоя приказ пропускается',oid in sk,sk)
    check('Без ошибок',not errs,errs);await b.close()
  print(f"{sum(res)}/{len(res)}")
asyncio.run(main())
