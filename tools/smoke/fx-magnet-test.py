"""Build 2: magnetic selection, real Chromium. python3 fx-magnet-test.py [index.html] [shots dir]"""
import sys,os
from playwright.sync_api import sync_playwright
HTML=os.path.abspath(sys.argv[1] if len(sys.argv)>1 else os.path.join(os.path.dirname(__file__),'../../app/src/main/assets/index.html'))
OUT=sys.argv[2] if len(sys.argv)>2 else '/tmp/fxshots'
os.makedirs(OUT,exist_ok=True)
pas=fail=0
def ok(c,m):
    global pas,fail
    pas+=bool(c);fail+=(not c);print(('PASS ' if c else 'FAIL ')+m)
TR="()=>[...document.querySelectorAll('#awWave .awi')].map(e=>e.style.transform+'|'+e.style.opacity)"
# translated icons with the dot product between their push and the direction to the selected icon
PUSH="""()=>{const f=document.querySelector('#awFi .ic').getBoundingClientRect(),fx=f.left+f.width/2,fy=f.top+f.height/2;
 return [...document.querySelectorAll('#awWave .awi')].filter(e=>e.style.translate&&e.style.translate!=='0px 0px').map(e=>{
  const [tx,ty]=e.style.translate.split(' ').map(parseFloat),r=e.querySelector('.ic').getBoundingClientRect(),dx=fx-(r.left+r.width/2-tx),dy=fy-(r.top+r.height/2-ty);
  return {m:Math.hypot(tx,ty),dot:(tx*dx+ty*dy)/(Math.hypot(dx,dy)||1)}})}"""
# samples with a near-zero total push are skipped: 0/0 gave NaN and poisoned min()
SEQJS="""()=>new Promise(res=>{const out=[],P=PUSHFN;YD.glide(AWENG.st().foc+1);const t0=performance.now();
 const step=()=>{const r=P();if(r.length){const tm=r.reduce((s,a)=>s+a.m,0);if(tm>0.02)out.push(r.reduce((s,a)=>s+a.m*a.dot,0)/tm)}
  if(performance.now()-t0<900)requestAnimationFrame(step);else res(out)};requestAnimationFrame(step)})""".replace('PUSHFN',PUSH)
