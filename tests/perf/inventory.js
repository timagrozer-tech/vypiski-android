// injected before page scripts: counts observers, timers, listeners, with the creating stack
(()=>{const W=window;const st=()=>{const s=(new Error().stack||'').split('\n').slice(2,4).map(x=>x.trim().replace(/^at /,'').replace(/https?:\/\/[^/]+\/index\.html/,'L')).join(' < ');return s};
W.__inv={mo:new Map(),iv:new Map(),to:0,ls:new Map(),raf:0};
const inc=(m,k)=>m.set(k,(m.get(k)||0)+1);
const MO=W.MutationObserver;W.MutationObserver=class extends MO{constructor(cb){super(cb);this.__s=st()}observe(t,o){inc(__inv.mo,this.__s+' | '+(t===document.body?'body':t===document.documentElement?'html':(t.id?'#'+t.id:t.tagName))+(o&&o.subtree?' subtree':'')+(o&&o.attributes?' attr':'')+(o&&o.characterData?' char':''));return super.observe(t,o)}};
const SI=W.setInterval;W.setInterval=function(f,ms,...a){inc(__inv.iv,st()+' @'+ms);return SI.call(this,f,ms,...a)};
const AEL=EventTarget.prototype.addEventListener;EventTarget.prototype.addEventListener=function(t,f,o){if(this===window||this===document||this===document.body)inc(__inv.ls,(this===window?'window':this===document?'document':'body')+':'+t+' '+st());return AEL.call(this,t,f,o)};
})();
