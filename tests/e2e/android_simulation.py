"""Simulates the Android app around the page: a fake window.VypiskiNative + /saf/<job>/<i> file serving with Range."""
import os
FIX=os.environ.get("VYP_FIX","fixtures-out")
OUTDIR=os.environ.get("VYP_OUT","/tmp/vypiski-test-out");os.makedirs(OUTDIR,exist_ok=True)

import asyncio, sys, os, glob, json, time, re, http.server, threading
from playwright.async_api import async_playwright

ROOT = sys.argv[1]
FOLDER = sorted(glob.glob(f"{FIX}/many/2016/0[1-6]/*.docx")) + [f"{FIX}/many/2016/~$lock.docx"]
open(FOLDER[-1], "wb").write(b"x" * 162)
SHARED = sorted(glob.glob(f"{FIX}/orders/*.docx"))[:3]
JOBS = {}

class H(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **k): super().__init__(*a, directory=ROOT, **k)
    def log_message(self, *a): pass
    def do_GET(self):
        m = re.match(r"^/saf/([^/]+)/(\d+)$", self.path)
        if not m: return super().do_GET()
        files = JOBS.get(m.group(1)); i = int(m.group(2))
        if not files or i >= len(files): self.send_error(404); return
        data = open(files[i], "rb").read(); rng = self.headers.get("Range")
        if rng:
            a, b = re.match(r"bytes=(\d+)-(\d*)", rng).groups(); a = int(a); b = int(b) if b else len(data) - 1
            self.send_response(206); self.send_header("Content-Range", f"bytes {a}-{b}/{len(data)}"); body = data[a:b + 1]
        else:
            self.send_response(200); body = data
        self.send_header("Content-Length", str(len(body))); self.send_header("Content-Type", "application/octet-stream"); self.end_headers(); self.wfile.write(body)

srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), H); threading.Thread(target=srv.serve_forever, daemon=True).start()
URL = f"http://127.0.0.1:{srv.server_address[1]}/index.html"

def entries(files, base):
    return [[base + "/" + os.path.relpath(f, f"{FIX}/many/2016"), os.path.getsize(f), int(os.path.getmtime(f) * 1000)] for f in files]

STUB = r"""
(()=>{const log=window.__nlog=[];const L=(...a)=>log.push(a.join(' '));let pend=null;
const later=(f,ms=30)=>setTimeout(f,ms);
window.VypiskiNative={
 version:()=> '12.73',keepAwake:on=>L('keepAwake',on),exportFolder:()=>'Загрузки / Выписки',resetExportFolder:()=>'Загрузки / Выписки',
 textZoom:()=>+(localStorage.getItem('sim.zoom')||100),setTextZoom:p=>{localStorage.setItem('sim.zoom',p);L('textZoom',p)},
 pickFolder:id=>{L('pickFolder');later(()=>VypiskiNativeEvents.resolve(id,{job:'F1',count:window.__F1.length,locks:1,other:0,dirs:6,files:window.__F1.length+1,root:'2016 год',tree:'content://tree/2016'}))},
 rescanFolder:(id,tree)=>{L('rescanFolder',tree);later(()=>VypiskiNativeEvents.resolve(id,{job:'F1',count:window.__F1.length,locks:1,other:0,dirs:6,files:window.__F1.length+1,root:'2016 год',tree}))},
 getEntries:(job,from,n)=>JSON.stringify((job==='F1'?window.__F1:window.__S1).slice(from,from+n)),releaseJob:()=>{},
 takeShared:()=>{const r=pend;pend=null;return r||''},
 importBegin:(t,x)=>L('importBegin',t,x),importProgress:(d,t,x)=>L('importProgress',d,t),importEnd:x=>L('importEnd',x),
 saveBegin:(n,m)=>{L('saveBegin',n);return 'S'+Date.now()},saveChunk:()=>true,saveEnd:()=>JSON.stringify({ok:true,where:'Загрузки / Выписки / file'}),
 shareBegin:(n,m)=>{L('shareBegin',n);return 'H'+Date.now()},shareEnd:()=>{L('shareEnd');return JSON.stringify({ok:true})},
 printHtml:()=>{},chooseExportFolder:()=>{}};
window.__offerShared=()=>{pend=JSON.stringify({job:'S1',count:window.__S1.length,files:window.__S1.length,dirs:0,locks:0,other:0,root:'3 файлов из другого приложения'});VypiskiNativeEvents.shared()};
})();
"""

