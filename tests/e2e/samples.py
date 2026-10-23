"""12.79: «Образец выписки» — a finished Word excerpt is matched to its order, compared word by word, a template is made from it automatically, remade, batch-checked."""
import os, sys as _s, subprocess;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
FIX=os.environ.get("VYP_FIX","fixtures-out")
import asyncio, sys, http.server, threading, functools, os, zipfile, re, shutil
from playwright.async_api import async_playwright
ROOT=sys.argv[1];FX=FIX+'/';SC='/tmp/vypiski-test-out/smpcases/';os.makedirs(SC,exist_ok=True)
subprocess.run([sys.executable,os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','fixtures','samples','mksmp.py'),SC],check=True)
files=[FX+'wfcases/Приказ_158_2025.docx',FX+'wfcases/Приказ_325_2025.docx']
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d)[:300] if d else ''))
async def upload(pg,paths):
    await pg.evaluate("()=>VypiskiSamples.page()");await pg.locator('#smp input[type=file]').wait_for(state='attached')
    await pg.set_input_files('#smp input[type=file]',paths)
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();ctx=await b.new_context(viewport={"width":417,"height":900},accept_downloads=True);pg=await ctx.new_page();errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
    await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await login(pg)
    await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()");await pg.wait_for_timeout(400)
    await pg.set_input_files("#wordFiles",files);await pg.wait_for_timeout(1500)
    await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
    while await pg.evaluate("()=>VypiskiImportJob.state().running"):await pg.wait_for_timeout(300)
    # navigation
    await pg.evaluate("()=>VypiskiShell.go('home')");await pg.wait_for_timeout(500)
    check('Плитка «Образец выписки» на главной',await pg.locator(".u3-tile[data-u3='samples']").count()==1)
    check('Пункт в «Ещё»',await pg.locator("#appMore [data-sec='samples']").count()==1)
    await pg.click(".u3-tile[data-u3='samples']");await pg.wait_for_timeout(300)
    check('Открывается экран образца',await pg.locator('#smp .smp-drop').count()==1)
    # 1) wizard-made excerpt round trip
    oid=await pg.evaluate("async()=>(await VypiskiCore.allMeta()).find(m=>m.number==='158').orderId")
    await pg.evaluate("id=>VypiskiWizard.open({orderId:id,query:'Елисееву Дмитрию',mode:'fio'})",oid);await pg.locator('#wz .wz-p').first.wait_for()
    await pg.click("#wz button:has-text('Проверить и сохранить')");await pg.locator('#wz .wz-fastsum').wait_for()
    await pg.check('#wz .wz-check input')
    async with pg.expect_download() as d:await pg.click("#wz button:has-text('Скачать DOCX')")
    f=await d.value;own=SC+'Из_мастера.docx';await f.save_as(own)
    await upload(pg,[own]);await pg.locator('#smp .smp-sum').wait_for(timeout=20000)
    t=await pg.inner_text('#smp')
    check('Выписка из мастера: найден приказ № 158','Приказ № 158 от 06.06.2025' in t,t[:200])
    L=await pg.evaluate("()=>{const c=VypiskiSamples.last().f.best.cmp;return [c.ok,c.diff,c.none]}")
    check('Выписка из мастера: все абзацы дословно',L[0]>=3 and L[1]==0 and L[2]==0,L)
    # 2) non-standard hand-made sample
    await upload(pg,[SC+'Выписка_158_Елисеев.docx']);await pg.locator('#smp .smp-sum').wait_for(timeout=20000)
    t=await pg.inner_text('#smp');L=await pg.evaluate("()=>{const x=VypiskiSamples.last();const c=x.f.best.cmp;return [c.ok,c.diff,c.none,x.f.how]}")
    check('Нестандартный образец: приказ найден по номеру и дате',L[3]=='по номеру и дате','Приказ № 158' in t and L)
    check('Нестандартный образец: 4 абзаца дословно',L[:3]==[4,0,0],L)
    P=await pg.evaluate("()=>{const x=VypiskiSamples.last();return {title:x.parsed.title,cert:x.parsed.certify,head:x.parsed.header.map(p=>p.text)}}")
    check('Распознаны заголовок и «ВЕРНО:»',P['title'][0]=='ВЫПИСКА' and P['cert'] and P['cert']['name']=='И.Петров' and P['cert']['rank']=='капитан' and P['cert']['lines']==['Начальник строевой части'],P)
    check('Оформление прочитано (12 пт, поля 30/15)','12 пт' in t and 'поля 20/15/20/30 мм' in t,t[t.find('Оформление'):][:200])
    check('Состав: §, вводная, пункт, основание',all(k in t for k in ['§','вводная часть','пункты','основание']),t[t.find('Взято'):][:120])
    check('Шаблон создан автоматически','Шаблон создан автоматически' in t,t[t.find('✓'):][:150])
    await pg.check('#smp .wz-check input');await pg.wait_for_timeout(500)
    T=await pg.evaluate("async()=>{const d=await VypiskiCore.getState('defaultTemplate');const t=await VypiskiDocument.template(d.id);return {id:d.id,fs:t.fontSize,m:t.margins,h:t.header,title:t.title,s:t.structure,fl:t.firstLineMm}}")
    check('Шаблон сохранён и выбран по умолчанию',T['id'].startswith('sample:') and T['fs']==12 and T['m']['left']==30 and T['m']['right']==15 and abs(T['fl']-12.5)<0.2,T)
    check('В шаблоне подстановки шапки',T['h']==['из приказа {{заголовок_приказа}}','{{дата_прописью}} № {{номер}}','{{место}}'] and T['title']=='ВЫПИСКА',T['h'])
    check('В шаблоне «ВЕРНО:» и без подписей приказа',T['s']['certify']['name']=='И.Петров' and T['s']['orderSignatures']==False,T['s'])
    # remake from the sample
    await upload(pg,[SC+'Выписка_158_Елисеев.docx']);await pg.locator('#smp .smp-sum').wait_for(timeout=20000)
    await pg.click("#smp button:has-text('Сделать такую же выписку заново')");await pg.locator('#wz').wait_for();await pg.wait_for_timeout(800)
    W=await pg.evaluate("()=>{const t=document.querySelector('#wz').innerText;return t}")
    check('Мастер открыт на «Проверке пунктов»','Проверка пунктов' in W or 'Отмечено' in W,W[:200])
    n=await pg.evaluate("()=>document.querySelectorAll('#wz input[type=checkbox]:checked').length");check('Отмечены ровно абзацы образца',n==4,n)
    # go to preview and check composed text = sample, certify present, no order signatures
    for _ in range(2):
        for cb in await pg.locator('#wz .wz-check input:not(:checked):not([disabled])').all():
          if await cb.is_visible() and await cb.is_enabled():await cb.check()
        nb=pg.locator('#wz .wz-next')
        if await nb.count() and await nb.first.is_enabled():await nb.first.click();await pg.wait_for_timeout(800)
    await pg.wait_for_timeout(1500);W=await pg.evaluate("()=>document.querySelector('#wz').innerText+' '+[...document.querySelectorAll('#wz iframe')].map(f=>f.contentDocument?.body?.innerText||'').join(' ')")
    check('В просмотре «ВЕРНО:» из образца и нет подписи командира','И.Петров' in W and 'А.Смирнов' not in W,W[-400:])
    # 3) sample with a changed word
    await upload(pg,[SC+'Выписка_158_с_ошибкой.docx']);await pg.locator('#smp .smp-sum').wait_for(timeout=20000)
    L=await pg.evaluate("()=>{const c=VypiskiSamples.last().f.best.cmp;return [c.ok,c.diff,c.none]}")
    ins=await pg.locator('#smp ins').all_inner_texts();dl=await pg.locator('#smp del').all_inner_texts()
    await pg.screenshot(path='/tmp/vypiski-test-out/samples_diff.png',full_page=True)
    check('Изменённый образец: расхождение найдено',L==[3,1,0] and 'разведывательного' in ins and 'стрелкового' in dl,[L,ins,dl])
    # 4) no such order
    await upload(pg,[SC+'Выписка_999_нет.docx']);await pg.wait_for_timeout(2500)
    t=await pg.inner_text('#smp');check('Нет приказа — понятное сообщение','Приказ не найден' in t and '№ 999' in t,t[:250])
    # 5) no number in the header → by personal number
    await upload(pg,[SC+'Выписка_без_номера.docx']);await pg.locator('#smp .smp-sum').wait_for(timeout=20000)
    L=await pg.evaluate("()=>{const x=VypiskiSamples.last();return [x.f.how,x.f.best.meta.number,x.f.best.cmp.ok]}")
    check('Без номера: найден по личному номеру',L==['по личному номеру','158',2],L)
    # 6) batch
    await upload(pg,[SC+'Выписка_158_Елисеев.docx',SC+'Выписка_158_с_ошибкой.docx',SC+'Выписка_999_нет.docx',own])
    await pg.locator("#smp button:has-text('Скачать отчёт CSV')").wait_for(timeout=30000)
    t=await pg.inner_text('#smp');check('Пакет: 4 файла, 2 с замечаниями','Проверено файлов: 4' in t and 'с замечаниями: 2' in t,t[:200])
    async with pg.expect_download() as d:await pg.click("#smp button:has-text('Скачать отчёт CSV')")
    f=await d.value;pth=await f.path();csv=open(pth,encoding='utf-8-sig').read()
    check('CSV отчёт',csv.count('\n')==4 and 'есть замечания' in csv and 'совпадает' in csv,csv[:300])
    await pg.evaluate("()=>VypiskiSamples.page()");await pg.wait_for_timeout(500)
    t=await pg.inner_text('#smp');check('Список шаблонов по образцам','Шаблоны по образцам' in t,t[-400:])
    n=await pg.evaluate("async()=>(await VypiskiSamples.savedList()).length");S=await pg.evaluate("async()=>(await VypiskiSamples.savedList()).map(t=>t.structure.samples.length)")
    check('Повторная загрузка того же образца не плодит шаблоны (5 разных структур)',n==5 and max(S)==1,[n,S])
    await pg.screenshot(path='/tmp/vypiski-test-out/samples.png',full_page=True)
    check('Без ошибок',not errs,errs);await b.close()
  print(f"{sum(res)}/{len(res)}")
asyncio.run(main())
