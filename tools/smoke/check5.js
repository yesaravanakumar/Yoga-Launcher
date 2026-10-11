const {JSDOM,VirtualConsole}=require('jsdom');const fs=require('fs'),path=require('path');
const html=fs.readFileSync(path.join(__dirname,'../../app/src/main/assets/index.html'),'utf8');
const sleep=ms=>new Promise(r=>setTimeout(r,ms));const errs=[];const calls=[];let delOk=true;
const A={getBattery:()=>'{"p":57}',getBadges:()=>'{}',getMedia:()=>'null',getNextAlarm:()=>'0',getNextEvent:()=>'null',hasNotificationAccess:()=>false,getPerms:()=>'{}',hasContactsPerm:()=>true,
 getContacts:()=>JSON.stringify([{n:'Amma',p:'111',u:''},{n:'Bala',p:'222',u:''},{n:'Chitra',p:'333',u:''}]),
 editContact:n=>{calls.push(['edit',n]);return true},deleteContact:n=>{calls.push(['del',n]);return delOk},
 saveSettings:()=>{},saveTheme:()=>{},logError:()=>{},launch:p=>{calls.push(['launch',p]);return true},getShortcuts:()=>'[]',haptic:()=>false,isKeyguardLocked:()=>false,setLockOver:()=>{},openUrl:u=>{calls.push(['openUrl',u]);return true},search:()=>{},appInfo:()=>true};
const vc=new VirtualConsole();vc.on('jsdomError',e=>errs.push(e.message));
const dom=new JSDOM(html,{runScripts:'dangerously',pretendToBeVisual:true,virtualConsole:vc,url:'https://appassets.test/index.html',
 beforeParse(w){w.matchMedia=w.matchMedia||(q=>({matches:false,addEventListener(){},removeEventListener(){},addListener(){},removeListener(){}}));w.HTMLCanvasElement.prototype.getContext=()=>null;w.scrollTo=()=>{};w.Element.prototype.scrollTo=()=>{};
 w.ResizeObserver=class{observe(){}unobserve(){}disconnect(){}};w.IntersectionObserver=class{observe(){}unobserve(){}disconnect(){}};w.Element.prototype.animate=function(){return{cancel(){},finish(){},set onfinish(f){}}};w.Android=A;w.confirm=()=>true;w.addEventListener('error',e=>errs.push(e.message))}});
const w=dom.window,d=w.document;let p=0,f=0;const ok=(c,m)=>{c?p++:f++;console.log((c?'PASS ':'FAIL ')+m)};
const names=()=>[...d.querySelectorAll('#awWave .awi')].map(e=>e.querySelector('span').textContent);
(async()=>{await sleep(1500);w.setApps([{n:'Gmail',p:'com.gmail'}]);w.eval('loadContacts()');w.eval('openDrawer()');await sleep(500);
 d.getElementById('awCt').click();await sleep(700);
 const css=[...d.querySelectorAll('style')].map(s=>s.textContent).join('\n');
 ok(!/#aw \.awr/.test(css),'per-row pill CSS gone');
 ok(!/#aw\.ctm #awEdit/.test(css),'Edit/Star/Home/Delete no longer disabled in Contacts view');
 const ca=d.getElementById('awCa');ok(!ca.hidden&&ca.querySelectorAll('.cab').length===4,'one pill for the selected contact with 4 icons: '+ca.innerHTML.match(/cab-\w+/g));
 const L=parseFloat(ca.style.left),T=parseFloat(ca.style.top),railL=parseFloat(d.getElementById('awRail').style.left),W=d.getElementById('aw').clientWidth||360;
 console.log('  pill left',L,'top',T,'| rail left at focus',railL,'(rail is 24 wide) | W',W,'| pill buttons',ca.style.getPropertyValue('--bw'));
 const Hh=780,Ww=360,y0=.56*Hh,yb=T+36,railEdge=Math.max(...[T,T+36].map(y=>.40*Ww-.40*Hh+Math.sqrt((.5*Hh)**2-(y-y0)**2)+12));console.log('  A-Z letters right edge beside the pill:',railEdge.toFixed(1));ok(L>=railEdge,'pill starts to the right of the A-Z letters (pill '+L.toFixed(1)+' >= '+railEdge.toFixed(1)+')');ok(L+4*parseFloat(ca.style.getPropertyValue('--bw'))<=W,'pill fits on screen');
 const foc=()=>d.getElementById('awNm').textContent;console.log('  focused:',foc());
 d.getElementById('awEdit').click();ok(calls.some(c=>c[0]==='edit'),'Edit opens phone contact editor: '+JSON.stringify(calls.slice(-1)));
 d.getElementById('awStar').click();ok(w.eval("JSON.parse(localStorage.getItem('mdc-cfav')||'[]').length")===1&&d.getElementById('awStar').style.color!=='','Star saves a favourite and turns gold: '+d.getElementById('awStar').style.color);
 d.getElementById('awStar').click();ok(w.eval("JSON.parse(localStorage.getItem('mdc-cfav')||'[]').length")===0,'Star again removes it');
 d.getElementById('awSms')&&0;
 d.getElementById('awHome').click();await sleep(100);
 ok(w.eval("home.some(p=>p.indexOf('contact:')===0)")&&w.eval("Object.keys(HCT).length")===1,'Home puts the contact on the home screen list: '+w.eval("JSON.stringify(home)"));
 const hic=[...d.querySelectorAll('#hic .hic, #hgrid .app')].map(e=>e.textContent);console.log('  home icons:',hic);
 ok(hic.some(t=>/Amma|Bala|Chitra/.test(t)),'contact icon is drawn on the home screen');
 w.eval("refreshApps()");ok(w.eval("home.some(p=>p.indexOf('contact:')===0)"),'contact stays on home after the app list refreshes');
 d.getElementById('awHome').click();await sleep(100);ok(!w.eval("home.some(p=>p.indexOf('contact:')===0)")&&w.eval("Object.keys(HCT).length")===0,'Home again removes it');
 const before=w.eval('CONTACTS.length');d.getElementById('awDel').click();await sleep(300);
 ok(calls.some(c=>c[0]==='del')&&w.eval('CONTACTS.length')===before-1,'Delete asks the phone and removes the contact: '+before+' -> '+w.eval('CONTACTS.length'));
 delOk=false;const b2=w.eval('CONTACTS.length');d.getElementById('awDel').click();await sleep(200);ok(w.eval('CONTACTS.length')===b2,'failed delete keeps the contact');
 console.log('errors',[...new Set(errs)].slice(0,3));console.log(p+' passed, '+f+' failed');process.exit(0)})();
