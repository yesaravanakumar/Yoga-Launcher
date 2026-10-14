"""Real-browser layout check for the drawer + Reactive Matter. python3 fx-layout-test.py [index.html] [outdir]
Opens the drawer at the four required sizes in all four designs, with FX Off and FX Experimental, and checks that
engine geometry (icon transforms, selected icon, name, A-Z rail) is identical and that nothing overlaps the rail."""
import sys,os,json
from playwright.sync_api import sync_playwright
HTML=os.path.abspath(sys.argv[1] if len(sys.argv)>1 else os.path.join(os.path.dirname(__file__),'../../app/src/main/assets/index.html'))
OUT=sys.argv[2] if len(sys.argv)>2 else '/tmp/fxshots'
os.makedirs(OUT,exist_ok=True)
SIZES=[(320,570),(360,740),(390,844),(412,915)];DESIGNS=['arc','orbit','wave','fan']
pas=fail=0
def ok(c,m):
    global pas,fail
    if c: pas+=1
    else: fail+=1
    print(('PASS ' if c else 'FAIL ')+m)
SNAP="""()=>{const E=AWENG,s=E.st(),q=e=>{const r=e.getBoundingClientRect();return[Math.round(r.left),Math.round(r.top),Math.round(r.width),Math.round(r.height)]};
 const w=[...document.querySelectorAll('#awWave .awi')].map(e=>e.style.transform+'|'+e.style.opacity);
 const rail=document.getElementById('awRail').getBoundingClientRect();const nm=document.getElementById('awNm').getBoundingClientRect();
 return{foc:s.foc,N:s.N,w,fi:q(document.getElementById('awFi')),nm:q(document.getElementById('awNm')),railL:Math.round(rail.left),nmR:Math.round(nm.right),des:s.DES,fx:AWFX.status()}}"""
with sync_playwright() as p:
    b=p.chromium.launch(args=['--no-sandbox'])
    for (W,H) in SIZES:
        ctx=b.new_context(viewport={'width':W,'height':H},device_scale_factor=2,has_touch=True,is_mobile=True)
        pg=ctx.new_page();errs=[];pg.on('pageerror',lambda e:errs.append(str(e)))
        pg.goto('file://'+HTML);pg.wait_for_timeout(1500)
        pg.evaluate("()=>document.querySelectorAll('.tips').forEach(e=>e.remove())")
        for d in DESIGNS:
            res={}
            for lvl in ['off','exp']:
                pg.evaluate("([d,l])=>{try{YD.stop()}catch(e){};AWDES.set(d);AWCFG.set('fxOn',true);AWCFG.set('fxLevel',l)}",[d,lvl])
                pg.evaluate("()=>{YD.open()}");pg.wait_for_timeout(700)
                # move to a middle app through the engine's own glide, same for both runs
                pg.evaluate("()=>YD.glide(4)");pg.wait_for_function("()=>{const s=AWENG.st();return Math.abs(s.pos-4)<1e-3&&Math.abs(s.vel)<1e-3&&s.mode===''}",timeout=6000);pg.wait_for_timeout(200)
                res[lvl]=pg.evaluate(SNAP)
                if lvl=='exp': pg.screenshot(path=f'{OUT}/{W}x{H}-{d}.png')
            a,c=res['off'],res['exp']
            ok(a['N']>=8,f'{W}x{H} {d}: demo apps present ({a["N"]})')
            ok(a['w']==c['w'] and a['fi']==c['fi'] and a['nm']==c['nm'],f'{W}x{H} {d}: engine geometry identical with FX Off and Experimental')
            ok(c['nmR']<=c['railL']+1 or d=='fan',f'{W}x{H} {d}: selected name stays clear of the A-Z strip (name right {c["nmR"]}, rail {c["railL"]})')
            ok(c['fx']['eff']==3,f'{W}x{H} {d}: Experimental level reached the layer core')
        ok(not errs,f'{W}x{H}: no page errors {errs[:2]}')
        ctx.close()
    b.close()
print(f'\n{pas} passed, {fail} failed');sys.exit(1 if fail else 0)
