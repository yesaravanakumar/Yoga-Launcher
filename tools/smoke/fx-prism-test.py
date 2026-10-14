"""Build 5: prism edges, real Chromium. python3 fx-prism-test.py [index.html] [shots dir]
Run one design at a time if the machine is slow:  PR_DES=wave python3 fx-prism-test.py"""
import sys,os,re,io
from playwright.sync_api import sync_playwright
HTML=os.path.abspath(sys.argv[1] if len(sys.argv)>1 else os.path.join(os.path.dirname(__file__),'../../app/src/main/assets/index.html'))
OUT=sys.argv[2] if len(sys.argv)>2 else '/tmp/fxshots'
os.makedirs(OUT,exist_ok=True)
DESIGNS=[os.environ['PR_DES']] if os.environ.get('PR_DES') else ['arc','orbit','wave','fan']
pas=fail=0
def ok(c,m):
    global pas,fail
    pas+=bool(c);fail+=(not c);print(('PASS ' if c else 'FAIL ')+m)
HAS="()=>!!document.querySelector('#awFx .pe')"
COUNT="()=>document.querySelectorAll('#awFx .pe').length"
BUSY="()=>AWFX._busy()"
# in-page sampler: one reading of the prism box (null when absent)
INFO_JS="""const info=()=>{const b=document.querySelector('#awFx .pe');if(!b)return null;
 const r=b.getBoundingClientRect(),ic=document.querySelector('#awFi .ic').getBoundingClientRect(),rail=document.getElementById('awRail').getBoundingClientRect(),
 nm=document.getElementById('awNm').getBoundingClientRect(),root=document.getElementById('aw').getBoundingClientRect();
 const pm=b.querySelector('.pm'),pf=b.querySelector('.pf'),icEl=document.querySelector('#awFi .ic'),icr=getComputedStyle(icEl).borderTopLeftRadius;
 const icRad=icr.indexOf('%')>0?parseFloat(icr)/100*Math.min(ic.width,ic.height):parseFloat(icr)*(ic.width/icEl.offsetWidth);
 const mx=pm?parseFloat(pm.style.getPropertyValue('--mx')):NaN,my=pm?parseFloat(pm.style.getPropertyValue('--my')):NaN;
 return {tag:b.tagName,kids:[...b.children].map(k=>k.className).join(','),op:+b.style.opacity,
  d:Math.max(Math.abs(r.left-ic.left),Math.abs(r.top-ic.top),Math.abs(r.width-ic.width),Math.abs(r.height-ic.height)),
  rad:parseFloat(getComputedStyle(b).borderTopLeftRadius)*(r.width/(b.offsetWidth||r.width)),icRad,   /* on screen: the shell may scale the drawer */
  inRoot:r.left>=root.left-.5&&r.right<=root.right+.5&&r.top>=root.top-.5&&r.bottom<=root.bottom+.5,
  clearRail:!(r.left<rail.right&&r.right>rail.left&&r.top<rail.bottom&&r.bottom>rail.top),
  nmOverlap:Math.max(0,Math.min(r.right,nm.right-3)-Math.max(r.left,nm.left+3))*Math.max(0,Math.min(r.bottom,nm.bottom-3)-Math.max(r.top,nm.top+3)),
  icNmOverlap:Math.max(0,Math.min(ic.right,nm.right-3)-Math.max(ic.left,nm.left+3))*Math.max(0,Math.min(ic.bottom,nm.bottom-3)-Math.max(ic.top,nm.top+3)),
  ang:Math.atan2(-my,-mx),pfOp:pf?+pf.style.opacity:-1,
  hasBg:getComputedStyle(b).backgroundColor!=='rgba(0, 0, 0, 0)'||getComputedStyle(b).backgroundImage!=='none',
  boxShadowOnBox:getComputedStyle(b).boxShadow!=='none',
  pe:+(b.style.pointerEvents==''),aria:b.getAttribute('aria-hidden')}};"""
def sample(pg,action,ms=1300,step=16):
    return pg.evaluate("async()=>{"+INFO_JS+"const out=[];"+action+";const t0=performance.now();while(performance.now()-t0<"+str(ms)+"){await new Promise(r=>setTimeout(r,"+str(step)+"));out.push(info())}return out}")
