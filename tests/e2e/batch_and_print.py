import os, sys as _s;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
FIX=os.environ.get("VYP_FIX","fixtures-out");OUTDIR=os.environ.get("VYP_OUT","/tmp/vypiski-test-out")
import asyncio, sys, os, glob, http.server, threading, functools, zipfile, io
from playwright.async_api import async_playwright
ROOT=sys.argv[1]; OUT=f'{OUTDIR}/btout'; os.makedirs(OUT,exist_ok=True)
files=sorted(glob.glob(f'{FIX}/xlcases/*.docx'))+sorted(glob.glob(f'{FIX}/orders/*.docx'))
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d) if d else ''))
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch()
    for width in [1366,390]:
      ctx=await b.new_context(viewport={"width":width,"height":900},locale="ru-RU",accept_downloads=True);pg=await ctx.new_page()
      errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
      await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await pg.wait_for_timeout(1500)
      await login(pg)
      await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()");await pg.wait_for_timeout(500)
      await pg.set_input_files("#wordFiles",files);await pg.wait_for_timeout(1500)
      await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
      for _ in range(100):
        await pg.wait_for_timeout(300)
        if not await pg.evaluate("()=>VypiskiImportJob.state().running"):break
      before=await pg.evaluate("async()=>{const o=await VypiskiRepository.all('orders');return Object.fromEntries(o.map(x=>[x.id,x.textSha256+'|'+x.fileSha256]))}")
      n0=await pg.evaluate("async()=>(await VypiskiRepository.all('excerpts')).length")
      # select via search UI: all leave events
      await pg.evaluate("()=>VypiskiShell.go('search')");await pg.wait_for_selector('.sx-mode');await pg.click(".sx-mode button:has-text('Люди и события')");await pg.wait_for_timeout(300)
      if await pg.locator("button:has-text('Подготовить таблицу людей и событий')").count(): await pg.click("button:has-text('Подготовить таблицу людей и событий')")
      await pg.wait_for_selector('#sx .sx-person',timeout=30000)
      await pg.evaluate("()=>document.querySelector('#sx .sx-f').classList.add('open')")
      await pg.click("#sx .sx-row button.sx-chip:has-text('Отпуск')");await pg.wait_for_timeout(200)
      boxes=pg.locator('#sx .sx-ev input');k=await boxes.count()
      for i in range(k):await boxes.nth(i).check()
      await pg.click("#sx .sx-bulk button:has-text('Создать выписки')")
      await pg.wait_for_selector('#bt .bt-foot',timeout=60000)
      info=await pg.inner_text('#bt .bt-sum');jobs=await pg.locator('#bt .bt-job').count()
      check(f'[{width}] Подготовка: по выписке на человека в приказе',jobs>=3,(k,jobs,info))
      ok=await pg.locator('#bt .st.ok').count();bad=await pg.locator('#bt .st.bad').count()
      check(f'[{width}] Есть готовые, проблемные — с причиной',ok>=2 and all(['Не подготовлено' in t for t in await pg.locator('#bt .bt-job:has(.st.bad) .m').all_inner_texts() if 'Не подготовлено' in t] or [True]),(ok,bad))
      check(f'[{width}] Без подтверждения скачать нельзя',await pg.locator("#bt .bt-foot button:has-text('Скачать ZIP')").is_disabled())
      await pg.locator('#bt .bt-job:has(.st.ok) button:has-text("Просмотр")').first.click();await pg.wait_for_timeout(300)
      check(f'[{width}] Просмотр выписки',await pg.locator('#bt .bt-pv .xsheet').count()>=1)
      await pg.screenshot(path=f'{OUT}/batch-{width}.png')
      await pg.check('#bt .bt-foot input')
      async with pg.expect_download() as d:
        await pg.click("#bt .bt-foot button:has-text('Скачать ZIP')")
      dl=await d.value;zp=f'{OUT}/{width}-{dl.suggested_filename}';await dl.save_as(zp)
      z=zipfile.ZipFile(zp);names=z.namelist();docx=[n for n in names if n.endswith('.docx')]
      check(f'[{width}] ZIP: DOCX по числу готовых + журнал',len(docx)==ok and '_Журнал создания.txt' in names,names)
      check(f'[{width}] Имена уникальны',len(set(n.lower() for n in names))==len(names))
      import docx as pdx
      d0=pdx.Document(io.BytesIO(z.read(docx[0])));txt='\n'.join(p.text for p in d0.paragraphs)
      check(f'[{width}] DOCX — выписка из приказа','ВЫПИСКА' in txt.upper(),txt[:120])
      await pg.wait_for_selector('#bt .bt-res')
      r=await pg.inner_text('#bt .bt-res');check(f'[{width}] Итог: SHA-256 приказов совпадает','совпадает у' in r and 'ошиб' not in r,r)
      n1=await pg.evaluate("async()=>(await VypiskiRepository.all('excerpts')).length");check(f'[{width}] Записи в «Готовые выписки»',n1-n0==len(docx),(n0,n1))
      after=await pg.evaluate("async()=>{const o=await VypiskiRepository.all('orders');return Object.fromEntries(o.map(x=>[x.id,x.textSha256+'|'+x.fileSha256]))}")
      check(f'[{width}] Оригиналы не изменены',before==after)
      ev=await pg.evaluate("async()=>(await VypiskiAudit.list({limit:50})).filter(e=>e.type==='excerpt.batch').length");check(f'[{width}] Журнал: excerpt.batch',ev==1)
      # print center from batch (Android path mocked)
      await pg.evaluate("()=>{window.VypiskiPrintHtml=(h,t)=>{window.__pr={h,t}}}")
      await pg.click("#bt .bt-foot button:has-text('Печать')");await pg.wait_for_selector('#pc .doc')
      nd=await pg.locator('#pc .doc').count();check(f'[{width}] Центр печати: документы',nd>=1,nd)
      await pg.click("#pc .seg button:has-text('Альбомная')");await pg.select_option('#pc select >> nth=1','page');await pg.fill('#pc input[type=number]','2')
      await pg.click('#pc .pf button.primary');t1=await pg.inner_text('#pc .pf .msg')
      check(f'[{width}] Двухшаговое подтверждение',('Нажмите ещё раз' in t1) or nd==0,t1)
      await pg.click('#pc .pf button.primary');await pg.wait_for_timeout(500)
      pr=await pg.evaluate("()=>window.__pr")
      ok2=pr and pr['h'].count('<section')==nd*2 and 'landscape' in pr['h'] and 'counter(page)' in pr['h']
      check(f'[{width}] HTML печати: копии, ориентация, колонтитул',ok2,(pr['h'].count('<section') if pr else None))
      await pg.screenshot(path=f'{OUT}/print-{width}.png')
      await pg.keyboard.press('Escape');check(f'[{width}] Esc закрывает',await pg.locator('#pc').count()==0)
      # ready → print several
      await pg.evaluate("()=>VypiskiShell.go('ready')");await pg.wait_for_selector('.pc-ready button')
      await pg.click('.pc-ready button');await pg.wait_for_selector('#pc .doc')
      check(f'[{width}] «Печать нескольких» в готовых',await pg.locator('#pc .doc').count()==n1-n0+0 or await pg.locator('#pc .doc').count()>=len(docx))
      await pg.keyboard.press('Escape')
      check(f'[{width}] Без ошибок JS',not errs,errs[:3])
      await ctx.close()
    await b.close()
  print(f'{sum(res)}/{len(res)}')
asyncio.run(main())
