"""Build 4: energy trace, real Chromium. python3 fx-trace-test.py [index.html]"""
import sys,os
from playwright.sync_api import sync_playwright
HTML=os.path.abspath(sys.argv[1] if len(sys.argv)>1 else os.path.join(os.path.dirname(__file__),'../../app/src/main/assets/index.html'))
pas=fail=0
CUR=['arc']
def ok(c,m):
    global pas,fail
    pas+=bool(c);fail+=(not c);print(('PASS ' if c else 'FAIL ')+m)
HAS="()=>!!document.querySelector('#awFx .etr')"
INFO="""()=>{const b=document.querySelector('#awFx .etr');if(!b)return null;const root=document.getElementById('aw').getBoundingClientRect(),
 ic=document.querySelector('#awFi .ic').getBoundingClientRect(),rail=document.getElementById('awRail').getBoundingClientRect(),nm=document.getElementById('awNm').getBoundingClientRect();
 const ds=[...b.children],vis=ds.filter(d=>+d.style.opacity>0.01);
 const hit=(r,q,p)=>r.right>q.left-p&&r.left<q.right+p&&r.bottom>q.top-p&&r.top<q.bottom+p;
 const rs=vis.map(d=>d.getBoundingClientRect());
 return {n:ds.length,vis:vis.length,
  onIcon:rs.some(r=>{const cx=(r.left+r.right)/2,cy=(r.top+r.bottom)/2;return cx>ic.left&&cx<ic.right&&cy>ic.top&&cy<ic.bottom}),
  onName:rs.some(r=>hit(r,nm,0)),onRail:rs.some(r=>hit(r,rail,0)),
  inRoot:rs.every(r=>r.left>=root.left-1&&r.right<=root.right+1&&r.top>=root.top-1&&r.bottom<=root.bottom+1),
  bg:ds.length?getComputedStyle(ds[0]).backgroundColor:'',c1:getComputedStyle(document.getElementById('aw')).getPropertyValue('--c1rgb').trim(),
  moved:[...document.querySelectorAll('#awWave .awi')].some(e=>e.style.translate),
  maxOp:Math.max(0,...ds.map(d=>+d.style.opacity||0))}}"""
def setup(pg,lv='balanced',extra=''):
    pg.evaluate("()=>{AWCFG.set('fxOn',true);AWCFG.set('fxLevel','%s');AWCFG.set('fx_trace',true);AWCFG.set('fx_ripple',false);AWCFG.set('fx_magnet',false);AWCFG.set('fx_holo',false);%s YD.open()}"%(lv,extra));pg.wait_for_timeout(1000)
def swipe(pg,cdp,W,H,steps=8,dy=.05,wait=12,up=True,end=True):
    xs=W*(.15 if CUR[0]=='wave' else .3);y0=H*(.7 if up else .3)   # in Wave the star/home/edit buttons sit at x=.3W
    cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':xs,'y':y0}]})
    seen=[]
    for i in range(1,steps+1):
        cdp.send('Input.dispatchTouchEvent',{'type':'touchMove','touchPoints':[{'x':xs,'y':y0+(-1 if up else 1)*i*H*dy}]});pg.wait_for_timeout(wait)
        v=pg.evaluate(INFO)
        if v:seen.append(v)
    if end:cdp.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]})
    return seen
