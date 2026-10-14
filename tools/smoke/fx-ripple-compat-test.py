"""Ripple must not break long-press menus, tap-to-open, search, A-Z, sorting. python3 fx-ripple-compat-test.py [index.html] [outdir]"""
import sys,os
from playwright.sync_api import sync_playwright
HTML=os.path.abspath(sys.argv[1] if len(sys.argv)>1 else os.path.join(os.path.dirname(__file__),'../../app/src/main/assets/index.html'))
OUT=sys.argv[2] if len(sys.argv)>2 else '/tmp/fxshots';os.makedirs(OUT,exist_ok=True)
pas=fail=0
def ok(c,m):
    global pas,fail
    pas+=bool(c);fail+=(not c);print(('PASS ' if c else 'FAIL ')+m)
PT="()=>{const e=[...document.querySelectorAll('#awWave .awi')].map(e=>({r:e.querySelector('.ic').getBoundingClientRect(),o:+e.style.opacity})).filter(o=>o.o>.6).sort((a,b)=>b.r.top-a.r.top)[0];return[e.r.left+e.r.width/2,e.r.top+e.r.height/2]}"
def fresh(b,lvl):
    ctx=b.new_context(viewport={'width':390,'height':844},device_scale_factor=2,has_touch=True,is_mobile=True);pg=ctx.new_page();errs=[];pg.on('pageerror',lambda e:errs.append(str(e)))
    pg.goto('file://'+HTML);pg.wait_for_timeout(1500);pg.evaluate("()=>document.querySelectorAll('.tips').forEach(e=>e.remove())")
    pg.evaluate("(l)=>{AWCFG.set('fxLevel',l);YD.open()}",lvl);pg.wait_for_timeout(700)
    return ctx,pg,errs
with sync_playwright() as p:
    b=p.chromium.launch(args=['--no-sandbox'])
    for lvl in ['off','exp']:
        ctx,pg,errs=fresh(b,lvl);cdp=ctx.new_cdp_session(pg)
        def t(ty,x=0,y=0):cdp.send('Input.dispatchTouchEvent',{'type':ty,'touchPoints':[{'x':x,'y':y}] if ty!='touchEnd' else []})
        x,y=pg.evaluate(PT);t('touchStart',x,y);pg.wait_for_timeout(900);t('touchEnd');pg.wait_for_timeout(200)
        ok(pg.evaluate("()=>document.getElementById('amenu').classList.contains('on')||getComputedStyle(document.getElementById('amenu')).display!=='none'&&getComputedStyle(document.getElementById('amenu')).visibility!=='hidden'&&document.getElementById('amenu').offsetParent!==null"),f'{lvl}: long-press opens the app context menu')
        ok(pg.evaluate("()=>AWFX._busy()")==0,f'{lvl}: nothing left animating after the long-press')
        pg.evaluate("()=>{try{closeMenu()}catch(e){}}");pg.wait_for_timeout(200)
        # A-Z jump
        pg.evaluate("()=>YD.glide(0)");pg.wait_for_timeout(500);r=pg.evaluate("()=>[...document.querySelectorAll('#awRail div')].find(d=>d.dataset.l==='W')&&1")
        pg.evaluate("()=>{const d=[...document.querySelectorAll('#awRail div')].find(d=>d.dataset.l==='W');const r=d.getBoundingClientRect();window.__w=[r.left+r.width/2,r.top+r.height/2]}");wx,wy=pg.evaluate("()=>window.__w")
        t('touchStart',wx,wy);pg.wait_for_timeout(60);t('touchEnd');pg.wait_for_timeout(700)
        nm=pg.evaluate("()=>document.getElementById('awNm').textContent");ok(nm.strip().upper().startswith('W'),f'{lvl}: A-Z strip jumps to W ({nm.strip()})')
        # search
        pg.evaluate("()=>document.getElementById('awSr').click()");pg.wait_for_timeout(200);pg.fill('#awQ','cal');pg.wait_for_timeout(500)
        nm=pg.evaluate("()=>document.getElementById('awNm').textContent");ok('cal' in nm.lower(),f'{lvl}: search finds Calculator/Calendar ({nm.strip()})')
        # badges + labels kept
        ok(pg.evaluate("()=>[...document.querySelectorAll('#awWave .awi')].every(e=>e.querySelector('span')&&e.querySelector('span').textContent)"),f'{lvl}: icon labels intact')
        ok(not errs,f'{lvl}: no page errors {errs[:2]}');ctx.close()
    # tap an icon with tapSel to select, and tap-to-launch path
    ctx,pg,errs=fresh(b,'exp');cdp=ctx.new_cdp_session(pg)
    pg.evaluate("()=>window.__l=[]");pg.evaluate("()=>{window.Android=window.Android||{};}")
    x,y=pg.evaluate(PT);cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':x,'y':y}]});pg.wait_for_timeout(40);cdp.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]});pg.wait_for_timeout(500)
    ok(not pg.evaluate("()=>document.getElementById('drawer').classList.contains('open')"),'exp: a quick tap still opens the app (drawer closes)')
    ctx.close()
    # screenshot of the ripple
    ctx,pg,errs=fresh(b,'exp');pg.evaluate("()=>{window.YTC={lp:()=>5000}}");cdp=ctx.new_cdp_session(pg);x,y=pg.evaluate(PT)
    cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':x,'y':y}]});pg.wait_for_timeout(10);pg.screenshot(path=f'{OUT}/ripple-390.png');ctx.close()
    b.close()
print(f'\n{pas} passed, {fail} failed');sys.exit(1 if fail else 0)
