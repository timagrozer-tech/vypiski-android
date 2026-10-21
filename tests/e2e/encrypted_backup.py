import os, sys as _s;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login,PASSWORD
FIX=os.environ.get("VYP_FIX","fixtures-out");OUTDIR=os.environ.get("VYP_OUT","/tmp/vypiski-test-out")
import asyncio, sys, os, glob, http.server, threading, functools
from playwright.async_api import async_playwright
ROOT=sys.argv[1]; OUT=f'{OUTDIR}/bkout'; os.makedirs(OUT,exist_ok=True)
files=sorted(glob.glob(f'{FIX}/xlcases/*.docx'))+sorted(glob.glob(f'{FIX}/orders/*.docx'))
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
URL=f"http://127.0.0.1:{srv.server_address[1]}/index.html"
PH='длинная фраза восстановления 42'
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d) if d else ''))
async def importAll(pg):
  await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()");await pg.wait_for_timeout(400)
  await pg.set_input_files("#wordFiles",files);await pg.wait_for_timeout(1500)
  await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
  for _ in range(100):
    await pg.wait_for_timeout(300)
    if not await pg.evaluate("()=>VypiskiImportJob.state().running"):break
async def make(pg,kind,phrase,orig=False):
  await pg.evaluate("()=>VypiskiShell.go('backup')");await pg.wait_for_selector('#vBk .acts')
  await pg.click("#vBk .acts button:has-text('%s')"%('Создать полную' if kind=='full' else 'Перенос'))
  ins=pg.locator('#vBk .dlg input[type=password]');await ins.nth(0).fill(phrase);await ins.nth(1).fill(phrase)
  if orig: await pg.check('#vBk .dlg input[type=checkbox]')
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch()
    ctx=await b.new_context(viewport={"width":1280,"height":900},locale="ru-RU",accept_downloads=True);pg=await ctx.new_page()
    errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
    await pg.goto(URL);await login(pg);await importAll(pg)
    n_orders=await pg.evaluate("async()=>(await VypiskiRepository.all('orders')).length")
    await make(pg,'full',PASSWORD)
    await pg.click("#vBk .dlg button.primary");await pg.wait_for_timeout(1500)
    check('Пароль входа нельзя использовать как фразу','пароль входа' in await pg.inner_text('#vBk .st'))
    await make(pg,'full',PH,orig=True)
    async with pg.expect_download() as d: await pg.click("#vBk .dlg button.primary")
    dl=await d.value;bp=f'{OUT}/{dl.suggested_filename}';await dl.save_as(bp);raw=open(bp,'rb').read()
    check('Файл .vypbak с заголовком',dl.suggested_filename.startswith('vypiski-full-backup-') and raw[:8]==b'VYPBAK1\n',dl.suggested_filename)
    check('Внутри нет открытого текста',('Петров'.encode() not in raw) and ('ПРИКАЗ'.encode() not in raw) and (b'orders' not in raw[400:]))
    await pg.wait_for_selector('#vBk .st.ok');t=await pg.inner_text('#vBk .st');check('Отчёт о копии с разделами','Приказы' in t,t[:200])
    await pg.screenshot(path=f'{OUT}/backup.png',full_page=True)
    # tampered copy
    bad=bytearray(raw);bad[-50]^=1;open(f'{OUT}/bad.vypbak','wb').write(bad)
    async def restore(page,path,phrase):
      await page.evaluate("()=>VypiskiShell.go('backup')");await page.wait_for_selector('#vBk .acts')
      await page.click("#vBk .acts button:has-text('Восстановить')");await page.set_input_files('#vBk .dlg input[type=file]',path)
      await page.fill('#vBk .dlg input[type=password]',phrase);await page.click("#vBk .dlg button:has-text('Проверить копию')")
      await page.wait_for_selector('#vBk .st .st, #vBk .st.ok, #vBk .st.bad',timeout=60000);await page.wait_for_timeout(300)
      return await page.inner_text('#vBk')
    t=await restore(pg,bp,'неверная фраза восстановления');check('Неверная фраза — отказ','Неверная фраза' in t)
    t=await restore(pg,f'{OUT}/bad.vypbak',PH);check('Повреждённый файл — отказ','повреждён' in t.lower())
    t=await restore(pg,bp,PH);check('Пробный прогон на той же базе: добавлять нечего','Будет добавлено записей: 0' in t,t[-300:])
    # new workstation
    ctx2=await b.new_context(viewport={"width":1280,"height":900},locale="ru-RU",accept_downloads=True);p2=await ctx2.new_page();p2.on("pageerror",lambda e:errs.append(str(e)))
    await p2.goto(URL);await login(p2,'admin2','Другой-пароль-77')
    t=await restore(p2,bp,PH);check('Новое место: пробный прогон показывает приказы',f'Будет добавлено записей' in t and 'Приказы' in t,t[-400:])
    c0=await p2.evaluate("async()=>(await VypiskiRepository.all('orders')).length");check('Пробный прогон ничего не изменил',c0==0)
    await p2.check('#vBk .opt input');await p2.click("#vBk button:has-text('Восстановить') >> nth=-1");await p2.wait_for_selector('#vBk .st.ok:has-text("Восстановлено")',timeout=60000)
    c1=await p2.evaluate("async()=>(await VypiskiRepository.all('orders')).length");check('Приказы восстановлены',c1==n_orders,(c1,n_orders))
    same=await p2.evaluate("async()=>{const o=await VypiskiRepository.all('orders');let ok=0;for(const x of o){const h=await VypiskiImport.sha256(VypiskiImport.normText(x.text));if(h===x.textSha256)ok++}return [ok,o.length]}")
    check('SHA-256 текстов после восстановления совпадают',same[0]==same[1],same)
    fo=await p2.evaluate("async()=>{const o=await VypiskiRepository.all('orders');let ok=0,n=0;for(const x of o){if(!x.originalStored)continue;n++;const g=await VypiskiImport.getOriginal(x.fileSha256);if(g&&await VypiskiImport.sha256(new Uint8Array(await g.blob.arrayBuffer()))===x.fileSha256)ok++}return [ok,n]}")
    check('Исходные файлы Word восстановлены байт-в-байт',fo[0]==fo[1],fo)
    await p2.evaluate("()=>VypiskiSearchEvents.open('')");await p2.wait_for_timeout(500)
    if await p2.locator("button:has-text('Подготовить таблицу')").count(): await p2.click("button:has-text('Подготовить таблицу')")
    await p2.wait_for_selector('#sx .sx-person',timeout=30000)
    check('Поиск работает после восстановления',await p2.locator('#sx .sx-person').count()>0)
    au=await p2.evaluate("async()=>(await VypiskiAudit.list({limit:20})).map(e=>e.type)");check('Журнал: восстановление записано','backup.restored' in au)
    # workspace transfer
    await pg.evaluate("()=>{localStorage.setItem('vypiski.sx.saved',JSON.stringify([{name:'проба',f:{q:'x',cats:[],years:[],months:[],by:'order',action:'',rank:''}}]))}")
    await make(pg,'workspace',PH)
    async with pg.expect_download() as d: await pg.click("#vBk .dlg button.primary")
    dl=await d.value;wp=f'{OUT}/{dl.suggested_filename}';await dl.save_as(wp)
    t=await restore(p2,wp,PH);check('Перенос рабочего места: настройки без приказов','Настройки программы' in t and 'Приказы' not in t,t[-300:])
    await p2.check('#vBk .opt input');await p2.click("#vBk button:has-text('Восстановить') >> nth=-1");await p2.wait_for_selector('#vBk .st.ok:has-text("Восстановлено")')
    check('Сохранённый поиск перенесён',await p2.evaluate("()=>localStorage.getItem('vypiski.sx.saved')||''")!='')
    check('Без ошибок JS',not errs,errs[:3])
    await b.close()
  print(f'{sum(res)}/{len(res)}')
asyncio.run(main())
