import os as _o, sys as _s;_s.path.insert(0,_o.path.dirname(_o.path.abspath(__file__)));from authhelp import login
import os
FIX=os.environ.get("VYP_FIX","fixtures-out")
OUTDIR=os.environ.get("VYP_OUT","/tmp/vypiski-test-out");os.makedirs(OUTDIR,exist_ok=True)
import asyncio, sys, glob, time, http.server, threading, functools
from playwright.async_api import async_playwright
root = sys.argv[1]; files = sorted(glob.glob(f"{FIX}/many/**/*.docx", recursive=True))
out = f"{OUTDIR}/scen"; import os; os.makedirs(out, exist_ok=True)
h = functools.partial(http.server.SimpleHTTPRequestHandler, directory=root); h.log_message = lambda *a: None
srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), h); threading.Thread(target=srv.serve_forever, daemon=True).start()
URL = f"http://127.0.0.1:{srv.server_address[1]}/index.html"
STATE = "()=>{const j=VypiskiImportJob.state();return {run:j.running,done:j.done,total:j.total,saved:j.stats&&j.stats.saved,vis:document.visibilityState}}"

async def nav(pg, sec):
    await pg.evaluate("s=>document.querySelector(`#appNav [data-sec='${s}'], #appMore [data-sec='${s}']`).click()", sec)
    await pg.wait_for_timeout(600)

async def pick_and_start(pg):
    await nav(pg, "import")
    await pg.set_input_files("#wordFiles", files)
    for _ in range(100):
        await pg.wait_for_timeout(200)
        if await pg.evaluate("()=>{const b=[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent));return !!b&&!b.disabled}"): break
    await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")

async def main():
    async with async_playwright() as p:
        b = await p.chromium.launch()
        ctx = await b.new_context(viewport={"width": 390, "height": 844}, locale="ru-RU")
        pg = await ctx.new_page()
        errs = []
        pg.on("pageerror", lambda e: errs.append("PAGEERROR " + str(e)))
        pg.on("console", lambda m: errs.append(f"{m.type}: {m.text}") if m.type == "error" else None)
        pg.on("dialog", lambda d: asyncio.ensure_future(d.accept()))
        await pg.goto(URL); await pg.wait_for_timeout(1500)
        await login(pg)

        print("== 1. import, then browse other sections while it runs")
        await pick_and_start(pg)
        await pg.wait_for_timeout(1500)
        await nav(pg, "orders"); await pg.wait_for_timeout(500)
        await pg.screenshot(path=f"{out}/1-orders-while-importing.png")
        print("  on orders:", await pg.evaluate(STATE), "| pill visible:", await pg.is_visible("#impPill"))
        await nav(pg, "search")
        await pg.locator("#content input >> visible=true").first.fill("Петров"); await pg.keyboard.press("Enter")
        await pg.wait_for_timeout(1500)
        await pg.screenshot(path=f"{out}/2-search-while-importing.png")
        t=await pg.inner_text("#content");print("  search works during import:", "Результатов" in t or "Людей:" in t, await pg.evaluate(STATE))

        print("== 2. page hidden (another tab in front)")
        other = await ctx.new_page(); await other.goto("about:blank"); await other.bring_to_front()
        s0 = await pg.evaluate(STATE); t0 = time.time()
        await asyncio.sleep(6)
        s1 = await pg.evaluate(STATE)
        print(f"  hidden={s1['vis']} processed while hidden: {s1['done']-s0['done']} files in {time.time()-t0:.1f}s")
        await pg.bring_to_front(); await other.close()

        print("== 3. 'crash' (reload) in the middle, then continue")
        st = await pg.evaluate(STATE); print("  before reload:", st)
        await pg.reload(); await login(pg); await pg.wait_for_timeout(2500)
        await pg.screenshot(path=f"{out}/3-after-crash-home.png")
        n_after_crash = await pg.evaluate("()=>VypiskiRepository.count('orders')")
        print("  orders saved before crash:", n_after_crash, "| resume card:", await pg.is_visible("#impResumeCard"))
        await pg.click("#impResumeCard >> text=Продолжить загрузку"); await pg.wait_for_timeout(1000)
        await pg.screenshot(path=f"{out}/4-resume-asks-files.png")
        print("  message:", await pg.inner_text("#impPrepStatus"))
        t0 = time.time()
        await pick_and_start(pg)
        for _ in range(400):
            await pg.wait_for_timeout(500)
            if not (await pg.evaluate(STATE))["run"]: break
        await pg.wait_for_timeout(1000)
        await pg.screenshot(path=f"{out}/5-done.png")
        print(f"  continue took {time.time()-t0:.1f}s:", (await pg.inner_text("#content")).split("\n")[2:4])
        cnt = await pg.evaluate("async()=>{const r=await VypiskiRepository.all('orders');return [r.length,new Set(r.map(x=>x.fileSha256)).size]}")
        print("  orders total / distinct files:", cnt)
        meta = await pg.evaluate("async()=>{await VypiskiCoreReady();return (await VypiskiCore.allMeta()).length}")
        print("  search index entries:", meta)
        print("\n".join(errs[:20]) or "no errors")
        await b.close()
asyncio.run(main())
