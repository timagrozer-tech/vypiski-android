import os as _o, sys as _s;_s.path.insert(0,_o.path.dirname(_o.path.abspath(__file__)));from authhelp import login
import os
FIX=os.environ.get("VYP_FIX","fixtures-out")
OUTDIR=os.environ.get("VYP_OUT","/tmp/vypiski-test-out");os.makedirs(OUTDIR,exist_ok=True)
import asyncio, sys, glob, http.server, threading, functools, json
from playwright.async_api import async_playwright
root=sys.argv[1]; files=sorted(glob.glob(f"{FIX}/orders/*.docx"))
h=functools.partial(http.server.SimpleHTTPRequestHandler, directory=root); h.log_message=lambda *a:None
srv=http.server.ThreadingHTTPServer(("127.0.0.1",0),h); threading.Thread(target=srv.serve_forever,daemon=True).start()
SCAN="""()=>{const lum=c=>{const m=c.match(/[\\d.]+/g);if(!m)return null;const [r,g,b,a=1]=m.map(Number);if(a<0.5)return null;const f=x=>{x/=255;return x<=.03928?x/12.92:Math.pow((x+.055)/1.055,2.4)};return .2126*f(r)+.7152*f(g)+.0722*f(b)};
const skip=e=>e.closest('.word-preview,.doc-preview,.wz-paper,.wz-preview,iframe,.sheet,.xsheet,.u2-doc,mark')||(e.tagName==='BUTTON'&&/primary|wz-next/.test(e.className))||(e.tagName==='INPUT'&&/checkbox|radio/.test(e.type));const res=[];
for(const e of document.querySelectorAll('#content *, #appNav *, #appMore *, #dfp *, header *')){if(skip(e))continue;const r=e.getBoundingClientRect();if(!r.width||!r.height)continue;const cs=getComputedStyle(e);if(cs.visibility==='hidden'||cs.display==='none')continue;
 const bg=lum(cs.backgroundColor);const txt=[...e.childNodes].some(n=>n.nodeType===3&&n.textContent.trim())||['INPUT','SELECT','TEXTAREA'].includes(e.tagName);
 const name=e.tagName.toLowerCase()+(e.id?'#'+e.id:'')+(e.className&&typeof e.className==='string'?'.'+e.className.trim().split(/\\s+/).join('.'):'');
 if(bg!==null&&bg>0.6)res.push('LIGHT-BG '+name+' '+cs.backgroundColor);
 if(txt){const fg=lum(cs.color);if(fg!==null&&fg<0.12&&!(bg!==null&&bg>0.3))res.push('DARK-TEXT '+name+' '+cs.color+' «'+(e.textContent||e.value||'').trim().slice(0,30)+'»')}}
// paper previews must keep dark text on white in every theme
for(const e of document.querySelectorAll('.u2-doc *, .word-preview *, .xsheet *')){const r=e.getBoundingClientRect();if(!r.width||![...e.childNodes].some(n=>n.nodeType===3&&n.textContent.trim()))continue;const fg=lum(getComputedStyle(e).color);if(fg!==null&&fg>0.3)res.push('LIGHT-TEXT-ON-PAPER '+e.tagName.toLowerCase()+' «'+e.textContent.trim().slice(0,30)+'»')}
return [...new Set(res)]}"""
SECS=["home","orders","search","table","create","batch","ready","import","templates","backup","diag","settings"]
async def m():
    async with async_playwright() as p:
        b=await p.chromium.launch(); pg=await b.new_page(viewport={"width":int(sys.argv[2]) if len(sys.argv)>2 else 390,"height":844})
        pg.on("dialog", lambda d: asyncio.ensure_future(d.accept()))
        await pg.goto(f"http://127.0.0.1:{srv.server_address[1]}/index.html"); await pg.wait_for_timeout(1200)
        await login(pg)
        await pg.evaluate("()=>document.querySelector(`[data-sec='import']`).click()"); await pg.wait_for_timeout(500)
        await pg.set_input_files("#wordFiles", files); await pg.wait_for_timeout(1500)
        await pg.evaluate("()=>[...document.querySelectorAll('#content button.primary')].find(x=>/Импорт/.test(x.textContent)).click()")
        for _ in range(100):
            await pg.wait_for_timeout(300)
            if not await pg.evaluate("()=>VypiskiImportJob.state().running"): break
        await pg.evaluate("()=>VypiskiTheme.set('dark')")
        total=0
        async def scan(label):
            nonlocal total
            r=await pg.evaluate(SCAN); total+=len(r)
            if r: print(f"== {label}: {len(r)}"); [print('  ',x) for x in r[:25]]
        for s in SECS:
            await pg.evaluate("s=>document.querySelector(`#appNav [data-sec='${s}'], #appMore [data-sec='${s}']`).click()",s); await pg.wait_for_timeout(800)
            await scan(s)
        await pg.evaluate("()=>document.querySelector(`[data-sec='search']`).click()"); await pg.wait_for_timeout(500)
        await pg.click(".sx-mode button:has-text('Люди и события')"); await pg.wait_for_timeout(400)
        if await pg.locator("button:has-text('Подготовить таблицу людей и событий')").count(): await pg.click("button:has-text('Подготовить таблицу людей и событий')")
        await pg.wait_for_selector("#sx .sx-q",timeout=30000); await pg.fill("#sx .sx-q","Петров"); await pg.wait_for_timeout(600)
        await scan("search people & events")
        await pg.click("#sx .sx-tools button:has-text('Календарь')"); await pg.wait_for_timeout(400); await scan("search calendar"); await pg.click("#sx .sx-tools button:has-text('Список')")
        await pg.click(".sx-mode button:has-text('Текст приказов')"); await pg.wait_for_selector("#dfs .df-q")
        await pg.locator("#content input >> visible=true").first.fill("Петров"); await pg.keyboard.press("Enter"); await pg.wait_for_timeout(1500)
        await scan("search results")
        await pg.locator("#content >> text=Приказ_105_2016 >> visible=true").first.click(); await pg.wait_for_timeout(800)
        await scan("search preview")
        await pg.evaluate("()=>document.querySelector(`[data-sec='orders']`).click()"); await pg.wait_for_timeout(600)
        await pg.locator("#content .u2-row >> visible=true").first.click(); await pg.wait_for_timeout(1200)
        await scan("document view")
        await pg.locator("#content button:has-text('Создать выписку из этого приказа') >> visible=true").first.click(); await pg.wait_for_timeout(900)
        await scan("wizard step2")
        await pg.locator("#wz input >> visible=true").first.fill("Петров Сергей Николаевич")
        await pg.locator("#wz button:has-text('Найти в приказе') >> visible=true").first.click(); await pg.wait_for_timeout(800)
        for i in range(5):
            await scan(f"wizard step {i+3}")
            nxt=pg.locator("#wz .wz-next >> visible=true")
            if not await nxt.count(): break
            if await nxt.first.is_disabled():
                h_=pg.locator("#wz .wz-why >> visible=true")
                if await h_.count():
                    await h_.first.click(); await pg.wait_for_timeout(400); await pg.evaluate("()=>{const a=document.activeElement;if(a&&a.type==='checkbox'&&!a.checked)a.click()}"); await pg.wait_for_timeout(400)
            await nxt.first.click(); await pg.wait_for_timeout(1000)
        await pg.screenshot(path=f"{OUTDIR}/tour/dark-wizard-end.png")
        for label,js in [("queue","()=>VypiskiWorkflow.queuePage()"),("journal","()=>VypiskiWorkflow.journalPage()"),("help","()=>{VypiskiWorkflow.helpPage();document.querySelectorAll('#wfHelp details').forEach(d=>d.open=true)}"),("palette","()=>VypiskiWorkflow.palette()"),("notes","()=>{document.getElementById('wfPal')?.remove();VypiskiWorkflow.openNotes()}"),("excel import tab","()=>{document.getElementById('wfNotes')?.remove();VypiskiRegisterView.open().then(()=>document.getElementById('rg')?._xpShow?.('import'))}"),("excel templates tab","()=>document.getElementById('rg')?._xpShow?.('tpl')"),("print center","()=>{VypiskiPrintCenter.open([{title:'Проба',t:VypiskiDocument.BUILTIN[0],model:VypiskiDocument.compose({template:VypiskiDocument.BUILTIN[0],requisites:{number:'1',date:'2024-01-21'},fragments:[{loc:'p1',kind:'p',role:'lead',text:'1. Текст.'}],header:['ПРИКАЗ'],signatures:[],certify:null})}])}"),("lock screen","()=>{document.getElementById('pc')?.remove();VypiskiAuth.lock()}")]:
            await pg.evaluate(js); await pg.wait_for_timeout(1200); await scan(label)
        await pg.fill('#vAuth input[type=password]',__import__('authhelp').PASSWORD); await pg.click('#vAuth button[type=submit]'); await pg.wait_for_timeout(800)
        print("TOTAL",total)
        await b.close()
asyncio.run(m())
