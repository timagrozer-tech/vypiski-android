import os, sys as _s;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
FIX=os.environ.get("VYP_FIX","fixtures-out");OUTDIR=os.environ.get("VYP_OUT","/tmp/vypiski-test-out")
import asyncio, sys, glob, http.server, threading, functools, zipfile, os
from playwright.async_api import async_playwright
ROOT=sys.argv[1];OUT=f'{OUTDIR}/bulk';os.makedirs(OUT,exist_ok=True)
files=sorted(glob.glob(f'{FIX}/xlcases/*.docx'))+sorted(glob.glob(f'{FIX}/orders/*.docx'))+[f'{FIX}/wfcases/Приказ_158_2025.docx',f'{FIX}/wfcases/Приказ_325_2025.docx']
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d) if d else ''))
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch()
    for width in [1366,390]:
      ctx=await b.new_context(viewport={"width":width,"height":900},locale="ru-RU",accept_downloads=True);pg=await ctx.new_page();errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
      await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await login(pg);await pg.evaluate("()=>localStorage.setItem('vypiski.s3.off','1')") # 12.79.12: the advanced (old) text search
      await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()");await pg.wait_for_timeout(400)
      await pg.set_input_files("#wordFiles",files);await pg.wait_for_timeout(1500)
      await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
      while await pg.evaluate("()=>VypiskiImportJob.state().running"):await pg.wait_for_timeout(300)
      while await pg.evaluate("()=>VypiskiCore.waiting?.()||0"):await pg.wait_for_timeout(300)
      # the section is in the menu
      if width<900:await pg.click('#appNav .more');await pg.wait_for_timeout(200)
      await pg.click("[data-sec='batch']:visible");await pg.wait_for_selector('#bk .bk-card')
      check(f'[{width}] Раздел «Массовое создание» в меню',await pg.inner_text('#content h2')=='Массовое создание выписок')
      check(f'[{width}] Таблица не строится при открытии',not await pg.evaluate("()=>VypiskiRegisterView.builtAt()"))
      await pg.click("#bk .bk-row:has-text('Годы') button:text-is('2024')");await pg.click("#bk .bk-row:has-text('Годы') button:text-is('2025')")
      await pg.click("#bk button:has-text('Найти события')");await pg.wait_for_selector('#bk .bk-job',timeout=30000)
      jobs=await pg.evaluate("()=>[...document.querySelectorAll('#bk .bk-job')].map(x=>x.innerText)")
      allLeave=all('отпуск' in j.lower() for j in jobs);years=all(('2024' in j or '2025' in j) for j in jobs)
      check(f'[{width}] Отпуска за 2024 и 2025: только отпуска нужных лет',jobs and allLeave and years,(len(jobs),jobs[:3]))
      check(f'[{width}] Отмена и отзыв не попадают по умолчанию',not any('отмена' in j or 'отзыв' in j for j in jobs),[j for j in jobs if 'отмена' in j or 'отзыв' in j][:2])
      s=await pg.inner_text('#bk .bk-sum');check(f'[{width}] Итог: выписок, людей, приказов','выписок выбрано' in s and 'людей' in s,s)
      await pg.locator('#bk .bk-job input').first.uncheck();s2=await pg.inner_text('#bk .bk-sum');check(f'[{width}] Можно снять отдельные',s2.split()[0]!=s.split()[0],(s,s2))
      await pg.click("#bk button:has-text('Выбрать всех')")
      await pg.click("#bk button:has-text('Подготовить выписки')");await pg.wait_for_selector("#bk .bk-conf:not([hidden])",timeout=60000)
      info=await pg.inner_text('#bk .bk-note >> nth=-1');check(f'[{width}] Подготовка с итогом','Готово к созданию' in await pg.inner_text('#bk'),info)
      await pg.locator('#bk .bk-job .st.ok').first.locator('xpath=..').locator('div').first.click()
      await pg.wait_for_selector('.pv-ov .pv-page section.docx, .pv-ov .pv-page .xsheet',timeout=30000)
      check(f'[{width}] Просмотр выписки из списка (12.79.12: отдельный экран просмотра)',await pg.locator('.pv-ov .pv-page section.docx, .pv-ov .pv-page .xsheet').count()==1)
      await pg.click('.pv-ov .pv-x');await pg.wait_for_timeout(200)
      check(f'[{width}] Без подтверждения создать нельзя',await pg.locator("#bk button:has-text('Создать и скачать ZIP')").is_disabled())
      await pg.select_option('#bk .bk-card:nth-of-type(3) .bk-grid select','50')
      await pg.check('#bk .bk-conf input')
      async with pg.expect_download() as d: await pg.click("#bk button:has-text('Создать и скачать ZIP')")
      dl=await d.value;zp=f'{OUT}/{width}-{dl.suggested_filename}';await dl.save_as(zp);names=zipfile.ZipFile(zp).namelist()
      check(f'[{width}] ZIP с понятным названием и журналом','Отпуск' in dl.suggested_filename and '2024–2025' in dl.suggested_filename and '_Журнал создания.txt' in names,(dl.suggested_filename,len(names)))
      await pg.wait_for_selector('#bk .bk-res');r=await pg.inner_text('#bk .bk-res');check(f'[{width}] Итог: создано, SHA-256 совпадает','Создано новых выписок' in r and 'совпадает у' in r,r)
      n=await pg.evaluate("async()=>(await VypiskiRepository.all('excerpts')).length");check(f'[{width}] В «Готовых выписках»',n==len([x for x in names if x.endswith('.docx')]),n)
      await pg.screenshot(path=f'{OUT}/bulk-{width}.png',full_page=False)
      # from people search: selection opens the same section
      await pg.evaluate("()=>VypiskiSearchEvents.open('Елисеев Дмитрий')");await pg.wait_for_selector('#sx .sx-ev input, #sx button:has-text("Подготовить таблицу")')
      if await pg.locator('#sx button:has-text("Подготовить таблицу")').count(): await pg.click('#sx button:has-text("Подготовить таблицу")')
      await pg.wait_for_selector('#sx .sx-ev input');await pg.locator('#sx .sx-ev input').first.check()
      await pg.click("#sx .sx-bulk button:has-text('Создать выписки')");await pg.wait_for_selector('#bk .bk-job')
      check(f'[{width}] Выбор в поиске открывает раздел массового создания',await pg.locator('#bk .bk-job').count()==1)
      # text search: several years
      await pg.evaluate("()=>VypiskiShell.go('search')");await pg.wait_for_selector('.sx-mode');await pg.click(".sx-mode button:has-text('Текст приказов')");await pg.wait_for_selector('.sx-ym button')
      await pg.fill('#dfs .df-q','Елисееву');await pg.click(".sx-ym button:text-is('2024')");await pg.wait_for_timeout(1200);await pg.click(".sx-ym button:text-is('2026')") if await pg.locator(".sx-ym button:text-is('2026')").count() else None
      await pg.click(".sx-ym button:text-is('2025')");await pg.wait_for_timeout(1500)
      on=await pg.locator('.sx-ym button.on').all_inner_texts();t=await pg.inner_text('#dfs')
      check(f'[{width}] Поиск: выбрано несколько лет',set(on)>={'2024','2025'},on)
      cards=await pg.evaluate("()=>[...document.querySelectorAll('#dfs .df-cards .t, #dfs .df-table tbody tr')].map(x=>x.innerText.slice(0,60))")
      check(f'[{width}] Поиск по двум годам находит приказы обоих лет',any('2024' in c for c in cards) and any('2025' in c for c in cards),cards[:6])
      await pg.click(".sx-ym button:text-is('2024')");await pg.wait_for_timeout(1500)
      cards=await pg.evaluate("()=>[...document.querySelectorAll('#dfs .df-cards .t, #dfs .df-table tbody tr')].map(x=>x.innerText.slice(0,60))")
      check(f'[{width}] Снятие года сужает поиск',cards and not any('2024' in c for c in cards),cards[:6])
      check(f'[{width}] Без ошибок JS',not errs,errs[:3])
      await ctx.close()
    await b.close()
  print(f'{sum(res)}/{len(res)}')
asyncio.run(main())
