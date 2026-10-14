/* Reactive Matter core checks (jsdom). node fx-core-test.js [index.html] */
const {JSDOM,VirtualConsole}=require('jsdom');const fs=require('fs'),path=require('path');
const html=fs.readFileSync(process.argv[2]||path.join(__dirname,'../../app/src/main/assets/index.html'),'utf8');
let pass=0,fail=0;const ok=(c,m)=>{c?pass++:fail++;console.log((c?'PASS ':'FAIL ')+m)};const sleep=ms=>new Promise(r=>setTimeout(r,ms));
function boot(rmStart=false){const state={rm:rmStart,ls:[]};const errs=[];const vc=new VirtualConsole();vc.on('jsdomError',()=>{});
 const dom=new JSDOM(html,{runScripts:'dangerously',pretendToBeVisual:true,virtualConsole:vc,url:'https://appassets.test/index.html',
 beforeParse(w){w.matchMedia=q=>({get matches(){return /reduced-motion/.test(q)?state.rm:false},addEventListener(t,f){if(/reduced-motion/.test(q))state.ls.push(f)},removeEventListener(){},addListener(){},removeListener(){}});
  w.HTMLCanvasElement.prototype.getContext=()=>null;w.scrollTo=()=>{};w.Element.prototype.scrollTo=()=>{};w.ResizeObserver=class{observe(){}unobserve(){}disconnect(){}};
  w.IntersectionObserver=class{observe(){}unobserve(){}disconnect(){}};w.Element.prototype.animate=function(){return{cancel(){},finish(){},set onfinish(f){}}};
  w.Android={getBattery:()=>JSON.stringify({p:57,c:false,f:false,pl:false}),getBadges:()=>'{}',getMedia:()=>'null',getNextAlarm:()=>'0',getNextEvent:()=>'null',hasNotificationAccess:()=>false,getPerms:()=>'{}',hasCalendarPerm:()=>false,hasContactsPerm:()=>false,getContacts:()=>'[]',saveSettings:()=>{},saveTheme:()=>{},logError:()=>{},getShortcuts:()=>'[]',haptic:()=>false,isKeyguardLocked:()=>false,setLockOver:()=>{},launch:()=>true};
  w.addEventListener('error',e=>errs.push(e.message))}});
 return {dom,w:dom.window,errs,state}}