async def main():
    f1 = entries(FOLDER, "2016 год")
    JOBS["F1"] = FOLDER; JOBS["S1"] = SHARED
    s1 = [[os.path.basename(f), os.path.getsize(f), 0] for f in SHARED]
    async with async_playwright() as p:
        b = await p.chromium.launch()
        ctx = await b.new_context(viewport={"width": 390, "height": 844}, locale="ru-RU")
        await ctx.add_init_script(f"window.__F1={json.dumps(f1)};window.__S1={json.dumps(s1)};" + STUB)
        pg = await ctx.new_page()
        errs = []
        pg.on("pageerror", lambda e: errs.append("PAGEERROR " + str(e)))
        pg.on("console", lambda m: errs.append(f"{m.type}: {m.text}") if m.type == "error" else None)
        pg.on("dialog", lambda d: asyncio.ensure_future(d.accept()))
        await pg.goto(URL); await pg.wait_for_timeout(1500)
        print("in-app class:", await pg.evaluate("document.documentElement.classList.contains('in-android-app')"))
        await pg.screenshot(path=f"{OUTDIR}/scen/app-home-empty.png")
        print("home storage text:", [l for l in (await pg.inner_text("#content")).split("\n") if "Данные хранятся" in l][:1])

        print("== folder import via the app")
        await pg.evaluate("()=>document.querySelector(`#appMore [data-sec='import']`).click()"); await pg.wait_for_timeout(700)
        await pg.screenshot(path=f"{OUTDIR}/scen/app-import-form.png")
        await pg.click("text=Выбрать папку целиком"); await pg.wait_for_timeout(2500)
        print("  status:", await pg.inner_text("#impPrepStatus"))
        await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
        await pg.wait_for_timeout(1500)
        st = await pg.evaluate("()=>{const j=VypiskiImportJob.state();return [j.running,j.done,j.total]}")
        print("  running:", st)
        await pg.reload(); await pg.wait_for_timeout(2500)   # app killed mid-way
        saved = await pg.evaluate("()=>VypiskiRepository.count('orders')")
        print("  after kill: saved", saved, "| resume card:", await pg.is_visible("#impResumeCard"))
        await pg.click("#impResumeCard >> text=Продолжить загрузку")
        for _ in range(240):
            await pg.wait_for_timeout(500)
            st = await pg.evaluate("()=>{const j=VypiskiImportJob.state();return [j.running,j.done,j.total,!!j.result]}")
            if st[3] and not st[0]: break
        txt = await pg.inner_text("#content")
        print("  resumed:", [l for l in txt.split("\n") if "Новых приказов" in l][:1])
        cnt = await pg.evaluate("async()=>{const r=await VypiskiRepository.all('orders');return [r.length,new Set(r.map(x=>x.fileSha256)).size]}")
        print("  orders / distinct:", cnt, "expected", len(FOLDER) - 1)
        log = await pg.evaluate("()=>window.__nlog")
        print("  native calls:", sorted(set(x.split(' ')[0] for x in log)))
        print("  notification end:", [x for x in log if x.startswith("importEnd")][-1:])

        print("== files shared from another app")
        await pg.evaluate("()=>window.__offerShared()"); await pg.wait_for_timeout(2500)
        await pg.screenshot(path=f"{OUTDIR}/scen/app-shared.png")
        print("  status:", await pg.inner_text("#impPrepStatus"))

        print("== save + share toast")
        await pg.evaluate("()=>VypiskiSaveFile(new Blob(['x'],{type:'text/plain'}),'test.txt')"); await pg.wait_for_timeout(500)
        print("  toast:", await pg.inner_text("#nativeToast"))
        await pg.screenshot(path=f"{OUTDIR}/scen/app-toast.png")
        await pg.click("#nativeToast button"); await pg.wait_for_timeout(500)
        print("  share calls:", [x for x in await pg.evaluate("()=>window.__nlog") if x.startswith("share")])

        print("== settings + text size")
        await pg.evaluate("()=>document.querySelector(`#appMore [data-sec='settings']`).click()"); await pg.wait_for_timeout(700)
        await pg.click("[aria-label='Крупнее текст']"); await pg.wait_for_timeout(300)
        await pg.screenshot(path=f"{OUTDIR}/scen/app-settings.png", full_page=True)
        print("  zoom calls:", [x for x in await pg.evaluate("()=>window.__nlog") if x.startswith("textZoom")])
        await pg.evaluate("()=>document.querySelector(`#appNav [data-sec='home']`).click()"); await pg.wait_for_timeout(1500)
        print("  backup card on home:", await pg.is_visible("#bkCard"))
        await pg.screenshot(path=f"{OUTDIR}/scen/app-home.png")
        print("\n".join(errs[:20]) or "no errors")
        await b.close()
asyncio.run(main())
