import os as _o, sys as _s;_s.path.insert(0,_o.path.dirname(_o.path.abspath(__file__)));from authhelp import login
import os
FIX=os.environ.get("VYP_FIX","fixtures-out")
OUTDIR=os.environ.get("VYP_OUT","/tmp/vypiski-test-out");os.makedirs(OUTDIR,exist_ok=True)
import asyncio, sys, glob, base64, json, http.server, threading, functools
from playwright.async_api import async_playwright
root = sys.argv[1]
files = sorted(glob.glob(f"{FIX}/edge/*.docx")) + sorted(glob.glob(f"{FIX}/orders/*.docx")) + sorted(glob.glob(f"{FIX}/many/**/*.docx", recursive=True))[:300] + sys.argv[2:]
h = functools.partial(http.server.SimpleHTTPRequestHandler, directory=root); h.log_message = lambda *a: None
srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), h); threading.Thread(target=srv.serve_forever, daemon=True).start()
JS = """async b64=>{const bin=atob(b64),u=new Uint8Array(bin.length);for(let i=0;i<bin.length;i++)u[i]=bin.charCodeAt(i);
 const I=window.VypiskiImport;const go=async()=>{try{const x=await I.docxXml(u);return JSON.stringify(I.extractStructure(x))}catch(e){return 'ERR '+e.message}};
 const a=await go();const D=window.DOMParser;window.DOMParser=undefined;let b;try{b=await go()}finally{window.DOMParser=D}return [a,b]}"""
async def main():
    async with async_playwright() as p:
        br = await p.chromium.launch(); pg = await br.new_page()
        await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html"); await pg.wait_for_timeout(1200)
        await login(pg)
        same = diff = 0
        for f in files:
            a, b = await pg.evaluate(JS, base64.b64encode(open(f, "rb").read()).decode())
            if a == b: same += 1
            else:
                diff += 1; print("DIFF", f.split("/")[-1]); print("  DOM :", a[:400]); print("  LITE:", b[:400])
            if "/edge/" in f: print("  ", f.split("/")[-1], "->", a[:160])
        print(f"same={same} diff={diff}")
        await br.close()
asyncio.run(main())
