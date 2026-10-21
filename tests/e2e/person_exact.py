"""12.77.4: a person is found exactly (surname+name in any case, or personal number) — in text search, the wizard and people search."""
import os, sys as _s;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
FIX=os.environ.get("VYP_FIX","fixtures-out")
import asyncio, sys, http.server, threading, functools
from playwright.async_api import async_playwright
ROOT=sys.argv[1];FX=f'{FIX}/wfcases/'
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d) if d else ''))
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();pg=await b.new_page(viewport={"width":1280,"height":900});errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
    await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await login(pg)
    await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()");await pg.wait_for_timeout(400)
    await pg.set_input_files("#wordFiles",[FX+'Приказ_158_2025.docx',FX+'Приказ_325_2025.docx']);await pg.wait_for_timeout(1500)
    await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
    while await pg.evaluate("()=>VypiskiImportJob.state().running"):await pg.wait_for_timeout(300)
    while await pg.evaluate("()=>VypiskiCore.waiting?.()||0"):await pg.wait_for_timeout(300)
    async def text(q):
      await pg.evaluate("()=>VypiskiShell.go('search')");await pg.wait_for_selector('#dfs .df-q');await pg.fill('#dfs .df-q',q);await pg.click('#dfs .df-go');await pg.wait_for_timeout(1500)
      return await pg.inner_text('#dfs')
    t=await text('Елисееву Дмитрию')
    check('Текст: «Елисееву Дмитрию» — только приказ с этим человеком','№ 158' in t or '158' in t,t[:200]);check('Текст: Елисеев Иван (приказ 325) не найден','325' not in t.split('Результатов')[-1][:20] and 'Ивану' not in t,t[t.find('Результатов'):][:80])
    for q in ['Елисеев Дмитрий','Елисееву Дмитрию Алексеевичу','СУ-067677','СУ 067677','су067677']:
      t=await text(q);check(f'Текст: «{q}» находит приказ № 158','Результатов: 1' in t,t[t.find('Результатов'):][:60])
    t=await text('Дмитрию');check('Одно слово «Дмитрию» по-прежнему ищет все упоминания','Результатов: 2' in t,t[t.find('Результатов'):][:60])
    # wizard from a search result
    await text('Елисееву Дмитрию');await pg.locator('#dfs >> text=Приказ_158_2025 >> visible=true').first.click()
    await pg.wait_for_timeout(500)
    btns=pg.locator("button:has-text('Создать выписку')")
    opened=False
    for i in range(await btns.count()):
      if await btns.nth(i).is_visible():await btns.nth(i).click();opened=True;break
    if not opened:
      oid=await pg.evaluate("async()=>(await VypiskiCore.allMeta()).find(m=>m.number==='158').orderId");await pg.evaluate("id=>VypiskiWizard.open({orderId:id,query:'Елисееву Дмитрию',mode:'fio'})",oid)
    await pg.wait_for_selector('#wz',timeout=10000);await pg.wait_for_timeout(800)
    sel=await pg.evaluate("()=>{const W=VypiskiWizard.state();return W.paras.filter(p=>W.selected.has(p.loc)).map(p=>p.text)}")
    picked=[x for x in sel if x[:3].strip('. ').isdigit()]
    check('Мастер: выбран только пункт 31 (Елисеев Дмитрий)',len(picked)==1 and picked[0].startswith('31.'),picked)
    oid=await pg.evaluate("async()=>(await VypiskiCore.allMeta()).find(m=>m.number==='158').orderId")
    for q in ['СУ-067677','Елисеев Д.А.','Елисеева Дмитрия Алексеевича']:
      await pg.evaluate("([id,q])=>VypiskiWizard.open({orderId:id,query:q,mode:'fio'})",[oid,q]);await pg.wait_for_timeout(800)
      sel=await pg.evaluate("()=>{const W=VypiskiWizard.state();return W.paras.filter(p=>W.selected.has(p.loc)).map(p=>p.text)}")
      picked=[x for x in sel if x[:3].strip('. ').isdigit()];check(f'Мастер «{q}»: только пункт 31',len(picked)==1 and picked[0].startswith('31.'),picked)
    # people & events
    await pg.evaluate("()=>VypiskiSearchEvents.open('')");await pg.wait_for_timeout(400)
    if await pg.locator("button:has-text('Подготовить таблицу')").count():await pg.click("button:has-text('Подготовить таблицу')")
    await pg.wait_for_function("()=>VypiskiRegisterView.builtAt()&&document.querySelector('#sx .sx-q')&&!document.querySelector('#sx .sx-find')",timeout=30000)
    for q,want in [('Елисеев Дмитрий',['Елисеев Дмитрий Алексеевич']),('Елисееву Дмитрию',['Елисеев Дмитрий Алексеевич']),('СУ-067677',['Елисеев Дмитрий Алексеевич']),('су 067677',['Елисеев Дмитрий Алексеевич']),('Елисеев',['Елисеев Дмитрий Алексеевич','Елисеев Иван Юрьевич'])]:
      await pg.fill('#sx .sx-q',q);await pg.wait_for_timeout(700);names=sorted(await pg.locator('#sx .sx-ph strong').all_inner_texts())
      check(f'Люди и события «{q}»',names==sorted(want),names)
    check('Без ошибок JS',not errs,errs[:3])
    await b.close()
  print(f'{sum(res)}/{len(res)}')
asyncio.run(main())
