/* v1.66: Edit home panel + widget stack/resize grip. node edit-test.js */
const {JSDOM,VirtualConsole}=require('jsdom');const fs=require('fs'),path=require('path');
const html=fs.readFileSync(path.join(__dirname,'../../app/src/main/assets/index.html'),'utf8');
let pass=0,fail=0;const ok=(c,m)=>{c?pass++:fail++;console.log((c?'PASS ':'FAIL ')+m)};const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const A={getBattery:()=>'{"p":57}',getBadges:()=>'{}',getMedia:()=>'null',getNextAlarm:()=>'0',getNextEvent:()=>'null',hasNotificationAccess:()=>false,getPerms:()=>'{}',saveSettings:()=>{},saveTheme:()=>{},logError:()=>{},launch:()=>true,getShortcuts:()=>'[]',haptic:()=>false,isKeyguardLocked:()=>false,setLockOver:()=>{},openUrl:()=>true,search:()=>{},appInfo:()=>true};
const errs=[];
const mk=bridge=>{const vc=new VirtualConsole();vc.on('jsdomError',e=>errs.push(String(e.message).slice(0,160)));
 return new JSDOM(html,{runScripts:'dangerously',pretendToBeVisual:true,virtualConsole:vc,url:'https://appassets.test/index.html',beforeParse(w){w.matchMedia=q=>({matches:false,addEventListener(){},removeEventListener(){},addListener(){},removeListener(){}});w.HTMLCanvasElement.prototype.getContext=()=>null;w.scrollTo=()=>{};w.Element.prototype.scrollTo=()=>{};w.ResizeObserver=class{observe(){}unobserve(){}disconnect(){}};w.IntersectionObserver=class{observe(){}unobserve(){}disconnect(){}};w.Element.prototype.animate=function(){return{cancel(){},finish(){},set onfinish(f){}}};if(bridge)w.Android=A}})};
