# Modes removed

Removed from index.html:
- Smart Modes (the Modes chip and sheet, custom modes, schedules, automation rules, favourite contacts and media controls inside the sheet)
- Golden Lotus app panel (Gaming / Business / Entertainment) and the home quick-launch strip
- Display modes Standard / Minimal / Performance / Stealth: Settings > Mode row, the Mode quick action, the "Switch mode" gesture, and the auto battery saver
- The Gaming / Business / Entertainment switch on the Business home

Kept:
- All the individual look settings (effects, mandala, pure black, dim, etc.) and "Power & performance" settings
- Phones that were on Performance or Stealth go back to the default Minimal look; Standard and Minimal stay as they were
- The Business home code is still in the file but switched off (it was only reachable through the mode switch). Settings it stored (yoga-biz-slots, yoga-biz-people) are untouched.
- Android side (Kotlin) is unchanged

Tests: smoke-load, smoke-monkey and e2e pass; the e2e checks for the Business calendar card and the Smart Modes night clock were replaced by "no mode controls left".
