# Yoga Launcher: what is still missing

Last checked against the v36 project. Items marked "done" were fixed in the same build.

## Done in this build
- Triple Arc: every circle now has the same icon size, the same gap along the arc and the same distance to the next circle (the Favourites circle used to sit further out). New "Icon spacing" option (Tight / Normal / Roomy). Circle names follow each circle's curve at the same angle, and are renamed Favourites / Recent / Smart / All apps.
- Lock screen is customisable under Settings > Lock screen: clock style (Dial / Digital), size, position, text alignment, seconds, date, colour glow, dim timer, page dots, unlock hint, features shown, and a Reset button.
- Yoga lock screen: same semi-circle dial as the home page (minutes and seconds rings, big hour, glass pill), rotating features (weather, next event, alarm, battery, now playing, notification count, next task), swipe up to unlock. Display only, it does not replace the phone's PIN or fingerprint.
- App icon: the manifest had no `android:icon`, so GitHub builds showed the default Android icon. Now set, with a new adaptive icon (chrome mark), round icon, themed (monochrome) icon and 512 px store icon.
- Weather by location: the manifest had no location permission and the WebView never answered the page's permission request. Both added.
- Wallpaper picker and settings import: file inputs did nothing in the WebView. A file chooser handler is added.
- `VIBRATE` permission added so haptics work.

## Features you asked for that are not built yet
1. (Already in v28, my earlier list was wrong: folders on the home grid and dock)
2. Several home pages with page dots, add/remove page, wrap-around
3. Home grid controls: grid size, icon size, label size for the home screen itself
4. Lock layout switch (stop accidental drags and deletes)
5. Material You wallpaper colours (light and follow-system themes already exist in v28)
6. Landscape, tablet and foldable layouts (the app is portrait-locked)
7. Long-press on empty space opens one edit mode (wallpaper, widgets, grid, settings)
8. Placing apps on a chosen home cell from the Triple Arc (it uses the first free cell)

## Written but not switched on (needs a build check)
9. Now-playing card, next calendar event, real Android widgets: `docs/native-v19/YogaNativeV19.kt`
10. Commute: the page shows it, but nothing sends the data
11. (Done in v36: next alarm and next calendar event)

## Android integration gaps
12. No prompt to set Yoga Launcher as the default home app (needs `RoleManager`)
13. No settings backup to a file or cloud from the app itself
14. No adaptive/themed icon support for other apps' icons
15. No work-profile or multi-user app support
16. Landscape lock means no split-screen friendly layout
17. Back gesture and edge-to-edge insets are only partly handled

## Release and store readiness
18. Only a debug APK is built. No release signing, no keystore secret in GitHub, no R8/minify
19. `versionCode` is fixed at 1, so updates will not install over each other cleanly. Bump it for each release
20. No GitHub Release on a version tag
21. `targetSdk 34` is behind what Google Play now asks for new apps. Check the current rule before publishing
22. `QUERY_ALL_PACKAGES` needs a justification on Google Play
23. No privacy policy page (needed for Play, because of location, contacts and notification access)
24. `allowBackup="true"` with no backup rules, so private data could be backed up
25. No automated tests and no lint step in the GitHub build
26. No crash reporting

## Quality and accessibility
27. Many controls have labels, but a full TalkBack pass has not been done
28. Text size is locked at 100 percent on purpose, so large-font users get no scaling
29. Not tested on a real device. Everything so far was checked in a desktop browser
