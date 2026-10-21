import os as _o, sys as _s;_s.path.insert(0,_o.path.dirname(_o.path.abspath(__file__)));from authhelp import login
import os
FIX=os.environ.get("VYP_FIX","fixtures-out")
OUTDIR=os.environ.get("VYP_OUT","/tmp/vypiski-test-out");os.makedirs(OUTDIR,exist_ok=True)
import asyncio, sys, os, glob, http.server, threading, functools
from playwright.async_api import async_playwright

root, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=root)
handler.log_message = lambda *a: None
srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
threading.Thread(target=srv.serve_forever, daemon=True).start()
port = srv.server_address[1]
files = sorted(glob.glob(f"{FIX}/orders/*.docx"))

async def nav(pg, sec):
    await pg.evaluate("s=>document.querySelector(`#appNav [data-sec='${s}'], #appMore [data-sec='${s}']`).click()", sec)
    await pg.wait_for_timeout(900)

async def main():
    async with async_playwright() as p:
        b = await p.chromium.launch()
        ctx = await b.new_context(viewport={"width": 390, "height": 844}, locale="ru-RU", accept_downloads=True)
        await ctx.add_init_script("window.VypiskiOnBack=()=>'exit'")
        pg = await ctx.new_page()
        errs = []
        pg.on("pageerror", lambda e: errs.append("PAGEERROR " + str(e)))
        pg.on("console", lambda m: errs.append(f"{m.type}: {m.text}") if m.type == "error" else None)
        pg.on("dialog", lambda d: asyncio.ensure_future(d.accept()))
        await pg.goto(f"http://127.0.0.1:{port}/index.html")
        await login(pg)
        await pg.wait_for_timeout(1500)
        await nav(pg, "import")
        await pg.set_input_files("#wordFiles", files)
        await pg.wait_for_timeout(2500)
        print("import button:", await pg.locator("#content button.primary >> visible=true").first.inner_text())
        await pg.locator("#content button.primary >> visible=true").first.click()
        for _ in range(60):
            await pg.wait_for_timeout(1000)
            t = await pg.inner_text("#content")
            if "Импорт завершён" in t or "Загрузка завершена" in t: break
        await pg.wait_for_timeout(1000)
        # horizontal overflow per section
        for sec in ["home", "orders", "import", "search", "create", "ready", "templates", "backup", "diag"]:
            await nav(pg, sec)
            w = await pg.evaluate("()=>[document.documentElement.scrollWidth, innerWidth, document.querySelector('#appNav .more').classList.contains('on')]")
            print(sec, "scrollWidth/innerWidth/moreOn:", w)
        # back button: orders -> open text -> back closes text
        await nav(pg, "orders")
        await pg.locator("#content .u2-seg button:has-text('Папки')").click(); await pg.wait_for_timeout(800)
        await pg.locator("#content button:has-text('Текст') >> visible=true").first.click()
        await pg.wait_for_timeout(600)
        print("pre open:", await pg.locator("#content .order-folder-item pre").count())
        print("back ->", await pg.evaluate("()=>window.VypiskiOnBack()"), "pre now:", await pg.locator("#content .order-folder-item pre").count())
        # full wizard from the orders card
        await pg.evaluate("()=>VypiskiWizard.open({orderId:null})") if False else await pg.evaluate("async()=>{const m=(await VypiskiCore.allMeta()).sort((a,b)=>a.number.localeCompare(b.number,'ru',{numeric:true}))[0];VypiskiWizard.open({orderId:m.orderId})}")
        await pg.wait_for_timeout(1200)
        await pg.locator("#wz input >> visible=true").first.fill("Петров Сергей Николаевич")
        await pg.locator("#wz button:has-text('Найти в приказе') >> visible=true").first.click()
        await pg.wait_for_timeout(1200)
        steps = []
        for i in range(10):
            t = await pg.inner_text("#wz")
            steps.append(t.split("\n")[0:2])
            nxt = pg.locator("#wz .wz-next >> visible=true")
            if not await nxt.count(): break
            if await nxt.first.is_disabled():
                h = pg.locator("#wz .wz-why >> visible=true")
                print("disabled at", steps[-1], "hint:", (await h.first.inner_text()) if await h.count() else None)
                await pg.screenshot(path=f"{out}/hint-{i}.png")
                if not await h.count(): break
                await h.first.click(); await pg.wait_for_timeout(600)
                await pg.evaluate("()=>{const a=document.activeElement; if(a&&a.type==='checkbox'&&!a.checked){a.click()}}")
                await pg.wait_for_timeout(600)
                if await nxt.first.is_disabled():
                    print("still disabled; hint:", (await h.first.inner_text()) if await h.count() else None); break
            await nxt.first.click()
            await pg.wait_for_timeout(1200)
        print("steps:", steps)
        await pg.screenshot(path=f"{out}/wizard-end.png")
        # try save docx
        btns = await pg.locator("#wz button >> visible=true").all_inner_texts()
        print("final buttons:", btns)
        dl = [x for x in btns if "DOCX" in x or "Скачать" in x or "Сохранить" in x]
        if dl:
            async with pg.expect_download() as d:
                await pg.locator(f"#wz button:has-text('{dl[0]}')").first.click()
            dd = await d.value
            await dd.save_as(f"{out}/excerpt.docx"); print("saved", dd.suggested_filename)
            await pg.wait_for_timeout(1000)
            await pg.screenshot(path=f"{out}/after-save.png")
        print("\n".join(errs[:30]) or "no console errors")
        await b.close()

asyncio.run(main())
