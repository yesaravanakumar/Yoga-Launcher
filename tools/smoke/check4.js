const {JSDOM,VirtualConsole}=require('jsdom');const fs=require('fs'),path=require('path');
const html=fs.readFileSync(path.join(__dirname,'../../app/src/main/assets/index.html'),'utf8');
const sleep=ms=>new Promise(r=>setTimeout(r,ms));const errs=[];const calls=[];
const A={getBattery:()=>'{"p":57}',getBadges:()=>'{}',getMedia:()=>'null',getNextAlarm:()=>'0',getNextEvent:()=>'null',hasNotificationAccess:()=>false,getPerms:()=>'{}',hasContactsPerm:()=>true,
 getContacts:()=>JSON.stringify([{n:'Amma',p:'111',u:''},{n:'Gautham',p:'222',u:''},{n:'Maya',p:'333',u:''}]),
 saveSettings:()=>{},saveTheme:()=>{},logError:()=>{},launch:p=>{calls.push(['launch',p]);return true},getShortcuts:()=>'[]',haptic:()=>false,isKeyguardLocked:()=>false,setLockOver:()=>{},openUrl:u=>{calls.push(['openUrl',u]);return true},search:()=>{},appInfo:()=>true};
const vc=new VirtualConsole();vc.on('jsdomError',e=>errs.push(e.message));
const dom=new JSDOM(html,{runScripts:'dangerously',pretendToBeVisual:true,virtualConsole:vc,url:'https://appassets.test/index.html',
 beforeParse(w){w.matchMedia=w.matchMedia||(q=>({matches:false,addEventListener(){},removeEventListener(){},addListener(){},removeListener(){}}));w.HTMLCanvasElement.prototype.getContext=()=>null;w.scrollTo=()=>{};w.Element.prototype.scrollTo=()=>{};
 w.ResizeObserver=class{observe(){}unobserve(){}disconnect(){}};w.IntersectionObserver=class{observe(){}unobserve(){}disconnect(){}};w.Element.prototype.animate=function(){return{cancel(){},finish(){},set onfinish(f){}}};w.Android=A;w.addEventListener('error',e=>errs.push(e.message))}});
const w=dom.window,d=w.document;let p=0,f=0;const ok=(c,m)=>{c?p++:f++;console.log((c?'PASS ':'FAIL ')+m)};
const names=()=>[...d.querySelectorAll('#awWave .awi')].map(e=>e.querySelector('span').textContent);
const type=v=>{const q=d.getElementById('awQ');q.value=v;q.dispatchEvent(new w.Event('input'))};
(async()=>{await sleep(1500);w.setApps([{n:'Gmail',p:'com.gmail'},{n:'Gallery',p:'com.gal'},{n:'Maps',p:'com.maps'}]);w.eval('loadContacts()');
 w.eval('openDrawer()');await sleep(500);
 const sr=d.getElementById('awSr');ok(!!sr,'search button exists in the arc');
 sr.click();await sleep(100);ok(d.getElementById('awSearch').classList.contains('on')&&sr.classList.contains('on'),'tap opens the search bar');
 type('ga');await sleep(200);console.log('  "ga" ->',names());
 ok(names().includes('Gallery')&&names().includes('Gautham'),'"ga" finds the app Gallery AND the contact Gautham');
 type('ma');await sleep(200);console.log('  "ma" ->',names());
 ok(names().includes('Maps')&&names().includes('Maya')&&names().includes('Amma'),'"ma" finds Maps, Maya and Amma together');
 type('maya');await sleep(200);d.getElementById('awQ').dispatchEvent(new w.KeyboardEvent('keydown',{key:'Enter',bubbles:true}));await sleep(80);
 ok(calls.some(c=>c[0]==='openUrl'&&c[1]==='tel:333'),'Enter on contact Maya dials her: '+JSON.stringify(calls.slice(-1)));
 w.eval('openDrawer()');await sleep(400);
 ok(!d.getElementById('awSr').classList.contains('on'),'reopening the drawer resets the search button');
 d.getElementById('awSr').click();type('zzzq');await sleep(200);ok(d.getElementById('aw').classList.contains('empty')&&/apps or contacts/.test(d.getElementById('awEmpty').textContent),'no match message: '+d.getElementById('awEmpty').textContent.trim());
 d.getElementById('awSr').click();await sleep(150);ok(!d.getElementById('awSearch').classList.contains('on'),'tapping again closes search');ok(names().length===3+0||names().length>=3,'full list is back: '+names());
 console.log('errors',[...new Set(errs)].slice(0,3));console.log(p+' passed, '+f+' failed');process.exit(0)})();
