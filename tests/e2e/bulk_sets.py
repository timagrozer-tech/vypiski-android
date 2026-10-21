"""12.77.7: bulk creation — frequent kinds first, «Ещё», saved sets of filters."""
import os, sys as _s;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
FIX=os.environ.get("VYP_FIX","fixtures-out")
os.makedirs('/tmp/vypiski-test-out',exist_ok=True)
import asyncio, sys, http.server, threading, functools
from playwright.async_api import async_playwright
ROOT=sys.argv[1]
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d)[:200] if d else ''))
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();pg=await b.new_page(viewport={"width":390,"height":844});errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
    await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await login(pg)
    await pg.evaluate("()=>{localStorage.removeItem('vypiski.bulk.f');localStorage.removeItem('vypiski.bulk.sets')}")
    await pg.evaluate("()=>VypiskiBulk.open()");await pg.wait_for_selector('#bk')
    n=await pg.locator('#bk .bk-row:has(.bk-more) button.bk-chip:not(.bk-more)').count()
    t=await pg.inner_text('#bk');check('Видов событий показано немного',n<=8,n);check('Есть «Ещё…»','Ещё' in t)
    await pg.click("#bk button.bk-more");n2=await pg.locator('#bk .bk-row:has(.bk-more) button.bk-chip:not(.bk-more)').count();check('«Ещё» раскрывает все',n2>n,(n,n2))
    await pg.click("#bk button.bk-more")
    check('Выбранный вид виден в свёрнутом списке',await pg.locator("#bk button.bk-chip.on:has-text('Отпуск')").count()==1)
    check('Действие и месяцы спрятаны',not await pg.evaluate("()=>document.querySelector('#bk details.bk-more-f').open"))
    await pg.click("#bk button:has-text('Сохранить набор')");v=await pg.input_value('#bk .bk-save input');check('Название набора по умолчанию',v.startswith('Отпуск'),v)
    await pg.fill('#bk .bk-save input','Отпуска тест');await pg.click("#bk .bk-save button:has-text('Сохранить')")
    await pg.click("#bk button.bk-chip.on:has-text('Отпуск')");check('Снятие вида',await pg.locator("#bk button.bk-chip.on:has-text('Отпуск')").count()==0)
    check('Набор появился',await pg.locator("#bk .bk-sets button:has-text('Отпуска тест')").count()==1)
    await pg.click("#bk .bk-sets button:has-text('Отпуска тест')");await pg.wait_for_timeout(300)
    check('Набор применяется',await pg.locator("#bk button.bk-chip.on:has-text('Отпуск')").count()==1)
    await pg.screenshot(path='/tmp/vypiski-test-out/bk-sets.png')
    await pg.click("#bk .bk-x");check('Набор удаляется',await pg.locator("#bk .bk-sets button").count()==0)
    check('Без ошибок',not errs,errs);await b.close()
  print(f"{sum(res)}/{len(res)}")
asyncio.run(main())
