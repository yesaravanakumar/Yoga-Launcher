"""Build 3: holographic snap, real Chromium. python3 fx-holo-test.py [index.html] [shots dir]"""
import sys,os,json
from playwright.sync_api import sync_playwright
HTML=os.path.abspath(sys.argv[1] if len(sys.argv)>1 else os.path.join(os.path.dirname(__file__),'../../app/src/main/assets/index.html'))
OUT=sys.argv[2] if len(sys.argv)>2 else '/tmp/fxshots'
os.makedirs(OUT,exist_ok=True)
pas=fail=0
def ok(c,m):
    global pas,fail
    pas+=bool(c);fail+=(not c);print(('PASS ' if c else 'FAIL ')+m)
BOX="()=>document.querySelector('#awFx>i')"
HAS="()=>!!document.querySelector('#awFx>i')"
INFO="""()=>{const b=document.querySelector('#awFx>i');if(!b)return null;const r=b.getBoundingClientRect(),ic=document.querySelector('#awFi .ic').getBoundingClientRect(),
 rail=document.getElementById('awRail').getBoundingClientRect(),nm=document.getElementById('awNm').getBoundingClientRect(),root=document.getElementById('aw').getBoundingClientRect();
 const kids=[...b.children];const cor=kids.filter(k=>k.classList.contains('hc'));
 const tr=cor.map(k=>{const m=/translate\\(([-\\d.]+)px,\\s*([-\\d.]+)px\\)/.exec(k.style.transform);return m?Math.hypot(+m[1],+m[2]):0});
 return {n:kids.length,cor:cor.length,hair:kids.some(k=>k.classList.contains('hhair')),tr,
  contains:r.left<=ic.left+1&&r.right>=ic.right-1&&r.top<=ic.top+1&&r.bottom>=ic.bottom-1,
  inRoot:r.left>=root.left&&r.right<=root.right&&r.top>=root.top&&r.bottom<=root.bottom,
  railR:[rail.left,rail.top,rail.right,rail.bottom].map(Math.round),frR:[r.left,r.top,r.right,r.bottom].map(Math.round),clearRail:!(r.left<rail.right&&r.right>rail.left&&r.top<rail.bottom&&r.bottom>rail.top),nmOverlap:(()=>{const q=document.getElementById('awNm').getBoundingClientRect();return Math.max(0,Math.min(r.right,q.right-3)-Math.max(r.left,q.left+3))*Math.max(0,Math.min(r.bottom,q.bottom-3)-Math.max(r.top,q.top+3))>0?1:0})(),
  css:[...b.children].map(k=>getComputedStyle(k).borderTopColor+'|'+k.className).join(';')+';'+document.getElementById('awFx').parentNode.id,sheet:[...document.styleSheets].map(s=>{try{return [...s.cssRules].map(r=>r.cssText).filter(t=>/#awFx \\.h/.test(t)).join('')}catch(e){return ''}}).join(''),pad:[ic.left-r.left,r.right-ic.right],op:cor.map(k=>+k.style.opacity)}}"""
