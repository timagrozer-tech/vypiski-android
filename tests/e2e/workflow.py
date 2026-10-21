import os, sys as _s;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login,PASSWORD
FIX=os.environ.get("VYP_FIX","fixtures-out");OUTDIR=os.environ.get("VYP_OUT","/tmp/vypiski-test-out")
import asyncio, sys, os, glob, http.server, threading, functools
from playwright.async_api import async_playwright
import openpyxl
ROOT=sys.argv[1]; OUT=f'{OUTDIR}/wfout'; os.makedirs(OUT,exist_ok=True)
files=sorted(glob.glob(f'{FIX}/xlcases/*.docx'))+sorted(glob.glob(f'{FIX}/orders/*.docx'))
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
URL=f"http://127.0.0.1:{srv.server_address[1]}/index.html"
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d) if d else ''))
async def imp(pg,fl):
  await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()");await pg.wait_for_timeout(600)
  if await pg.locator("#content button:has-text('Загрузить ещё')").count(): await pg.click("#content button:has-text('Загрузить ещё')");await pg.wait_for_timeout(300)
  await pg.set_input_files("#wordFiles",fl);await pg.wait_for_timeout(1500)
  await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
  for _ in range(100):
    await pg.wait_for_timeout(300)
    if not await pg.evaluate("()=>VypiskiImportJob.state().running"):break
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch()
    ctx=await b.new_context(viewport={"width":1366,"height":900},locale="ru-RU",accept_downloads=True);pg=await ctx.new_page()
    errs=[];pg.on("pageerror",lambda e:errs.append(str(e)));pg.on("dialog",lambda d:asyncio.ensure_future(d.accept('проверено по оригиналу')))
    await pg.goto(URL);await login(pg);await imp(pg,files)
    # excerpts via mass creation (leave events)
    n=await pg.evaluate("async()=>{await VypiskiRegisterView.build();const r=VypiskiRegisterView.rows().filter(x=>x.category==='Отпуск');VypiskiBatch.open(r);return r.length}")
    await pg.wait_for_selector('#bt .bt-foot');await pg.check('#bt .bt-foot input')
    async with pg.expect_download() as d: await pg.click("#bt .bt-foot button:has-text('Скачать ZIP')")
    await pg.wait_for_selector('#bt .bt-res')
    await pg.evaluate("()=>VypiskiShell.go('ready')");await pg.wait_for_selector('#content .wf-bar .wf-chip')
    chips=await pg.locator('#content .wf-bar .wf-chip.s0').count();check('Готовые выписки: статус «Черновик»',chips>=5,chips)
    check('Кнопки «Журнал выдачи» и «Очередь проверки»',await pg.locator('.wf-top button').all_inner_texts()==['Журнал выдачи','Очередь проверки'])
    item=pg.locator('#content .sh-item[data-excerpt-id]').first;eid=await item.get_attribute('data-excerpt-id')
    r=await pg.evaluate("async id=>{try{await VypiskiWorkflow.issue(id,{recipient:'Военкомат',basis:'запрос 1'});return 'issued'}catch(e){return e.message}}",eid)
    check('Нельзя выдать неодобренную выписку','одобренную' in r,r)
    r=await pg.evaluate("async id=>{try{await VypiskiWorkflow.transition(id,'approve');return 'ok'}catch(e){return e.message}}",eid)
    check('Нельзя перескочить «На проверке»','нельзя перейти' in r,r)
    await item.locator(".wf-bar button:has-text('На проверку')").click();await pg.wait_for_timeout(400)
    check('Черновик → На проверке',await item.locator('.wf-bar .wf-chip').first.inner_text()=='На проверке')
    r=await pg.evaluate("async id=>{const a=await VypiskiWorkflow.approval(id);try{await VypiskiWorkflow.transition(id,'approve',{expectRev:a.rev-1});return 'ok'}catch(e){return e.message}}",eid)
    check('Конфликт версий не теряет данные (оптимистическая блокировка)','другой пользователь' in r,r)
    await item.locator(".wf-bar button:has-text('Одобрить')").click();await pg.wait_for_timeout(400)
    check('На проверке → Одобрено',await item.locator('.wf-bar .wf-chip').first.inner_text()=='Одобрено')
    await item.locator(".wf-bar button:has-text('Выдать')").click();f=pg.locator('.wf-form input')
    await f.nth(0).fill('Военный комиссариат');await f.nth(1).fill('Запрос № 15 от 20.10.2026');await pg.click(".wf-form button:has-text('Записать')");await pg.wait_for_timeout(500)
    t=await item.inner_text();check('Выдача записана в журнал, статус «Выдано»','Записано в журнал выдачи' in await pg.inner_text('#content') and 'Выдано' in t,t[-120:])
    hist=await pg.evaluate("async id=>(await VypiskiWorkflow.approval(id)).events.map(e=>e.from+'→'+e.to)",eid);check('История согласования',hist==['Черновик→На проверке','На проверке→Одобрено','Одобрено→Выдано'],hist)
    await pg.click(".wf-top button:has-text('Журнал выдачи')");await pg.wait_for_selector('.wf-table');jt=await pg.inner_text('.wf-table')
    check('Журнал выдачи: запись с номером',f'{__import__("datetime").date.today().year}-0001' in jt and 'Военный комиссариат' in jt,jt[:200])
    async with pg.expect_download() as d: await pg.click("button:has-text('Скачать журнал XLSX')")
    dl=await d.value;jp=f'{OUT}/journal.xlsx';await dl.save_as(jp);ws=openpyxl.load_workbook(jp).active
    hdr=[c.value for c in ws[3]];check('Журнал XLSX без ФИО по умолчанию','ФИО (из выписки)' not in hdr and 'Получатель' in hdr and ws.auto_filter.ref,hdr)
    # actuality: another version of order 50
    await imp(pg,[f'{FIX}/wfcases/Приказ_50_2025_изм.docx',f'{FIX}/wfcases/Приказ_7_2024.docx',f'{FIX}/wfcases/Приказ_10_2024.docx'])
    await pg.evaluate("()=>VypiskiShell.go('ready')");await pg.wait_for_selector('#content .wf-bar .wf-chip');await pg.wait_for_timeout(800)
    bad=await pg.locator('#content .wf-bar .wf-chip.bad').count();check('Выписки по приказу № 50 помечены «требует проверки»',bad>=1,bad)
    # queue
    await pg.click(".wf-top button:has-text('Очередь проверки')");await pg.wait_for_selector('.wf-q',timeout=30000)
    types=set(await pg.locator('.wf-q .h .wf-chip:nth-child(2)').all_inner_texts());
    check('Очередь: актуальность, версии, пропуски номеров, ФИО',{'Актуальность выписки','Другая версия приказа','Пропуск номеров','Возможная ошибка в ФИО'}<=types,types)
    crit=await pg.locator(".wf-q:has-text('Выписка уже выдана') .wf-prio:not(.n)").count();check('Выданная выписка с изменившимся источником — критично',crit>=1)
    fio=await pg.inner_text(".wf-q:has(.wf-chip:text-is('Возможная ошибка в ФИО'))");check('ФИО: предложение с уверенностью, без объединения','Елесеев' in fio and 'уверенность' in fio and 'ничего не объединяет' in fio,fio[:200])
    q=pg.locator(".wf-q:has(.wf-chip:text-is('Другая версия приказа'))").first
    await q.locator("button:has-text('Сравнить версии')").click();await pg.wait_for_selector('.wf-diff .del')
    check('Сравнение версий подсвечивает отличия','2025-02-05' in await pg.inner_text('.wf-diff'))
    await q.locator('select').select_option('подтверждена');await q.locator("button:has-text('Сохранить')").click();await pg.wait_for_timeout(300)
    t=await pg.evaluate("()=>document.getElementById('vToast')?.textContent||''");check('Решение без основания не принимается','основание' in t,t)
    q=pg.locator(".wf-q:has(.wf-chip:text-is('Другая версия приказа'))").first
    await q.locator('select').select_option('подтверждена');await q.locator('input').fill('действует изменённая версия');await q.locator("button:has-text('Сохранить')").click();await pg.wait_for_timeout(800)
    check('Подтверждённая задача уходит из открытых',await pg.locator(".wf-q:has(.wf-chip:text-is('Другая версия приказа'))").count()==0)
    await pg.screenshot(path=f'{OUT}/queue.png')
    # notifications
    await pg.wait_for_timeout(200);await pg.evaluate("()=>document.querySelector('header .wf-bell').click()");await pg.wait_for_selector('#wfNotes')
    await pg.wait_for_timeout(1500);nt=await pg.evaluate("()=>document.getElementById('wfNotes')?.textContent||'NONE'");check('Уведомления: загрузка приказов и выдача','Загрузка приказов завершена' in nt and 'Выписка выдана' in nt,nt[:400])
    await pg.click("#wfNotes button:has-text('Отметить прочитанными')");await pg.wait_for_timeout(1500)
    badge=await pg.evaluate("()=>document.querySelector('header .wf-bell .n')?.textContent||''");check('После прочтения остаются только критичные',badge in ('','1','2','3'),badge)
    # palette & keys
    await pg.click('body');await pg.keyboard.press('Control+k');await pg.wait_for_selector('#wfPal input');await pg.keyboard.type('журнал');await pg.keyboard.press('Enter');await pg.wait_for_timeout(400)
    check('Ctrl+K → «Журнал выдачи»',await pg.inner_text('#content h2')=='Журнал выдачи выписок')
    await pg.keyboard.press('Control+k');await pg.keyboard.type('Елисеев');await pg.wait_for_timeout(300)
    pl=await pg.inner_text('#wfPal .l');check('Ctrl+K находит людей','Елисеев Роман Андреевич' in pl,pl[:120])
    await pg.keyboard.press('Escape');check('Esc закрывает палитру',await pg.locator('#wfPal').count()==0)
    await pg.click('body');await pg.keyboard.press('Control+f');await pg.wait_for_timeout(800);check('Ctrl+F → поиск',await pg.inner_text('#content h2')=='Поиск')
    await pg.click('body');await pg.keyboard.press('F1');await pg.wait_for_timeout(300);ht=await pg.evaluate("()=>document.getElementById('content').textContent")
    check('F1 → справка офлайн','Горячие клавиши' in ht and 'OCR' in ht and 'Справка к версии' in ht)
    # diagnostics
    await pg.evaluate("()=>VypiskiShell.go('diag')");await pg.wait_for_selector('.wf-diag button');await pg.click(".wf-diag button:has-text('Проверить сейчас')")
    await pg.wait_for_selector('.wf-res .ok',timeout=60000);dt=await pg.inner_text('.wf-res');check('«Проверить сейчас»: контрольные суммы приказов','Текст совпадает с контрольной суммой' in dt and 'НЕ совпадает' not in dt,dt[:300])
    st=await pg.inner_text('.wf-card:has-text("Статистика работы")');check('Статистика без ФИО','Выписок создано' in st and 'admin' in st and 'Елисеев' not in st)
    await pg.screenshot(path=f'{OUT}/diag.png',full_page=True)
    # template versions
    await pg.evaluate("()=>VypiskiShell.go('templates')");await pg.wait_for_selector('#content > select')
    await pg.click("#content button:has-text('Создать копию')");await pg.wait_for_timeout(800);await pg.wait_for_selector('.wf-tpl')
    await pg.wait_for_timeout(300);await pg.click("#content button:has-text('Сохранить') >> nth=0");await pg.wait_for_selector('.wf-tpl table')
    await pg.click(".wf-tpl button:has-text('Утвердить')");await pg.wait_for_selector(".wf-tpl .sh-ok");check('Шаблон утверждён',True)
    inp=pg.locator('#content .sh-form input').first;await inp.fill('Мой шаблон — изменён');await pg.click("#content button:has-text('Сохранить') >> nth=0");await pg.wait_for_selector('.wf-tpl .sh-bad')
    check('Изменение после утверждения заметно','изменён после утверждения' in await pg.inner_text('.wf-tpl'))
    revs=await pg.locator('.wf-tpl table tr').count()-1;check('Каждое сохранение — новая версия',revs>=2,revs)
    await pg.click(".wf-tpl button:has-text('Восстановить') >> nth=0")
    await pg.wait_for_selector('.wf-tpl .sh-ok',timeout=10000)
    check('Восстановление утверждённой версии создаёт новую версию',await pg.locator('.wf-tpl table tr').count()-1>revs)
    # register correction history
    await pg.evaluate("()=>VypiskiRegisterView.open()");await pg.wait_for_function("()=>/Разобрано/.test(document.querySelector('#rg .rg-status')?.textContent||'')",timeout=30000)
    await pg.click("#rg .xp-tabs button:has-text('Отчёты')");await pg.locator('#rg tbody tr:not(.grp)').first.click();await pg.wait_for_selector('#rgSheet:not([hidden]) details')
    await pg.click('#rgSheet details summary');fl=pg.locator('#rgSheet .fix input[data-k=fio]');old=await fl.input_value();await fl.fill(old+'-ТЕСТ')
    await pg.click("#rgSheet button:has-text('Сохранить исправление')");await pg.wait_for_timeout(300);e=await pg.inner_text('#rgSheet .sh-bad')
    check('Исправление без причины не сохраняется','причину' in e,e)
    await pg.fill('#rgSheet .fix input[type=text]:not([data-k])','опечатка распознавания');await pg.click("#rgSheet button:has-text('Сохранить исправление')");await pg.wait_for_timeout(1500)
    ev=await pg.evaluate("async()=>(await VypiskiAudit.list({limit:50,type:'register.corrected'}))[0]?.details")
    check('Журнал исправления: старое/новое, причина, автор',ev and ev['before']['fio']==old and ev['after']['fio']==old+'-ТЕСТ' and ev['reason']=='опечатка распознавания' and ev['actor']=='admin',ev)
    check('Без ошибок JS',not errs,errs[:3])
    await b.close()
  print(f'{sum(res)}/{len(res)}')
asyncio.run(main())
