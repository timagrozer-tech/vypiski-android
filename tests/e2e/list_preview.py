"""12.79.12: «Выписки по списку» — one enrollment (the last), «отрыв» is the last movement (not an appointment), exclusion inside
a БВП point, SVO «для выполнения задач»; preview before download (Word view, next/prev, «в архив», Back); bulk list filters.
Synthetic orders (mkhist.py), invented names."""
import sys as _s,os;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
import asyncio,sys,glob,http.server,threading,functools,subprocess
from playwright.async_api import async_playwright
ROOT=sys.argv[1];FIX=os.environ.get('VYP_HIST','/tmp/vypiski-test-out/histcases')
_h=os.path.dirname(os.path.abspath(__file__));_mk=[p for p in [os.path.join(_h,'mkhist.py'),os.path.join(_h,'..','fixtures','history','mkhist.py')] if os.path.exists(p)][0]
subprocess.run([sys.executable,_mk,FIX],check=True)
h=functools.partial(http.server.SimpleHTTPRequestHandler,directory=ROOT);h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h);threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[];SH=os.environ.get('SHOTS')
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d) if d else ''))
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch()
    for W in [390,1366]:
      ctx=await b.new_context(viewport={"width":W,"height":860},locale="ru-RU");pg=await ctx.new_page();errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
      await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await pg.wait_for_timeout(1500);await login(pg)
      await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()");await pg.wait_for_timeout(500)
      await pg.set_input_files("#wordFiles",sorted(glob.glob(FIX+'/*.docx')));await pg.wait_for_timeout(1500)
      await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
      for _ in range(100):
        await pg.wait_for_timeout(300)
        if not await pg.evaluate("()=>VypiskiImportJob.state().running"):break
      await pg.evaluate("()=>{try{localStorage.removeItem('vypiski.lq.items')}catch{}VypiskiListReq.page()}");await pg.wait_for_selector('#lq')
      await pg.click("#lq button:has-text('Вставить текст')");await pg.fill('#lq textarea','Солнцев Иван Валерьевич — зачисление, край. убытие на СВО, БВП, исключение, отрыв\nВетлугин Роман Андреевич — зачисление, отрыв\nНеизвестнов Пётр Петрович — зачисление\nАлмазов Иван Иванович — зачисление')
      await pg.click("#lq button:has-text('Добавить из текста')");await pg.click("#lq button:has-text('Найти людей и приказы')")
      await pg.wait_for_selector('#lq .lq-chip.ok',timeout=60000);await pg.wait_for_timeout(500)
      chips=await pg.evaluate("()=>[...document.querySelectorAll('#lq .lq-p')][0].querySelector('.lq-res').innerText")
      check(f'[{W}] Зачисление: одно (последнее) из трёх',('Зачисление: 1 (из 3)' in chips),chips.replace('\n',' | ')[:300])
      check(f'[{W}] СВО «для выполнения задач» найдено, исключение внутри БВП найдено','СВО: 1' in chips and 'Исключение: 1' in chips and 'БВП: 1' in chips,chips.replace('\n',' | ')[:300])
      await pg.evaluate("()=>document.querySelector('#lq .lq-p .lq-ev').open=true")
      evs=await pg.evaluate("()=>[...document.querySelector('#lq .lq-p .lq-ev').querySelectorAll('.lq-evr span')].map(x=>x.textContent)")
      check(f'[{W}] Отрыв — последнее движение, не назначение на должность',not any('Назначение' in e for e in evs) and any('327' in e and '§ 3, п. 2' in e for e in evs),evs)
      zach=[e for e in evs if e.startswith('Зачисление')]
      check(f'[{W}] Взято последнее зачисление (№ 327)',len(zach)==1 and '327' in zach[0],zach)
      al=await pg.evaluate("()=>[...document.querySelectorAll('#lq .lq-p')][3].querySelector('.lq-res').innerText")
      check(f'[{W}] Зачисление из таблицы: подробность — дата, а не шапка таблицы','с 27 сентября 2022' in await pg.evaluate("()=>{const d=[...document.querySelectorAll('#lq .lq-p')][3].querySelector('.lq-ev');d.open=true;return d.innerText}"),al[:200])
      # filter
      await pg.click("#lq .lq-fl button:has-text('Не найдены')");vis=await pg.evaluate("()=>[...document.querySelectorAll('#lq .lq-p')].filter(x=>!x.classList.contains('lq-hide')).length")
      check(f'[{W}] Фильтр «Не найдены» оставляет одного',vis==1,vis)
      await pg.click("#lq .lq-fl button:has-text('Все')")
      # preview
      if SH:
        await pg.evaluate("()=>document.querySelector('#lq .lq-p').scrollIntoView()");await pg.screenshot(path=f'{SH}/lq-{W}.png')
      await pg.click("#lq .lq-dock button:has-text('Просмотреть')");await pg.wait_for_selector('.pv-ov')
      await pg.wait_for_selector('.pv-ov .pv-page section.docx, .pv-ov .pv-page .xsheet',timeout=30000)
      if SH:await pg.screenshot(path=f'{SH}/pv-{W}.png')
      txt=await pg.inner_text('.pv-ov .pv-page');n1=await pg.inner_text('.pv-ov .pv-n')
      check(f'[{W}] Просмотр: выписка как в Word',await pg.locator('.pv-ov .pv-page section.docx').count()==1 and 'ВЫПИСКА' in txt.upper(),(n1,txt[:80]))
      if W<700:
        await pg.click(".pv-ov button:has-text('Крупнее')");await pg.wait_for_timeout(300)
        zz=await pg.evaluate("()=>{const b=document.querySelector('.pv-body');return [b.scrollWidth,b.clientWidth]}")
        if SH:await pg.screenshot(path=f'{SH}/pvz-{W}.png')
        check(f'[{W}] «Крупнее» увеличивает страницу (прокрутка внутри просмотра)',zz[0]>zz[1]*1.3,zz)
        await pg.click(".pv-ov button:has-text('Крупнее')");await pg.click(".pv-ov button:has-text('По ширине')");await pg.wait_for_timeout(200)
      await pg.click(".pv-ov button:has-text('Следующая')");await pg.wait_for_timeout(300);await pg.wait_for_selector('.pv-ov .pv-page section.docx',timeout=30000)
      n2=await pg.inner_text('.pv-ov .pv-n');check(f'[{W}] Следующая выписка',n2.startswith('2 из'),n2)
      await pg.uncheck('.pv-ov .pv-tools input[type=checkbox]');await pg.wait_for_timeout(100)
      dbg=await pg.evaluate("()=>[document.querySelectorAll('.pv-ov').length,document.querySelectorAll('.pv-ov .pv-x').length,document.querySelector('.pv-ov .pv-tools input').checked]")
      r=await pg.evaluate("()=>VypiskiOnBack()");await pg.wait_for_timeout(200);gone=await pg.locator('.pv-ov').count()==0
      check(f'[{W}] «Назад» закрывает просмотр',r=='handled' and gone,r)
      off=await pg.evaluate("()=>[...document.querySelectorAll('#lq .lq-p')][0].querySelectorAll('.lq-evr input:not(:checked)').length")
      check(f'[{W}] Снятая в просмотре выписка отключена в списке',off==1,off)
      ws=await pg.evaluate("()=>document.documentElement.scrollWidth<=innerWidth+1");check(f'[{W}] Нет горизонтальной прокрутки',ws)
      # to bulk
      await pg.click("#lq .lq-dock button:has-text('Создать выписки')");await pg.wait_for_selector('#bk .bk-job',timeout=20000)
      await pg.click("#bk button:has-text('Подготовить выписки')")
      for _ in range(100):
        await pg.wait_for_timeout(300)
        if await pg.locator('#bk button:has-text("Создать и скачать ZIP")').is_visible():break
      if SH:
        await pg.evaluate("()=>document.querySelector('#bk .bk-fl').scrollIntoView()");await pg.screenshot(path=f'{SH}/bk-{W}.png')
      fl=await pg.inner_text('#bk .bk-fl');check(f'[{W}] Фильтр списка массового создания',('Готово' in fl and 'Все' in fl),fl.replace('\n',' '))
      heads=await pg.locator('#bk .bk-ph').count();check(f'[{W}] Группировка по людям',heads>=2,heads)
      await pg.click("#bk button:has-text('Просмотреть выписки')");await pg.wait_for_selector('.pv-ov .pv-page section.docx, .pv-ov .pv-page .xsheet',timeout=30000)
      allt=[]
      for k in range(12):
        await pg.wait_for_selector('.pv-ov .pv-page section.docx, .pv-ov .pv-page .xsheet, .pv-ov .pv-msg:not(:has-text("Подготовка"))',timeout=30000)
        allt.append(await pg.inner_text('.pv-ov .pv-page'))
        if await pg.locator(".pv-ov button:has-text('Следующая')").is_disabled():break
        await pg.click(".pv-ov button:has-text('Следующая')");await pg.wait_for_timeout(200)
      al=[t for t in allt if 'Алмазов' in t]
      check(f'[{W}] Выписка из таблицы — только строка этого человека',al and all('Берёзов' not in t and 'Ветлугин' not in t and 'Солнцев' not in t for t in al),[t[:300] for t in al][:1])
      await pg.keyboard.press('Escape');check(f'[{W}] Esc закрывает просмотр',await pg.locator('.pv-ov').count()==0)
      r=await pg.evaluate("()=>VypiskiOnBack()");await pg.wait_for_timeout(500)
      check(f'[{W}] «Назад» с массового создания — к списку',await pg.locator('#lq').count()==1 or await pg.evaluate("()=>!!document.querySelector('#content h2')&&/Выписки по списку/.test(document.querySelector('#content h2').textContent)"),r)
      check(f'[{W}] Стрелка «назад» у заголовка',await pg.locator('#content>.nav-back').count()==1)
      check(f'[{W}] Без ошибок на странице',not errs,errs[:3])
      await ctx.close()
    await b.close()
  print(f'{sum(res)}/{len(res)} passed');sys.exit(0 if all(res) else 1)
asyncio.run(main())
