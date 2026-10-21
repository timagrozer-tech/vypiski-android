"""12.77.7: orders are parsed after import in idle slices; people search works without the full table; performance check in Diagnostics."""
import os, sys as _s;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
FIX=os.environ.get("VYP_FIX","fixtures-out")
os.makedirs('/tmp/vypiski-test-out',exist_ok=True)
import asyncio, sys, glob, http.server, threading, functools
from playwright.async_api import async_playwright
ROOT=sys.argv[1];FX=FIX+'/'
files=sorted(glob.glob(FX+'xlcases/*.docx'))+[FX+'wfcases/Приказ_158_2025.docx',FX+'wfcases/Приказ_325_2025.docx']
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d)[:300] if d else ''))
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();pg=await b.new_page(viewport={"width":390,"height":844});errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
    await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await login(pg)
    await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()");await pg.wait_for_timeout(400)
    await pg.set_input_files("#wordFiles",files);await pg.wait_for_timeout(1500)
    await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
    while await pg.evaluate("()=>VypiskiImportJob.state().running"):await pg.wait_for_timeout(300)
    while await pg.evaluate("()=>VypiskiCore.waiting?.()||0"):await pg.wait_for_timeout(300)
    n0=await pg.evaluate("()=>VypiskiPreparse.pending()");check('После загрузки приказы в очереди разбора',n0>0,n0)
    for i in range(40):
      if await pg.evaluate("()=>VypiskiPreparse.pending()")==0 and not await pg.evaluate("()=>VypiskiPreparse.running()"):break
      await pg.wait_for_timeout(500)
    cov=await pg.evaluate("()=>VypiskiRegisterView.coverage()");check('Очередь разобрана в покое: все приказы разобраны',cov['parsed']==cov['orders'],cov)
    check('Таблица при этом не строилась',not await pg.evaluate("()=>VypiskiRegisterView.builtAt()"))
    # settings
    await pg.evaluate("()=>document.querySelector(`#appNav [data-sec=settings], #appMore [data-sec=settings]`).click()");await pg.wait_for_timeout(1000)
    t=await pg.inner_text('#content');check('Настройки: переключатель разбора',  'Разбирать приказы на людей и события' in t)
    check('Настройки: показано, сколько разобрано',f"Разобрано приказов: {cov['orders']} из {cov['orders']}" in t, t[t.find('Разобрано'):][:80])
    await pg.screenshot(path='/tmp/vypiski-test-out/prep-settings.png',full_page=True)
    # direct search without table
    await pg.evaluate("()=>VypiskiSearchEvents.open('Елисеев Дмитрий')");await pg.wait_for_selector('#sx .sx-ev input',timeout=20000)
    t=await pg.inner_text('#sx');check('Поиск человека без таблицы: найден',('Елисеев Дмитрий' in t) and 'Елисеев Роман' not in t,t[:200])
    check('…и общая таблица не построена',not await pg.evaluate("()=>VypiskiRegisterView.builtAt()"))
    await pg.screenshot(path='/tmp/vypiski-test-out/prep-direct.png')
    await pg.fill('#sx .sx-q','СУ-067677');await pg.click('#sx .sx-find');await pg.wait_for_timeout(1500);t=await pg.inner_text('#sx')
    check('Поиск по личному номеру без таблицы',('Елисеев Дмитрий' in t),t[:200])
    await pg.fill('#sx .sx-q','Несуществующий Человек');await pg.click('#sx .sx-find');await pg.wait_for_timeout(1500);t=await pg.inner_text('#sx')
    check('Нет результата — понятное сообщение','ничего не найдено' in t,t[:200])
    await pg.locator('#sx .sx-ev input').count()
    # empty query
    await pg.evaluate("()=>VypiskiSearchEvents.open('')");await pg.wait_for_timeout(600);t=await pg.inner_text('#sx')
    check('Пустой запрос: подсказка и кнопка подготовки','Найти' in t and 'Подготовить таблицу для всей базы' in t,t[:200])
    await pg.click("#sx button:has-text('Подготовить таблицу для всей базы')");await pg.wait_for_selector('#sx .sx-person',timeout=30000)
    check('Подготовка таблицы по кнопке работает',await pg.evaluate("()=>!!VypiskiRegisterView.builtAt()"))
    # diag
    await pg.evaluate("()=>VypiskiShell.go('diag')");await pg.wait_for_selector('#ppDiag');await pg.click("#ppDiag button:has-text('Запустить замер')")
    await pg.wait_for_selector("#ppDiag button:has-text('Повторить замер')",timeout=60000);t=await pg.inner_text('#ppDiag .pp-out')
    print(t)
    check('Замер: есть разбор и процессор','Разбор на людей и события' in t and 'Процессор' in t)
    check('Замер без ФИО','Елисеев' not in t and 'Иванов' not in t)
    await pg.screenshot(path='/tmp/vypiski-test-out/prep-diag.png',full_page=True)
    check('Без ошибок страницы',not errs,errs)
    await b.close()
  print(f"{sum(res)}/{len(res)}")
asyncio.run(main())