(async()=>{
 let t=boot();await sleep(1200);let w=t.w;const F=w.AWFX,C=w.AWCFG;
 ok(!!F&&!!C&&!!w.AWENG,'AWFX core, AWCFG and AWENG are present');
 ok(!!F&&F.status().user===1&&F.status().eff===1,'default is Subtle, on');
 C.set('fx_ripple',false);const seen=[];F.layer('p1',{min:1,on:{open:()=>seen.push('open'),select:()=>seen.push('select'),down:()=>seen.push('down'),tap:()=>seen.push('tap')},stop:()=>seen.push('stop')});
 F.layer('p3',{min:3,on:{open:()=>seen.push('p3open')}});
 C.set('fxLevel','balanced');ok(F.status().eff===2,'Balanced maps to level 2');
 C.set('fxLevel','exp');ok(F.status().eff===3,'Experimental maps to level 3');
 C.set('fxLevel','off');ok(F.status().eff===0,'Off maps to 0');
 C.set('fxLevel','subtle');C.set('fxOn',false);ok(F.status().eff===0,'master toggle off beats the preset');C.set('fxOn',true);ok(F.status().eff===1,'master toggle back on');
 /* min level gating */
 F.on('open',{});ok(seen.includes('open')&&!seen.includes('p3open'),'a layer needing Experimental stays quiet at Subtle');
 C.set('fxLevel','exp');seen.length=0;F.on('open',{});ok(seen.includes('p3open'),'it runs at Experimental');
 /* per-effect switch */
 C.set('fx_p3',false);seen.length=0;F.on('open',{});ok(!seen.includes('p3open'),'per-effect switch disables one layer only');C.set('fx_p3',true);C.set('fxLevel','subtle');
 /* gesture cancels a running layer, close settles everything */
 seen.length=0;F.on('select',{});F.on('down',{});ok(seen.indexOf('stop')>=0&&seen.indexOf('stop')<seen.indexOf('down'),'a new gesture stops a running layer before it handles the touch');
 /* ticker ends by itself */
 let n=0;w.YD.open();await sleep(30);F.tick(()=>{n++;return n<5?true:false});await sleep(400);ok(n===5&&F._busy()===0,'shared ticker stops itself when its tasks finish (no perpetual loop)');
 /* close settles tasks and layer */
 seen.length=0;F.on('select',{});F.tick(()=>true);ok(F._busy()>0,'a running task keeps the ticker busy');w.YD.stop();ok(F._busy()===0&&seen.includes('stop'),'closing the drawer cancels tasks and stops layers');
 /* inline styles put back */
 const el=w.document.createElement('div');el.style.translate='4px 2px';F.mark(el,'translate');F.settle();ok(el.style.translate==='','settle() restores marked inline styles');
 /* a throwing layer is isolated */
 F.layer('bad',{min:1,on:{select:()=>{throw new Error('boom')}}});F.on('select',{});F.on('select',{});ok(F._layers.get('bad').dead&&t.errs.length===0,'a layer that throws is switched off, the drawer keeps working');
 /* frame guard */
 C.set('fxLevel','exp');C.set('fxAuto',true);for(let i=0;i<48;i++)F._sample(40);ok(F.status().cap===2&&F.status().eff===2,'two slow windows ease Experimental to Balanced');
 for(let i=0;i<96;i++)F._sample(40);ok(F.status().eff===0,'continued slow frames end at Off');
 F.retest();ok(F.status().cap===3&&F.status().eff===3,'Re-test restores the chosen level');
 C.set('fxAuto',false);for(let i=0;i<96;i++)F._sample(40);ok(F.status().eff===3,'Auto-protect off: no easing');C.set('fxAuto',true);
 for(let i=0;i<48;i++)F._sample(12);ok(F.status().eff===3,'fast frames never ease');
 /* choosing a level again clears an old ease */
 for(let i=0;i<48;i++)F._sample(40);ok(F.status().eff<3,'eased again');C.set('fxLevel','balanced');ok(F.status().cap===3&&F.status().eff===2,'picking a preset resets the guard');
 /* settings tab */
 C.open();await sleep(50);const tabs=[...w.document.querySelectorAll('#awCfg .tb button')].map(b=>b.textContent);ok(tabs.includes('FX'),'Arc settings has an FX tab');
 const fxb=[...w.document.querySelectorAll('#awCfg .tb button')].find(b=>b.textContent==='FX');fxb.click();await sleep(30);
 const labs=[...w.document.querySelectorAll('#awCfg .bd label')].map(l=>l.textContent);ok(labs.includes('Reactive Matter FX')&&labs.includes('Intensity'),'FX tab shows the master switch and Intensity'); ok(labs.includes('Prism edges'),'FX tab lists the Prism edges switch');
 const btns=[...w.document.querySelectorAll('#awCfg .bd .sw button')].map(b=>b.textContent);ok(['Off','Subtle','Balanced','Experimental'].every(x=>btns.includes(x)),'Off / Subtle / Balanced / Experimental presets are offered');
 [...w.document.querySelectorAll('#awCfg .bd .sw button')].find(b=>b.textContent==='Off').click();ok(C.get().fxLevel==='off'&&F.status().eff===0,'tapping Off turns FX off and saves it');
 ok(!!w.document.getElementById('awFxSt'),'FX tab shows a status line');
 /* reduced motion */
 let t2=boot(true);await sleep(1200);const F2=t2.w.AWFX;const got=[];F2.layer('mo',{min:1,rm:false,on:{open:()=>got.push('mo')}});F2.layer('st',{min:1,rm:true,on:{open:a=>got.push('st')}});
 F2.on('open',{});ok(!got.includes('mo')&&got.includes('st'),'reduced motion: only layers with a static fallback run');
 t2.state.rm=false;t2.state.ls.forEach(f=>f());got.length=0;F2.on('open',{});ok(got.includes('mo'),'leaving reduced motion re-enables motion layers');
 ok(t.errs.length===0&&t2.errs.length===0,'no page errors');
 console.log('\n'+pass+' passed, '+fail+' failed');process.exit(fail?1:0)})();
