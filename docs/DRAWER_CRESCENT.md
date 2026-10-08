# Crescent app drawer (v37)

All old drawer styles (list, honeycomb, ellipse, Arc 4.0 three-ring fan, Triple Arc) were removed
from `app/src/main/assets/index.html`. One new arc drawer replaces them: **Yoga Crescent**.

- One big arc on your thumb side. Drag up/down (or fling) and apps roll along the curve.
- The app in the thumb zone grows, gets a focus lens, its name, category and a big letter.
- Wheel wraps around when there are 16+ apps. Mouse wheel and arrow keys work too.
- A-Z index runs along an inner arc; press or slide on it to jump.
- Search and category chips sit on top. Tap an app to launch, hold for the app menu / drag to home.
- Select (multi-select), sort order, hidden apps, rename, badges and pinned stars all still work.

Settings: Themes & Look > Arcs (10 presets, side, curve, icon size, lines, glow, letter, names,
colours, save your own) and App drawer (A-Z index, sort, recents).
New setting keys: dside, dcv, disz, dln, dgl, dlt, dlb. Removed: ds, dst, al, dvw, acn, acmin, acmax,
acc, acr, aco, acl, tside, trg, tsz, tgap, tfx, tlb, tgd, t1-t4, arw, ars, aisz, abm, alt, arng, alv.

## v38 upgrades
- Action buttons under the focused app: Open, Pin/Unpin (star fills when pinned), App options. Tap the name to launch.
- Swipe sideways on the arc to move between category chips (the chip row follows).
- New chips: Recent (newest first) and Top (most opened first).
- Usage ring on the focus lens fills by how often you open the app, plus "n / total  opened N x".
- Icons lean along the curve (labels stay level); the focused icon glows.
- A-Z index press shows an enlarged letter while you scrub.
- New settings (Themes & Look > Arcs): Focus height (Higher/Centre/Lower), Icon spacing (Tight/Normal/Roomy),
  Icons lean, Usage ring and count, Haptic tick. Presets use them (Pocket Wheel = tight + low, Solar Flare = roomy, Whisper = quiet).
New setting keys: dfy, dsp, dtl, duc, dhp.
