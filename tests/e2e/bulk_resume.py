"""12.77.9: bulk creation interrupted by an app restart is continued without duplicates; already created excerpts can be zipped."""
import os, sys as _s;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
FIX=os.environ.get("VYP_FIX","fixtures-out")
import asyncio, sys, glob, http.server, threading, functools, zipfile, os
from playwright.async_api import async_playwright
ROOT=sys.argv[1];FX=FIX+'/';OUT='/tmp/vypiski-test-out/bkres';os.makedirs(OUT,exist_ok=True)
files=sorted(glob.glob(FX+'xlcases/*.docx'))+sorted(glob.glob(FX+'orders/*.docx'))+sorted(glob.glob(FX+'many/**/*.docx',recursive=True))[:400]
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d)[:250] if d else ''))
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();ctx=await b.new_context(viewport={"width":390,"height":844},accept_downloads=True);pg=await ctx.new_page();errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
    await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await login(pg)
    await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()");await pg.wait_for_timeout(400)
    await pg.set_input_files("#wordFiles",files);await pg.wait_for_timeout(1500)
    await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
    while await pg.evaluate("()=>VypiskiImportJob.state().running"):await pg.wait_for_timeout(300)
    while await pg.evaluate("()=>VypiskiCore.waiting?.()||0"):await pg.wait_for_timeout(300)
    await pg.evaluate("()=>{localStorage.setItem('vypiski.bulk.f',JSON.stringify({part:50}));localStorage.removeItem('vypiski.bulk.run')}")
    await pg.evaluate("()=>VypiskiBulk.open()");await pg.wait_for_selector('#bk .bk-row:not([hidden])')
    await pg.click("#bk button:has-text('Найти события')");await pg.wait_for_selector('#bk .bk-job',timeout=60000)
    total=await pg.evaluate("()=>+document.querySelector('#bk .bk-sum b').textContent.replace(/\\s/g,'')")
    await pg.click("#bk button:has-text('Подготовить выписки')");await pg.wait_for_selector("#bk .bk-conf:not([hidden])",timeout=120000)
    await pg.check('#bk .bk-conf input')
    # interrupt: reload page in the middle of creation (after the first saved excerpt)
    await pg.evaluate("()=>{const o=VypiskiRepository.put;window.__n=0}")
    await pg.click("#bk button:has-text('Создать и скачать ZIP')")
    for i in range(200):
      run=await pg.evaluate("()=>{const r=JSON.parse(localStorage.getItem('vypiski.bulk.run')||'null');if(r)r.done={...r.done,...JSON.parse(localStorage.getItem('vypiski.bulk.run.d')||'{}')};return r}")
      if run and len(run.get('done',{}))>=1:break
      await pg.wait_for_timeout(20)
    n_ex=await pg.evaluate("async()=>(await VypiskiRepository.all('excerpts')).length")
    await pg.reload();await login(pg)
    run=await pg.evaluate("()=>{const r=JSON.parse(localStorage.getItem('vypiski.bulk.run')||'null');if(r)r.done={...r.done,...JSON.parse(localStorage.getItem('vypiski.bulk.run.d')||'{}')};return r}")
    check('После перерыва состояние сохранено',run and len(run['keys'])==total,(run and len(run['keys']),total, run and len(run['done'])))
    await pg.evaluate("()=>VypiskiBulk.open()");await pg.wait_for_selector('#bk .bk-resume',timeout=10000)
    t=await pg.inner_text('#bk .bk-resume');check('Показано предложение продолжить','не завершено' in t,t)
    nd=len(run['done'])
    if nd:
      async with pg.expect_download() as d:await pg.click("#bk .bk-resume button:has-text('Скачать уже созданные')")
      dl=await d.value;path=f"{OUT}/{dl.suggested_filename}";await dl.save_as(path);z=zipfile.ZipFile(path);check('Архив созданных до перерыва',len(z.namelist())==nd,(len(z.namelist()),nd))
    await pg.click("#bk .bk-resume button:has-text('Продолжить')");await pg.wait_for_selector('#bk .bk-job',timeout=60000)
    on=await pg.evaluate("()=>+document.querySelector('#bk .bk-sum b').textContent.replace(/\\s/g,'')")
    check('Восстановлено: выбраны все, кроме уже созданных',on==total-nd,(on,total,nd))
    await pg.click("#bk button:has-text('Подготовить выписки')");await pg.wait_for_selector("#bk .bk-conf:not([hidden])",timeout=120000);await pg.check('#bk .bk-conf input')
    async with pg.expect_download() as d:await pg.click("#bk button:has-text('Создать и скачать ZIP')")
    dl=await d.value;await pg.wait_for_selector('#bk .bk-res',timeout=60000)
    n2=await pg.evaluate("async()=>{const a=await VypiskiRepository.all('excerpts');return [a.length,new Set(a.map(e=>e.orderId+'|'+e.person)).size]}")
    dd=await pg.evaluate("async()=>{const a=await VypiskiRepository.all('excerpts');const m=new Map();for(const e of a){const k=e.orderId+'|'+e.person;m.set(k,[...(m.get(k)||[]),e.fileName+' @'+e.batchId])}return [...m.entries()].filter(x=>x[1].length>1).slice(0,4)}")
    print(dd)
    check('Выписки без дублей',n2[0]==n2[1] and n2[0]>nd,n2)
    check('После завершения состояние очищено',await pg.evaluate("()=>localStorage.getItem('vypiski.bulk.run')")is None)
    check('Без ошибок',not errs,errs);await b.close()
  print(f"{sum(res)}/{len(res)}")
asyncio.run(main())