TL="()=>[...document.querySelectorAll('#awWave .awi')].filter(e=>e.style.translate&&e.style.translate!=='0px 0px').length"
def state(pg):return pg.evaluate("()=>JSON.stringify({t:[...document.querySelectorAll('#awWave .awi')].map(e=>e.style.transform+'|'+e.style.opacity).sort(),f:document.getElementById('awFi').style.transform})")
with sync_playwright() as p:
    b=p.chromium.launch(args=['--no-sandbox'])
    for W,H in [(320,570),(360,740),(390,844),(412,915)]:
        ctx=b.new_context(viewport={'width':W,'height':H},device_scale_factor=2,has_touch=True,is_mobile=True);pg=ctx.new_page();errs=[];pg.on('pageerror',lambda e:errs.append(str(e)))
        pg.goto('file://'+HTML);pg.wait_for_timeout(1500);pg.evaluate("()=>document.querySelectorAll('.tips').forEach(e=>e.remove())")
        pg.evaluate("()=>{AWCFG.set('fxOn',true);AWCFG.set('fxLevel','balanced');AWCFG.set('fx_magnet',true);AWCFG.set('fx_ripple',false);AWCFG.set('fxAuto',false);YD.open()}");pg.wait_for_timeout(1000)
        tag=f'{W}x{H}'
        # selection change by programmatic glide (the same path A-Z jump and taps use)
        pg.evaluate("()=>YD.glide(4)")
        best=0;allpos=[];selfree=True
        for i in range(14):
            pg.wait_for_timeout(25)
            n=pg.evaluate(TL);best=max(best,n)
            allpos+=pg.evaluate(PUSH)
            selfree=selfree and pg.evaluate("()=>document.getElementById('awFi').style.translate||''")==''
        ok(best>=2,f'{tag}: neighbours lean when the selection changes ({best} icons)')
        ok(allpos and max(a['m'] for a in allpos)<=6.5,f'{tag}: displacement stays subtle (max {max([a["m"] for a in allpos] or [0]):.1f}px at Balanced)')
        ok(selfree,f'{tag}: the selected icon is never displaced')
        pg.wait_for_timeout(700)
        seq=[v for v in pg.evaluate(SEQJS) if v is not None and v==v]   # drop 0/0 samples (NaN), they poison min()
        ok(seq and sum(seq[:3])>0,f'{tag}: a one-step selection first pulls neighbours toward the selected icon')
        ok(seq and min(seq)<-0.05,f'{tag}: then a small rebound away from it before settling')
        pg.wait_for_timeout(900)
        ok(pg.evaluate(TL)==0 and pg.evaluate("()=>AWFX._busy()")==0,f'{tag}: settles by itself, nothing left displaced, no frame loop running')
        # settled geometry equals the FX Off geometry (engine untouched)
        pg.evaluate("()=>YD.glide(9)");pg.wait_for_timeout(2200);s_on=state(pg)
        pg.evaluate("()=>{AWCFG.set('fxLevel','off');YD.glide(0)}");pg.wait_for_timeout(2200);pg.evaluate("()=>YD.glide(9)");pg.wait_for_timeout(2200);s_off=state(pg)
        import json
        if s_on!=s_off:
            a1,a2=json.loads(s_on),json.loads(s_off);print('  diff:',[(i,u,v) for i,(u,v) in enumerate(zip(a1['t'],a2['t'])) if u!=v][:3],a1['f']==a2['f'])
        ok(s_on==s_off,f'{tag}: settled drawer transforms identical with the effect on and FX Off')
        ok(pg.evaluate(TL)==0,f'{tag}: FX Off: no movement')
        pg.evaluate("()=>{AWCFG.set('fxLevel','balanced');AWCFG.set('fx_magnet',false);YD.glide(2)}");pg.wait_for_timeout(120)
        ok(pg.evaluate(TL)==0,f'{tag}: per-effect switch off: no movement')
        # touch cancels
        pg.evaluate("()=>{AWCFG.set('fx_magnet',true)}");pg.wait_for_timeout(900)
        pg.evaluate("()=>YD.glide(6)");pg.wait_for_timeout(120)
        cdp=ctx.new_cdp_session(pg)
        cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':W*.5,'y':H*.5}]});pg.wait_for_timeout(90)
        ok(pg.evaluate(TL)==0 and pg.evaluate("()=>AWFX._busy()")==0,f'{tag}: a new touch cancels the effect at once')
        cdp.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]});pg.wait_for_timeout(900)
        # close mid-effect
        pg.evaluate("()=>YD.glide(1)");pg.wait_for_timeout(100)
        pg.evaluate("()=>YD.stop()");pg.wait_for_timeout(50)
        ok(pg.evaluate(TL)==0 and pg.evaluate("()=>AWFX._busy()")==0,f'{tag}: closing the drawer mid-effect cleans up')
        # opening cascade does not trigger it
        pg.evaluate("()=>YD.open()");pg.wait_for_timeout(250)
        ok(pg.evaluate(TL)==0,f'{tag}: opening the drawer does not trigger it')
        ok(not errs,f'{tag}: no page errors {errs[:2]}')
        if W==390:
            pg.wait_for_timeout(900);pg.evaluate("()=>{AWCFG.set('fxLevel','exp')}");pg.evaluate("()=>YD.glide(5)");pg.wait_for_timeout(70);pg.screenshot(path=f'{OUT}/magnet-390.png')
        ctx.close()
    ctx=b.new_context(viewport={'width':390,'height':844},has_touch=True,is_mobile=True,reduced_motion='reduce');pg=ctx.new_page();pg.goto('file://'+HTML);pg.wait_for_timeout(1500);pg.evaluate("()=>document.querySelectorAll('.tips').forEach(e=>e.remove())")
    pg.evaluate("()=>{AWCFG.set('fxLevel','exp');YD.open()}");pg.wait_for_timeout(1000);pg.evaluate("()=>YD.glide(5)");pg.wait_for_timeout(80)
    ok(pg.evaluate(TL)==0,'reduced motion: no icon movement')
    b.close()
print(f'\n{pas} passed, {fail} failed');sys.exit(1 if fail else 0)
