"""12.79.13: «Текст приказов» by the user's sketch — query, 🔍, filter, «Недавние», years column, table «Заголовок | Имя файла | Тип |
Вид для выписки», kind filter (Зачисление, СОЧ, БВП, отрыв, исключение, СВО); computer: side filters, check boxes, preview pane.
Synthetic orders (mkhist.py), invented names."""
import sys as _s,os;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
import asyncio,sys,glob,http.server,threading,functools,subprocess
from playwright.async_api import async_playwright
ROOT=sys.argv[1];FIX='/tmp/vypiski-test-out/histcases'
_h=os.path.dirname(os.path.abspath(__file__));_mk=[p for p in [os.path.join(_h,'mkhist.py'),os.path.join(_h,'..','fixtures','history','mkhist.py')] if os.path.exists(p)][0]
subprocess.run([sys.executable,_mk,FIX],check=True)
h=functools.partial(http.server.SimpleHTTPRequestHandler,directory=ROOT);h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h);threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[];SH=os.environ.get('SHOTS')
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d) if d else ''))
async def rows(pg):return await pg.evaluate("()=>[...document.querySelectorAll('#s3 tbody tr')].map(r=>[...r.cells].map(c=>c.innerText.trim()))")
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch()
    for W in [390,1500]:
      ctx=await b.new_context(viewport={"width":W,"height":900},locale="ru-RU");pg=await ctx.new_page();errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
      await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await pg.wait_for_timeout(1500);await login(pg)
      await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()");await pg.wait_for_timeout(500)
      await pg.set_input_files("#wordFiles",sorted(glob.glob(FIX+'/*.docx')));await pg.wait_for_timeout(1500)
      await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
      for _ in range(100):
        await pg.wait_for_timeout(300)
        if not await pg.evaluate("()=>VypiskiImportJob.state().running"):break
      await pg.evaluate("()=>{localStorage.setItem('vypiski.sx.mode2','text');VypiskiShell.go('search')}");await pg.wait_for_selector('#s3 .s3-q input')
      check(f'[{W}] Экран по эскизу: режимы, строка, 🔍, фильтр, годы, таблица',await pg.locator('#content .sx-mode button.on:has-text("Текст приказов")').count()==1 and await pg.locator('#s3 .s3-q button').count()==2 and await pg.locator('#s3 .s3-years label').count()>=4 if W<1000 else await pg.locator('#s3 .s3-side label').count()>=8)
      check(f'[{W}] Пустой экран — примеры запросов',await pg.locator('#s3 .s3-ex button').count()>=3)
      await pg.fill('#s3 .s3-q input','СОЛНЦЕВА ИВАНА');await pg.press('#s3 .s3-q input','Enter')
      await pg.wait_for_function("()=>document.querySelectorAll('#s3 tbody tr').length>=4&&!/Поиск…/.test(document.querySelector('#s3 .s3-st').textContent)",timeout=60000)
      R=await rows(pg);heads=await pg.evaluate("()=>[...document.querySelectorAll('#s3 thead th')].filter(t=>getComputedStyle(t).display!=='none').map(t=>t.childNodes[0]?.textContent?.trim()||t.innerText.trim())")
      check(f'[{W}] Колонки таблицы',[h for h in heads if h][:4]==['Заголовок','Имя файла','Тип','Вид для выписки'] if W<1000 else 'Вид для выписки' in heads and any(h.startswith('Дата') for h in heads),heads)
      kinds=[r[5] for r in R]
      check(f'[{W}] «КАРАГИШИЕВА ТИМУРА»-запрос заглавными: найдены события человека',any('Зачисление' in k for k in kinds) and any('Без вести' in k or k=='БВП' for k in kinds) and any('Назначение' in k for k in kinds),kinds)
      check(f'[{W}] Заголовок «Выписка из приказа», имя файла, тип',all(r[1]=='Выписка из приказа' for r in R) and any(r[2].startswith('Приказ 327') for r in R) and all(r[4] in ('docx','doc') for r in R),R[:2])
      check(f'[{W}] «Недавние» с запросом',await pg.locator('#s3 .s3-hist button:has-text("СОЛНЦЕВА ИВАНА")').count()==1)
      if SH:await pg.screenshot(path=f'{SH}/s3-{W}.png')
      if W<1000:
        ch=await pg.inner_text('#s3 .s3-kinds');check(f'[{W}] Чипы видов со счётчиками','Зачисление' in ch and 'Отрыв' in ch and '3' in ch,ch.replace('\n',' '))
        await pg.click("#s3 .s3-kinds button:has-text('Зачисление')");await pg.wait_for_timeout(200);check(f'[{W}] Чип вида фильтрует',len(await rows(pg))==3)
        await pg.click("#s3 .s3-kinds button:has-text('Все виды')");await pg.wait_for_timeout(200)
        await pg.click("#s3 .s3-dock button:has-text('Выбрать')");await pg.wait_for_timeout(200)
        check(f'[{W}] Режим выбора на телефоне — галочки',await pg.locator('#s3 tbody td.chk input').first.is_visible())
        await pg.click("#s3 .s3-dock button:has-text('Готово')");await pg.wait_for_timeout(200)
      async with pg.expect_download() as d:await pg.click("#s3 .s3-dock button:has-text('CSV')")
      dl=await d.value;pth=await dl.path();csv=open(pth,encoding='utf-8-sig').read()
      check(f'[{W}] Выгрузка таблицы CSV','Вид для выписки' in csv and 'Приказ 327' in csv,csv[:120])
      async def kind(v,label):
        if W<1000:await pg.click(f"#s3 .s3-kinds button:has-text('{label}')")
        else:await pg.click(f"#s3 .s3-side label:has-text('{label}') input")
      await kind('отрыв','Отрыв');await pg.wait_for_timeout(300);R2=await rows(pg)
      check(f'[{W}] Вид «Отрыв» — одна строка, последнее перемещение (не назначение)',len(R2)==1 and 'Назначение' not in R2[0][5] and '327' in R2[0][2],R2)
      await kind('все зачисления','Зачисление');await pg.wait_for_timeout(300);R3=await rows(pg)
      check(f'[{W}] Вид «Зачисление» — все зачисления',len(R3)==3 and all('Зачисление' in r[5] for r in R3),[r[2] for r in R3])
      await kind('','Все виды');await pg.wait_for_timeout(200)
      if W<1000:
        await pg.click("#s3 .s3-years label:has-text('2022') input");await pg.wait_for_timeout(300)
      else:
        await pg.click("#s3 .s3-side label:has-text('2022') input");await pg.wait_for_timeout(300)
      mt=await pg.inner_text('#s3 .s3-months');check(f'[{W}] Один год — месяцы',('сен' in mt and 'окт' in mt),mt.replace('\n',' '))
      await pg.click("#s3 .s3-months button:has-text('окт')");await pg.wait_for_timeout(200);R5=await rows(pg);check(f'[{W}] Месяц сужает',len(R5)==1 and '20.10.2022' in R5[0][2],[r[2] for r in R5])
      await pg.click("#s3 .s3-months button:has-text('весь год')");await pg.wait_for_timeout(200)
      R4=await rows(pg);check(f'[{W}] Год 2022 — только приказы 2022 года',R4 and all('2022' in r[2] for r in R4),[r[2] for r in R4])
      if W<1000:await pg.click("#s3 .s3-years button.all")
      else:await pg.click("#s3 .s3-side button:has-text('Все годы')")
      await pg.wait_for_timeout(300)
      if W<1000:
        await pg.click("#s3 tbody tr:first-child button:has-text('Выписка из приказа')");await pg.wait_for_selector('.pv-ov .pv-page section.docx, .pv-ov .pv-page .xsheet',timeout=30000)
        t=await pg.inner_text('.pv-ov .pv-page');check(f'[{W}] «Выписка из приказа» — просмотр как в Word','ВЫПИСКА' in t.upper(),t[:60])
        check(f'[{W}] В просмотре есть «Создать»',await pg.locator('.pv-ov button:has-text("Создать")').is_visible())
        async with pg.expect_download() as d:await pg.click(".pv-ov button:has-text('DOCX')")
        dl=await d.value;check(f'[{W}] Скачать DOCX из просмотра',dl.suggested_filename.endswith('.docx'),dl.suggested_filename)
        await pg.click('.pv-ov .pv-x')
      else:
        await pg.wait_for_selector('#s3 .s3-pane section.docx, #s3 .s3-pane .xsheet',timeout=30000)
        check(f'[{W}] Справа — выписка выбранной строки',await pg.locator('#s3 .s3-main.with-pane').count()==1)
        await pg.focus('#s3 tbody tr:first-child');await pg.keyboard.press('ArrowDown');await pg.wait_for_timeout(300)
        check(f'[{W}] Стрелки ↑↓ двигают выбор',await pg.evaluate("()=>document.querySelector('#s3 tbody tr.sel')===document.querySelectorAll('#s3 tbody tr')[1]"))
        await pg.click('#s3 tbody tr:nth-child(1) td.chk input');await pg.click('#s3 tbody tr:nth-child(2) td.chk input')
        check(f'[{W}] Выбор строк — «Создать выписки (2)»',await pg.locator("#s3 .s3-dock button:has-text('Создать выписки (2)')").count()==1)
        if SH:await pg.screenshot(path=f'{SH}/s3-pane-{W}.png')
      await pg.click("#s3 .s3-dock button:has-text('Создать выписки')");await pg.wait_for_selector('#bk .bk-job',timeout=20000)
      check(f'[{W}] Создание открывает массовое создание с найденными',await pg.locator('#bk .bk-job').count()>=2)
      await pg.evaluate("()=>VypiskiOnBack()");await pg.wait_for_selector('#s3',timeout=10000)
      await pg.wait_for_timeout(150);check(f'[{W}] Возврат — результаты сразу, без повторного поиска',await pg.locator('#s3 tbody tr').count()>=4 and 'Поиск…' not in await pg.inner_text('#s3 .s3-st'))
      check(f'[{W}] «Назад» возвращает к поиску',await pg.locator('#s3').count()==1)
      await pg.click("#content .sx-mode button:has-text('Люди и события')");await pg.wait_for_timeout(800)
      check(f'[{W}] Режим «Люди и события» работает',await pg.locator('#sx').count()==1 and await pg.locator('#s3').count()==0)
      await pg.click("#content .sx-mode button:has-text('Текст приказов')");await pg.wait_for_selector('#s3')
      check(f'[{W}] И обратно — новый «Текст приказов»',await pg.locator('#s3').count()==1)
      await pg.fill('#s3 .s3-q input','предписание');await pg.press('#s3 .s3-q input','Enter')
      await pg.wait_for_function("()=>document.querySelectorAll('#s3 tbody tr').length>=1&&!/Поиск…/.test(document.querySelector('#s3 .s3-st').textContent)",timeout=60000)
      check(f'[{W}] Поиск по словам — фрагмент с выделением',await pg.locator('#s3 tbody .snip mark').count()>=1,await pg.inner_text('#s3 tbody'))
      check(f'[{W}] Нет горизонтальной прокрутки страницы',await pg.evaluate("()=>document.documentElement.scrollWidth<=innerWidth+1"))
      check(f'[{W}] Без ошибок',not errs,errs[:3])
      await ctx.close()
    await b.close()
  print(f'{sum(res)}/{len(res)} passed');sys.exit(0 if all(res) else 1)
asyncio.run(main())
