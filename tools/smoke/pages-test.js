/* v1.57 home pages + return-from-drawer. node pages-test.js */
const {JSDOM,VirtualConsole}=require('jsdom');const fs=require('fs'),path=require('path');
const html=fs.readFileSync(path.join(__dirname,'../../app/src/main/assets/index.html'),'utf8');
let pass=0,fail=0;const ok=(c,m)=>{c?pass++:fail++;console.log((c?'PASS ':'FAIL ')+m)};
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const errs=[];const vc=new VirtualConsole();vc.on('jsdomError',e=>errs.push(e.message));
const A={getBattery:()=>'{"p":57}',getBadges:()=>'{}',getMedia:()=>'null',getNextAlarm:()=>'0',getNextEvent:()=>'null',hasNotificationAccess:()=>false,getPerms:()=>'{}',saveSettings:()=>{},saveTheme:()=>{},logError:()=>{},launch:()=>true,getShortcuts:()=>'[]',haptic:()=>false,isKeyguardLocked:()=>false,setLockOver:()=>{},openUrl:()=>true,search:()=>{},appInfo:()=>true};
const dom=new JSDOM(html,{runScripts:'dangerously',pretendToBeVisual:true,virtualConsole:vc,url:'https://appassets.test/index.html',
 beforeParse(w){w.matchMedia=q=>({matches:false,addEventListener(){},removeEventListener(){},addListener(){},removeListener(){}});
  w.HTMLCanvasElement.prototype.getContext=()=>null;w.scrollTo=()=>{};w.Element.prototype.scrollTo=()=>{};w.ResizeObserver=class{observe(){}unobserve(){}disconnect(){}};
  w.IntersectionObserver=class{observe(){}unobserve(){}disconnect(){}};w.Element.prototype.animate=function(){return{cancel(){},finish(){},set onfinish(f){}}};w.Android=A;w.addEventListener('error',e=>errs.push(e.message))}});
(async()=>{
 const w=dom.window,d=w.document;await sleep(1500);
 w.setApps([{n:'Gmail',p:'com.gmail'},{n:'Camera',p:'com.cam'},{n:'Spotify',p:'com.spot'}]);await sleep(100);
 const g=d.getElementById('hpg');
 ok(!!g,'#hpg exists');
 ok(g.querySelectorAll('.hpd').length===3,'starts with 3 page dots (v1.86 default)');
 ok(!!g.querySelector('.hpa'),'"+" add-page button present');
 g.querySelector('.hpa').click();
 ok(g.querySelectorAll('.hpd').length===4,'+ adds a 4th page');
 ok(g.querySelectorAll('.hpd')[3].classList.contains('on'),'new page is the current page');
 ok(!!g.querySelector('.hpr'),'empty extra page offers the remove button');
 g.querySelectorAll('.hpd')[0].click();
 ok(g.querySelectorAll('.hpd')[0].classList.contains('on'),'tapping dot 1 switches back');
 ok(JSON.parse(w.localStorage.getItem('mdc-hpg'))===0,'current page is remembered (mdc-hpg)');
 g.querySelectorAll('.hpd')[3].click();g.querySelector('.hpr').click();
 ok(g.querySelectorAll('.hpd').length===3,'remove deletes the empty page');
 // drawer opened from the Home grid page returns there
 w.document.querySelector('.pg-h button[data-pg="pgF"]').click();
 ok(d.getElementById('pgF').classList.contains('open'),'Home grid page opens');
 d.querySelector('#hgrid .add').click();
 ok(d.getElementById('drawer').classList.contains('open')&&!d.getElementById('pgF').classList.contains('open'),'Add app opens the drawer');
 d.getElementById('dclose').click();
 ok(!d.getElementById('drawer').classList.contains('open')&&d.getElementById('pgF').classList.contains('open'),'closing the drawer returns to the Home grid page');
 d.querySelector('#pgF .pgx').click();
 ok(!d.getElementById('pgF').classList.contains('open'),'closing the page goes to the clock home');
 // drawer opened from the clock home returns to the clock home
 w.openDrawer&&0;w.YD.open();
 d.getElementById('dclose').click();
 ok(!d.getElementById('drawer').classList.contains('open')&&!d.getElementById('pgF').classList.contains('open'),'drawer from the clock home closes to the clock home');
 // back button path
 d.querySelector('.pg-h button[data-pg="pgF"]').click();d.querySelector('#hgrid .add').click();
 w.onBack();
 ok(d.getElementById('pgF').classList.contains('open')&&!d.getElementById('drawer').classList.contains('open'),'Back from the drawer returns to the Home grid page');
 w.onBack();ok(!d.getElementById('pgF').classList.contains('open'),'second Back leaves to the clock home');
 console.log('errors:',errs.length?errs.slice(0,3):'none');
 console.log(pass+' pass, '+fail+' fail');process.exit(fail?1:0);
})();
