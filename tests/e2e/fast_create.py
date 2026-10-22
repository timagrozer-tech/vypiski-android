"""12.78: excerpt in one screen — «Проверить и сохранить», one confirmation, «Скачать DOCX»; logo in the header; no order list in step 1."""
import os, sys as _s;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
FIX=os.environ.get("VYP_FIX","fixtures-out")
os.makedirs('/tmp/vypiski-test-out',exist_ok=True)
import asyncio, sys, glob, http.server, threading, functools
from playwright.async_api import async_playwright
ROOT=sys.argv[1];FX=FIX+'/'
files=[FX+'wfcases/Приказ_158_2025.docx',FX+'wfcases/Приказ_325_2025.docx']
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d)[:250] if d else ''))
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();ctx=await b.new_context(viewport={"width":417,"height":900},accept_downloads=True);pg=await ctx.new_page();errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
    await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await login(pg)
    check('Логотип в шапке',await pg.locator('header h2 .u3-logo').count()==1)
    await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()");await pg.wait_for_timeout(400)
    await pg.set_input_files("#wordFiles",files);await pg.wait_for_timeout(1500)
    await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
    while await pg.evaluate("()=>VypiskiImportJob.state().running"):await pg.wait_for_timeout(300)
    await pg.evaluate("()=>VypiskiShell.go('create')");await pg.wait_for_timeout(800)
    t=await pg.inner_text('#wz');check('Шаг 1 без списка приказов',await pg.locator('#wz .wz-ord').count()==0 and 'Найти в поиске' in t,t[:150])
    oid=await pg.evaluate("async()=>(await VypiskiCore.allMeta()).find(m=>m.number==='158').orderId")
    await pg.evaluate("id=>VypiskiWizard.open({orderId:id,query:'Елисееву Дмитрию',mode:'fio'})",oid);await pg.locator('#wz .wz-p').first.wait_for()
    await pg.screenshot(path='/tmp/vypiski-test-out/fast1.png')
    check('На шаге «Предложение» — «Проверить и сохранить» и ручной выбор',await pg.locator("#wz button:has-text('Проверить и сохранить')").count()==1 and await pg.locator("#wz .wz-man").count()==1)
    await pg.click("#wz button:has-text('Проверить и сохранить')");await pg.locator('#wz .wz-fastsum').wait_for()
    t=await pg.inner_text('#wz');check('Итоговый экран: оформление и проверка',('дословно совпадают' in t) and ('Оформление: шаблон' in t),t[:300])
    dl=pg.locator("#wz button:has-text('Скачать DOCX')");check('Без подтверждения скачать нельзя',await dl.is_disabled())
    await pg.screenshot(path='/tmp/vypiski-test-out/fast2.png',full_page=True)
    await pg.check('#wz .wz-check input');check('После подтверждения можно скачать',not await dl.is_disabled())
    async with pg.expect_download() as d:await dl.click()
    f=await d.value;check('Скачан DOCX одним нажатием',f.suggested_filename.endswith('.docx'),f.suggested_filename)
    await pg.locator('#wz .wz-ok').first.wait_for();n=await pg.evaluate("async()=>(await VypiskiRepository.all('excerpts')).length");check('Сохранена в «Готовых выписках»',n==1,n)
    await pg.screenshot(path='/tmp/vypiski-test-out/fast3.png')
    # «Изменить оформление» path still works
    await pg.evaluate("id=>VypiskiWizard.open({orderId:id,query:'Елисееву Дмитрию',mode:'fio'})",oid);await pg.locator('#wz .wz-p').first.wait_for()
    await pg.click("#wz button:has-text('Проверить и сохранить')");await pg.locator('#wz .wz-fastsum').wait_for()
    await pg.click("#wz .wz-fastsum button:has-text('Изменить оформление')");await pg.wait_for_timeout(600)
    check('«Изменить оформление» открывает шаг «Оформление»','Шапка выписки' in await pg.inner_text('#wz'))
    check('Без ошибок',not errs,errs);await b.close()
  print(f"{sum(res)}/{len(res)}")
asyncio.run(main())