def setup(pg,lv='balanced'):
    pg.evaluate("()=>{AWCFG.set('fxOn',true);AWCFG.set('fxLevel','%s');AWCFG.set('fx_prism',true);AWCFG.set('fx_ripple',false);AWCFG.set('fx_magnet',false);AWCFG.set('fx_holo',false);AWCFG.set('fx_trace',false);YD.open()}"%lv);pg.wait_for_timeout(1000)
def seen(s):return [v for v in s if v]
def lum(png):
    from PIL import Image
    im=Image.open(io.BytesIO(png)).convert('L');w,h=im.size;c=im.crop((int(w*.3),int(h*.3),int(w*.7),int(h*.7)));px=list(c.getdata());return sum(px)/len(px)
STATE="()=>JSON.stringify({t:[...document.querySelectorAll('#awWave .awi')].map(e=>e.style.transform+'|'+e.style.opacity+'|'+e.style.translate).sort(),f:document.getElementById('awFi').style.transform})"
with sync_playwright() as p:
    b=p.chromium.launch(args=['--no-sandbox'])
    for DES in DESIGNS:
      for W,H in [(320,570),(360,740),(390,844),(412,915)]:
        ctx=b.new_context(viewport={'width':W,'height':H},device_scale_factor=2,has_touch=True,is_mobile=True);pg=ctx.new_page();errs=[];pg.on('pageerror',lambda e:errs.append(str(e)))
        pg.goto('file://'+HTML);pg.wait_for_timeout(1500);pg.evaluate("()=>document.querySelectorAll('.tips').forEach(e=>e.remove())")
        pg.evaluate("(d)=>AWDES.set(d)",DES);setup(pg)
        tag=f'{DES} {W}x{H}'
        full=DES=='arc' and W in(320,390)
        ok(not pg.evaluate(HAS),f'{tag}: nothing on screen before any snap')
        s=sample(pg,"YD.glide(AWENG.st().foc+1)",1300);v=seen(s)
        ok(len(v)>5,f'{tag}: rim light appears after a snap ({len(v)} samples)')
        if v:
            mid=[x for x in v if x['op']>.5] or v
            ok(all(x['tag']=='B' for x in v),f'{tag}: overlay uses <b> elements only')
            ok(max(x['d'] for x in v)<=1.5,f'{tag}: box matches the selected icon rect in every frame (max off {max(x["d"] for x in v):.2f}px)')
            ok(max(abs(x['rad']-x['icRad']) for x in v)<=1.5,f'{tag}: corner radius matches the icon ({v[len(v)//2]["rad"]:.1f} vs {v[len(v)//2]["icRad"]:.1f})')
            ok(all(x['inRoot'] for x in v),f'{tag}: stays inside the drawer')
            ok(all(x['clearRail'] for x in v),f'{tag}: clear of the A-Z strip')
            ok(all(x['nmOverlap']<=x['icNmOverlap']+1 for x in v),f'{tag}: never overlaps the selected name more than the icon itself does (engine pop included)')
            ok(not any(x['hasBg'] for x in v),f'{tag}: no fill, no background (icon face is never covered)')
            ok(not any(x['boxShadowOnBox'] for x in v),f'{tag}: no shadow on the container itself')
            ok('pm' in v[0]['kids'] and 'pf' in v[0]['kids'] and 'po' not in v[0]['kids'],f'{tag}: Balanced = crescent + pale fringe, no outer glow')
            ang=[x['ang'] for x in v if x['op']>.3];ok(len(ang)>3 and max(ang)-min(ang)>1.2,f'{tag}: the reflection travels around the rim ({(max(ang)-min(ang)) if ang else 0:.2f} rad)')
            ok(max(x['op'] for x in v)>.6 and v[-1]['op']<.5,f'{tag}: fades in and out (peak {max(x["op"] for x in v):.2f})')
        sheet=pg.evaluate("()=>[...document.styleSheets].map(s=>{try{return [...s.cssRules].map(r=>r.cssText).filter(t=>/#awFx \\.pe/.test(t)).join('')}catch(e){return ''}}).join('')")
        ok('var(--c1rgb)' in sheet and 'var(--c1)' in sheet,f'{tag}: accent comes from --c1 / --c1rgb')
        chk=re.sub(r'color-mix\(in srgb,var\(--c1\) \d+%,(white|#fff)\)','',sheet)   # the minifier writes white as #fff; it is only ever mixed into --c1
        ok(not re.search(r'#[0-9a-fA-F]{3,6}\b|rgba?\(\s*\d',chk),f'{tag}: no hardcoded palette (only --c1 mixed toward white)')
        ok('mask' not in sheet,f'{tag}: no mask (cannot degrade into a solid fill)')
        pg.wait_for_timeout(300)
        ok(not pg.evaluate(HAS) and pg.evaluate(BUSY)==0,f'{tag}: removes itself, no frame loop left')
        pg.evaluate("()=>YD.glide(9)");pg.wait_for_timeout(2400);s_on=pg.evaluate(STATE)
        pg.evaluate("()=>{AWCFG.set('fxLevel','off');YD.glide(0)}");pg.wait_for_timeout(2200);pg.evaluate("()=>YD.glide(9)");pg.wait_for_timeout(2200)
        ok(s_on==pg.evaluate(STATE),f'{tag}: settled icon transforms/translate identical with the effect on and FX Off')
        ok(not pg.evaluate(HAS),f'{tag}: FX Off: nothing')
        if full:
            pg.evaluate("()=>{AWCFG.set('fxLevel','balanced');AWCFG.set('fx_prism',false);YD.glide(2)}");pg.wait_for_timeout(500)
            ok(not pg.evaluate(HAS),f'{tag}: per-effect switch off: nothing')
            pg.evaluate("()=>{AWCFG.set('fx_prism',true)}");pg.wait_for_timeout(900)
            # levels
            pg.evaluate("()=>AWCFG.set('fxLevel','subtle')");sv=seen(sample(pg,"YD.glide(4)",1100))
            pg.evaluate("()=>AWCFG.set('fxLevel','exp')");ev=seen(sample(pg,"YD.glide(6)",1100))
            ok(sv and 'pf' not in sv[0]['kids'] and 'po' not in sv[0]['kids'],f'{tag}: Subtle: one crescent only')
            ok(ev and 'po' in ev[0]['kids'] and 'pf' in ev[0]['kids'],f'{tag}: Experimental: crescent + fringe + faint outer glow')
            ok(sv and ev and max(x['op'] for x in sv)<max(x['op'] for x in ev),f'{tag}: intensity scales brightness')
            pg.wait_for_timeout(600);pg.evaluate("()=>AWCFG.set('fxLevel','balanced')")
            # no stacking on rapid re-select
            mx=pg.evaluate("async()=>{let m=0;for(let i=0;i<8;i++){YD.glide((i*3)%9);await new Promise(r=>setTimeout(r,70));m=Math.max(m,document.querySelectorAll('#awFx .pe').length)}await new Promise(r=>setTimeout(r,1800));return [m,document.querySelectorAll('#awFx .pe').length]}")
            ok(mx[0]<=1 and mx[1]==0,f'{tag}: rapid re-selects never stack (max {mx[0]}) and it ends by itself')
            cdp=ctx.new_cdp_session(pg)
            # selecting another app while it shows removes it
            pg.evaluate("()=>YD.glide(AWENG.st().foc+1)");pg.wait_for_timeout(450)
            ok(pg.evaluate(HAS),f'{tag}: (rim light is up before the touch)')
            cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':W*.2,'y':H*.45}]});pg.wait_for_timeout(60)
            ok(not pg.evaluate(HAS) and pg.evaluate(BUSY)==0,f'{tag}: a new touch cancels it at once')
            cdp.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]});pg.wait_for_timeout(900)
            # real fast swipe: nothing until it settles
            xs=W*.3
            cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':xs,'y':H*.7}]})
            for i in range(1,9):
                cdp.send('Input.dispatchTouchEvent',{'type':'touchMove','touchPoints':[{'x':xs,'y':H*.7-i*H*.05}]});pg.wait_for_timeout(12)
            during=pg.evaluate(HAS)
            cdp.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]})
            ok(not during,f'{tag}: nothing while the finger is still dragging')
            sw=seen(pg.evaluate("async()=>{"+INFO_JS+"const o=[];const t0=performance.now();while(performance.now()-t0<3200){await new Promise(r=>setTimeout(r,20));o.push(info())}return o}"))
            ok(len(sw)>0,f'{tag}: appears after a fast swipe has settled')
            pg.wait_for_timeout(1000)
            # close mid-effect, reopen
            pg.evaluate("()=>YD.glide(1)");pg.wait_for_timeout(350);pg.evaluate("()=>YD.stop()");pg.wait_for_timeout(50)
            ok(not pg.evaluate(HAS) and pg.evaluate(BUSY)==0,f'{tag}: closing the drawer mid-effect cleans up')
            pg.evaluate("()=>YD.open()");pg.wait_for_timeout(500)
            ok(not pg.evaluate(HAS),f'{tag}: opening the drawer does not trigger it')
            # engine features still work
            r=pg.evaluate("()=>{const d=[...document.querySelectorAll('#awRail div')].find(x=>x.dataset.l==='S');if(!d)return null;const b=d.getBoundingClientRect();return {x:b.left+b.width/2,y:b.top+b.height/2}}")
            if r:
                cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':r['x'],'y':r['y']}]});pg.wait_for_timeout(40)
                cdp.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]});pg.wait_for_timeout(900)
                ok(pg.evaluate("()=>document.getElementById('awNm').textContent")[:1].upper()=='S',f'{tag}: A-Z jump still lands on S')
            lab=pg.evaluate("()=>{const f=document.getElementById('awFi');return (f.getAttribute('aria-label')||'')+'|'+f.getAttribute('role')}")
            ok(len(lab)>3 and 'null' not in lab.split('|')[0],f'{tag}: selected icon keeps its accessibility label ({lab})')
            pg.wait_for_timeout(1000)
            # circle icons: radius follows the shape setting
            pg.evaluate("()=>AWCFG.set('shape','ci')");pg.wait_for_timeout(300)
            cv=seen(sample(pg,"YD.glide(AWENG.st().foc+2)",900))
            ok(cv and abs(cv[len(cv)//2]['rad']-cv[len(cv)//2]['icRad'])<=1.5 and cv[len(cv)//2]['rad']>20,f'{tag}: circle icon shape: ring is round too ({cv[len(cv)//2]["rad"]:.1f}px)' if cv else f'{tag}: circle shape')
            pg.evaluate("()=>AWCFG.set('shape','auto')");pg.wait_for_timeout(1500)
        ok(not errs,f'{tag}: no page errors {errs[:2]}')
        if DES=='arc' and W==390:
            # pixel regression: the icon face must not be darkened or covered by the effect
            def face(prism,lvl):
                pg.evaluate("(a)=>{AWCFG.set('fxLevel',a[1]);AWCFG.set('fx_prism',a[0])}",[prism,lvl]);pg.wait_for_timeout(1500)
                pg.evaluate("()=>YD.glide(AWENG.st().foc+1)");pg.wait_for_timeout(420)
                r=pg.evaluate("()=>{const r=document.querySelector('#awFi .ic').getBoundingClientRect();return [r.left,r.top,r.width,r.height]}")
                png=pg.screenshot(clip={'x':r[0],'y':r[1],'width':r[2],'height':r[3]});return lum(png),png
            for lvl in ['subtle','balanced','exp']:
                a,pa=face(True,lvl);c,pc=face(False,lvl)
                ok(abs(a-c)/max(c,1)<.12,f'{tag}: icon face brightness unchanged at {lvl} (with {a:.1f}, without {c:.1f})')
                if lvl=='exp':open(f'{OUT}/prism-exp-390.png','wb').write(pa)
        ctx.close()
    ctx=b.new_context(viewport={'width':390,'height':844},has_touch=True,is_mobile=True,reduced_motion='reduce');pg=ctx.new_page();pg.goto('file://'+HTML);pg.wait_for_timeout(1500);pg.evaluate("()=>document.querySelectorAll('.tips').forEach(e=>e.remove())")
    setup(pg,'exp');rv=seen(sample(pg,"YD.glide(AWENG.st().foc+1)",1200))
    ok(rv,'reduced motion: the rim light still appears (static version)')
    ang=[x['ang'] for x in rv if x['op']>.2]
    ok(ang and max(ang)-min(ang)<.05,f'reduced motion: it does not travel (spread {(max(ang)-min(ang)) if ang else 0:.3f} rad)')
    pg.wait_for_timeout(400);ok(not pg.evaluate(HAS) and pg.evaluate(BUSY)==0,'reduced motion: and it fades away')
    ctx.close();b.close()
print(f'\n{pas} passed, {fail} failed');sys.exit(1 if fail else 0)
