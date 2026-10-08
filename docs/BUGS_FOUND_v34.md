# Bug check history

Checked: page syntax and markup, 83 switches / 1,248 dropdown options / 397 buttons in Settings, all four app drawers, lock screen, weather, music player (with a fake phone bridge), 300-app list, corrupted saved data, hostile app names, timer leaks, and every Android file.
Not checked: a real phone, and a real Android build. All Kotlin changes are uncompiled until the first GitHub build.

## Fixed in v35
1. Notification count badges were blank. Numbers now show, up to 99+.
2. Uninstall most likely did nothing. Added `REQUEST_DELETE_PACKAGES`.
3. App list went stale when apps changed while another app was open. The listener now runs the whole time.
4. "Show it when the screen was off for..." could fail. Android now sends the real screen-off time.
5. Notification counts were too high (music, downloads, group headers). Now left out.
6. The page could be navigated away with the phone bridge exposed. Only the bundled page loads now.
7. Slow app-list refresh (labels read twice). Now once.
8. Accessibility service config had no feedback type. Added.

## Fixed in v36
9. **Next alarm and next calendar event now work.** Added `getNextAlarm`, `getNextEvent`, `requestCalendar`, `openNext` and the calendar permission. Tapping the alarm or event line opens the phone's clock or calendar.
10. **Notification previews now work.** The notification listener keeps the latest three per app, and tapping a preview opens that notification. Added `hasNotificationAccess`, `requestNotificationAccess`, `getNotifications`, `openNotification`, so the Settings buttons and status text work too.
11. **Song shown twice** under the date and in the music player. The date-line copy now shows only when the player is switched off.
12. **Icon cache never refreshed.** An app update or removal now clears that app's cached icon.
13. **Font-size and display-size changes restarted the screen.** Those changes are now handled without a restart.
14. **Updates could not install over each other** because `versionCode` was always 1. GitHub builds now use the run number.
15. **Empty "Android widgets" section** showed in the app build, which cannot host widgets. It is now hidden there.

## Still open
- **Real Android widgets** (add, place, resize, stack). `docs/native-v19/YogaNativeV19.kt` has the code but it has not been compiled or tested, so it is not switched on.
- **Commute** never shows. Nothing sends it, and a real commute time needs a maps service key.
- **Release readiness:** debug APK only, no release signing, no tests, no lint step, `targetSdk 34`.
- **Not tested on a real phone.**

## Looked fine
Corrupted or hostile saved data (5 kinds) never broke startup and caused no object pollution. `<img onerror>` text in app names, tasks, notifications, media and events did not run. Timers did not pile up over 15 lock/unlock cycles. Opening any drawer with 300 apps took about 100 ms. Clock hour and minute stayed centred at 50, 100 and 250 percent.
