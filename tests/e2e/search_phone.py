"""12.79.6: search on a phone — sticky query line, icon buttons, recent queries and examples, automatic search of a full name or
personal number, a wrong keyboard layout offered to fix, query variants (case, initials, personal number without a dash)."""
import os, sys as _s, subprocess;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
import asyncio, sys, http.server, threading, functools, glob
from playwright.async_api import async_playwright
ROOT=sys.argv[1];LC='/tmp/vypiski-test-out/leavecases/';os.makedirs(LC,exist_ok=True)
subprocess.run([sys.executable,os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','fixtures','leave','mkleave.py'),LC],check=True)
files=[LC+'Приказ 338 от 19.10.2026.docx']
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d)[:300] if d else ''))
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();ctx=await b.new_context(viewport={"width":412,"height":915},is_mobile=True,has_touch=True);pg=await ctx.new_page();errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
    await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await login(pg);await pg.evaluate("()=>localStorage.setItem('vypiski.s3.off','1')") # 12.79.12: the advanced (old) text search
    await pg.evaluate("()=>VypiskiShell.go('import')");await pg.wait_for_timeout(500)
    await pg.set_input_files("#wordFiles",files);await pg.wait_for_timeout(1500)
    await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
    while await pg.evaluate("()=>VypiskiImportJob.state().running"):await pg.wait_for_timeout(300)
    while await pg.evaluate("()=>VypiskiCore.waiting?.()||0"):await pg.wait_for_timeout(300)
    await pg.evaluate("()=>localStorage.setItem('vypiski.sx.hist',JSON.stringify(['Образцов Максим']))")
    # people mode
    await pg.evaluate("()=>VypiskiShell.go('search')");await pg.wait_for_timeout(800)
    await pg.tap("text=Люди и события");await pg.wait_for_timeout(800)
    w=await pg.evaluate("()=>{const f=document.querySelector('#sx .sx-find');return [f.getBoundingClientRect().width,f.getAttribute('aria-label'),document.querySelector('#sx .sx-q').placeholder,getComputedStyle(document.querySelector('#sx .sx-bar')).position]}")
    check('Кнопки-значки, короткая подсказка, строка закреплена',w[0]<=56 and w[1]=='Найти' and w[2]=='ФИО или личный номер' and w[3]=='sticky',w)
    t=await pg.inner_text('#sx');check('Недавние запросы и примеры','Недавние запросы' in t and 'Образцов Максим' in t and 'Примеры' in t,t[:200])
    await pg.tap("#sx .ms-start button:has-text('Образцов Максим')");await pg.wait_for_selector('#sx .sx-person',timeout=15000)
    check('Недавний запрос ищется нажатием','Образцов' in await pg.inner_text('#sx .sx-person'))
    async def people(q,auto=False):
        await pg.evaluate("()=>VypiskiShell.go('search')");await pg.wait_for_timeout(600)
        await pg.locator('#sx .sx-q').fill('');await pg.locator('#sx .sx-q').type(q,delay=20)
        if not auto:await pg.keyboard.press('Enter')
        for _ in range(40):
            await pg.wait_for_timeout(200)
            if await pg.locator('#sx .sx-person:has-text("Тестов")').count():break
        return await pg.inner_text('#sx')
    t=await people('Тестов Владимир',auto=True);check('ФИО полностью ищется без нажатия «Найти»','Тестов Владимир Евгеньевич' in t,t[:120])
    t=await people('СА-570942',auto=True);check('Личный номер ищется без нажатия «Найти»','Тестов' in t,t[:120])
    for q in ['са570942','ТЕСТОВ В.Е.','тестов']:
        t=await people(q);check(f'Вариант запроса «{q}»','Тестов' in t,t[:120])
    await pg.evaluate("()=>VypiskiShell.go('search')");await pg.wait_for_timeout(600)
    await pg.locator('#sx .sx-q').fill('Ntcnjd');await pg.wait_for_timeout(300)
    check('Подсказка раскладки (люди)',await pg.locator("#sx .ms-layout button:has-text('Искать «Тестов»')").count()==1)
    await pg.tap("#sx .ms-layout button");await pg.wait_for_selector('#sx .sx-person',timeout=8000)
    check('По подсказке найден человек','Тестов' in await pg.inner_text('#sx'))
    # text mode
    await pg.tap("text=Текст приказов");await pg.wait_for_timeout(800)
    hid=await pg.evaluate("()=>[...document.querySelectorAll('#dfs .sx-ym')].map(r=>getComputedStyle(r).display)")
    check('Пустая строка «Месяц» скрыта',hid[-1]=='none',hid)
    st=await pg.evaluate("()=>getComputedStyle(document.querySelector('#dfs .df-status')).display")
    check('Строка «Индекс: N из N» скрыта, когда индекс готов',st=='none',st)
    await pg.locator('#dfs .df-q').fill('Ntcnjd');await pg.locator('#dfs .df-q').dispatch_event('input');await pg.wait_for_timeout(300)
    await pg.tap("#dfs .ms-layout button");await pg.wait_for_timeout(1500)
    t=await pg.inner_text('#dfs');check('Текст: подсказка раскладки ищет «Тестов»',await pg.locator('#dfs .df-q').input_value()=='Тестов' and 'Приказ 338' in t,t[:200])
    one=await pg.evaluate("()=>{const t=document.querySelector('#dfs .df-tools');return t.scrollWidth>t.clientWidth||t.getBoundingClientRect().height<60}")
    check('Инструменты в одну строку',one)
    check('Без ошибок',not errs,errs);await b.close()
  print(f"{sum(res)}/{len(res)}")
asyncio.run(main())
