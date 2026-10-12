/* hold any part of the home screen -> that part's settings. node hold-test.js */
const {JSDOM,VirtualConsole}=require('jsdom');const fs=require('fs'),path=require('path');
const html=fs.readFileSync(path.join(__dirname,'../../app/src/main/assets/index.html'),'utf8');
let pass=0,fail=0;const ok=(c,m)=>{c?pass++:fail++;console.log((c?'PASS ':'FAIL ')+m)};const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const A={getBattery:()=>'{"p":57}',getBadges:()=>'{}',getMedia:()=>'null',getNextAlarm:()=>'0',getNextEvent:()=>'null',hasNotificationAccess:()=>false,getPerms:()=>'{}',saveSettings:()=>{},saveTheme:()=>{},logError:()=>{},launch:()=>true,getShortcuts:()=>'[]',haptic:()=>false,isKeyguardLocked:()=>false,setLockOver:()=>{},openUrl:()=>true,search:()=>{},appInfo:()=>true};
const dom=new JSDOM(html,{runScripts:'dangerously',pretendToBeVisual:true,virtualConsole:new VirtualConsole(),url:'https://appassets.test/index.html',beforeParse(w){w.matchMedia=q=>({matches:false,addEventListener(){},removeEventListener(){},addListener(){},removeListener(){}});w.HTMLCanvasElement.prototype.getContext=()=>null;w.scrollTo=()=>{};w.Element.prototype.scrollTo=()=>{};w.ResizeObserver=class{observe(){}unobserve(){}disconnect(){}};w.IntersectionObserver=class{observe(){}unobserve(){}disconnect(){}};w.Element.prototype.animate=function(){return{cancel(){},finish(){},set onfinish(f){}}};w.Android=A}});
(async()=>{const w=dom.window,d=w.document;await sleep(1500);
 const phone=d.querySelector('.phone');let target=null;d.elementsFromPoint=()=>target?[target]:[];
 const rectOK=e=>{e.getBoundingClientRect=()=>({left:0,right:50,top:0,bottom:50,width:50,height:50})};
 async function hold(sel,expect){
   const sh=d.getElementById('sheet');await sleep(1600);if(sh.classList.contains('open')){d.getElementById('done').click();await sleep(400)}
   target=d.querySelector(sel);if(!target){ok(false,sel+' exists');return}
   target.dispatchEvent(new w.MouseEvent('pointerdown',{bubbles:true,clientX:10,clientY:10,button:0}));
   await sleep(700);
   target.dispatchEvent(new w.MouseEvent('pointerup',{bubbles:true,clientX:10,clientY:10}));await sleep(60);
   const open=sh.classList.contains('open'),bt0=0,bt=(d.getElementById('fbt')||{}).textContent||'';
   if(!open)console.log('  sheet did not open; HOLDSEC=',w.HOLDSEC);ok(open&&bt.toUpperCase().includes(expect.toUpperCase()),'hold '+sel+' -> '+expect+'  (badge: "'+bt+'")');
 }
 await hold('#wxl','Widgets');
 d.getElementById('nt').textContent='Alarm 6:30';
 await hold('#nt','At a glance');
 await hold('#dd','Clock');
 d.querySelector('#hpg .hpd').dispatchEvent(new w.Event('x'));
 await hold('#hpg .hpd','Widgets');
 console.log(pass+' pass, '+fail+' fail');process.exit(fail?1:0)})();
