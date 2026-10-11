// Usage: node harness.js <index.html> <label> [cpuRate]
const { chromium } = require('playwright');
const fs=require('fs');
const file=process.argv[2], label=process.argv[3]||'run', rate=+(process.argv[4]||1);
const icons=JSON.parse(fs.readFileSync(__dirname+'/icons.json','utf8'));
const pkgs=Object.keys(icons);
const apps=pkgs.map((p,i)=>({n:'App '+String.fromCharCode(65+i%26)+i,p, ...(i%7==0?{k:'Games'}:i%5==0?{k:'Media'}:{})}));
function initScript(){
  window.__calls={}; window.__timers={si:0,st:0,raf:0};
  const wrap=(o)=>new Proxy(o,{get(t,k){const v=t[k];if(typeof v!=='function')return v;return function(){window.__calls[k]=(window.__calls[k]||0)+1;return v.apply(t,arguments)}}});
  const A={getBattery:()=>JSON.stringify({p:57,c:false,f:false,pl:false}),getBadges:()=>'{}',getMedia:()=>'null',
    getNextAlarm:()=>'0',getNextEvent:()=>'null',hasNotificationAccess:()=>false,getPerms:()=>JSON.stringify({home:true,notif:false,access:false,location:false,contacts:false,calendar:false}),
    hasCalendarPerm:()=>false,hasContactsPerm:()=>false,getContacts:()=>'[]',saveSettings:()=>{},saveTheme:()=>{},logError:()=>{},
    launch:()=>true,getShortcuts:()=>'[]',haptic:()=>false,isKeyguardLocked:()=>false,setLockOver:()=>{},openUrl:()=>true,search:()=>{},
    suggestAsync:()=>{},suggest:()=>'[]',searchContacts:()=>'[]',placeWidget:()=>{},hideWidget:()=>{},sysAction:()=>'ok',getNotifications:()=>'[]'};
  window.Android=wrap(A);
  const si=window.setInterval,st=window.setTimeout,raf=window.requestAnimationFrame;
  window.setInterval=function(){window.__timers.si++;return si.apply(this,arguments)};
  window.setTimeout=function(){window.__timers.st++;return st.apply(this,arguments)};
  window.requestAnimationFrame=function(){window.__timers.raf++;return raf.apply(this,arguments)};
  window.__long=[];try{new PerformanceObserver(l=>l.getEntries().forEach(e=>window.__long.push(Math.round(e.duration)))).observe({type:'longtask',buffered:true})}catch(e){}
  window.__errs=[];window.addEventListener('error',e=>window.__errs.push(e.message));
}
(async()=>{
  const b=await chromium.launch({args:['--no-sandbox','--enable-precise-memory-info','--js-flags=--expose-gc']});
  const ctx=await b.newContext({viewport:{width:393,height:851},deviceScaleFactor:2.75,hasTouch:true,isMobile:true});
  const p=await ctx.newPage();
  const cdp=await ctx.newCDPSession(p);
  await cdp.send('Performance.enable');
  if(rate>1) await cdp.send('Emulation.setCPUThrottlingRate',{rate});
  await p.addInitScript(initScript);
  const errs=[];p.on('pageerror',e=>errs.push(e.message.slice(0,120)));
  const m=async()=>{const r=await cdp.send('Performance.getMetrics');const o={};r.metrics.forEach(x=>o[x.name]=x.value);return o};
  const t0=Date.now();
  await p.goto('file://'+require('path').resolve(file),{waitUntil:'load'});
  const loadMs=Date.now()-t0;
  const m1=await m();
  const nav=await p.evaluate(()=>{const n=performance.getEntriesByType('navigation')[0];const fcp=(performance.getEntriesByName('first-contentful-paint')[0]||{}).startTime;return{dcl:Math.round(n.domContentLoadedEventEnd),load:Math.round(n.loadEventEnd),fcp:Math.round(fcp||0)}});
  // simulate onPageFinished: setApps then icon batches of 24
  const t1=Date.now();
  await p.evaluate(({apps})=>{window.setApps(apps)},{apps});
  const appsMs=Date.now()-t1;
  const t2=Date.now();
  for(let i=0;i<pkgs.length;i+=24){const batch={};pkgs.slice(i,i+24).forEach(k=>batch[k]=icons[k]);await p.evaluate(b=>window.setIconPack(b),batch);}
  await p.waitForTimeout(800);
  const iconMs=Date.now()-t2;
  const m2=await m();
  // idle 20s on home
  const c0=await p.evaluate(()=>JSON.stringify({c:window.__calls,t:window.__timers}));
  const mi0=await m();
  await p.waitForTimeout(20000);
  const mi1=await m();
  const c1=await p.evaluate(()=>JSON.stringify({c:window.__calls,t:window.__timers}));
  const A=JSON.parse(c0),B=JSON.parse(c1);
  const d=(k)=>+(mi1[k]-mi0[k]).toFixed(3);
  const idle={wallS:20,taskS:d('TaskDuration'),scriptS:d('ScriptDuration'),layoutS:d('LayoutDuration'),styleS:d('RecalcStyleDuration'),layoutCount:d('LayoutCount'),styleCount:d('RecalcStyleCount')};
  idle.cpuPct=+(100*idle.taskS/20).toFixed(1);
  const rate_=(k)=>({});
  const calls={};for(const k in B.c){const n=B.c[k]-(A.c[k]||0);if(n)calls[k]=+(n/20).toFixed(2)+'/s'}
  const tm={interval:B.t.si-A.t.si,timeout:B.t.st-A.t.st,raf:B.t.raf-A.t.raf};
  // drawer open: frame deltas
  await p.evaluate(()=>{window.__fr=[];let l=performance.now();(function f(t){window.__fr.push(t-l);l=t;if(window.__fr.length<600)requestAnimationFrame(f)})(l)});
  const to0=Date.now();
  await p.evaluate(()=>{try{openDrawer()}catch(e){window.__errs.push('open:'+e.message)}});
  await p.waitForTimeout(1500);
  const fr=await p.evaluate(()=>window.__fr.slice(1,90));
  const sorted=fr.slice().sort((a,b)=>a-b);
  const jank=fr.filter(x=>x>33.4).length;
  const mem=await p.evaluate(()=>({heapMB:+(performance.memory.usedJSHeapSize/1048576).toFixed(1),nodes:document.getElementsByTagName('*').length}));
  const m3=await m();
  const long=await p.evaluate(()=>window.__long);
  const errsPage=await p.evaluate(()=>window.__errs);
  const res={label,cpuRate:rate,loadMs,nav,appsMs,iconMs,scriptSecAtLoad:+m1.ScriptDuration.toFixed(3),taskSecAtLoad:+m1.TaskDuration.toFixed(3),
    idle,idleBridgeCallsPerSec:calls,idleTimersPer20s:tm,
    drawerFrames:{n:fr.length,p50:+sorted[Math.floor(sorted.length*.5)].toFixed(1),p95:+sorted[Math.floor(sorted.length*.95)].toFixed(1),max:+sorted[sorted.length-1].toFixed(1),over33ms:jank},
    mem,heapPeakMB:+(m3.JSHeapUsedSize/1048576).toFixed(1),domNodesMetric:m3.Nodes,longTasks:long.length,longTaskMax:Math.max(0,...long),longTaskSum:long.reduce((a,b)=>a+b,0),errors:[...new Set([...errs,...errsPage])].slice(0,5)};
  console.log(JSON.stringify(res,null,1));
  await b.close();
})().catch(e=>{console.error('HARNESS FAIL',e.message);process.exit(1)});
