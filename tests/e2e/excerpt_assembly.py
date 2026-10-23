"""12.79.15: excerpt assembly on order structures from real unit orders (synthetic, invented names): sub-items «а)», «2.1.»,
people without numbers under «I. …:», a table inside a numbered point, Word auto-numbering, initials in one § and the full name in another,
rank and name of the signer on one line. Every event is assembled like bulk creation and checked."""
import sys as _s,os;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
import asyncio,sys,glob,http.server,threading,functools,subprocess,json
from playwright.async_api import async_playwright
ROOT=sys.argv[1];FIX='/tmp/vypiski-test-out/stress'
_h=os.path.dirname(os.path.abspath(__file__));_mk=[p for p in [os.path.join(_h,'mkstress.py'),os.path.join(_h,'..','fixtures','stress','mkstress.py')] if os.path.exists(p)][0]
subprocess.run([sys.executable,_mk,FIX],check=True)
h=functools.partial(http.server.SimpleHTTPRequestHandler,directory=ROOT);h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h);threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d) if d else ''))
JS=r"""async()=>{const rows=await VypiskiRegisterView.scan(()=>true);const out={};
 for(const r of rows){if(r.mention)continue;const j=VypiskiBulk.group([r])[0];const a=await VypiskiBulk.prepareJob(j);
  const k=(r.full||r.fio).split(' ')[0]+'|'+r.ref;out[k]=a.ok?{fr:a.fragments.map(f=>(f.num?f.num+'. ':'')+f.text),others:(a.others||[]).length,sig:(a.signatures||[]).map(b=>b.lines.map(l=>l.role).join('+')),blocks:a.model.blocks.filter(b=>/^sign-/.test(b.role)).map(b=>b.role+':'+b.text)}:{why:a.why}}
 return out}"""
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();pg=await b.new_page();errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
    await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await pg.wait_for_timeout(1500);await login(pg)
    await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()");await pg.wait_for_timeout(500)
    await pg.set_input_files("#wordFiles",sorted(glob.glob(FIX+'/*.doc*')));await pg.wait_for_timeout(1500)
    await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
    for _ in range(100):
      await pg.wait_for_timeout(300)
      if not await pg.evaluate("()=>VypiskiImportJob.state().running"):break
    R=await pg.evaluate(JS)
    def fr(k):return R.get(k,{}).get('fr') or []
    st=lambda k:[x[:2] for x in fr(k)]
    check('Все события собраны',all('fr' in v for v in R.values()) and len(R)==30,{k:v.get('why') for k,v in R.items() if 'why' in v})
    check('«1. …, стрелку:» + «а)», «б)» — пункт этого человека целиком',st('Альфин|§ 1, п. 1')==['§ ','1.','а)','б)','Ос'],fr('Альфин|§ 1, п. 1'))
    check('«2.1.», «2.2.» входят в пункт 2, чужой «1.» не попадает',st('Бетин|§ 1, п. 2')==['§ ','2.','2.','2.','Ос'],fr('Бетин|§ 1, п. 2'))
    check('Без номеров под «I. В основной отпуск:» — только своя строка и своё основание',[x[:12] for x in fr('Эпсилонов|§ 4')]==['§ 4','Полагать убы','I. В основно','Ефрейтора Эп','Основание: р'],fr('Эпсилонов|§ 4'))
    check('«II. В командировку:» — свой подраздел',any(x.startswith('II.') for x in fr('Зетов|§ 4')) and not any(x.startswith('I. ') for x in fr('Зетов|§ 4')),fr('Зетов|§ 4'))
    k=[x for x in R if x.startswith('Лямбдин')][0];f=fr(k)
    check('Таблица в пункте: шапка и только своя строка',len(f)==5 and f[2].startswith('№') and 'Лямбдин' in f[3] and not any('Каппов' in x or 'Мюшин' in x for x in f),f)
    check('Следующий пункт «2.» после таблицы — без чужого «1. Нижепоименованных…»',st('Иотов|§ 2, п. 2')==['§ ','2.','Ос'],fr('Иотов|§ 2, п. 2'))
    check('Автонумерация Word: номер пункта в выписке',fr('Ксиев|§ 3, п. 2')[1].startswith('2. Рядовому Ксиеву') and fr('Нюшин|§ 3, п. 1')[1].startswith('1. Рядовому Нюшину'),(fr('Ксиев|§ 3, п. 2'),fr('Нюшин|§ 3, п. 1')))
    check('Инициалы в § 1, полное ФИО в § 2 — обе выписки',len(fr('Омегин|§ 1, п. 1'))==3 and 'Омегина П.Р.' in fr('Омегин|§ 1, п. 1')[1] and len(fr('Омегин|§ 2, п. 1'))==3,(fr('Омегин|§ 1, п. 1'),fr('Омегин|§ 2, п. 1')))
    bl=R.get('Гамов|§ 2, п. 1',{}).get('blocks',[])
    check('Подпись «полковник<Tab>А.Энский» — звание и фамилия отдельно',bl[:3]==['sign-position:КОМАНДИР ВОЙСКОВОЙ ЧАСТИ 55555','sign-rank:полковник','sign-name:А.Энский'],bl)
    check('Подписи не попадают в текст пункта',not any('Энский' in x or 'Штабов' in x for v in R.values() for x in v.get('fr',[])))
    f=fr('Хиев|§ 5');check('Таблица на нескольких страницах: шапка один раз',len(f)==5 and sum(1 for x in f if x.startswith('№'))==1 and 'Хиев' in f[3],f)
    f=fr('Сигмин|п. 2');check('Приказ без §: вводная часть, «ПРИКАЗЫВАЮ:» и свой пункт',[x[:6] for x in f]==['В целя','ПРИКАЗ','2. Ряд'],f)
    check('Двое в одном пункте — предупреждение «групповой пункт»',R.get('Пиев|п. 1',{}).get('others')==1,R.get('Пиев|п. 1'))
    bl=R.get('Сигмин|п. 2',{}).get('blocks',[]);check('«ВрИО …», «гвардии подполковник»; «М.П.» и «Исп.» не в выписке',bl==['sign-position:ВрИО КОМАНДИРА ВОЙСКОВОЙ ЧАСТИ 55555','sign-rank:гвардии подполковник','sign-name:С.Временный'],bl)
    check('Word 97 (.doc) с автонумерацией: номер пункта в выписке',fr('Чаев|§ 6, п. 2')[1:2]==['2. Рядовому Чаеву Олегу Ильичу, ЕА-100021, объявить благодарность.'] and fr('Цветов|§ 6, п. 1')[1].startswith('1. Рядовому Цветову'),(fr('Чаев|§ 6, п. 2'),fr('Цветов|§ 6, п. 1')))
    f=fr('Галкин|§ 3, п. 4');check('Заголовок военкомата в две строки — над своими людьми, чужой заголовок не попадает',[x[:12] for x in f]==['§ 3','Нижепоименов','ИЗ ВОЕННОГО ','И ЛЕСНОГО РА','4. Рядового ','Основание: и'] and 'ЭНСК' not in ' '.join(f),f)
    f=fr('Бобрин|§ 4, п. 2');check('Строка «дата рождения + личный номер» — часть пункта, не пункт 11',[x[:10] for x in f]==['§ 4','Нижепоимен','ИЗ ВОЕННОГ','2. Ефрейто','11.11.1991','Основание:'],f)
    f=fr('Волчин|§ 5, п. 2');check('«1. Нижепоименованный личный состав …:» над автонумерованным списком — у каждого',[x[:10] for x in f]==['§ 5','Полагать у','1. Нижепои','с 11 ноябр','2. Рядовог','Основание:'],f)
    check('Общее «Основание:» в конце группы — у каждого из группы',all(any(x.startswith('Основание') for x in fr(k)) for k in ['Аистов|§ 3, п. 1','Бобрин|§ 3, п. 2','Аистов|§ 4, п. 1','Аистов|§ 5, п. 1']))
    check('Без ошибок',not errs,errs[:3])
    await b.close()
  print(f'{sum(res)}/{len(res)} passed');sys.exit(0 if all(res) else 1)
asyncio.run(main())
