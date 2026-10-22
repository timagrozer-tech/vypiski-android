"""12.79.3: bulk leave excerpts follow a sample excerpt — one point per excerpt (§, its lead, roman sub-heading, item, basis),
the order's signatures, «ВЕРНО:» and the blank lines exactly as in the sample."""
import os, sys as _s, subprocess;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
import asyncio, sys, http.server, threading, functools, zipfile, io, re, os
from playwright.async_api import async_playwright
ROOT=sys.argv[1];LC='/tmp/vypiski-test-out/leavecases/';os.makedirs(LC,exist_ok=True)
subprocess.run([sys.executable,os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','fixtures','leave','mkleave.py'),LC],check=True)
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d)[:400] if d else ''))
W='{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
def paras(data):
  from docx import Document
  d=Document(io.BytesIO(data));out=[]
  for p in d.paragraphs:
    pPr=p._p.pPr;jc=pPr.find(W+'jc').get(W+'val') if pPr is not None and pPr.find(W+'jc') is not None else 'left'
    jc={'end':'right','start':'left'}.get(jc,jc);t=p.text.strip().replace('‑','-')
    out.append((jc if t else '',t))
  return out
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();ctx=await b.new_context(viewport={"width":1366,"height":900},accept_downloads=True);pg=await ctx.new_page();errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
    await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await login(pg)
    await pg.evaluate("()=>VypiskiShell.go('import')");await pg.wait_for_timeout(500)
    await pg.set_input_files("#wordFiles",[LC+'Приказ 338 от 19.10.2026.docx',LC+'Выписка отпуск образец.docx']);await pg.wait_for_timeout(1500)
    await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
    await pg.wait_for_timeout(500)
    while await pg.evaluate("()=>VypiskiImportJob.state().running"):await pg.wait_for_timeout(300)
    while await pg.evaluate("()=>VypiskiCore.waiting?.()||0"):await pg.wait_for_timeout(300)
    T=await pg.evaluate("async()=>(await VypiskiSamples.savedList()).map(t=>({name:t.name,hg:t.headerGap,sg:t.signGap,cg:t.certifyGap,kind:t.structure.kind,os:t.structure.orderSignatures}))")
    check('Шаблон по образцу: отпуск, отступы 1/1/1, подписи приказа',len(T)==1 and T[0]['kind']=='Отпуск' and (T[0]['hg'],T[0]['sg'],T[0]['cg'])==(1,1,1) and T[0]['os'],T)
    await pg.evaluate("()=>VypiskiBatch.open()");await pg.wait_for_selector('#bk .bk-card')
    await pg.click("#bk .bk-row:has-text('Годы') button:text-is('2026')")
    await pg.click("#bk button:has-text('Найти события')");await pg.wait_for_selector('#bk .bk-job',timeout=30000)
    jobs=await pg.evaluate("()=>[...document.querySelectorAll('#bk .bk-job')].map(x=>x.innerText)")
    check('Найдены отпуска: по одному на пункт (командировка — нет)',len(jobs)==4 and not any('Командировочн' in j for j in jobs),jobs)
    await pg.click("#bk button:has-text('Подготовить выписки')");await pg.wait_for_selector("#bk .bk-conf:not([hidden])",timeout=60000)
    check('Все готовы',await pg.locator('#bk .bk-job .st.ok').count()==4,await pg.inner_text('#bk .bk-note >> nth=-1'))
    await pg.check('#bk .bk-conf input')
    async with pg.expect_download() as d: await pg.click("#bk button:has-text('Создать и скачать ZIP')")
    dl=await d.value;z=zipfile.ZipFile(await dl.path());docs={n:z.read(n) for n in z.namelist() if n.endswith('.docx')}
    check('В архиве 4 выписки',len(docs)==4,list(docs))
    sample=paras(open(LC+'Выписка отпуск образец.docx','rb').read())
    test=[v for k,v in docs.items() if 'Тестов' in k and '§ 5' not in ' '.join(t for _,t in paras(v))]
    got=paras(test[0]) if test else []
    diff=[(i,a,b) for i,(a,b) in enumerate(zip(sample,got)) if a!=b]
    check('Выписка Тестова совпадает с образцом построчно (текст, выравнивание, пустые строки)',len(sample)==len(got) and not diff,(len(sample),len(got),diff[:4]))
    allt=' '.join(t for _,t in got)
    check('Только пункт отпуска: без § 5 (прибытие)','§ 5' not in allt and 'Из отпуска' not in allt,allt[:200])
    other=[v for k,v in docs.items() if 'Образцов' in k][0];o=[t for _,t in paras(other) if t]
    check('Другая выписка: § 6, вводная, «I. В отпуск:», свой пункт 3, основание, подписи, ВЕРНО',o[5:9]==['§ 6','Полагать убывшими:','I. В отпуск:',o[8]] and o[8].startswith('3. Сержанта Образцова') and 'ВЕРНО:' in o and o[-1]=='С.Заверитель',o)
    check('Два события одного человека: имена файлов различаются пунктом',any('Тестов' in k and '§' in k for k in docs),list(docs))
    lst=z.read('_Список выписок.csv').decode('utf-8-sig').splitlines()
    check('В архиве список выписок (CSV) с личными номерами и пунктами',len(lst)==5 and 'Личный номер' in lst[0] and any('СА-570942' in l and '§ 6, п. 1' in l for l in lst),lst[:3])
    # direction filter + folders by order
    await pg.evaluate("()=>VypiskiBatch.open()");await pg.wait_for_selector('#bk .bk-card')
    await pg.evaluate("()=>{const d=document.querySelector('#bk details.bk-more-f');if(d)d.open=true}")
    await pg.click("#bk button.bk-chip:text-is('убыл в отпуск')")
    await pg.click("#bk button:has-text('Найти события')");await pg.wait_for_selector('#bk .bk-job',timeout=30000);await pg.wait_for_timeout(300)
    jobs=await pg.evaluate("()=>[...document.querySelectorAll('#bk .bk-job')].map(x=>x.innerText)")
    check('Фильтр «убыл в отпуск»: прибытие из отпуска (§ 5) не попадает',len(jobs)==3 and not any('§ 5' in j for j in jobs),jobs)
    await pg.select_option('#bk select:near(:text("Папки в архиве"))','order')
    await pg.click("#bk button:has-text('Подготовить выписки')");await pg.wait_for_selector("#bk .bk-conf:not([hidden])",timeout=60000)
    await pg.check('#bk .bk-conf input')
    async with pg.expect_download() as d: await pg.click("#bk button:has-text('Создать и скачать ZIP')")
    z2=zipfile.ZipFile(await (await d.value).path());nm=[n for n in z2.namelist() if n.endswith('.docx')]
    check('Папки по приказам',len(nm)==3 and all(n.startswith('Приказ № 338 ДСП от 19.10.2026/') for n in nm),nm)
    check('Без ошибок',not errs,errs);await b.close()
  print(f"{sum(res)}/{len(res)}")
asyncio.run(main())
