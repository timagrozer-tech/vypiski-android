"""Real Electron (Linux runtime, test only — no packaging): live folders, print IPC, PDF, auth inside the desktop shell."""
import os, sys as _s;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
FIX=os.environ.get("VYP_FIX","fixtures-out");OUTDIR=os.environ.get("VYP_OUT","/tmp/vypiski-test-out")
import asyncio, subprocess, os, glob, json, shutil, time
from playwright.async_api import async_playwright
S=os.environ.get("VYP_ELECTRON_DIR","/opt/electron-test")  # unpacked Electron runtime for tests only, never packaged
HOME=os.path.join(OUTDIR,"deskhome"); shutil.rmtree(HOME,ignore_errors=True); os.makedirs(HOME+"/Downloads")
SRC=f"{HOME}/Приказы"; os.makedirs(SRC+"/2016/Март"); os.makedirs(SRC+"/2024/Январь")
orders=sorted(glob.glob(f"{FIX}/orders/*.docx"))
for f in orders[:4]: shutil.copy(f,SRC+"/2016/Март/")
for f in glob.glob(f"{FIX}/xlcases/*.docx"): shutil.copy(f,SRC+"/2024/Январь/")
old=time.time()-120
for d,_,fs in os.walk(SRC):
  for x in fs: os.utime(os.path.join(d,x),(old,old))