STATE="()=>JSON.stringify({t:[...document.querySelectorAll('#awWave .awi')].map(e=>e.style.transform+'|'+e.style.opacity).sort(),f:document.getElementById('awFi').style.transform})"
def maxprobe(pg,ms,js=INFO):
    best=None;seen=[];
    for i in range(ms//20):
        pg.wait_for_timeout(20);v=pg.evaluate(js)
        if v:seen.append(v)
    return seen
def setup(pg,lv='balanced'):
    pg.evaluate("()=>{AWCFG.set('fxOn',true);AWCFG.set('fxLevel','%s');AWCFG.set('fx_holo',true);AWCFG.set('fx_ripple',false);AWCFG.set('fx_magnet',false);YD.open()}"%lv);pg.wait_for_timeout(1000)
with sync_playwright() as p:
    b=p.chromium.launch(args=['--no-sandbox'])
    for DES in ['arc','orbit','wave','fan']:
      for W,H in [(320,570),(360,740),(390,844),(412,915)]:
        ctx=b.new_context(viewport={'width':W,'height':H},device_scale_factor=2,has_touch=True,is_mobile=True);pg=ctx.new_page();errs=[];pg.on('pageerror',lambda e:errs.append(str(e)))
        pg.goto('file://'+HTML);pg.wait_for_timeout(1500);pg.evaluate("()=>document.querySelectorAll('.tips').forEach(e=>e.remove())")
        pg.evaluate("(d)=>AWDES.set(d)",DES);setup(pg)
        tag=f'{DES} {W}x{H}'
        full=DES=='arc' and W in(320,390)   # full battery on two sizes of the default layout; geometry on all
        ok(not pg.evaluate(HAS),f'{tag}: nothing on screen before any snap')
        pg.evaluate("()=>YD.glide(AWENG.st().foc+1)")
        seen=maxprobe(pg,700)
        ok(len(seen)>0,f'{tag}: frame assembles after a snap')
        if seen:
            f=seen[0];m=seen[len(seen)//2]
            ok(f['cor']==4 and f['hair'],f'{tag}: four corners + hairline at Balanced')
            ok(m['contains'] and m['inRoot'],f'{tag}: frame surrounds the selected icon and stays inside the drawer')
            ok(m['clearRail'],f'{tag}: frame clear of the A-Z strip {m["railR"] if not m["clearRail"] else ""} {m["frR"] if not m["clearRail"] else ""}')
            ok(m['nmOverlap']==0,f'{tag}: frame stays out of the selected name text area')
            ok(max(m['pad'])<=8,f'{tag}: frame hugs the icon (pad {m["pad"][0]:.1f}/{m["pad"][1]:.1f}px)')
            ok('var(--c1)' in m['sheet'] and 'var(--c1rgb)' in m['sheet'],f'{tag}: accent comes from --c1/--c1rgb')
            ok(not __import__('re').search(r'#[0-9a-fA-F]{3,6}|rgba?\(\s*\d',m['sheet']),f'{tag}: no hardcoded palette')
            ok(f['tr'] and max(f['tr'])>2 and min(seen[-1]['tr'] or [0])<=max(f['tr']),f'{tag}: corners start offset ({max(f["tr"]):.1f}px) and converge')
        pg.wait_for_timeout(1200)
        ok(not pg.evaluate(HAS) and pg.evaluate("()=>AWFX._busy()")==0,f'{tag}: removes itself, no frame loop left')
        pg.evaluate("()=>YD.glide(9)");pg.wait_for_timeout(2400);s_on=pg.evaluate(STATE)
        pg.evaluate("()=>{AWCFG.set('fxLevel','off');YD.glide(0)}");pg.wait_for_timeout(2200);pg.evaluate("()=>YD.glide(9)");pg.wait_for_timeout(2200)
        ok(s_on==pg.evaluate(STATE),f'{tag}: settled drawer transforms identical with the effect on and FX Off')
        ok(not pg.evaluate(HAS),f'{tag}: FX Off: no frame')
        if full:
            pg.evaluate("()=>{AWCFG.set('fxLevel','balanced');AWCFG.set('fx_holo',false);YD.glide(2)}");pg.wait_for_timeout(450)
            ok(not pg.evaluate(HAS),f'{tag}: per-effect switch off: no frame')
            pg.evaluate("()=>{AWCFG.set('fx_holo',true)}");pg.wait_for_timeout(900)
            # subtle: no hairline
            pg.evaluate("()=>{AWCFG.set('fxLevel','subtle');YD.glide(4)}");sv=maxprobe(pg,600)
            ok(sv and not sv[0]['hair'] and sv[0]['cor']==4,f'{tag}: Subtle: corners only')
            pg.wait_for_timeout(1000)
            pg.evaluate("()=>{AWCFG.set('fxLevel','exp');YD.glide(6)}");ev=maxprobe(pg,600)
            ok(ev and ev[0]['hair'] and 'hg' in ev[0]['css'],f'{tag}: Experimental: adds a soft glow on the corners')
            ok(sv and ev and max(max(v['tr'] or [0]) for v in sv)<max(max(v['tr'] or [0]) for v in ev),f'{tag}: intensity scales the assembly distance')
            pg.wait_for_timeout(1000);pg.evaluate("()=>AWCFG.set('fxLevel','balanced')")
            cdp=ctx.new_cdp_session(pg)
            # new touch cancels
            pg.evaluate("()=>YD.glide(AWENG.st().foc+1)");pg.wait_for_timeout(350)
            ok(pg.evaluate(HAS),f'{tag}: (frame is up before the touch)')
            cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':W*.2,'y':H*.45}]});pg.wait_for_timeout(60)
            ok(not pg.evaluate(HAS) and pg.evaluate("()=>AWFX._busy()")==0,f'{tag}: a new touch cancels it at once')
            cdp.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]});pg.wait_for_timeout(900)
            # real fast swipe: frame appears only after the wave settles
            xs=W*.3
            cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':xs,'y':H*.7}]})
            for i in range(1,9):
                cdp.send('Input.dispatchTouchEvent',{'type':'touchMove','touchPoints':[{'x':xs,'y':H*.7-i*H*.05}]});pg.wait_for_timeout(12)
            during=pg.evaluate(HAS)
            cdp.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]})
            ok(not during,f'{tag}: no frame while the finger is still dragging')
            sw=maxprobe(pg,2600)
            ok(len(sw)>0,f'{tag}: frame assembles after a fast swipe settles')
            pg.wait_for_timeout(1400)
            # selecting another app while it shows removes it
            pg.evaluate("()=>YD.glide(AWENG.st().foc+1)");pg.wait_for_timeout(420)
            pg.evaluate("()=>YD.glide(AWENG.st().foc+3)");pg.wait_for_timeout(30)
            ok(pg.evaluate("()=>document.querySelectorAll('#awFx>i').length")<=1,f'{tag}: re-selecting never stacks frames')
            pg.wait_for_timeout(1600)
            ok(not pg.evaluate(HAS),f'{tag}: ...and it still ends by itself')
            # close mid-effect
            pg.evaluate("()=>YD.glide(1)");pg.wait_for_timeout(300);pg.evaluate("()=>YD.stop()");pg.wait_for_timeout(50)
            ok(not pg.evaluate(HAS) and pg.evaluate("()=>AWFX._busy()")==0,f'{tag}: closing the drawer mid-effect cleans up')
            pg.evaluate("()=>YD.open()");pg.wait_for_timeout(300)
            ok(not pg.evaluate(HAS),f'{tag}: opening the drawer does not trigger it')
            # A-Z jump and tap-to-select still work (engine untouched)
            r=pg.evaluate("()=>{const d=[...document.querySelectorAll('#awRail div')].find(x=>x.dataset.l==='S');if(!d)return null;const b=d.getBoundingClientRect(),o={x:b.left+b.width/2,y:b.top+b.height/2};return o}")
            if r:
                cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':r['x'],'y':r['y']}]});pg.wait_for_timeout(40)
                cdp.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]});pg.wait_for_timeout(900)
                ok(pg.evaluate("()=>document.getElementById('awNm').textContent")[:1].upper()=='S',f'{tag}: A-Z jump still lands on S')
            pg.wait_for_timeout(1200)
        ok(not errs,f'{tag}: no page errors {errs[:2]}')
        if DES=='arc' and W==390:
            pg.evaluate("()=>{AWCFG.set('fxLevel','exp');YD.glide(AWENG.st().foc+1)}");pg.wait_for_timeout(430);pg.screenshot(path=f'{OUT}/holo-390.png')
        ctx.close()
    for lab,kw in [('reduced motion',dict(reduced_motion='reduce'))]:
        ctx=b.new_context(viewport={'width':390,'height':844},has_touch=True,is_mobile=True,**kw);pg=ctx.new_page();pg.goto('file://'+HTML);pg.wait_for_timeout(1500);pg.evaluate("()=>document.querySelectorAll('.tips').forEach(e=>e.remove())")
        setup(pg,'exp');pg.evaluate("()=>YD.glide(AWENG.st().foc+1)");sv=maxprobe(pg,500)
        ok(sv,f'{lab}: static frame still appears')
        ok(sv and all(max(v['tr'])==0 for v in sv),f'{lab}: no corner movement')
        pg.wait_for_timeout(1300);ok(not pg.evaluate(HAS),f'{lab}: and it fades away')
        ctx.close()
    b.close()
print(f'\n{pas} passed, {fail} failed');sys.exit(1 if fail else 0)
