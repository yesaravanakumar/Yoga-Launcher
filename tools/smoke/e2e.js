/* End-to-end checks for the fixes in this pass. Runs index.html in jsdom with a mock Android bridge.
   node e2e.js [path/to/index.html]   (needs: npm install) */
const {JSDOM,VirtualConsole}=require('jsdom');const fs=require('fs'),path=require('path');
const html=fs.readFileSync(process.argv[2]||path.join(__dirname,'../../app/src/main/assets/index.html'),'utf8');
let pass=0,fail=0;const ok=(c,m)=>{c?pass++:fail++;console.log((c?'PASS ':'FAIL ')+m)};
const sleep=ms=>new Promise(r=>setTimeout(r,ms));

function boot({calendar=false,alarm=0,launchOk=true,event=null,pre={}}={}){
  const calls=[];const store={};
  const A={
    getBattery:()=>JSON.stringify({p:57,c:true,f:false,pl:true}),getBadges:()=>'{}',getMedia:()=>'null',
    getNextAlarm:()=>String(alarm),getNextEvent:()=>event?JSON.stringify(event):'null',
    hasNotificationAccess:()=>false,getPerms:()=>JSON.stringify({calendar,contacts:false}),hasCalendarPerm:()=>calendar,
    hasContactsPerm:()=>false,getContacts:()=>'[]',askContacts:()=>calls.push(['askContacts']),requestCalendar:()=>calls.push(['requestCalendar']),
    saveSettings:j=>calls.push(['saveSettings',j]),saveTheme:j=>calls.push(['saveTheme',j]),logError:()=>{},
    copyText:t=>{calls.push(['copyText',t]);return true},shareText:t=>{calls.push(['shareText',t]);return true},
    saveFile:(n,m,c)=>{calls.push(['saveFile',n,m,c]);return true},
    launch:p=>{calls.push(['launch',p]);return launchOk},getShortcuts:()=>'[]',haptic:()=>false,isKeyguardLocked:()=>false,setLockOver:()=>{},
    uninstall:p=>{calls.push(['uninstall',p]);return true},appInfo:()=>true,openUrl:()=>true,search:()=>{}};
  const errs=[];const vc=new VirtualConsole();vc.on('jsdomError',()=>{});
  const dom=new JSDOM(html,{runScripts:'dangerously',pretendToBeVisual:true,virtualConsole:vc,url:'https://appassets.test/index.html',
    beforeParse(w){w.matchMedia=w.matchMedia||(q=>({matches:false,addEventListener(){},removeEventListener(){},addListener(){},removeListener(){}}));
      w.HTMLCanvasElement.prototype.getContext=()=>null;w.scrollTo=()=>{};w.Element.prototype.scrollTo=()=>{};w.ResizeObserver=class{observe(){}unobserve(){}disconnect(){}};
      w.IntersectionObserver=class{observe(){}unobserve(){}disconnect(){}};w.Element.prototype.animate=function(){return{cancel(){},finish(){},set onfinish(f){}}};
      w.Android=A;w.confirm=()=>true;Object.entries(pre).forEach(([k,v])=>w.localStorage.setItem(k,v));w.addEventListener('error',e=>errs.push(e.message))}});
  return {dom,w:dom.window,calls,errs};
}
(async()=>{
  /* 1. no demo apps / fake shortcuts inside the Android app */
  let t=boot();await sleep(1200);let w=t.w;
  ok(w.eval("apps.length===0"),'no demo apps before the phone sends its list');
  w.setApps([{n:'Camera',p:'com.cam'},{n:'Mail',p:'com.mail'}]);
  ok(w.eval("apps.every(a=>!a.p.startsWith('demo.'))"),'apps are only the phone\'s apps');
  ok(w.eval("appShortcuts({n:'Camera',p:'com.cam'}).length===0"),'no fake long-press shortcuts (Selfie / Scan QR)');
  ok(['naSim','pkIn','pkUn'].every(id=>w.document.getElementById(id).style.display==='none'),'simulator buttons hidden on a real device');
  ok([...w.document.querySelectorAll('[data-k="gcm"]')].every(x=>(x.closest('label')||x).style.display==='none'),'Commute toggle and quick tile hidden (no route source)');

  /* 2. launch: recent only recorded when Android opened the app */
  w.eval("launch({n:'Camera',p:'com.cam'})");
  ok(t.calls.some(c=>c[0]==='launch'&&c[1]==='com.cam'),'launch goes to Android.launch');
  ok(JSON.parse(w.localStorage.getItem('mdc-recent')||'[]').includes('com.cam'),'successful launch is recorded as recent');
  let f=boot({launchOk:false});await sleep(600);f.w.setApps([{n:'Gone',p:'com.gone'}]);f.w.eval("launch({n:'Gone',p:'com.gone'})");
  ok(!JSON.parse(f.w.localStorage.getItem('mdc-recent')||'[]').includes('com.gone'),'failed launch is NOT recorded as recent');

  /* 3. full-state backup */
  t=boot({pre:{'mdc-home':'["com.cam"]','mdc-hid':'["com.mail"]','mdc-ov':'{"com.cam":{"n":"Lens"}}','mdc-fol':'[{"n":"Work"}]','yoga-biz-slots':'{"0":"com.mail"}','mdc-smcustom':'[]','mdc-wx':'{"cache":1}'}});await sleep(600);w=t.w;
  w.document.getElementById('qaExport').click();
  const sf=t.calls.find(c=>c[0]==='saveFile');ok(!!sf,'Backup export calls Android.saveFile');
  const bk=sf?JSON.parse(sf[3]):{keys:{}};
  ok(bk.format===2,'backup is format 2');
  ['mdc-home','mdc-hid','mdc-ov','mdc-fol','yoga-biz-slots','mdc-smcustom','mdc-set'].forEach(k=>ok(k in bk.keys,'backup includes '+k));
  ok(!('mdc-wx' in bk.keys),'backup leaves out the weather cache');
  /* restore into a fresh page */
  const r=boot();await sleep(600);const input=r.w.document.getElementById('qaImport');
  const payload=JSON.stringify({format:2,keys:{'mdc-home':'["x.y"]','yoga-biz-slots':'{"1":"a.b"}','mdc-set':JSON.stringify({h24:true}),'evil-key':'1','mdc-bad':'{not json'}});
  Object.defineProperty(input,'files',{value:[{size:payload.length,text:async()=>payload}],configurable:true});
  r.w.eval("window.__reloaded=0;");input.onchange&&await input.onchange();await sleep(50);
  ok(r.w.localStorage.getItem('mdc-home')==='["x.y"]'&&r.w.localStorage.getItem('yoga-biz-slots')==='{"1":"a.b"}','restore writes the saved keys');
  ok(r.w.localStorage.getItem('evil-key')===null&&r.w.localStorage.getItem('mdc-bad')===null,'restore ignores unknown keys and invalid JSON');
  ok(r.calls.some(c=>c[0]==='saveSettings'),'restore also updates the native settings copy');
  const junk=boot();await sleep(600);const i2=junk.w.document.getElementById('qaImport');
  Object.defineProperty(i2,'files',{value:[{size:5,text:async()=>'hello'}],configurable:true});await i2.onchange();
  ok(junk.w.document.getElementById('toast').textContent==='Invalid backup file','garbage file shows "Invalid backup file"');

  /* 4. clipboard + share through the bridge */
  w.document.getElementById('qaCopy').click();await sleep(30);
  ok(t.calls.some(c=>c[0]==='copyText'),'"Copy time" uses Android.copyText');
  ok(w.document.getElementById('toast').textContent==='Time copied','toast says Time copied only after success');
  w.document.getElementById('qaShare').click();await sleep(30);
  ok(t.calls.some(c=>c[0]==='shareText'),'Share uses the Android share sheet');

  /* 5. empty inputs give feedback */
  w.document.getElementById('tin').value='  ';w.document.getElementById('tadd').click();
  ok(w.document.getElementById('toast').textContent==='Type a task first','empty task shows a message');

  /* 6. modes are gone: no mode switch, no Smart Modes, no Golden Lotus panel */
  ok(!!w.document.getElementById('modeSeg')&&!!w.document.getElementById('qaMode'),'mode selector and Mode quick action present (restored in merge)');
  ok(!w.YogaModes&&!w.cycleMode,'no mode API left');

  console.log(`\n${pass} passed, ${fail} failed`);process.exit(fail?1:0);
})();
