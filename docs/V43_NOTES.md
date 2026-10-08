# v43

- Page: error log (Settings > About > Diagnostics) and every empty catch now logs to it
- Page: applySettings layers moved into one ordered hook list (ASET); a failing step no longer blocks the rest
- Android: real widget hosting (YogaWidgets.kt); pickWidget, placeWidget, hideWidget, removeWidget added to YogaBridge
- Android: MainActivity holds the WebView in a FrameLayout so widgets can sit on top
- Not yet tested on a phone. Check the GitHub Actions build first.

## v43.1 (drawer batch 1)

- Drawer action buttons 44 px, close button 48 px; drawer labels and hint 11 px
- Empty search now offers Search the web and Search Play Store
- Stronger haptic tick when the A-Z scrub moves to a new letter

## v43.2

- A to Z letters are printed along the arc scale; the letter of the focused app is highlighted, and all letters brighten while scrubbing with the round button
