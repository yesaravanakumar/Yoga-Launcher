"""Smoke-load in real Chromium (stand-in where jsdom is unavailable): page loads, no errors, FX core and layers registered."""
import sys,os
from playwright.sync_api import sync_playwright
HTML=os.path.abspath(sys.argv[1] if len(sys.argv)>1 else os.path.join(os.path.dirname(__file__),'../../app/src/main/assets/index.html'))
pas=fail=0
def ok(c,m):
    global pas,fail
    pas+=bool(c);fail+=(not c);print(('PASS ' if c else 'FAIL ')+m)
with sync_playwright() as p:
    b=p.chromium.launch(args=['--no-sandbox']);pg=b.new_page(viewport={'width':390,'height':844});errs=[];pg.on('pageerror',lambda e:errs.append(str(e)))
    pg.goto('file://'+HTML);pg.wait_for_timeout(2000)
    ok(not errs,f'loads with no page errors {errs[:2]}')
    ok(pg.evaluate("()=>!!window.AWFX&&!!window.AWENG"),'AWFX core and AWENG present')
    ok(pg.evaluate("()=>[...AWFX._layers.keys()].join()")=='ripple,magnet,holo,trace,prism','layers registered: ripple, magnet, holo, trace, prism')
    ok(pg.evaluate("()=>AWCFG.get().fx_magnet!==false"),'fx_magnet defaults on')
    ok(pg.evaluate("()=>AWCFG.get().fx_holo!==false"),'fx_holo defaults on')
    ok(pg.evaluate("()=>AWCFG.get().fx_prism!==false"),'fx_prism defaults on')
    pg.evaluate("()=>YD.open()");pg.wait_for_timeout(800)
    ok(pg.evaluate("()=>document.querySelectorAll('#awWave .awi').length")>5,'drawer opens with apps')
    pg.evaluate("()=>{AWCFG.set('fxLevel','balanced');for(let i=0;i<20;i++)YD.glide(i%9)}");pg.wait_for_timeout(1500)
    ok(pg.evaluate("()=>AWFX._busy()")==0 and not errs,f'rapid selection changes settle, no errors {errs[:2]}')
    b.close()
print(f'\n{pas} passed, {fail} failed');sys.exit(1 if fail else 0)
