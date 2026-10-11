const { chromium } = require('playwright');
const fs=require('fs');
const file=process.argv[2], phase=process.argv[3]||'load', rate=+(process.argv[4]||4);
const icons=JSON.parse(fs.readFileSync(__dirname+'/icons.json','utf8'));const pkgs=Object.keys(icons);
const apps=pkgs.map((p,i)=>({n:'App '+String.fromCharCode(65+i%26)+i,p}));
const init=fs.readFileSync(__dirname+'/harness.js','utf8').match(/function initScript\(\)\{([\s\S]*?)\n\}\n\(async/)[1];
(async()=>{
 const b=await chromium.launch({args:['--no-sandbox']});
 const ctx=await b.newContext({viewport:{width:393,height:851},deviceScaleFactor:2.75,hasTouch:true,isMobile:true});
 const p=await ctx.newPage();const cdp=await ctx.newCDPSession(p);
 await cdp.send('Profiler.enable');await cdp.send('Profiler.setSamplingInterval',{interval:500});
 await cdp.send('Emulation.setCPUThrottlingRate',{rate});
 await p.addInitScript(new Function(init));
 if(phase==='load'){await cdp.send('Profiler.start');await p.goto('file://'+require('path').resolve(file),{waitUntil:'load'});
   await p.evaluate(a=>window.setApps(a),apps);for(let i=0;i<pkgs.length;i+=24){const bt={};pkgs.slice(i,i+24).forEach(k=>bt[k]=icons[k]);await p.evaluate(x=>window.setIconPack(x),bt)}await p.waitForTimeout(1000);}
 else if(phase==='drawer'){await p.goto('file://'+require('path').resolve(file),{waitUntil:'load'});await p.evaluate(a=>window.setApps(a),apps);for(let i=0;i<pkgs.length;i+=24){const bt={};pkgs.slice(i,i+24).forEach(k=>bt[k]=icons[k]);await p.evaluate(x=>window.setIconPack(x),bt)}await p.waitForTimeout(2500);await cdp.send('Profiler.start');await p.evaluate(()=>openDrawer());await p.waitForTimeout(2500);}
 else{await p.goto('file://'+require('path').resolve(file),{waitUntil:'load'});await p.evaluate(a=>window.setApps(a),apps);await p.waitForTimeout(2500);await cdp.send('Profiler.start');await p.waitForTimeout(12000);}
 const {profile}=await cdp.send('Profiler.stop');
 const dt=profile.timeDeltas;const self={};const byId={};profile.nodes.forEach(n=>byId[n.id]=n);
 profile.samples.forEach((id,i)=>{self[id]=(self[id]||0)+(dt[i]||0)});
 const agg={};let total=0;
 for(const id in self){const n=byId[id];const cf=n.callFrame;const k=(cf.functionName||'(anon)')+' @'+(cf.url.split('/').pop()||'')+':'+(cf.lineNumber+1)+':'+(cf.columnNumber+1);agg[k]=(agg[k]||0)+self[id];total+=self[id]}
 const rows=Object.entries(agg).sort((a,b)=>b[1]-a[1]).slice(0,22);
 console.log('phase',phase,'rate',rate,'total sampled ms',(total/1000).toFixed(0));
 rows.forEach(([k,v])=>console.log((v/1000).toFixed(1).padStart(8)+'ms  '+k));
 await b.close();
})();
