"""12.79.14: phone — Russian file buttons, quick search on the home screen, the bottom bar hides for the keyboard, «наверх». Synthetic data."""
import sys as _s,os;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login
import asyncio,sys,glob,http.server,threading,functools,subprocess
from playwright.async_api import async_playwright
ROOT=sys.argv[1];FIX='/tmp/vypiski-test-out/histcases'
_h=os.path.dirname(os.path.abspath(__file__));_mk=[p for p in [os.path.join(_h,'mkhist.py'),os.path.join(_h,'..','fixtures','history','mkhist.py')] if os.path.exists(p)][0]
subprocess.run([sys.executable,_mk,FIX],check=True)
h=functools.partial(http.server.SimpleHTTPRequestHandler,directory=ROOT);h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h);threading.Thread(target=srv.serve_forever,daemon=True).start()
res=[]
def check(n,ok,d=''):res.append(bool(ok));print(('PASS ' if ok else 'FAIL ')+n+(' — '+str(d) if d else ''))
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();ctx=await b.new_context(viewport={"width":390,"height":844},is_mobile=True,has_touch=True,locale="ru-RU");pg=await ctx.new_page();errs=[];pg.on("pageerror",lambda e:errs.append(str(e)))
    await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await pg.wait_for_timeout(1500);await login(pg)
    await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()");await pg.wait_for_timeout(500)
    await pg.set_input_files("#wordFiles",sorted(glob.glob(FIX+'/*.docx')));await pg.wait_for_timeout(1500)
    await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
    for _ in range(100):
      await pg.wait_for_timeout(300)
      if not await pg.evaluate("()=>VypiskiImportJob.state().running"):break
    await pg.evaluate("()=>VypiskiShell.go('create')");await pg.wait_for_timeout(600)
    t=await pg.inner_text('#content');check('Русская кнопка выбора файла, без «Choose File»','Выбрать файл' in t and 'Choose File' not in t and 'No file chosen' not in t)
    await pg.evaluate("()=>VypiskiShell.go('home')");await pg.wait_for_selector('#content .mq input')
    await pg.fill('#content .mq input','Солнцева Ивана');await pg.press('#content .mq input','Enter')
    await pg.wait_for_function("()=>document.querySelectorAll('#s3 tbody tr').length>=4",timeout=60000)
    check('Поиск с главной открывает «Текст приказов» с результатами',await pg.input_value('#s3 .s3-q input')=='Солнцева Ивана')
    # keyboard: the visual viewport shrinks while a field has focus
    await pg.focus('#s3 .s3-q input');await pg.set_viewport_size({"width":390,"height":420});await pg.wait_for_timeout(400)
    hid=await pg.evaluate("()=>getComputedStyle(document.getElementById('appNav')).display");check('Клавиатура открыта — нижняя панель скрыта',hid=='none',hid)
    await pg.evaluate("()=>document.activeElement.blur()");await pg.set_viewport_size({"width":390,"height":844});await pg.wait_for_timeout(500)
    shown=await pg.evaluate("()=>getComputedStyle(document.getElementById('appNav')).display");check('Клавиатура закрыта — панель снова видна',shown!='none',shown)
    await pg.evaluate("()=>VypiskiShell.go('templates')");await pg.wait_for_timeout(600);await pg.evaluate("()=>window.scrollTo(0,3000)");await pg.wait_for_timeout(400)
    v=await pg.evaluate("()=>{const t=document.getElementById('mTop');return !!t&&!t.hidden}");check('Длинный экран — кнопка «наверх»',v)
    if v:
      await pg.click('#mTop');await pg.wait_for_timeout(900);check('«Наверх» поднимает',await pg.evaluate("()=>scrollY<50"))
    await pg.evaluate("()=>VypiskiShell.go('batch')") if False else None
    await pg.evaluate("()=>document.querySelector(`#appMore [data-sec='batch']`).click()");await pg.wait_for_timeout(800)
    chips=await pg.evaluate("()=>[...document.querySelectorAll('#bk .bk-chip')].map(b=>b.textContent)");check('В массовом создании виды без повторов',len(chips)==len(set(chips)),chips)
    check('Без ошибок',not errs,errs[:3])
    await b.close()
  print(f'{sum(res)}/{len(res)} passed');sys.exit(0 if all(res) else 1)
asyncio.run(main())
