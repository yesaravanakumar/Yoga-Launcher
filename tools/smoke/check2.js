const {JSDOM,VirtualConsole}=require('jsdom');const fs=require('fs'),path=require('path');
const html=fs.readFileSync(path.join(__dirname,'../../app/src/main/assets/index.html'),'utf8');
const sleep=ms=>new Promise(r=>setTimeout(r,ms));let pass=0,fail=0;const ok=(c,m)=>{c?pass++:fail++;console.log((c?'PASS ':'FAIL ')+m)};
const calls=[];const errs=[];const vc=new VirtualConsole();vc.on('jsdomError',e=>errs.push(e.message));
const ev={t:'Standup',s:Date.now()+20*60000,d:30*60000};
const A={getBattery:()=>'{"p":57}',getBadges:()=>'{"com.gmail":3}',getMedia:()=>'null',getNextAlarm:()=>'0',getNextEvent:()=>JSON.stringify(ev),hasNotificationAccess:()=>false,getPerms:()=>'{"calendar":true}',hasCalendarPerm:()=>true,
 saveSettings:()=>{},saveTheme:()=>{},logError:()=>{},launch:p=>{calls.push(['launch',p]);return true},getShortcuts:()=>'[]',haptic:()=>false,isKeyguardLocked:()=>false,setLockOver:()=>{},openUrl:u=>{calls.push(['openUrl',u]);return true},search:()=>{},appInfo:()=>true,getContacts:()=>'[]',hasContactsPerm:()=>true,openNext:k=>calls.push(['openNext',k])};
const dom=new JSDOM(html,{runScripts:'dangerously',pretendToBeVisual:true,virtualConsole:vc,url:'https://appassets.test/index.html',
 beforeParse(w){w.matchMedia=w.matchMedia||(q=>({matches:false,addEventListener(){},removeEventListener(){},addListener(){},removeListener(){}}));w.HTMLCanvasElement.prototype.getContext=()=>null;w.scrollTo=()=>{};w.Element.prototype.scrollTo=()=>{};
 w.ResizeObserver=class{observe(){}unobserve(){}disconnect(){}};w.IntersectionObserver=class{observe(){}unobserve(){}disconnect(){}};w.Element.prototype.animate=function(){return{cancel(){},finish(){},set onfinish(f){}}};w.Android=A;w.addEventListener('error',e=>errs.push(e.message))}});
const w=dom.window,d=w.document;
(async()=>{await sleep(1500);
 w.setApps([{n:'Gmail',p:'com.gmail'},{n:'Google Calendar',p:'com.cal'},{n:'WhatsApp',p:'com.wa'},{n:'Zerodha Kite',p:'com.kite'}]);await sleep(100);
 /* ---- Business home ---- */
 const biz=d.getElementById('yogaBiz');
 w.YogaBusinessHome.sync();
 ok(!d.body.classList.contains('yb-on')&&!biz.classList.contains('on'),'Business home stays OFF after sync() (hard-coded on=false, nothing can switch it on)');
 w.YogaBusinessHome.render();
 const tiles=[...biz.querySelectorAll('.yb-app')];
 ok(tiles.length>=6,'Business panel renders when forced: '+tiles.length+' work-app tiles');
 const labels=tiles.map(t=>t.querySelector('.yb-lb').textContent);console.log('  tiles:',labels.join(', '));
 ok(labels.includes('Gmail')&&labels.includes('Zerodha Kite'),'tiles map to installed apps (Gmail, Kite)');
 ok(!!biz.querySelector('.yb-bd'),'unread badge shows on Gmail');
 ok(/Standup/.test(biz.querySelector('.yb-card').textContent),'next-meeting card shows the event');
 const web=tiles.find(t=>t.classList.contains('yb-web'));ok(!!web,'tiles with no installed app fall back to website: '+(web&&web.textContent));
 if(web){web.click();ok(calls.some(c=>c[0]==='openUrl'),'website tile opens URL')}
 tiles.find(t=>/Gmail/.test(t.textContent)).click();ok(calls.some(c=>c[0]==='launch'&&c[1]==='com.gmail'),'Gmail tile launches the app');
 /* ---- Drawer search ---- */
 try{w.eval('openDrawer()')}catch(e){console.log('openDrawer err',e.message)}await sleep(400);console.log('  drawer class:',d.getElementById('drawer').className,'| awi:',d.querySelectorAll('#awWave .awi').length);
 const qi=d.getElementById('awQ'),aw=d.getElementById('awSearch');
 ok(!!qi,'drawer search input exists');
 if(w.AW&&w.AW.search)w.AW.search();
 const apps=()=>[...d.querySelectorAll('#awWave .awi')].map(e=>e.textContent.trim());
 qi.value='whats';qi.dispatchEvent(new w.Event('input'));await sleep(150);
 console.log('  drawer results for "whats":',apps());
 ok(apps().length===1&&/WhatsApp/.test(apps()[0]),'drawer search "whats" filters to WhatsApp');
 qi.value='gm';qi.dispatchEvent(new w.Event('input'));await sleep(150);console.log('  drawer "gm":',apps());
 ok(apps().some(x=>/Gmail/.test(x)),'drawer search "gm" finds Gmail');
 qi.value='qqq';qi.dispatchEvent(new w.Event('input'));await sleep(150);
 ok(d.getElementById('aw').classList.contains('empty'),'no match -> "No apps found" state');
 qi.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Enter',bubbles:true}));await sleep(80);
 ok(calls.some(c=>c[0]==='openUrl'&&/qqq/.test(c[1])),'Enter with no match runs a web search: '+JSON.stringify(calls.slice(-1)));
 w.eval('openDrawer()');await sleep(300);qi.value='whats';qi.dispatchEvent(new w.Event('input'));await sleep(150);
 qi.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Enter',bubbles:true}));await sleep(80);
 console.log('  calls after Enter:',JSON.stringify(calls.slice(-3)),'drawer:',d.getElementById('drawer').className);ok(calls.some(c=>c[0]==='launch'&&c[1]==='com.wa'),'Enter on "whats" launches WhatsApp');
 console.log('errors:',[...new Set(errs)].slice(0,5));console.log(pass+' passed, '+fail+' failed');process.exit(0)})();
