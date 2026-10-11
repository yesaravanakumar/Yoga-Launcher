# Business home screen: architecture and roadmap

## Finding
The home quick-launch strip (`yhHomeQuick`, key `yoga-holo-mode`) is a second mode system next to Smart Modes (`mdc-smode`, mode `business`). Its Business slots were web links only. Two sources of truth is the main architecture risk.

## Decisions
- Keep the stack (Kotlin WebView + index.html). No rewrite.
- Business slots open the installed app first, and fall back to the website only if no app matches. The label shows the real app name, or the website name if none is installed.
- Later steps move Business config into the versioned store (`mdc-smcfg`, schema v3) and make the strip read Smart Modes, so there is one source of truth.

## Roadmap
1. Plan approved (this file).
2. DONE (this zip): strip prefers installed apps, re-renders when the native app list arrives. Checked headless only.
3. DONE (Step 3 zip): full Business home panel (`yogaBiz`) shown when the home strip is on Business. Clock, next-meeting card (Android.getNextEvent), 8 work-app tiles with unread badges (Android.getBadges), quick-call contacts (up to 5). Edit mode: tap a tile to pick any installed app, or Reset. Settings saved in localStorage keys `yoga-biz-slots` and `yoga-biz-people`.
4. Next: schedule switch (auto Business on weekdays), Business self-test in Settings.
5. Next: move settings into the versioned store and merge the strip's mode with Smart Modes.

No Kotlin files changed. The panel covers the home area while Business is on; the dock stays visible.

## Not yet tested
Real phone, and the Gradle build (CI only). No Kotlin files were changed.

## Motion (Step 4)
Business home and its pickers only; the app drawer keeps its own motion.
- Spring physics: one damped spring (stiffness 300, damping 24, about 5% overshoot) sampled into a CSS `linear()` curve. Falls back to an ease curve on older WebViews.
- Press feedback: scale to 0.95 in 60 ms, spring back on release. No delay on taps.
- Screen: sections rise in with a 30 ms stagger when Business opens; fades out in 130 ms on leaving while the normal home is usable at once.
- Meeting card expands on tap (length and end time). Mode switch has a sliding pill. Edit mode pops the tiles.
- Picker sheet slides up with a spring, swipe down on its handle to dismiss. Contacts show placeholder rows while loading.
- Only transform and opacity are animated, except the one card expansion. Panel text and slots are fixed-size so nothing jumps.
- Respects phone reduced-motion, the drawer Motion effects setting (Soft = shorter, Off = none) and the launcher's lite mode.
- Refreshes only redraw when data actually changed; the countdown and clock update in place.
