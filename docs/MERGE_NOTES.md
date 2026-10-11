# Merge: v1.51 page + uploaded v1.47 page

Base is the v1.51 page (Arc Wave drawer, matte look, Android platform layer). Put back from the uploaded page, which the v44 change had removed:
- Display modes Standard / Minimal / Performance / Stealth (Settings > Mode selector, Mode quick action, "Switch mode" gesture)
- Auto battery saver (Performance at 20%, back when charging or 30%)
- "Mode" rows in the settings preview, status card and diagnostics; Battery saver quick tile
- Favicon and apple-touch-icon

Changed on the way: entering Stealth no longer opens the search overlay (the old code fired a `change` event that live preview reacted to).
Left as v1.51: parallax tilt stays off, matte look, newer text and sizing code. Phones saved on Minimal stay on Minimal.
