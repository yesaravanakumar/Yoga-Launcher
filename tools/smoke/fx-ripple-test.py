"""Build 1: liquid touch ripple, real Chromium. python3 fx-ripple-test.py [index.html]"""
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
TL="()=>[...document.querySelectorAll('#awWave .awi')].filter(e=>e.style.translate&&e.style.translate!=='0px 0px').length"
PT="()=>{const e=[...document.querySelectorAll('#awWave .awi')].map(e=>({r:e.querySelector('.ic').getBoundingClientRect(),o:+e.style.opacity,f:e})).filter(o=>o.o>.6).sort((a,b)=>b.r.top-a.r.top)[0];return[e.r.left+e.r.width/2,e.r.top+e.r.height/2]}"
with sync_playwright() as p:
    b=p.chromium.launch(args=['--no-sandbox'])
    for W,H in [(320,570),(360,740),(390,844),(412,915)]:
        ctx=b.new_context(viewport={'width':W,'height':H},device_scale_factor=2,has_touch=True,is_mobile=True);pg=ctx.new_page();errs=[];pg.on('pageerror',lambda e:errs.append(str(e)))
        pg.goto('file://'+HTML);pg.wait_for_timeout(1500);pg.evaluate("()=>document.querySelectorAll('.tips').forEach(e=>e.remove())")
        pg.evaluate("()=>{AWCFG.set('fxOn',true);AWCFG.set('fxLevel','balanced');AWCFG.set('fx_ripple',true);AWCFG.set('fx_trace',false);AWCFG.set('fx_holo',false);AWCFG.set('fx_prism',false);YD.open()}");pg.wait_for_timeout(700)
        cdp=ctx.new_cdp_session(pg)
        def t(ty,x=0,y=0):cdp.send('Input.dispatchTouchEvent',{'type':ty,'touchPoints':[{'x':x,'y':y}] if ty!='touchEnd' else []})
        x,y=pg.evaluate(PT)
        base=pg.evaluate(TR)
        t('touchStart',x,y);pg.wait_for_timeout(110)
        ring=pg.evaluate("()=>!!document.querySelector('#awFx i')");ok(ring,f'{W}x{H}: ring appears after a short hold')
        best=0;moved_ok=False;sel_ok=True
        for i in range(8):
            pg.wait_for_timeout(30)
            n=pg.evaluate(TL);best=max(best,n)
            sel=pg.evaluate("()=>document.getElementById('awFi').style.translate||''");sel_ok=sel_ok and sel==''
        ok(best>=1,f'{W}x{H}: nearby icons are displaced during the ripple ({best} icons)')
        ok(sel_ok,f'{W}x{H}: the selected icon is never displaced')
        ok(pg.evaluate(TR)==base,f'{W}x{H}: drawer transform strings unchanged while rippling')
        pg.wait_for_timeout(700)
        ok(pg.evaluate(TL)==0 and not pg.evaluate("()=>!!document.querySelector('#awFx i')"),f'{W}x{H}: ripple settles by itself, no icon left displaced, ring removed')
        ok(pg.evaluate("()=>AWFX._busy()")==0,f'{W}x{H}: no frame loop left running')
        t('touchEnd')
        # drag cancels
        t('touchStart',x,y);pg.wait_for_timeout(110)
        for i in range(1,5): t('touchMove',x,y-i*22);pg.wait_for_timeout(16)
        ok(pg.evaluate(TL)==0 and not pg.evaluate("()=>!!document.querySelector('#awFx i')"),f'{W}x{H}: starting a drag cancels the ripple at once')
        t('touchEnd');pg.wait_for_timeout(600)
        # the app's hold-to-drag takes over: ripple lets go
        pg.evaluate("()=>{YD.open()}");pg.wait_for_timeout(300);x,y=pg.evaluate(PT);t('touchStart',x,y);pg.wait_for_timeout(700)
        ok(pg.evaluate(TL)==0 and pg.evaluate("()=>AWFX._busy()")==0,f'{W}x{H}: hold-to-drag and context menus take over cleanly (no ripple left)');t('touchEnd');pg.wait_for_timeout(300)
        pg.evaluate("()=>{try{closeMenu()}catch(e){}}");pg.evaluate("()=>{YD.open()}");pg.wait_for_timeout(500)
        # quick tap never ripples
        x,y=pg.evaluate(PT)
        t('touchStart',x,y);pg.wait_for_timeout(30);t('touchEnd');pg.wait_for_timeout(120)
        ok(not pg.evaluate("()=>!!document.querySelector('#awFx i')"),f'{W}x{H}: a quick tap never flashes a ripple')
        # off / per-effect switch
        pg.evaluate("()=>{YD.open()}");pg.wait_for_timeout(500)
        for lab,js in [('FX Off',"AWCFG.set('fxLevel','off')"),('ripple switch off',"AWCFG.set('fxLevel','balanced');AWCFG.set('fx_ripple',false)")]:
            pg.evaluate("()=>{"+js+"}");x,y=pg.evaluate(PT);t('touchStart',x,y);pg.wait_for_timeout(160)
            ok(not pg.evaluate("()=>!!document.querySelector('#awFx i')"),f'{W}x{H}: no ripple with {lab}');t('touchEnd');pg.wait_for_timeout(100)
        # close during ripple
        pg.evaluate("()=>{AWCFG.set('fx_ripple',true);AWCFG.set('fx_trace',false)}");x,y=pg.evaluate(PT);t('touchStart',x,y);pg.wait_for_timeout(150)
        pg.evaluate("()=>YD.stop()");pg.wait_for_timeout(50)
        ok(pg.evaluate("()=>AWFX._busy()")==0 and pg.evaluate("()=>document.querySelectorAll('#awFx i').length")==0,f'{W}x{H}: closing the drawer mid-ripple cleans up');t('touchEnd')
        ok(not errs,f'{W}x{H}: no page errors {errs[:2]}')
        if W==390:
            pg.evaluate("()=>{AWCFG.set('fxLevel','exp');YD.open()}");pg.wait_for_timeout(600);x,y=pg.evaluate(PT);t('touchStart',x,y);pg.wait_for_timeout(190);pg.screenshot(path=f'{OUT}/ripple-390.png');t('touchEnd')
        ctx.close()
    # reduced motion: still ring, no displacement
    ctx=b.new_context(viewport={'width':390,'height':844},has_touch=True,is_mobile=True,reduced_motion='reduce');pg=ctx.new_page();pg.goto('file://'+HTML);pg.wait_for_timeout(1500);pg.evaluate("()=>document.querySelectorAll('.tips').forEach(e=>e.remove())")
    pg.evaluate("()=>{AWCFG.set('fxLevel','exp');YD.open()}");pg.wait_for_timeout(700);cdp=ctx.new_cdp_session(pg)
    x,y=pg.evaluate(PT);cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':x,'y':y}]});pg.wait_for_timeout(130)
    ok(pg.evaluate("()=>!!document.querySelector('#awFx i')") and pg.evaluate(TL)==0,'reduced motion: still ring, no icon movement')
    ok(pg.evaluate("()=>document.querySelector('#awFx i').style.transform")=='none','reduced motion: ring does not scale')
    b.close()
print(f'\n{pas} passed, {fail} failed');sys.exit(1 if fail else 0)
