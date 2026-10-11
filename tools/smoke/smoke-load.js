const {JSDOM,VirtualConsole}=require('jsdom');const fs=require('fs');
const html=fs.readFileSync(process.argv[2]||require('path').join(__dirname,'../../app/src/main/assets/index.html'),'utf8');
const errs=[];const vc=new VirtualConsole();
vc.on('jsdomError',e=>errs.push('JSDOM: '+String(e.detail&&e.detail.message||e.message).slice(0,200)));
vc.on('error',(...a)=>errs.push('console.error: '+a.map(String).join(' ').slice(0,200)));
const dom=new JSDOM(html,{runScripts:'dangerously',pretendToBeVisual:true,virtualConsole:vc,url:'https://appassets.test/index.html',
 beforeParse(w){w.matchMedia=w.matchMedia||(q=>({matches:false,addEventListener(){},removeEventListener(){},addListener(){},removeListener(){}}));
  w.HTMLCanvasElement.prototype.getContext=()=>null;w.scrollTo=()=>{};w.ResizeObserver=class{observe(){}unobserve(){}disconnect(){}};w.IntersectionObserver=class{observe(){}unobserve(){}disconnect(){}};w.requestIdleCallback=f=>setTimeout(f,1);
  w.Element.prototype.animate=function(){return{cancel(){},finish(){},set onfinish(f){}}};
  w.addEventListener('error',e=>errs.push('window.onerror: '+e.message+' @'+(e.lineno||'')));
  w.addEventListener('unhandledrejection',e=>errs.push('rejection: '+String(e.reason).slice(0,160)));}});
setTimeout(()=>{const w=dom.window;console.log('errors:',errs.length);[...new Set(errs)].slice(0,25).forEach(e=>console.log(' -',e));
 console.log('Business panel present:',!!w.document.getElementById('yogaBiz'),'| YogaBusinessHome:',typeof w.YogaBusinessHome,'| apps type',typeof w.apps);process.exit(0)},3500);
