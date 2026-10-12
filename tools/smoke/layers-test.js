/* mandala layer rotation. node layers-test.js */
const {JSDOM,VirtualConsole}=require('jsdom');const fs=require('fs'),path=require('path');
const html=fs.readFileSync(path.join(__dirname,'../../app/src/main/assets/index.html'),'utf8');
let pass=0,fail=0;const ok=(c,m)=>{c?pass++:fail++;console.log((c?'PASS ':'FAIL ')+m)};const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const errs=[];const vc=new VirtualConsole();vc.on('jsdomError',e=>errs.push(e.message));
const A={getBattery:()=>'{"p":57}',getBadges:()=>'{}',getMedia:()=>'null',getNextAlarm:()=>'0',getNextEvent:()=>'null',hasNotificationAccess:()=>false,getPerms:()=>'{}',saveSettings:()=>{},saveTheme:()=>{},logError:()=>{},launch:()=>true,getShortcuts:()=>'[]',haptic:()=>false,isKeyguardLocked:()=>false,setLockOver:()=>{},openUrl:()=>true,search:()=>{},appInfo:()=>true};
const dom=new JSDOM(html,{runScripts:'dangerously',pretendToBeVisual:true,virtualConsole:vc,url:'https://appassets.test/index.html',beforeParse(w){w.matchMedia=q=>({matches:false,addEventListener(){},removeEventListener(){},addListener(){},removeListener(){}});w.HTMLCanvasElement.prototype.getContext=()=>null;w.scrollTo=()=>{};w.Element.prototype.scrollTo=()=>{};w.ResizeObserver=class{observe(){}unobserve(){}disconnect(){}};w.IntersectionObserver=class{observe(){}unobserve(){}disconnect(){}};w.Element.prototype.animate=function(){return{cancel(){},finish(){},set onfinish(f){}}};w.Android=A}});
(async()=>{const w=dom.window,d=w.document;await sleep(1500);
 const M=d.getElementById('mandala');
 const lays=()=>[...M.querySelectorAll('.mlay')];
 const anim=l=>l.style.getPropertyValue('animation');
 w.setSettings({mspin:true,mand:true,mst:'lotus',mrl0:3,mrl1:-2,mrl2:2,mrl3:-1,mrl4:1,mrl5:-1});await sleep(150);
 ok(lays().length===6,'lotus builds 6 layers (got '+lays().length+')');
 ok(lays().every(l=>l.querySelector('svg')&&l.querySelector('svg').children.length),'every layer has its own drawing');
 ok(lays().filter(l=>l.querySelector('use,circle,path')).length===lays().length,'no empty layers');
 const L=lays();
 ok(/mandalaSpin/.test(anim(L[0]))&&!/reverse/.test(anim(L[0])),'layer 1 spins clockwise: '+anim(L[0]));
 ok(/reverse/.test(anim(L[1])),'layer 2 spins anticlockwise: '+anim(L[1]));
 ok(parseInt(anim(L[0]).match(/(\d+)s/)[1])<parseInt(anim(L[1]).match(/(\d+)s/)[1]),'layer 1 (speed 3) is faster than layer 2 (speed 2)... or equal-ish check');
 w.setSettings({mrl3:0});await sleep(100);
 ok(anim(lays().find(l=>l.dataset.i==='3'))==='none','speed 0 -> that layer stands still');
 w.setSettings({mrl0:0,mrl1:0,mrl2:0,mrl3:0,mrl4:0,mrl5:0});await sleep(100);
 ok(lays().every(l=>anim(l)==='none'),'Stop all -> every layer still');
 ok(!/mandalaSpin/.test(M.style.animation),'... and the whole mandala does not spin either (Stop all really stops)');
 w.setSettings({mspin:false,mrl0:3,mrl1:-2});await sleep(100);
 ok(lays().every(l=>anim(l)==='none'),'"Slow mandala spin" off -> nothing rotates');
 w.setSettings({mspin:true,mrl0:3});await sleep(100);
 w.setSettings({mrl0:3,mspd:4});await sleep(100);ok(parseInt(anim(lays()[0]).match(/(\d+)s/)[1])<107,'Spin speed slider scales the layers: '+anim(lays()[0]));w.setSettings({mspd:2});
 // other styles
 for(const st of ['star','flower','sacred','spiro','wheel','crystal','hex','zen','sun','radar','yantra','padma','mehndi','peacock','guil','orbit']){
   w.setSettings({mst:st});await sleep(60);const n=lays().length;
   if(!(n>=1&&lays().every(l=>l.querySelector('svg').children.length)))ok(false,st+' layers='+n);
   else pass++;
 }
 console.log('all 16 other styles build layers');
 // use refs resolve (shared defs present)
 const uses=[...M.querySelectorAll('use')];const ids=new Set([...M.querySelectorAll('defs path')].map(p=>p.id));
 ok(uses.every(u=>ids.has((u.getAttribute('href')||'').slice(1))),'every <use> points at a shape defined in the shared defs');
 ok(!errs.length,'no script errors '+(errs[0]||''));
 console.log(pass+' pass, '+fail+' fail');process.exit(fail?1:0)})();