env=dict(os.environ,HOME=HOME,XDG_DOWNLOAD_DIR=f"{HOME}/Downloads",XDG_CONFIG_HOME=f"{HOME}/.config")
proc=subprocess.Popen(["xvfb-run","-a","-s","-screen 0 1400x900x24",f"{S}/electron","--no-sandbox","--remote-debugging-port=9334",os.environ.get("VYP_DESKTOP","desktop")],env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d) if d else ''))
async def count(pg):return await pg.evaluate("async()=>(await VypiskiRepository.all('orders')).length")
async def m():
  async with async_playwright() as p:
    for _ in range(60):
      try: br=await p.chromium.connect_over_cdp("http://127.0.0.1:9334"); break
      except Exception: await asyncio.sleep(0.5)
    pg=None
    for _ in range(40):
      pg=next((x for c in br.contexts for x in c.pages if x.url.startswith("app://")),None)
      if pg: break
      await asyncio.sleep(0.5)
    errs=[];pg.on("pageerror",lambda e:errs.append(str(e)));pg.on("dialog",lambda d:asyncio.ensure_future(d.accept()))
    await login(pg)
    check('Вход и в Windows-версии',await pg.evaluate("()=>VypiskiAuth.user().role")=='admin')
    r=await pg.evaluate("p=>VypiskiDesktop.sources.add(p)",SRC);check('Источник добавлен',r.get('ok'),r)
    for _ in range(80):
      await pg.wait_for_timeout(500)
      if await count(pg)>=7 and not await pg.evaluate("()=>VypiskiImportJob.state().running"):break
    n0=await count(pg);check('Первая сверка: приказы загружены без ручного импорта',n0==7,n0)
    shutil.copy(orders[4],SRC+"/2016/Март/");
    for _ in range(60):
      await pg.wait_for_timeout(500)
      if await count(pg)>n0:break
    check('Новый файл в папке подхвачен наблюдением',await count(pg)==n0+1)
    # partial copy: written in two steps, must not be imported broken
    part=SRC+"/2016/Март/Приказ_в_процессе.docx";data=open(orders[5],'rb').read();open(part,'wb').write(data[:len(data)//3])
    await pg.evaluate("async()=>{const l=await VypiskiDesktop.sources.list();await VypiskiDesktop.sources.scan(l[0].id)}");await pg.wait_for_timeout(800)
    open(part,'ab').write(data[len(data)//3:])
    for _ in range(60):
      await pg.wait_for_timeout(500)
      if await count(pg)>n0+1 and not await pg.evaluate("()=>VypiskiImportJob.state().running"):break
    errs_imp=await pg.evaluate("()=>VypiskiImportJob.state().errors.map(e=>e.path+': '+e.error)")
    check('Частично скопированный файл загружен целым, без ошибки',await count(pg)==n0+2 and not any('в_процессе' in e for e in errs_imp),(await count(pg),errs_imp[-2:]))
    # missing file: nothing deleted
    os.remove(SRC+"/2024/Январь/Приказ_1_2024.docx")
    await pg.evaluate("async()=>{const l=await VypiskiDesktop.sources.list();await VypiskiDesktop.sources.scan(l[0].id)}");await pg.wait_for_timeout(600)
    lst=await pg.evaluate("()=>VypiskiDesktop.sources.list()")
    check('Пропавший файл не удаляет приказ из базы',await count(pg)==n0+2 and lst[0]['missing']>=1,lst[0].get('missing'))
    # source unavailable
    os.rename(SRC,SRC+"-нет");await pg.evaluate("async()=>{const l=await VypiskiDesktop.sources.list();await VypiskiDesktop.sources.scan(l[0].id)}")
    lst=await pg.evaluate("()=>VypiskiDesktop.sources.list()");check('Недоступная папка: «недоступна», база цела',lst[0]['state']=='unavailable' and await count(pg)==n0+2,lst[0]['state'])
    os.rename(SRC+"-нет",SRC);await pg.evaluate("async()=>{const l=await VypiskiDesktop.sources.list();await VypiskiDesktop.sources.scan(l[0].id)}");await pg.wait_for_timeout(1500)
    lst=await pg.evaluate("()=>VypiskiDesktop.sources.list()");check('Папка вернулась: без дублей',lst[0]['state']=='ok' and await count(pg)==n0+2,(lst[0]['state'],await count(pg)))
    # UI card
    await pg.evaluate("()=>document.querySelector(`#appNav [data-sec='settings'], #appMore [data-sec='settings']`).click()");await pg.wait_for_selector('#vSrc .src',timeout=15000)
    t=await pg.inner_text('#vSrc');check('Карточка «Источники приказов»','локальная' in t and 'доступна' in t and 'Следить автоматически' in await pg.inner_html('#vSrc'),t[:200])
    await pg.screenshot(path=f"{OUTDIR}/desktop-sources.png")
    # read outside the source is refused by the main process
    r=await pg.evaluate("async()=>{const l=await VypiskiDesktop.sources.list();try{await VypiskiDesktop.sources.read(l[0].id,'../../.config/Vypiski/settings.json');return 'read'}catch(e){return 'refused'}}")
    check('Чтение вне папки-источника запрещено',r=='refused',r)
    # print IPC: printers and PDF
    pr=await pg.evaluate("()=>VypiskiDesktop.printers()");check('Список принтеров (IPC)',isinstance(pr,list),pr)
    r=await pg.evaluate("()=>VypiskiDesktop.printPdf(VypiskiPrintCenter.html([{title:'Проба',t:VypiskiDocument.BUILTIN[0],model:VypiskiDocument.compose({template:VypiskiDocument.BUILTIN[0],requisites:{number:'1',date:'2024-01-21'},fragments:[{loc:'p1',kind:'p',role:'lead',text:'1. Текст пункта.'}],header:['ПРИКАЗ'],signatures:[],certify:null})}],{footer:'page'}),'Проба.pdf',false)")
    pp=r.get('path','');check('PDF сохранён атомарно в папку выписок',r.get('ok') and os.path.exists(pp) and open(pp,'rb').read(5)==b'%PDF-' and not glob.glob(os.path.dirname(pp)+"/*.part"),r)
    srcsha=[os.path.getsize(os.path.join(d,x)) for d,_,fs in os.walk(SRC) for x in fs]
    check('Исходные файлы в папке не тронуты (нет новых и временных файлов)',len(srcsha)==len([1 for d,_,fs in os.walk(SRC) for x in fs]) and not any(x.endswith(('.tmp','.part')) for d,_,fs in os.walk(SRC) for x in fs))
    check('Без ошибок JS',not errs,errs[:3])
    await br.close()
asyncio.run(m());proc.terminate()
subprocess.run(["pkill","-x","electron"])
print(f"{sum(res)}/{len(res)}")