def gone(pg,ms=3800):
    for i in range(ms//50):
        if not pg.evaluate(HAS):break
        pg.wait_for_timeout(50)
    pg.wait_for_timeout(100)
def probe(pg,ms):
    seen=[]
    for i in range(ms//25):
        pg.wait_for_timeout(25);v=pg.evaluate(INFO)
        if v:seen.append(v)
    return seen
with sync_playwright() as p:
    b=p.chromium.launch(args=['--no-sandbox'])
    for DES in os.environ.get('TR_DES','arc,orbit,wave,fan').split(','):
      for W,H in [tuple(map(int,x.split('x'))) for x in os.environ.get('TR_SZ','320x570,360x740,390x844,412x915').split(',')]:
        ctx=b.new_context(viewport={'width':W,'height':H},device_scale_factor=2,has_touch=True,is_mobile=True);pg=ctx.new_page();errs=[];pg.on('pageerror',lambda e:errs.append(str(e)))
        pg.goto('file://'+HTML);pg.wait_for_timeout(1500);pg.evaluate("()=>document.querySelectorAll('.tips').forEach(e=>e.remove())")
        CUR[0]=DES;pg.evaluate("(d)=>AWDES.set(d)",DES);setup(pg);cdp=ctx.new_cdp_session(pg)
        tag=f'{DES} {W}x{H}'
        full=DES=='arc' and W in(320,390)
        ok(not pg.evaluate(HAS),f'{tag}: nothing on screen before a gesture')
        # slow drag never shows it
        sl=swipe(pg,cdp,W,H,steps=6,dy=.01,wait=60)
        ok(not any(s['vis'] for s in sl),f'{tag}: slow drag shows no trail')
        pg.wait_for_timeout(1200)
        # fast swipe shows it, clear of icon / name / A-Z strip, inside the drawer, theme colour
        fs=swipe(pg,cdp,W,H,steps=8,dy=.05,wait=12)
        vis=[s for s in fs if s['vis']]
        ok(len(vis)>0,f'{tag}: fast swipe shows the trail')
        if vis:
            ok(all(s['n']==6 for s in vis),f'{tag}: Balanced uses 6 dots')
            ok(not any(s['onIcon'] for s in vis),f'{tag}: no dot on the selected icon')
            ok(not any(s['onName'] for s in vis),f'{tag}: no dot on the selected name')
            ok(not any(s['onRail'] for s in vis),f'{tag}: no dot on the A-Z strip')
            ok(all(s['inRoot'] for s in vis),f'{tag}: all dots inside the drawer')
            ok(not any(s['moved'] for s in vis),f'{tag}: icons are not moved by the trail')
            ok(all(s['c1'] and s['c1'].replace(' ','') in s['bg'].replace(' ','').replace('rgb(','').replace('rgba(','') for s in vis[:1]),f'{tag}: colour comes from --c1rgb ({vis[0]["bg"]} / {vis[0]["c1"]})')
        gone(pg)
        ok(not pg.evaluate(HAS),f'{tag}: trail removes itself after the fling')
        pg.wait_for_timeout(300)
        ok(pg.evaluate("()=>AWFX._busy()")==0,f'{tag}: no ticker or styled element left behind')
        ok(not errs,f'{tag}: no page errors {errs[:2]}')
        if full:
            for lv,n in [('subtle',4),('exp',8)]:
                pg.evaluate("(l)=>{AWCFG.set('fxLevel',l)}",lv);pg.wait_for_timeout(300)
                s2=[s for s in swipe(pg,cdp,W,H) if s['vis']]
                ok(s2 and all(s['n']==n for s in s2),f'{tag}: {lv} uses {n} dots')
                if lv=='exp':ok(pg.evaluate("()=>!!document.querySelector('#awFx .etr.eg')"),f'{tag}: experimental adds the soft glow')
                gone(pg)
            pg.evaluate("()=>AWCFG.set('fxLevel','balanced')");pg.wait_for_timeout(300)
            # new touch cancels it at once
            cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':W*.3,'y':H*.7}]})
            for i in range(1,7):
                cdp.send('Input.dispatchTouchEvent',{'type':'touchMove','touchPoints':[{'x':W*.3,'y':H*.7-i*H*.05}]});pg.wait_for_timeout(12)
            cdp.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]});pg.wait_for_timeout(120)
            had=pg.evaluate(HAS)
            cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':W*.3,'y':H*.45}]});pg.wait_for_timeout(60)
            ok(had and not pg.evaluate(HAS),f'{tag}: a new touch cancels the trail at once')
            cdp.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]});gone(pg)
            # closing the drawer mid-trace removes it
            swipe(pg,cdp,W,H);pg.wait_for_timeout(120);had=pg.evaluate(HAS);pg.evaluate("()=>YD.stop()");pg.wait_for_timeout(60)
            ok(had and not pg.evaluate(HAS) and pg.evaluate("()=>AWFX._busy()")==0,f'{tag}: closing the drawer removes the trail')
            pg.evaluate("()=>YD.open()");pg.wait_for_timeout(1000)
            # switch off / FX off
            pg.evaluate("()=>AWCFG.set('fx_trace',false)")
            ok(not any(s['vis'] for s in swipe(pg,cdp,W,H)),f'{tag}: per-effect switch off shows nothing');gone(pg)
            pg.evaluate("()=>{AWCFG.set('fx_trace',true);AWCFG.set('fxOn',false)}")
            ok(not any(s['vis'] for s in swipe(pg,cdp,W,H)),f'{tag}: FX master off shows nothing');gone(pg)
            pg.evaluate("()=>{AWCFG.set('fxOn',true);AWCFG.set('fxLevel','off')}")
            ok(not any(s['vis'] for s in swipe(pg,cdp,W,H)),f'{tag}: preset Off shows nothing');gone(pg)
            pg.evaluate("()=>AWCFG.set('fxLevel','balanced')");pg.wait_for_timeout(300)
            # drawer still works: A-Z jump, search, tap opens
            f0=pg.evaluate("()=>AWENG.st().foc")
            pg.evaluate("()=>{const r=[...document.querySelectorAll('#awRail div')].find(d=>d.dataset.l==='W');r&&r.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true}))}")
            pg.evaluate("()=>YD.glide(10)");pg.wait_for_timeout(900)
            ok(pg.evaluate("()=>AWENG.st().foc")==10,f'{tag}: selection still moves (A-Z / glide)')
            ok(pg.evaluate("()=>!!document.querySelector('#awFi').getAttribute('aria-label')||document.querySelector('#awNm').textContent.length>0"),f'{tag}: selected name still readable')
        ctx.close()
    # reduced motion: never shown
    ctx=b.new_context(viewport={'width':390,'height':844},has_touch=True,is_mobile=True,reduced_motion='reduce');pg=ctx.new_page();errs=[];pg.on('pageerror',lambda e:errs.append(str(e)))
    pg.goto('file://'+HTML);pg.wait_for_timeout(1500);pg.evaluate("()=>document.querySelectorAll('.tips').forEach(e=>e.remove())");setup(pg);cdp=ctx.new_cdp_session(pg)
    ok(not any(s['vis'] for s in swipe(pg,cdp,390,844)),'reduced motion: trail never runs');pg.wait_for_timeout(800)
    ok(pg.evaluate("()=>AWFX._busy()")==0 and not errs,'reduced motion: nothing left, no errors')
    ctx.close();b.close()
print(f'\n{pas} passed, {fail} failed');sys.exit(1 if fail else 0)
