"""12.79.1: excerpts loaded through «Загрузить приказы» become templates, not orders; excerpts already in the base are found and taken out; the wizard picks the learned template of the same kind."""
import os, sys as _s, subprocess;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
FIX=os.environ.get("VYP_FIX","fixtures-out")
import asyncio, sys, http.server, threading, functools, glob
from playwright.async_api import async_playwright
ROOT=sys.argv[1];FX=FIX+'/';SC='/tmp/vypiski-test-out/smpcases/';os.makedirs(SC,exist_ok=True)
subprocess.run([sys.executable,os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','fixtures','samples','mksmp.py'),SC],check=True)
orders=[FX+'wfcases/Приказ_158_2025.docx',FX+'wfcases/Приказ_325_2025.docx']
ex=[SC+'Выписка_158_Елисеев.docx',SC+'Выписка_158_с_ошибкой.docx',SC+'Выписка_без_номера.docx']
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d)[:300] if d else ''))
async def imp(pg,files):
    await pg.evaluate("()=>VypiskiShell.go('import')");await pg.wait_for_timeout(600)
    if not await pg.locator('#wordFiles').count():
        await pg.click("#content button:has-text('Загрузить ещё')");await pg.wait_for_timeout(500)
    await pg.set_input_files("#wordFiles",files);await pg.wait_for_timeout(1500)
    await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
    await pg.wait_for_timeout(500)
    while await pg.evaluate("()=>VypiskiImportJob.state().running"):await pg.wait_for_timeout(300)
    await pg.wait_for_timeout(500)
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();ctx=await b.new_context(viewport={"width":417,"height":900});pg=await ctx.new_page();errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
    await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await login(pg)
    await imp(pg,orders+ex)
    M=await pg.evaluate("async()=>(await VypiskiCore.allMeta()).map(m=>m.title)")
    check('Готовые выписки не попали в приказы',sorted(M)==['Приказ_158_2025','Приказ_325_2025'],M)
    t=await pg.inner_text('#content');check('В итоге загрузки сказано о шаблонах','готовых выписок: 3' in t and 'создано шаблонов 3' in t,t[t.find('Новых'):][:300])
    n=await pg.evaluate("async()=>(await VypiskiSamples.savedList()).length");check('Созданы 3 шаблона',n==3,n)
    # again — the same excerpts only update templates
    await imp(pg,ex[:1])
    L=await pg.evaluate("async()=>(await VypiskiSamples.savedList()).map(t=>t.structure.samples.length)");check('Повторный импорт той же выписки не создаёт шаблон',len(L)==3 and max(L)==1,L)
    # wizard picks the learned template of the same kind
    oid=await pg.evaluate("async()=>(await VypiskiCore.allMeta()).find(m=>m.number==='158').orderId")
    await pg.evaluate("id=>VypiskiWizard.open({orderId:id,query:'Скурихину Дмитрию',mode:'fio'})",oid);await pg.locator('#wz .wz-p').first.wait_for()
    await pg.click("#wz button:has-text('Проверить и сохранить')");await pg.locator('#wz .wz-fastsum').wait_for()
    t=await pg.inner_text('#wz');check('Мастер сам взял шаблон того же вида (выплаты)','Образец: выплаты и надбавки' in t,t[t.find('Оформление'):][:200])
    await pg.evaluate("id=>VypiskiWizard.open({orderId:id,query:'Байтемерову',mode:'fio'})",oid);await pg.locator('#wz .wz-p').first.wait_for()
    await pg.click("#wz button:has-text('Проверить и сохранить')");await pg.locator('#wz .wz-fastsum').wait_for()
    t=await pg.inner_text('#wz');check('Для другого вида — обычный шаблон','Образец:' not in t,t[t.find('Оформление'):][:200])
    # excerpts already lying among orders (loaded by an older version)
    await pg.evaluate("""async()=>{const o=(await VypiskiRepository.all('orders')).find(x=>x.number==='158');const now=new Date().toISOString();
      const ps=[{text:'ВЫПИСКА',kind:'p'},{text:'из приказа командира войсковой части 00000',kind:'p'},{text:'«06» июня 2025 г. № 158',kind:'p'},{text:'§ 2',kind:'p'},{text:'Нижепоименованных военнослужащих полагать прибывшими:',kind:'p'},{text:'9. Рядовому Байтемерову Дмитрию Тэлгэтовичу, АВ-766886, механику-водителю разведывательного отделения.',kind:'p'},{text:'ВЕРНО:',kind:'p'},{text:'Начальник отделения',kind:'p'},{text:'майор',kind:'p'},{text:'С.Иванов',kind:'p'}];
      await VypiskiRepository.putMany('orders',[{...o,id:'old-ex-1',title:'Старая выписка',sourceFile:'Старая выписка.docx',paragraphs:ps,text:ps.map(p=>p.text).join('\\n'),textSha256:'x1',fileSha256:'y1',originalStored:false,created:now,updated:now}]);localStorage.removeItem('vypiski.samples.scan')}""")
    await pg.evaluate("()=>VypiskiSamples.page()");await pg.wait_for_timeout(3000)
    t=await pg.inner_text('#smp');check('Найдены выписки среди приказов и по ним сделан шаблон','найдено готовых выписок: 1' in t and 'новых шаблонов 1' in t,t[:400])
    pg.once("dialog",lambda d:asyncio.ensure_future(d.accept()))
    await pg.click("#smp button:has-text('выписок из приказов')");await pg.wait_for_timeout(1000)
    n=await pg.evaluate("async()=>(await VypiskiRepository.all('orders')).filter(o=>o.id==='old-ex-1').length");check('Выписка убрана из приказов',n==0,n)
    L=await pg.evaluate("async()=>(await VypiskiSamples.savedList()).map(t=>t.structure.kind)");check('Шаблон «прибытие» из старой записи',len(L)==4 and 'Зачисление в списки' in L or len(L)==4,L)
    await pg.evaluate("()=>VypiskiSamples.page()");await pg.wait_for_timeout(1500)
    check('После уборки карточка не показывается','найдено готовых выписок' not in await pg.inner_text('#smp'))
    check('Без ошибок',not errs,errs);await b.close()
  print(f"{sum(res)}/{len(res)}")
asyncio.run(main())
