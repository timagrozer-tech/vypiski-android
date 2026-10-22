"""12.77.11: the table keeps no paragraph texts in memory; XLSX, CSV and the details sheet still get them verbatim."""
import os, sys as _s;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
FIX=os.environ.get("VYP_FIX","fixtures-out")
os.makedirs('/tmp/vypiski-test-out',exist_ok=True)
import asyncio, sys, glob, http.server, threading, functools, zipfile, re, os
from playwright.async_api import async_playwright
ROOT=sys.argv[1];OUT='/tmp/vypiski-test-out/xltext';os.makedirs(OUT,exist_ok=True)
files=sorted(glob.glob(FIX+'/xlcases/*.docx'))+glob.glob(FIX+'/wfcases/Приказ_158_2025.docx')
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d)[:200] if d else ''))
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();ctx=await b.new_context(viewport={"width":1280,"height":900},accept_downloads=True);pg=await ctx.new_page();errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
    await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await login(pg)
    await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()");await pg.wait_for_timeout(400)
    await pg.set_input_files("#wordFiles",files);await pg.wait_for_timeout(1500)
    await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
    while await pg.evaluate("()=>VypiskiImportJob.state().running"):await pg.wait_for_timeout(300)
    await pg.evaluate("()=>VypiskiRegisterView.open()");await pg.locator('#rg .rg-actions').wait_for()
    await pg.wait_for_timeout(1500)
    check('В памяти таблицы нет текста пунктов',await pg.evaluate("()=>VypiskiRegisterView.rows().every(r=>r.text===undefined)"))
    async with pg.expect_download() as d:await pg.click("#rg button:has-text('Скачать XLSX')")
    dl=await d.value;path=f"{OUT}/a.xlsx";await dl.save_as(path)
    z=zipfile.ZipFile(path);x=z.read('xl/worksheets/sheet1.xml').decode()
    check('XLSX: дословный текст пункта на месте','Младшему сержанту' in x or 'сержанту' in x,len(x))
    async with pg.expect_download() as d:await pg.click("#rg button:has-text('CSV')")
    dl=await d.value;path=f"{OUT}/a.csv";await dl.save_as(path);t=open(path,encoding='utf-8-sig').read()
    check('CSV: дословный текст пункта на месте','сержант' in t,len(t))
    await pg.locator('#rg .rg-card .b, #rg tbody tr').first.click();await pg.wait_for_timeout(800)
    t=await pg.inner_text('#rgSheet pre');check('Карточка: дословный текст загружается',len(t)>20 and t!='…',t[:80])
    check('Без ошибок',not errs,errs);await b.close()
  print(f"{sum(res)}/{len(res)}")
asyncio.run(main())