const tap=(w,t)=>{ev(w,t,'pointerdown',{clientX:5,clientY:5});ev(w,t,'pointerup',{clientX:5,clientY:5});t.click()};
const ev=(w,t,type,o)=>t.dispatchEvent(new w.MouseEvent(type,Object.assign({bubbles:true,button:0},o)));
(async()=>{
 /* ---- 1. long-press empty home -> Edit home ---- */
 {const dom=mk(true),w=dom.window,d=w.document;await sleep(1500);
  const em=d.getElementById('emode'),sh=d.getElementById('sheet'),phone=d.querySelector('.phone');let target=d.getElementById('wall')||phone;d.elementsFromPoint=()=>[target];
  ev(w,target,'pointerdown',{clientX:10,clientY:10});await sleep(700);ev(w,target,'pointerup',{clientX:10,clientY:10});await sleep(60);
  ok(em.classList.contains('open'),'long-press on empty home opens Edit home');
  ok(!sh.classList.contains('open'),'...and does not jump into a Settings section');
  const tabs=[...d.querySelectorAll('#emTabs button')].map(b=>b.textContent);
  ok(JSON.stringify(tabs)==='["Wallpaper","Widgets","Grid","Settings"]','tabs: '+tabs.join(', '));
  ok(d.querySelectorAll('#emBody .sw button').length>3&&d.querySelectorAll('#emBody input[type=range]').length===2,'Wallpaper tab: swatches + dim + blur');
  const sw=[...d.querySelectorAll('#emBody .sw button')].find(b=>b.dataset.v==='theme');tap(w,sw);
  ok(JSON.parse(w.localStorage.getItem('mdc-set')).wp==='theme','Wallpaper swatch saves (theme)');
  const tab=n=>tap(w,[...d.querySelectorAll('#emTabs button')].find(b=>b.textContent===n));
  tab('Grid');const c5=[...d.querySelectorAll('#emBody .seg button')].find(b=>b.textContent==='5');tap(w,c5);
  ok(JSON.parse(w.localStorage.getItem('mdc-set')).hcol==='5','Grid tab: columns = 5 saved');
  tab('Widgets');ok(d.querySelectorAll('#emBody input[type=checkbox]').length===7,'Widgets tab: 7 card switches');
  tab('Settings');tap(w,[...d.querySelectorAll('#emBody button')].find(b=>b.textContent==='All settings'));await sleep(500);
  ok(sh.classList.contains('open')&&!em.classList.contains('open'),'Settings tab: All settings opens Settings, panel closes');
  d.getElementById('done').click();await sleep(300);
  /* hold on the clock still opens its own section */
  target=d.getElementById('read')||d.querySelector('.date');ev(w,target,'pointerdown',{clientX:10,clientY:10});await sleep(700);ev(w,target,'pointerup',{clientX:10,clientY:10});await sleep(60);
  ok(sh.classList.contains('open')&&!em.classList.contains('open'),'hold on clock/date still opens only that Settings section');
  await sleep(400);w.close()}
 /* ---- 2. Widgets page: stack + resize grip (browser preview, placeholder widgets) ---- */
 {const dom=mk(false),w=dom.window,d=w.document;await sleep(1500);
  w.openPage?0:0;d.getElementById('emode');
  w.EDITMODE.open();[...d.querySelectorAll('#emTabs button')][1].click();
  [...d.querySelectorAll('#emBody button')].find(b=>b.textContent==='Add widget').click();await sleep(700);
  ok(d.getElementById('pgW').classList.contains('open'),'Add widget opens the Widgets page');
  ok(d.querySelectorAll('#wgBody .awp').length===1,'a placeholder widget was added (no Android bridge)');
  const grip=d.querySelector('#wgBody .awh');ok(!!grip,'resize grip is shown under the widget');
  const p=()=>d.querySelector('#wgBody .awp'),h0=parseInt(p().style.height);
  ev(w,grip,'pointerdown',{clientX:50,clientY:100});ev(w,grip,'pointermove',{clientX:50,clientY:190});
  ok(parseInt(p().style.height)===h0+90,'dragging the grip down 90px grows it ('+h0+' -> '+p().style.height+')');
  ev(w,grip,'pointerup',{clientX:50,clientY:190});await sleep(50);
  const saved=JSON.parse(w.localStorage.getItem('mdc-aw'));ok(saved[0].px===h0+90,'new height saved ('+saved[0].px+' px)');
  const g2=d.querySelector('#wgBody .awh');ev(w,g2,'pointerdown',{clientX:50,clientY:300});ev(w,g2,'pointermove',{clientX:50,clientY:-900});ev(w,g2,'pointerup',{clientX:50,clientY:-900});
  ok(parseInt(p().style.height)===60,'height never goes below 60 px');
  const g3=d.querySelector('#wgBody .awh');ev(w,g3,'pointerdown',{clientX:50,clientY:0});ev(w,g3,'pointermove',{clientX:50,clientY:5000});ev(w,g3,'pointerup',{clientX:50,clientY:5000});
  ok(parseInt(p().style.height)===420,'height never goes above 420 px');
  [...d.querySelectorAll('#wgBody .awbar button')].find(b=>b.textContent==='M').click();
  ok(parseInt(p().style.height)===160&&JSON.parse(w.localStorage.getItem('mdc-aw'))[0].px===undefined,'M preset resets to 160 px');
  [...d.querySelectorAll('#wgBody .awbar button')].find(b=>b.textContent==='Stack').click();await sleep(100);
  ok(d.querySelectorAll('#wgBody .awp').length===1&&JSON.parse(w.localStorage.getItem('mdc-aw'))[0].w.length===2,'Stack adds a 2nd widget into the same slot');
  ok(!!d.querySelector('#wgBody .awd')&&d.querySelector('#wgBody .awd-n').textContent==='2/2','stack dots and 2/2 counter shown');
  const before=p().textContent;[...d.querySelectorAll('#wgBody .awbar button')].find(b=>b.getAttribute('aria-label')==='Previous widget in stack').click();
  ok(p().textContent!==before,'previous/next flips between stacked widgets');
  await sleep(400);w.close()}
 /* ---- 3. free placement of icons ---- */
 {const dom=mk(true),w=dom.window,d=w.document;await sleep(1500);
  const L=[{n:'Alpha',p:'p.a'},{n:'Beta',p:'p.b'},{n:'Gamma',p:'p.c'}];w.setApps(L);await sleep(100);
  ok(w.homeAdd(L[0])==='ok','app placed on a grid cell first');
  d.getElementById('dock').getBoundingClientRect=()=>({left:0,right:400,top:700,bottom:760,width:400,height:60});
  w.EDITMODE.open();tap(w,[...d.querySelectorAll('#emTabs button')].find(b=>b.textContent==='Grid'));
  const cb=[...d.querySelectorAll('#emBody label.tg')].find(l=>/Free placement/.test(l.textContent));ok(!!cb,'Grid tab has the Free placement switch');
  cb.querySelector('input').checked=true;cb.querySelector('input').dispatchEvent(new w.Event('change'));
  ok(JSON.parse(w.localStorage.getItem('mdc-set')).hfree===true,'Free placement saved');
  w.EDITMODE.close();
  const el=d.querySelector('#hicons .hic');const pe=(type,x,y)=>{const e=new w.MouseEvent(type,{bubbles:true,clientX:x,clientY:y});Object.defineProperty(e,'pointerId',{value:1});d.dispatchEvent(e)};
  w.DRG.go(L[0],el,100,300,1);pe('pointermove',200,350);pe('pointerup',200,350);await sleep(100);
  const hp=JSON.parse(w.localStorage.getItem('mdc-hpos'))['p.a'];
  ok(hp&&hp.fx===160&&hp.fy===310&&hp.c===-1,'dropping at (200,350) stores the exact spot: '+JSON.stringify(hp));
  const ic=d.querySelector('#hicons .hic');ok(ic&&ic.style.left==='160px'&&ic.style.top==='310px','icon is drawn at that exact spot');
  w.DRG.go(L[0],ic,200,350,1);pe('pointermove',120,450);pe('pointerup',120,450);await sleep(100);
  const hp2=JSON.parse(w.localStorage.getItem('mdc-hpos'))['p.a'];ok(hp2.fx===80&&hp2.fy===410,'moving it again follows the finger: '+JSON.stringify(hp2));
  await sleep(400);w.close()}
 /* ---- 4. widgets pinned to the home screen: move + resize ---- */
 {const dom=mk(false),w=dom.window,d=w.document;await sleep(1500);
  w.EDITMODE.open();[...d.querySelectorAll('#emTabs button')][1].click();
  tap(w,[...d.querySelectorAll('#emBody button')].find(b=>b.textContent==='Add widget'));await sleep(700);
  tap(w,[...d.querySelectorAll('#wgBody .awbar button')].find(b=>b.textContent==='Pin to home'));await sleep(100);
  const phone=d.querySelector('.phone');
  ok(phone.classList.contains('arr')&&!d.getElementById('pgW').classList.contains('open'),'Pin to home closes the Widgets page and starts arranging');
  const hw=d.querySelector('#hwid .hw');ok(!!hw,'widget shows on the home screen layer');
  let m=JSON.parse(w.localStorage.getItem('mdc-aw'))[0].hm;ok(m&&m.pg===0&&m.w>100,'pinned with a position and size: '+JSON.stringify(m));
  const x0=m.x,y0=m.y;
  ev(w,hw,'pointerdown',{clientX:100,clientY:200});ev(w,hw,'pointermove',{clientX:110,clientY:260});ev(w,hw,'pointerup',{clientX:110,clientY:260});await sleep(50);
  m=JSON.parse(w.localStorage.getItem('mdc-aw'))[0].hm;ok(m.x===x0+10&&m.y===y0+60,'dragging moves it freely and saves ('+m.x+','+m.y+')');
  const rz=d.querySelector('#hwid .hw-r'),w0=m.w,h0=m.h;
  ev(w,rz,'pointerdown',{clientX:300,clientY:300});ev(w,rz,'pointermove',{clientX:290,clientY:340});ev(w,rz,'pointerup',{clientX:290,clientY:340});await sleep(50);
  m=JSON.parse(w.localStorage.getItem('mdc-aw'))[0].hm;ok(m.h===h0+40&&m.w<=w0&&m.w>=90,'corner handle resizes it and saves ('+m.w+'x'+m.h+')');
  const x1=m.x,y1=m.y;ev(w,d.querySelector('#hwid .hw'),'pointerdown',{clientX:100,clientY:200});ev(w,d.querySelector('#hwid .hw'),'pointermove',{clientX:-900,clientY:-900});ev(w,d.querySelector('#hwid .hw'),'pointerup',{clientX:-900,clientY:-900});
  m=JSON.parse(w.localStorage.getItem('mdc-aw'))[0].hm;ok(m.x===0&&m.y===40,'it cannot be dragged off the screen ('+m.x+','+m.y+')');
  tap(w,d.getElementById('arDone'));ok(!phone.classList.contains('arr')&&!!d.querySelector('#hwid .hw'),'Done leaves the widget on the home screen');
  w.EDITMODE.open();[...d.querySelectorAll('#emTabs button')][1].click();
  tap(w,[...d.querySelectorAll('#emBody button')].find(b=>b.textContent==='Arrange home widgets'));await sleep(500);
  ok(phone.classList.contains('arr'),'Edit home > Arrange home widgets re-enters arrange mode');
  tap(w,[...d.querySelectorAll('#hwid .hw-b button')].find(b=>b.getAttribute('aria-label')==='Take off the home screen'));
  ok(!JSON.parse(w.localStorage.getItem('mdc-aw'))[0].hm&&!d.querySelector('#hwid .hw'),'x takes it off the home screen again');
  await sleep(400);w.close()}
 console.log('page errors:',errs.length?errs.join(' | '):'none');
 console.log(pass+' pass, '+fail+' fail');process.exit(fail||errs.length?1:0)})();
