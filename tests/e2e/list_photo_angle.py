"""12.79.11: a list photographed at an angle on a table — the sheet is found, straightened, shadows and table rules removed before OCR. Synthetic image (mkpersp.py), invented names."""
import sys as _s,os;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
import asyncio,sys,http.server,threading,functools,base64
from playwright.async_api import async_playwright
import subprocess
ROOT=sys.argv[1];FIX='/tmp/vypiski-test-out/listcases'
_h=os.path.dirname(os.path.abspath(__file__))
for m in ['mklist.py','mkpersp.py']:subprocess.run([sys.executable,os.path.join(_h,'..','fixtures','list',m),FIX],check=True)
h=functools.partial(http.server.SimpleHTTPRequestHandler,directory=ROOT);h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h);threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d) if d else ''))
JS="""async([d,flat])=>{const bin=Uint8Array.from(atob(d),c=>c.charCodeAt(0));const f=new Blob([bin],{type:'image/jpeg'});
 const t0=performance.now();const im=await VypiskiListReq.prepImage(f,0,flat);const rows=await VypiskiListReq.readList(im.buf,()=>{});
 return {ms:Math.round(performance.now()-t0),info:im.info,rows:rows.map(x=>[x.fio,x.need])}}"""
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();pg=await b.new_page();errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
    await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await pg.wait_for_timeout(1500);await login(pg)
    ang=base64.b64encode(open(os.path.join(FIX,'Список под углом.jpg'),'rb').read()).decode()
    r=await pg.evaluate(JS,[ang,True])
    check('Лист под углом найден и выровнен',r['info'].get('flat'),r['info'])
    fios=[a for a,_ in r['rows']]
    want=['Тестов Владимир Евгеньевич','Образцов Максим Александрович','Халилов Халим Шинабекович','Неизвестный Иван Иванович','Командировочный Иван Петрович','Шевченко Павел Олегович']
    got=sum(1 for w in want if w in fios);check('Все 6 строк распознаны после выравнивания',got==6,(got,r['rows']))
    check('«Что нужно» на своих строках',dict(r['rows']).get('Шевченко Павел Олегович','').lower().startswith('периоды соч') and dict(r['rows']).get('Тестов Владимир Евгеньевич','').lower().startswith('отрыв'),r['rows'])
    check('Подготовка и распознавание быстрее 20 с',r['ms']<20000,r['ms'])
    flat=base64.b64encode(open(os.path.join(FIX,'Список.jpg'),'rb').read()).decode()
    r2=await pg.evaluate(JS,[flat,True])
    check('Ровный снимок не «выравнивается» зря',not r2['info'].get('flat'),r2['info'])
    check('Ровный снимок: 5 строк',len([1 for a,b in r2['rows'] if a and b])==5,r2['rows'])
    check('Без ошибок на странице',not errs,errs[:3])
    await b.close()
  print(f'{sum(res)}/{len(res)} passed');sys.exit(0 if all(res) else 1)
asyncio.run(main())
