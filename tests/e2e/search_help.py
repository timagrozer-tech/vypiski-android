"""12.78: «Как искать» examples all work and can be tapped; «Создать выписку» on a result; «who is it» card; clear button."""
import os, sys as _s;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
FIX=os.environ.get("VYP_FIX","fixtures-out")
os.makedirs('/tmp/vypiski-test-out',exist_ok=True)
import asyncio, sys, glob, http.server, threading, functools, re
from playwright.async_api import async_playwright
ROOT=sys.argv[1]
files=sorted(glob.glob(FIX+'/xlcases/*.docx'))+sorted(glob.glob(FIX+'/orders/*.docx'))+[FIX+'/wfcases/Приказ_158_2025.docx',FIX+'/wfcases/Приказ_325_2025.docx']
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d)[:200] if d else ''))
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();pg=await b.new_page(viewport={"width":417,"height":900});errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
    await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await login(pg);await pg.evaluate("()=>localStorage.setItem('vypiski.s3.off','1')") # 12.79.12: the advanced (old) text search
    await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()");await pg.wait_for_timeout(400)
    await pg.set_input_files("#wordFiles",files);await pg.wait_for_timeout(1500)
    await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
    while await pg.evaluate("()=>VypiskiImportJob.state().running"):await pg.wait_for_timeout(300)
    while await pg.evaluate("()=>VypiskiCore.waiting?.()||0"):await pg.wait_for_timeout(300)
    await pg.evaluate("()=>{localStorage.setItem('vypiski.sx.mode2','text')}")
    await pg.evaluate("()=>VypiskiShell.go('search')");await pg.locator('.sx-mode').wait_for()
    if not await pg.locator('#dfs .df-q').count():await pg.click(".sx-mode button:has-text('Текст приказов')")
    await pg.locator('#dfs .df-q').wait_for()
    await pg.click("#dfs button:has-text('Как искать')");n=await pg.locator('#dfs .df-ex').count();check('«Как искать»: примеры показаны',n>=12,n)
    await pg.screenshot(path='/tmp/vypiski-test-out/help.png',full_page=True)
    exs=await pg.evaluate("()=>[...document.querySelectorAll('#dfs .df-ex code')].map(c=>c.textContent)")
    async def cnt(q):
      await pg.fill('#dfs .df-q',q);await pg.click('#dfs .df-go');await pg.wait_for_timeout(1300)
      t=await pg.inner_text('#dfs');m=re.search(r'Результатов: (\d+)',t);return int(m.group(1)) if m else (0 if 'Ничего не найдено' in t else -1)
    base=await cnt('отпуск')
    for q in exs:
      n=await cnt(q)
      ok=n>0 or q in ('имя:Приказ_15','путь:2016','"Иванов отпуск"~5','год:2026','номер:335','тип:doc','елисеева~')
      if q=='отпуск -отзыв':ok=0<n<=base
      check(f'Пример «{q}»',ok and n>=0,n)
    # tap an example
    if not await pg.locator('#dfs .df-ex').first.is_visible():await pg.click("#dfs button:has-text('Как искать')")
    await pg.locator('#dfs .df-ex').first.click();await pg.wait_for_timeout(1300)
    check('Нажатие на пример подставляет и ищет',(await pg.input_value('#dfs .df-q'))==exs[0] and 'Результатов' in await pg.inner_text('#dfs'))
    await pg.screenshot(path='/tmp/vypiski-test-out/search_res.png',full_page=True)
    check('На карточке результата — «Создать выписку»',await pg.locator('#dfs .df-acts button:has-text("Создать выписку")').count()>=1)
    await pg.locator('#dfs .df-acts button:has-text("Создать выписку")').first.click();await pg.locator('#wz').wait_for()
    await pg.wait_for_timeout(800);t=await pg.inner_text('#wz');check('Выписка прямо из результата: пункт найден',('Предложение' in t) and ('Елисеев' in t),t[:200])
    await pg.evaluate("()=>VypiskiShell.go('search')");await pg.locator('#dfs .df-q').wait_for()
    await pg.fill('#dfs .df-q','Елисееву Дмитрию');check('Кнопка очистки появилась',await pg.locator('#dfs .u3-clear').is_visible())
    await pg.click('#dfs .df-go');await pg.locator('#dfs .u3-who').wait_for(timeout=20000);t=await pg.inner_text('#dfs .u3-who')
    check('Карточка человека над результатами','Елисеев Дмитрий Алексеевич' in t and 'событий' in t,t)
    await pg.screenshot(path='/tmp/vypiski-test-out/who.png')
    await pg.click("#dfs .u3-who button:has-text('Выписки по событиям')");await pg.locator('#bk .bk-job').first.wait_for(timeout=20000)
    check('«Выписки по событиям» открывает массовое создание',await pg.locator('#bk .bk-job').count()>=1)
    await pg.evaluate("()=>VypiskiShell.go('search')");await pg.locator('#dfs .df-q').wait_for();await pg.fill('#dfs .df-q','отпуск');await pg.click('#dfs .df-go');await pg.wait_for_timeout(1500)
    check('Для обычных слов карточки человека нет',await pg.locator('#dfs .u3-who').count()==0)
    await pg.click('#dfs .u3-clear');check('Очистка строки',await pg.input_value('#dfs .df-q')=='')
    check('Без ошибок',not errs,errs);await b.close()
  print(f"{sum(res)}/{len(res)}")
asyncio.run(main())
