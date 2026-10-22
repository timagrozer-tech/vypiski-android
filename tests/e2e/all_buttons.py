"""12.77.11: presses every button on every screen (except deleting, resetting, locking) and fails on any JavaScript error. Args: ASSETS [WIDTH]."""
import os, sys as _s;_s.path.insert(0,os.path.dirname(os.path.abspath(__file__)));from authhelp import login,PASSWORD
FIX=os.environ.get("VYP_FIX","fixtures-out")
os.makedirs('/tmp/vypiski-test-out',exist_ok=True)
import asyncio, sys, glob, http.server, threading, functools, re, json
from playwright.async_api import async_playwright
ROOT=sys.argv[1];W=int(sys.argv[2]) if len(sys.argv)>2 else 390
files=sorted(glob.glob(FIX+'/xlcases/*.docx'))+sorted(glob.glob(FIX+'/orders/*.docx'))+glob.glob(FIX+'/wfcases/Приказ_158_2025.docx')+glob.glob(FIX+'/wfcases/Приказ_325_2025.docx')
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
SKIP=re.compile(r'парол|Учётн|Пользовател|Удал|Очист|Сброс|Выход|Заблок|Аннулир|Восстанов|Забыть|Выйти|Сменить пароль|Новый пароль|Перенос рабочего|Отключ|Удалить все|Остановить|Отмена загрузки|Пересобрать|Перестроить',re.I)
SCREENS=["VypiskiShell.go('home')","VypiskiShell.go('orders')","VypiskiShell.go('search')","VypiskiSearchEvents.open('Иванов')","VypiskiSearchEvents.open('')","VypiskiRegisterView.open()","VypiskiShell.go('create')","VypiskiBulk.open()","VypiskiShell.go('ready')","VypiskiShell.go('templates')","VypiskiShell.go('backup')","VypiskiShell.go('diag')","VypiskiWorkflow.queuePage()","VypiskiWorkflow.journalPage()","VypiskiShell.go('import')","document.querySelector(`#appNav [data-sec=settings], #appMore [data-sec=settings]`).click()","VypiskiWorkflow.helpPage?.()"]
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();ctx=await b.new_context(viewport={"width":W,"height":844},accept_downloads=True,has_touch=W<900);pg=await ctx.new_page()
    errs=[];cur={'s':'','b':''}
    await pg.add_init_script("addEventListener('unhandledrejection',e=>console.error('UNHANDLED REJECTION: '+(e.reason&&(e.reason.stack||e.reason.message)||e.reason)))")
    pg.on("pageerror",lambda e:errs.append(f"[{cur['s']} / {cur['b']}] pageerror: {str(e)[:300]}"))
    pg.on("console",lambda m:errs.append(f"[{cur['s']} / {cur['b']}] console.error: {m.text[:300]}") if m.type=='error' else None)
    pg.on("dialog",lambda d:asyncio.ensure_future(d.dismiss()))
    pg.on("download",lambda d:asyncio.ensure_future(d.cancel()))
    await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html");await login(pg)
    await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()");await pg.wait_for_timeout(400)
    await pg.set_input_files("#wordFiles",files);await pg.wait_for_timeout(1500)
    await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
    while await pg.evaluate("()=>VypiskiImportJob.state().running"):await pg.wait_for_timeout(300)
    clicked=0
    for scr in SCREENS:
      cur['s']=scr[:40]
      for rnd in range(2):
        try:await pg.evaluate(f"async()=>{{{scr}}}")
        except Exception as e:errs.append(f'[{scr}] open failed {e}')
        await pg.wait_for_timeout(900)
        n=await pg.evaluate("()=>{const bs=[...document.querySelectorAll('#content button, #content summary, #content [role=tab]')].filter(b=>b.offsetParent&&!b.disabled);bs.forEach((b,i)=>b.dataset.mk=i);return bs.length}")
        for i in range(n):
          loc=pg.locator(f'#content [data-mk="{i}"]')
          if not await loc.count():continue
          try:
            t=(await loc.first.inner_text(timeout=500)).strip()[:40]
          except Exception:continue
          if SKIP.search(t) or not await loc.first.is_visible():continue
          cur['b']=t
          try:await loc.first.click(timeout=1500,no_wait_after=True);clicked+=1
          except Exception:pass
          await pg.wait_for_timeout(250)
          # close overlays that may have opened
          await pg.evaluate("()=>{document.getElementById('wfNotes')?.remove();const sh=document.getElementById('rgSheet');if(sh)sh.hidden=true;document.querySelector('.pc-overlay, #pcDlg, .dlg-back')?.remove?.()}")
          await pg.keyboard.press('Escape')
          if await pg.locator('#vAuth').count():
            lab=await pg.get_attribute('#vAuth','aria-label') or ''
            if await pg.locator('#vAuth button:has-text("Отмена")').count():await pg.click('#vAuth button:has-text("Отмена")')
            else:
              errs.append(f"[{cur['s']} / {cur['b']}] note: auth dialog «{lab}»")
              await pg.locator('#vAuth input').last.fill(PASSWORD);await pg.click('#vAuth button[type=submit]')
              try:await pg.wait_for_selector('#vAuth',state='detached',timeout=8000)
              except Exception:await pg.evaluate("()=>document.getElementById('vAuth')?.remove()")
          # if navigation left the screen, reopen it for the remaining buttons
          if not await pg.locator(f'#content [data-mk="{i+1}"]').count() and i+1<n:
            try:await pg.evaluate(f"async()=>{{{scr}}}");await pg.wait_for_timeout(700)
            except Exception:pass
            await pg.evaluate("()=>{const bs=[...document.querySelectorAll('#content button, #content summary, #content [role=tab]')].filter(b=>b.offsetParent&&!b.disabled);bs.forEach((b,i)=>b.dataset.mk=i)}")
    print('clicked',clicked)
    u=[];[u.append(e) for e in errs if e not in u]
    print('ERRORS',len(u));[print(' ',e) for e in u[:80]];print(f"{1 if not u else 0}/1")
    await b.close()
asyncio.run(main())
