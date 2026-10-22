"""12.79.12: group points — a table of enrolled people and a list «…следующих военнослужащих:» with one line per person — the excerpt
keeps the § , the lead, the table header, the person's own line and «Основание:»; the next numbered item is not a child of the list.
Synthetic order (mkgroup.py), invented names."""
import sys as _s,os;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
import asyncio,sys,glob,http.server,threading,functools,subprocess,json
from playwright.async_api import async_playwright
ROOT=sys.argv[1];FIX='/tmp/vypiski-test-out/groupcases'
_h=os.path.dirname(os.path.abspath(__file__));_mk=[p for p in [os.path.join(_h,'mkgroup.py'),os.path.join(_h,'..','fixtures','groups','mkgroup.py')] if os.path.exists(p)][0]
subprocess.run([sys.executable,_mk,FIX],check=True,stdout=subprocess.DEVNULL)
h=functools.partial(http.server.SimpleHTTPRequestHandler,directory=ROOT);h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h);threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d) if d else ''))
JS="""async([q,s,p])=>{const g=(await VypiskiCore.search({q,mode:'fio'})).groups[0];if(!g)return null;const a=await VypiskiWizard.auto({orderId:g.order.orderId,query:q,refs:s?[{s,p}]:null});return {ok:a.ok,why:a.why,others:(a.others||[]).length,trimmed:a.trimmed||0,frag:(a.fragments||[]).map(f=>f.text)}}"""
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();pg=await b.new_page();errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
    await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await pg.wait_for_timeout(1500);await login(pg)
    await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()");await pg.wait_for_timeout(500)
    await pg.set_input_files("#wordFiles",sorted(glob.glob(FIX+'/*.docx')));await pg.wait_for_timeout(1500)
    await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
    for _ in range(100):
      await pg.wait_for_timeout(300)
      if not await pg.evaluate("()=>VypiskiImportJob.state().running"):break
    a=await pg.evaluate(JS,['Березин Пётр Петрович','20',''])
    check('Таблица: в выписке только строка человека и шапка',a and a['ok'] and len(a['frag'])==5 and 'Березин Пётр Петрович' in a['frag'][3] and a['frag'][2].startswith('№ п/п') and a['frag'][4].startswith('Основание') and a['others']==0,a)
    a=await pg.evaluate(JS,['Ветров Сергей Сергеевич','21','1'])
    check('Список «следующих военнослужащих:» — только своя строка',a and a['ok'] and len(a['frag'])==4 and 'Ветрова' in a['frag'][2] and a['frag'][1].startswith('1. Полагать') and a['others']==0,a)
    a=await pg.evaluate(JS,['Особняков Тимур Ильич','',''])
    check('Следующий пункт «2.» без чужого «1. …:»',a and a['ok'] and [f[:2] for f in a['frag']]==['§ ','2.','Ос'],a)
    check('Без ошибок на странице',not errs,errs[:3])
    await b.close()
  print(f'{sum(res)}/{len(res)} passed');sys.exit(0 if all(res) else 1)
asyncio.run(main())
