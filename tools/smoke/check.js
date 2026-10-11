/* Checks for: Show preview toggle, music removal, Business home, search. node check.js */
const {JSDOM,VirtualConsole}=require('jsdom');const fs=require('fs'),path=require('path');
const html=fs.readFileSync(path.join(__dirname,'../../app/src/main/assets/index.html'),'utf8');
let pass=0,fail=0;const ok=(c,m)=>{c?pass++:fail++;console.log((c?'PASS ':'FAIL ')+m)};
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
function boot(extra={}){const calls=[];
 const A=Object.assign({getBattery:()=>'{"p":57,"c":false,"f":false,"pl":false}',getBadges:()=>'{}',getMedia:()=>'null',getNextAlarm:()=>'0',getNextEvent:()=>'null',
  hasNotificationAccess:()=>false,getPerms:()=>'{}',saveSettings:()=>{},saveTheme:()=>{},logError:()=>{},launch:p=>{calls.push(['launch',p]);return true},
  getShortcuts:()=>'[]',haptic:()=>false,isKeyguardLocked:()=>false,setLockOver:()=>{},openUrl:u=>{calls.push(['openUrl',u]);return true},search:()=>{},appInfo:()=>true},extra);
 const errs=[];const vc=new VirtualConsole();vc.on('jsdomError',e=>errs.push('jsdom '+e.message));vc.on('error',(...a)=>errs.push(a.join(' ')));
 const dom=new JSDOM(html,{runScripts:'dangerously',pretendToBeVisual:true,virtualConsole:vc,url:'https://appassets.test/index.html',
  beforeParse(w){w.matchMedia=w.matchMedia||(q=>({matches:false,addEventListener(){},removeEventListener(){},addListener(){},removeListener(){}}));
   w.HTMLCanvasElement.prototype.getContext=()=>null;w.scrollTo=()=>{};w.Element.prototype.scrollTo=()=>{};w.ResizeObserver=class{observe(){}unobserve(){}disconnect(){}};
   w.IntersectionObserver=class{observe(){}unobserve(){}disconnect(){}};w.Element.prototype.animate=function(){return{cancel(){},finish(){},set onfinish(f){}}};
   w.Android=A;w.addEventListener('error',e=>errs.push(e.message))}});
 return {w:dom.window,calls,errs}}
const APPS=[{n:'Gmail',p:'com.gmail'},{n:'Google Pay',p:'com.gpay'},{n:'Calendar',p:'com.cal'},{n:'Camera',p:'com.cam'},{n:'WhatsApp',p:'com.wa'},{n:'Spotify',p:'com.spot'},{n:'Zerodha Kite',p:'com.kite'}];
const type=(w,id,v)=>{const i=w.document.getElementById(id);i.value=v;i.dispatchEvent(new w.Event('input'));};
(async()=>{
 const t=boot();const w=t.w;await sleep(1500);w.setApps(APPS);await sleep(100);
 const d=w.document;
 /* --- Show preview --- */
 const hb=d.getElementById('v39h'),sum=d.getElementById('v39s');
 ok(hb&&/Hide/.test(hb.textContent),'preview card shows with a Hide button');
 ok(/Your home screen/.test(sum.textContent),'preview summary is filled');
 hb.click();ok(/Show preview/.test(hb.textContent)&&/hidden/.test(sum.textContent),'Hide -> card says hidden, button says "Show preview"');
 hb.click();await sleep(50);
 ok(/Hide/.test(hb.textContent)&&/Your home screen/.test(sum.textContent),'Show preview -> summary is back (was stuck on "hidden")');
 ok(!/Music/.test(sum.textContent),'preview card no longer mentions Music');
 /* --- Music --- */
 ok(!d.getElementById('mplay'),'no music player element above the dock');
 ok(![...d.querySelectorAll('.sh-body *')].some(e=>/Music player/i.test(e.textContent)&&e.children.length===0),'no "Music player" setting');
 ok(w.eval("typeof window.mplayRefresh")==='undefined','mplayRefresh gone');
 /* --- Search (home command box) --- */
 w.openCommand();await sleep(60);
 const items=()=>(d.getElementById('commandResults')._items||[]).map(i=>i.label);
 const q=(v)=>{type(w,'commandInput',v);return sleep(80).then(items)};
 let r=await q('gma');ok(r[0]==='Gmail','search "gma" -> Gmail first: '+r.slice(0,3));
 r=await q('gp');ok(r[0]==='Google Pay','initials "gp" -> Google Pay first: '+r.slice(0,3));
 r=await q('kite');ok(r[0]==='Zerodha Kite','word-start "kite" -> Zerodha Kite: '+r.slice(0,3));
 r=await q('xyzzy');ok(r.length===1&&/Search .* for/.test(r[0]),'no app match -> only web search row: '+r);
 r=await q('2+3*4');ok(/= 14/.test(r[0]||''),'calculator "2+3*4": '+r[0]);
 r=await q('example.com');ok(/Open example\.com/.test(r.join('|')),'URL row for example.com');
 r=await q('set');ok(r.includes('Settings'),'"set" lists the Settings action: '+r);
 r=await q('g');ok(r.length>0,'single letter gives results');
 /* Enter on app launches it */
 await q('cam');const inp=d.getElementById('commandInput');inp.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Enter',bubbles:true}));await sleep(50);
 ok(t.calls.some(c=>c[0]==='launch'&&c[1]==='com.cam'),'Enter on "cam" launches Camera');
 /* Enter typed fast (before the rAF refresh) */
 w.openCommand();await sleep(60);
 inp.value='zzzz';inp.dispatchEvent(new w.Event('input'));inp.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Enter',bubbles:true}));await sleep(120);
 ok(t.calls.some(c=>c[0]==='openUrl'&&/zzzz/.test(c[1])),'Enter right after typing searches the typed text (not the old list): '+JSON.stringify(t.calls.slice(-2)));
 console.log('errors:',[...new Set(t.errs)].slice(0,5));
 console.log(pass+' passed, '+fail+' failed');process.exit(fail?1:0)})();
