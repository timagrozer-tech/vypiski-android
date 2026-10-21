"""12.77.10: a crash or unexpected restart leaves a report in Diagnostics («Отчёты о сбоях»); names and numbers are masked."""
import os, sys as _s;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
os.makedirs('/tmp/vypiski-test-out',exist_ok=True)
import asyncio, sys, http.server, threading, functools, os
from playwright.async_api import async_playwright
ROOT=sys.argv[1];STATE='/tmp/vypiski-test-out/crashprof'
import shutil;shutil.rmtree(STATE,ignore_errors=True)
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
URL=f"http://127.0.0.1:{srv.server_address[1]}/index.html"
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d)[:300] if d else ''))
async def main():
  async with async_playwright() as p:
    ctx=await p.chromium.launch_persistent_context(STATE,viewport={"width":390,"height":844},accept_downloads=True);pg=ctx.pages[0];errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
    await pg.goto(URL);await login(pg)
    check('Нормальный запуск: отчётов нет',await pg.evaluate("()=>VypiskiCrash.reports().length")==0)
    # a program error and some screens
    await pg.evaluate("()=>VypiskiShell.go('orders')");await pg.wait_for_timeout(300);await pg.evaluate("()=>VypiskiShell.go('search')");await pg.wait_for_timeout(300)
    await pg.evaluate("()=>{setTimeout(()=>{throw new Error('Проверка: сбой у Иванов Иван Иванович, СУ-123456')},0)}");await pg.wait_for_timeout(300)
    await pg.evaluate("()=>{Promise.reject(new Error('отказ обещания'))}");await pg.wait_for_timeout(2500)
    s=await pg.evaluate("()=>VypiskiCrash._session()")
    check('Ошибки сеанса записаны и замаскированы',len(s['errors'])==2 and 'Иванов' not in s['errors'][0]['m'] and '123456' not in s['errors'][0]['m'],s['errors'])
    check('Экраны записаны',any('экран: Реестр приказов' in c[1] for c in s['crumbs']) and any('экран: Поиск' in c[1] for c in s['crumbs']),[c[1] for c in s['crumbs']][-6:])
    # crash the page process
    cdp=await ctx.new_cdp_session(pg)
    print('crashing',flush=True)
    try:await asyncio.wait_for(cdp.send('Page.crash'),3)
    except Exception as e:print('crash sent',type(e).__name__,flush=True)
    await asyncio.sleep(1)
    pg2=await ctx.new_page();pg2.on("pageerror",lambda e:errs.append(str(e)));await pg2.goto(URL);await login(pg2)
    reps=await pg2.evaluate("()=>VypiskiCrash.reports()")
    check('После сбоя сохранён отчёт',len(reps)==1 and reps[0]['kind']=='restart',[(r['kind'],r['title']) for r in reps])
    await pg2.wait_for_selector('#crashBanner',timeout=8000);t=await pg2.inner_text('#crashBanner');check('Показано сообщение после входа','Отчёт сохранён' in t,t)
    await pg2.screenshot(path='/tmp/vypiski-test-out/crash-banner.png')
    await pg2.click("#crashBanner button:has-text('Открыть отчёт')");await pg2.wait_for_selector('#crashCard details[open] pre',timeout=8000)
    txt=await pg2.inner_text('#crashCard details[open] pre');print(txt)
    check('Отчёт: экран, ошибки, действия','последний экран: Поиск' in txt and 'Ошибки программы' in txt and 'Последние действия' in txt)
    check('Отчёт без ФИО и номеров','Иванов' not in txt and '123456' not in txt)
    await pg2.screenshot(path='/tmp/vypiski-test-out/crash-diag.png',full_page=True)
    async with pg2.expect_download() as d:await pg2.click("#crashCard details[open] button:has-text('Скачать .txt')")
    dlf=await d.value;check('Скачивание .txt',dlf.suggested_filename.startswith('vypiski-sboi-'),dlf.suggested_filename)
    # manual
    await pg2.click("#crashCard button:has-text('Создать отчёт сейчас')");n=await pg2.evaluate("()=>VypiskiCrash.reports().length");check('Отчёт вручную',n==2,n)
    # clean close → no report
    await pg2.close();pg3=await ctx.new_page();await pg3.goto(URL);await login(pg3);await pg3.wait_for_timeout(1500)
    check('Штатное закрытие: новых отчётов нет',await pg3.evaluate("()=>VypiskiCrash.reports().length")==2)
    check('Сообщение не повторяется',await pg3.locator('#crashBanner').count()==0)
    check('Без ошибок страницы (кроме проверочных)',all('Проверка' in e or 'отказ обещания' in e for e in errs),errs)
    await ctx.close()
  print(f"{sum(res)}/{len(res)}")
asyncio.run(main())
