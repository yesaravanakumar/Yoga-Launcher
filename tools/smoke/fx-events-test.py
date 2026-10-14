"""Engine -> FX event check in real Chromium. python3 fx-events-test.py [index.html]"""
import sys,os
from playwright.sync_api import sync_playwright
HTML=os.path.abspath(sys.argv[1] if len(sys.argv)>1 else os.path.join(os.path.dirname(__file__),'../../app/src/main/assets/index.html'))
pas=fail=0
def ok(c,m):
    global pas,fail
    pas+=c;fail+=(not c);print(('PASS ' if c else 'FAIL ')+m)
with sync_playwright() as p:
    b=p.chromium.launch(args=['--no-sandbox']);ctx=b.new_context(viewport={'width':390,'height':844},device_scale_factor=2,has_touch=True,is_mobile=True)
    pg=ctx.new_page();errs=[];pg.on('pageerror',lambda e:errs.append(str(e)));pg.goto('file://'+HTML);pg.wait_for_timeout(1500)
    pg.evaluate("()=>document.querySelectorAll('.tips').forEach(e=>e.remove())")
    pg.evaluate("""()=>{window.__ev=[];const L=n=>(a)=>__ev.push(n);AWFX.layer('probe',{min:1,rm:true,keep:true,on:{open:L('open'),select:L('select'),down:L('down'),drag:L('drag'),release:L('release'),snap:L('snap'),tap:L('tap')},stop:()=>__ev.push('stop')});AWCFG.set('fxLevel','subtle')}""")
    pg.evaluate("()=>{YD.open()}");pg.wait_for_timeout(600)
    ev=pg.evaluate("()=>__ev.slice()");ok('open' in ev and 'select' in ev,f'open and select fire when the drawer opens {sorted(set(ev))}')
    pg.evaluate("()=>{__ev.length=0}")
    # a real touch drag with fling on the wave
    # press on a real app icon below the selected one (the engine starts a drag from there)
    pt=pg.evaluate("()=>{const f=AWENG.st().foc;const e=[...document.querySelectorAll('#awWave .awi')].map(e=>({e,r:e.getBoundingClientRect(),o:+e.style.opacity})).filter(o=>o.o>.6).sort((a,b)=>b.r.top-a.r.top)[0];const ic=e.e.querySelector('.ic').getBoundingClientRect();return[ic.left+ic.width/2,ic.top+ic.height/2]}")
    x,y0=pt
    cdp=ctx.new_cdp_session(pg)
    def t(type,yy):cdp.send('Input.dispatchTouchEvent',{'type':type,'touchPoints':[{'x':x,'y':yy}] if type!='touchEnd' else []})
    t('touchStart',y0)
    for i in range(1,8):
        t('touchMove',y0-i*28);pg.wait_for_timeout(16)
    t('touchEnd',0);pg.wait_for_timeout(900)
    ev=pg.evaluate("()=>__ev.slice()")
    ok('down' in ev and 'drag' in ev and 'release' in ev,f'touch drag emits down, drag, release {sorted(set(ev))}')
    ok('select' in ev,'selection change emits select during the swipe')
    pg.evaluate("()=>{__ev.length=0}");pg.evaluate("()=>YD.glide(2)");pg.wait_for_timeout(500)
    ok('snap' in pg.evaluate("()=>__ev.slice()"),'glide (A-Z jump, keys) emits snap')
    pg.evaluate("()=>{__ev.length=0}");pg.evaluate("()=>YD.stop()")
    ev=pg.evaluate("()=>__ev.slice()");ok('stop' in ev,'closing the drawer stops the layer')
    # tap
    pg.evaluate("()=>{YD.open()}");pg.wait_for_timeout(600);pg.evaluate("()=>{__ev.length=0}")
    pt=pg.evaluate("()=>{const e=[...document.querySelectorAll('#awWave .awi')].map(e=>({r:e.querySelector('.ic').getBoundingClientRect(),o:+e.style.opacity})).filter(o=>o.o>.6).sort((a,b)=>b.r.top-a.r.top)[0];return[e.r.left+e.r.width/2,e.r.top+e.r.height/2]}")
    x,y0=pt
    t('touchStart',y0);pg.wait_for_timeout(30);t('touchEnd',0);pg.wait_for_timeout(200)
    ev=pg.evaluate("()=>__ev.slice()");ok('down' in ev,'a touch on the wave emits down')
    ok(not errs,f'no page errors {errs[:2]}')
    b.close()
print(f'\n{pas} passed, {fail} failed');sys.exit(1 if fail else 0)
