// Usage: node static_check.cjs <index.html>  — TypeScript (checkJs) over every script block of the page.
// Reports only what is a real bug in this code base: an undefined name (not a global set by another block),
// «did you mean …» typos and syntax errors. Needs the «typescript» package (npm i -g typescript or TS_PATH=…).
const fs=require('fs'),path=require('path'),os=require('os');
let ts;for(const p of [process.env.TS_PATH,'typescript','/opt/node-tools/node_modules/typescript','/opt/npm-tools/node_modules/typescript'].filter(Boolean)){try{ts=require(p);break}catch{}}
if(!ts){console.log('typescript not found — skipped');process.exit(0)}
const html=fs.readFileSync(process.argv[2]||'index.html','utf8');
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'vyp-ts-'));const files=[];let i=0;
for(const m of html.matchAll(/<script(?: id="([^"]+)")?( type="text\/plain")?>([\s\S]*?)<\/script>/g)){if(m[2])continue;const f=path.join(dir,(m[1]||'anon'+i++)+'.js');fs.writeFileSync(f,m[3]);files.push(f)}
// names other blocks put on window/root/globalThis, and names of external libraries
const globals=new Set(['JSZip','define','global','setImmediate','module','exports','require','importScripts','TesseractCore']);
for(const m of html.matchAll(/(?:window|root|globalThis|self)\.([A-Za-z_$][\w$]*)\s*=(?!=)/g))globals.add(m[1]);
const prog=ts.createProgram(files,{allowJs:true,checkJs:true,noEmit:true,target:ts.ScriptTarget.ES2022,lib:['lib.es2023.d.ts','lib.dom.d.ts','lib.dom.iterable.d.ts'],strict:false,skipLibCheck:true,types:[]});
const bad=[];
for(const d of ts.getPreEmitDiagnostics(prog)){const msg=ts.flattenDiagnosticMessageText(d.messageText,' ');const where=d.file?`${path.basename(d.file.fileName)}:${d.file.getLineAndCharacterOfPosition(d.start).line+1}`:'';
 if(d.code===2304){const n=(/Cannot find name '([^']+)'/.exec(msg)||[])[1];if(globals.has(n))continue;bad.push(where+' '+msg)}
 else if(d.code===2552||(d.code>=1000&&d.code<2000&&d.code!==1345))bad.push(where+' '+msg)}
fs.rmSync(dir,{recursive:true,force:true});
for(const b of bad)console.log('FAIL',b);console.log(`blocks=${files.length} problems=${bad.length}`);process.exit(bad.length?1:0);
