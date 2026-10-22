"""12.79.11: names in other cases — petrovich-based search and nominative («Абу», «Павлу», «Львом», «Любови»). Synthetic data only."""
import sys as _s,os;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
import asyncio,sys,glob,http.server,threading,functools
from playwright.async_api import async_playwright
import subprocess
ROOT=sys.argv[1];FIX='/tmp/vypiski-test-out/pvcases'
subprocess.run([sys.executable,os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','fixtures','names','mkpv.py'),FIX],check=True)
files=sorted(glob.glob(FIX+'/*.docx'))
h=functools.partial(http.server.SimpleHTTPRequestHandler,directory=ROOT);h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h);threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d) if d else ''))
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();ctx=await b.new_context(viewport={"width":1366,"height":900},locale="ru-RU");pg=await ctx.new_page()
    errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
    await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await pg.wait_for_timeout(1500);await login(pg)
    await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()");await pg.wait_for_timeout(500)
    await pg.set_input_files("#wordFiles",files);await pg.wait_for_timeout(1500)
    await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
    for _ in range(100):
      await pg.wait_for_timeout(300)
      if not await pg.evaluate("()=>VypiskiImportJob.state().running"):break
    for q in ['Шевченко Павел Олегович','Карабасов Абу Халиевич','Ильин Илья Сергеевич','Петров Лев Ильич','Сидорова Любовь Ивановна']:
      r=await pg.evaluate("async q=>{const r=await VypiskiCore.search({q,mode:'fio'});return r.groups.map(x=>(x.fragments||[]).length)}",q)
      check(f'Поиск «{q}» находит приказ',len(r)==1 and r[0]>=1,r)
    r=await pg.evaluate("async()=>{const r=await VypiskiCore.search({q:'Шевченко Пётр Олегович',mode:'fio'});return r.groups.length}")
    check('Другое имя не находится',r==0,r)
    names=await pg.evaluate("()=>{const t=`2. Ефрейтору Карабасову Абу Халиевичу, ЕВ-301002\\n4. Объявить благодарность младшему сержанту Петрову Льву Ильичу\\n5. Рядовой Сидоровой Любови Ивановне\\nсержанта Ильина Илью Сергеевича`;return t.split('\\n').flatMap(l=>VypiskiRegister.personsIn(l).map(p=>p.full))}")
    check('Именительный падеж имён (Абу, Лев, Любовь, Илья)',names==['Карабасов Абу Халиевич','Петров Лев Ильич','Сидорова Любовь Ивановна','Ильин Илья Сергеевич'],names)
    forms=await pg.evaluate("()=>VypiskiRegister.caseForms('Шевченко Павел Олегович')")
    check('Падежные формы (petrovich)','Шевченко Павлу Олеговичу' in forms and 'Шевченко Павла Олеговича' in forms,forms)
    check('Без ошибок на странице',not errs,errs[:3])
    await b.close()
  print(f'{sum(res)}/{len(res)} passed');sys.exit(0 if all(res) else 1)
asyncio.run(main())
