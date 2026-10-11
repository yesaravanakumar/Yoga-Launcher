# Performance + UI changes (index.html)
- Removed every `backdrop-filter` (60 declarations); opaque fallbacks added for dock, sheets, toast, search bars, chips, music player.
- Removed blurred outer glow `box-shadow` layers (72) and glow `text-shadow` layers (18). Rings and inset highlights kept.
- `place()` (arc drawer): removed the inner-icon rotate/scale/rotate stack and speed stretch; fewer z-index and label-opacity writes.
- Lock screen and home screen minutes/seconds pills: frosted glass (blur 22px), locked on in every mode (`lk-pill-glass` style block at the end of the file).
- Music widget: hidden whenever nothing is playing; removed the "Show it when nothing is playing" toggle.
- Two gold image mandalas, "Royal Gold" (default) and "Gold Medallion" (Settings > Mandala). The pictures are embedded inside index.html, so it is still a single file. The mandala is now on in Mini and Performance modes too (only Stealth hides it); one-time migration switches existing installs.
- Tilt / motion parallax is permanently disabled (sensor listener, pointer parallax, demo and the settings section are off or hidden).
- New vector mandala "Royal Gold · vector" (default): 6 drawn layers (centre, inner petals, middle petals, outer petals, leaf ring, dotted rim). Settings > Mandala > Layer rotation: per-layer speed and direction (↻/↺, 0 = still), plus Alternate / Reverse all / Stop all. Needs "Slow mandala spin" on. Rotation is compositor-only and works in Mini mode too; Performance and Stealth keep it off.
- Palette default is now Gold; mandala brightness default 45%.

# v1.50 drawer motion
- Icons: 96 px WebP (was 128 px PNG), decoded asynchronously; rows outside the screen get their picture only when scrolled near (IntersectionObserver); Crescent loads icons as they appear. Icon bursts redraw the drawer once instead of once per 24-icon batch.
- Rows outside the screen are not painted (content-visibility) in Grid / List / Smart.
- Blur, glass, masks and filters are switched off while the drawer opens, closes, scrolls or is dragged, then restored.
- Swipe up on Home drags the drawer up with the finger; swipe down on the header or at the top of the list drags it closed; release springs open or shut (elapsed-time spring, same on 60/120 Hz).
- Only the first two rows fade in on open (opacity + transform only).
- Crescent wheel physics use fixed 8 ms sub-steps.
- Version string updated to v1.50.
- Not yet tested on a real phone; Kotlin change uncompiled until the first GitHub build.

# Step 6 performance pass
See docs/PERF_AUDIT.md. Mandala images now in assets/img/; theme thumbnails and Settings chips build lazily; media polling reduced; Kotlin: background app scan, bounded search suggestions, battery de-dupe (uncompiled).
