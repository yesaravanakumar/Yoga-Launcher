# v1.83: icons must stay on the flow line while the arc is dragged and flung (all four designs, FX on and off).
import sys, os
from playwright.sync_api import sync_playwright
SRC = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else 'app/src/main/assets/index.html')
JS = """()=>{const E=AWENG,s=E.st(),rr=E.root(),root=rr.getBoundingClientRect(),sc=root.width/(rr.clientWidth||root.width);let w=0;
 E.els.forEach((e,i)=>{if(!(+e.style.opacity>.3))return;const ic=e.querySelector('.ic').getBoundingClientRect();
  const cx=(ic.left+ic.width/2-root.left)/sc,cy=(ic.top+ic.height/2-root.top)/sc,p=E.slot(i-s.pos,i);w=Math.max(w,Math.hypot(cx-p[0],cy-p[1]))});return w}"""
bad = 0
with sync_playwright() as p:
    b = p.chromium.launch()
    pg = b.new_page(viewport={'width': 390, 'height': 844}, has_touch=True, is_mobile=True)
    pg.goto('file://' + SRC); pg.wait_for_timeout(2500)
    pg.evaluate("()=>{[...document.querySelectorAll('button')].filter(b=>/got it/i.test(b.textContent)).forEach(b=>b.click())}")
    cdp = pg.context.new_cdp_session(pg)
    t = lambda ty, x, y: cdp.send('Input.dispatchTouchEvent', {'type': ty, 'touchPoints': [{'x': x, 'y': y}] if ty != 'touchEnd' else []})
    for fx in (True, False):
        pg.evaluate("f=>AWCFG.set('fxOn',f)", fx)
        for des in ('arc', 'wave', 'orbit', 'fan'):
            pg.evaluate("d=>{AWDES.set(d);YD.open()}", des); pg.wait_for_timeout(1400)
            t('touchStart', 300, 700); y = 700; mx = 0
            for _ in range(8): y -= 45; t('touchMove', 300, y); pg.wait_for_timeout(10); mx = max(mx, pg.evaluate(JS))
            t('touchEnd', 300, y)
            for _ in range(40): pg.wait_for_timeout(25); mx = max(mx, pg.evaluate(JS))
            ok = mx < 1.5; bad += not ok
            print(('PASS' if ok else 'FAIL'), des, 'fx' if fx else 'nofx', round(mx, 2), 'px')
    b.close()
sys.exit(1 if bad else 0)
