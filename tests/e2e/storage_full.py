"""12.77.11: a full phone storage gives a plain Russian message instead of a technical error."""
import os, sys as _s;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
FIX=os.environ.get("VYP_FIX","fixtures-out")
os.makedirs('/tmp/vypiski-test-out',exist_ok=True)
import asyncio, sys, glob, http.server, threading, functools
from playwright.async_api import async_playwright
ROOT=sys.argv[1]
files=sorted(glob.glob(FIX+'/xlcases/*.docx'))
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d)[:300] if d else ''))
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();pg=await b.new_page(viewport={"width":390,"height":844});errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
    await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await login(pg)
    await pg.evaluate("""()=>{const P=IDBObjectStore.prototype.put;IDBObjectStore.prototype.put=function(v,k){if(this.name==='orders')throw new DOMException('The current transaction exceeded its quota limitations.','QuotaExceededError');return P.call(this,v,k)}}""")
    await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()");await pg.wait_for_timeout(400)
    await pg.set_input_files("#wordFiles",files);await pg.wait_for_timeout(1500)
    await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
    for i in range(100):
      if not await pg.evaluate("()=>VypiskiImportJob.state().running"):break
      await pg.wait_for_timeout(300)
    t=await pg.inner_text('#content');st=await pg.evaluate("()=>JSON.stringify(VypiskiImportJob.state())")
    print(t[:600]);print(st[:400])
    check('Понятное сообщение о нехватке места',('не хватает места' in t) or ('нехватки места' in t))
    await b.close()
  print(f"{sum(res)}/{len(res)}")
asyncio.run(main())
