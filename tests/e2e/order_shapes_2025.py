"""12.79.17: excerpts from orders shaped like real 2025 orders (synthetic, invented names): only the signers with a position
(no drafter visas), no other person's point with a non-Russian name, only the unit label of this person, the same man in
capitals is not «another person» when searched by surname only."""
import sys as _s,os;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
import asyncio,sys,glob,http.server,threading,functools,json,subprocess
from playwright.async_api import async_playwright
ROOT=sys.argv[1];FIX='/tmp/vypiski-test-out/cases2025'
_h=os.path.dirname(os.path.abspath(__file__));_mk=[p for p in [os.path.join(_h,'mkcases.py'),os.path.join(_h,'..','fixtures','cases2025','mkcases.py')] if os.path.exists(p)][0]
subprocess.run([sys.executable,_mk,FIX],check=True)
h=functools.partial(http.server.SimpleHTTPRequestHandler,directory=ROOT);h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h);threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d) if d and not ok else ''))
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();pg=await b.new_page();errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
    await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await pg.wait_for_timeout(1500);await login(pg)
    await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()");await pg.wait_for_timeout(500)
    await pg.set_input_files("#wordFiles",sorted(glob.glob(FIX+'/*.docx')));await pg.wait_for_timeout(1500)
    await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
    for _ in range(200):
      await pg.wait_for_timeout(300)
      if not await pg.evaluate("()=>VypiskiImportJob.state().running"):break
    r=await pg.evaluate("""async()=>{const rows=(await VypiskiRegisterView.scan(()=>true)).filter(r=>!r.mention&&/Ивашк/i.test(JSON.stringify(r)));const out={};
      const sh=a=>({ok:a.ok,why:a.why,others:a.others,f:(a.fragments||[]).map(f=>String(f.text).trim()),s:(a.signatures||[]).map(b=>b.lines.map(l=>l.text.trim()).join(' / '))});
      for(const r of rows){const j=VypiskiBulk.group([r])[0];const a=await VypiskiBulk.prepareJob(j);const n=(/№\\s*(\\d+)/.exec(a.name||'')||[])[1]||(/(60\\d)/.exec(JSON.stringify(r))||[])[1];
       out[n]=sh(a);out[n+'s']=sh(await VypiskiBulk.prepareJob({orderId:j.orderId,full:'Ивашкин',fio:'Ивашкин',rows:[],ref:''}))}return out}""")
    print(sorted(r.keys()))
    for n in ['601','602','603','604']:
      x=r.get(n) or {}
      check(f'{n}: выписка собрана',x.get('ok'),x)
      check(f'{n}: подписи — командир и один ВрИО НШ, без виз исполнителей',len(x.get('s',[]))==2 and 'Штабной' in x['s'][1],x.get('s'))
    f=lambda n:' | '.join(r.get(n,{}).get('f',[]))
    check('602: нет чужого пункта 1191 (нерусское имя)','1191' not in f('602') and '1229.' in f('602'),f('602'))
    check('603: только «Танковый батальон», без чужой метки','Танковый батальон' in f('603') and '3 Стрелковый' not in f('603'),f('603'))
    check('604: метка следующего подразделения после основания не попала','1 Стрелковый' in f('604') and '3 Стрелковый' not in f('604'),f('604'))
    check('601: пункт с выплатами целиком, без чужих','Основание: приказ командира дивизии' in f('601') and 'Сидоренко' not in f('601'),f('601'))
    for n in ['601s','602s']:
      x=r.get(n) or {}
      check(f'{n[:3]} (поиск по фамилии): не «групповой пункт»',x.get('ok') and not x.get('others'),x.get('others'))
    check('602 (поиск по фамилии): без пункта 1191','1191' not in f('602s'),f('602s'))
    check('Без ошибок',not errs,errs[:3])
    await b.close()
  print(f'{sum(res)}/{len(res)} passed');sys.exit(0 if all(res) else 1)
asyncio.run(main())
