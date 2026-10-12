# v1.57 - Home pages + "back to where you were"

## Home pages (clock home screen)
- Dots appear under the home icons (above the dock). Tap a dot to switch page.
- `+` adds a page (max 5). `-` appears on an EMPTY page and removes it.
- "Add to home screen" / arc multi-select fills the current page, then the next pages,
  then creates a new page automatically when everything is full.
- While dragging an icon, drop it on a page dot to move it to that page.
- The page you were on is remembered (also after closing the app).
- Clock and date stay fixed; only the icons change per page.
- "Remove all home screen apps" resets back to 1 page.

## Returning from the app drawer
- Opening the drawer from the Home grid page ("Add app") and closing it (X, arc tab, Back, Esc)
  now returns to that Home grid page instead of the default home.
- Opening it from the clock home returns to the clock home, on the same page you left.

Test: `node tools/smoke/pages-test.js` and `pages-test2.js`.
- Glass switch removed: settings toggles use the default switch style (v1.57).

## Hold to open settings
- Hold the weather line or a page dot -> Widgets settings; hold next alarm / event / now-playing -> At a glance & notifications; date and clock text -> Clock (dial, mandala, wallpaper, dock, search already worked).
- Test: node tools/smoke/hold-test.js

## Mandala layer rotation (fix)
- Layer rotation did nothing before: the per-layer elements were never built. buildMandala now splits every style into up to 6 layers by radius (Centre, Inner, Middle, Outer petals, Leaf ring, Dotted rim); each rotates by its own slider (+ clockwise, - anticlockwise, 0 still). Spin speed scales all layers. Needs Slow mandala spin on.
- Test: node tools/smoke/layers-test.js
