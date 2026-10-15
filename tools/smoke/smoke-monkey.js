const {JSDOM,VirtualConsole}=require('jsdom');const fs=require('fs');
const file=process.argv[2]||require('path').join(__dirname,'../../app/src/main/assets/index.html');
const html=fs.readFileSync(file,'utf8');const errs=[];const vc=new VirtualConsole();
vc.on('jsdomError',e=>errs.push('JSDOM: '+String(e.detail&&e.detail.message||e.message).slice(0,160)));
const dom=new JSDOM(html,{runScripts:'dangerously',pretendToBeVisual:true,virtualConsole:vc,url:'https://appassets.test/index.html',
 beforeParse(w){w.matchMedia=q=>({matches:false,addEventListener(){},removeEventListener(){},addListener(){},removeListener(){}});
  w.HTMLCanvasElement.prototype.getContext=()=>null;w.Element.prototype.scrollTo=function(){};w.confirm=()=>true;w.scrollTo=()=>{};w.ResizeObserver=class{observe(){}unobserve(){}disconnect(){}};w.IntersectionObserver=class{observe(){}unobserve(){}disconnect(){}};
  w.Element.prototype.animate=function(){return{cancel(){},finish(){},set onfinish(f){}}};w.Element.prototype.scrollIntoView=()=>{};
  w.open=(u)=>{w.__opened=(w.__opened||[]);w.__opened.push(u);return null};
  w.addEventListener('error',e=>errs.push('onerror: '+e.message));
  const calls=[];w.__calls=calls;
  w.Android=new Proxy({},{get:(t,k)=>{if(k==='then')return undefined;return(...a)=>{calls.push(k+'('+a.map(x=>String(x).slice(0,30)).join(',')+')');
    const m={getContacts:'[{"n":"Anita Rao","p":"+91 98765 43210"}]',hasContactsPerm:true,getBadges:'{"com.google.android.gm":4}',getNextEvent:JSON.stringify({t:'Review',s:Date.now()+600000,d:1800000}),getBattery:'{"level":80,"charging":false}',getPerms:'{}',getNextAlarm:'0',getMedia:'null',getShortcuts:'[]',getNotifications:'[]',isKeyguardLocked:false,getCommute:'null',suggest:'[]'};
    return k in m?m[k]:undefined}}});}});
const w=dom.window,D=w.document;const sleep=ms=>new Promise(r=>setTimeout(r,ms));
(async()=>{await sleep(800);
 const apps=['Gmail','Kite','Google Pay','Calendar','Chrome','Phone','WhatsApp','Maps','Camera','YouTube','Settings','Clock'].map((n,i)=>({n,p:'com.x.'+n.replace(/ /g,'').toLowerCase(),k:i%3?'':'Media'}));
 apps[0].p='com.google.android.gm';apps.push({...apps[1]});   // duplicate package on purpose
 w.setApps(apps);await sleep(400);
 // monkey: click every visible-ish button/anchor once, then a few key presses
 let n=0;const lim=+(process.env.CLICKS||0);const bs=[...D.querySelectorAll('button,[role=button],.app,.gapp')].filter(b=>!b.classList.contains('v49c')&&!b.closest('#sw,#packs')).slice(0,lim);
 for(const b of bs){if(process.env.TRACE)console.log('click',n,(b.id||b.className||b.textContent).toString().slice(0,40));try{b.click();n++}catch(e){errs.push('click threw '+e.message)}await sleep(2)}
 for(const k of ['Escape','/','ArrowDown','Enter']){D.dispatchEvent(new w.KeyboardEvent('keydown',{key:k,bubbles:true}))}
 await sleep(500);
 const log=w.eval('ERRLOG.map(x=>"["+x.w+"] "+x.m+(x.n>1?" x"+x.n:""))');
 console.log('clicked',n,'| jsdom/onerror:',[...new Set(errs)].length,'| page ERRLOG:',log.length);
 [...new Set(errs)].slice(0,10).forEach(e=>console.log(' js:',e));log.slice(0,25).forEach(e=>console.log(' log:',e));
 console.log('bridge calls (sample):',[...new Set(w.__calls.map(c=>c.split('(')[0]))].slice(0,30).join(' '));
 console.log('window.open urls:',w.__opened);
 process.exit(0)})();
