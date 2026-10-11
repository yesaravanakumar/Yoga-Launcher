const {JSDOM,VirtualConsole}=require('jsdom');const fs=require('fs'),path=require('path');
const html=fs.readFileSync(path.join(__dirname,'../../app/src/main/assets/index.html'),'utf8');
const sleep=ms=>new Promise(r=>setTimeout(r,ms));const asked=[];const errs=[];
const PNG='data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==';
const calls=[];const A={waChat:n=>{calls.push(['waChat',n]);return true},waCall:n=>{calls.push(['waCall',n]);return true},openUrl:u=>{calls.push(['openUrl',u]);return true},getBattery:()=>'{"p":57}',getBadges:()=>'{}',getMedia:()=>'null',getNextAlarm:()=>'0',getNextEvent:()=>'null',hasNotificationAccess:()=>false,getPerms:()=>'{}',hasContactsPerm:()=>true,
 getContacts:()=>JSON.stringify([{n:'Amma',p:'111',u:'content://com.android.contacts/contacts/1/photo'},{n:'Bala',p:'222',u:''},{n:'Evil',p:'333',u:'file:///etc/passwd'}]),
 getContactPhoto:u=>{asked.push(u);return PNG},saveSettings:()=>{},saveTheme:()=>{},logError:()=>{},launch:()=>true,getShortcuts:()=>'[]',haptic:()=>false,isKeyguardLocked:()=>false,setLockOver:()=>{},search:()=>{},appInfo:()=>true};
const vc=new VirtualConsole();vc.on('jsdomError',e=>errs.push(e.message));
const dom=new JSDOM(html,{runScripts:'dangerously',pretendToBeVisual:true,virtualConsole:vc,url:'https://appassets.test/index.html',resources:'usable',
 beforeParse(w){w.matchMedia=w.matchMedia||(q=>({matches:false,addEventListener(){},removeEventListener(){},addListener(){},removeListener(){}}));w.HTMLCanvasElement.prototype.getContext=()=>null;w.scrollTo=()=>{};w.Element.prototype.scrollTo=()=>{};
 w.ResizeObserver=class{observe(){}unobserve(){}disconnect(){}};w.IntersectionObserver=class{observe(){}unobserve(){}disconnect(){}};w.Element.prototype.animate=function(){return{cancel(){},finish(){},set onfinish(f){}}};w.Android=A}});
const w=dom.window;let p=0,f=0;const ok=(c,m)=>{c?p++:f++;console.log((c?'PASS ':'FAIL ')+m)};
(async()=>{await sleep(1500);w.setApps([{n:'Gmail',p:'com.gmail'}]);
 w.eval('loadContacts()');w.eval('openDrawer()');await sleep(500);w.document.getElementById('awCt').click();await sleep(800);console.log('  contacts:',w.eval('CONTACTS.length'),'awi:',w.document.querySelectorAll('#awWave .awi').length);
 ok(w.eval("CONTACTS.length")===3,'3 contacts loaded');
 ok(w.eval("CONTACTS.find(c=>c.n==='Amma').u").startsWith('content://com.android.contacts/'),'photo uri kept for Amma');
 ok(w.eval("CONTACTS.find(c=>c.n==='Evil').u")==='','non-contacts uri dropped');
 console.log('  getContactPhoto asked for:',asked);
 ok(asked.length===1&&/photo/.test(asked[0]),'photo requested only for contacts that have one');
 ok(w.document.querySelectorAll('#awWave .awr').length===0,'no pill on the other contact rows');
 const ca=w.document.getElementById('awCa');
 ok(ca&&!ca.hidden,'action row visible for a selected contact');
 ok(ca&&ca.querySelectorAll('button').length===4,'4 buttons incl. SMS');ok(!!ca.querySelector('.cab-sms'),'SMS button present');
 ca.querySelector('.cab-call').click();ok(calls.some(c=>c[0]==='openUrl'&&/^tel:/.test(c[1])),'Call opens dialer: '+JSON.stringify(calls.slice(-1)));
 ca.querySelector('.cab-msg').click();ok(calls.some(c=>c[0]==='waChat'),'WhatsApp message -> waChat');
 ca.querySelector('.cab-wac').click();ok(calls.some(c=>c[0]==='waCall'),'WhatsApp call -> waCall');
 w.document.getElementById('awCt').click();await sleep(400);
 ok(ca.hidden,'row hidden when back on apps');
 console.log('errors',errs.slice(0,3));console.log(p+' passed, '+f+' failed');process.exit(0)})();
