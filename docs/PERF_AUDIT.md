# Performance audit (Step 6)

Measured in headless Chromium (mock Android bridge, 160 apps, CPU throttle 4x/6x). Web layer only; no real-device or battery data.

## Baseline findings
- ~10,700 DOM elements at start; 91% inside the closed Settings sheet (5,800 SVG theme/pack thumbnails).
- index.html 1.0 MB: 313 KB base64 image mandalas (non-default) + 25 KB unused favicons.
- Settings app chips rebuilt on every app-list change; drawer category counts re-parsed localStorage per app.
- Music widget polled getMedia every 1 s and hasNotificationAccess ~2x/s, even without notification access.
- Native: app scan on UI thread in onPageFinished; one Thread per search keystroke; duplicate battery pushes.
- Baseline (single runs): DOMContentLoaded ~0.5 s (1x), ~2.3 s (4x), ~3.1-3.6 s (6x); idle main thread 8% / 20% / 25%.

## Changes
- Mandala images moved to assets/img/*.webp; favicon links removed (index.html 1,025 KB -> 688 KB).
- Theme/pack thumbnails built on first open of Settings > Themes (boot DOM 10,661 -> 4,728).
- Settings chips built only while Settings is open.
- Drawer category counts in one pass.
- Media poll: skipped without notification access, every 2 s when idle, access check every 10 s.
- 800 ms mark() interval skips while hidden.
- Kotlin (UNCOMPILED - build in CI first): app scan on a background thread (settings still follow the app list); suggestions one at a time, stale ones answered []; unchanged battery readings not re-sent.

## Verified
Original vs patched: drawer categories/counts, Settings chips, 59+16 thumbnails, both image mandalas identical; no page errors.

## Not done / open
- Final 3-run load/idle comparison did not finish; drawer-open at 4-6x is still choppy (needs a device trace).
- Dial draws ~25 frames/s at default settings (many SVG setAttribute calls per frame).
- WebView onPause/onResume in onStop/onStart deliberately not added (untested with lock-screen hooks).
- R8 minification off (needs @JavascriptInterface keep rules).

Bench scripts: tools/perf (npm i playwright sharp; node mkicons.js; node harness.js <index.html> label 4).
