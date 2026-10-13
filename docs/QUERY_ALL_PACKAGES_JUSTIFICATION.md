# QUERY_ALL_PACKAGES: Play Console justification (draft)

Play lists device search, antivirus, file managers and browsers as permitted uses and expects a core purpose of
discovering all installed apps. It also expects you to explain why a less intrusive method (a `<queries>` block) is not
enough. **"Launcher" is not named on that list**, so the written case below leans on the launcher's app search and
the claim must be true when the reviewer tests it.

## Text to paste into the declaration form
Yoga Launcher is a home-screen replacement. Its core function is to show, search and open every app installed on the
phone, in the home screen, dock, app drawer and a launcher-wide search box that searches apps (and contacts). It must
discover all apps rather than a fixed list, including apps with no launcher icon that still post notifications or offer
widgets, sideloaded and work-profile apps, and apps installed or removed while the launcher is running. The list is used
only on the device to build the drawer, search results, icon badges and shortcuts. It is never uploaded, shared or used
for advertising, analytics or profiling.

## Video (required by Play, 30-60 s)
1. Fresh install, set as default home app. 2. Open the app drawer: all apps are listed. 3. Install an app from Play,
return home: it appears without restarting. 4. Type part of an app's name in search: it is found. 5. Long-press an app:
shortcuts and uninstall confirmation. Say aloud that the list stays on the device.

## Before you submit: check whether you can drop the permission
The manifest already has `<queries>` entries for `MAIN/LAUNCHER`, `MAIN/HOME` and three icon-pack actions. Those alone
let a launcher list every app that has a launcher icon, which is most of what the drawer needs. Test a build without the
permission on Android 11+ (remove the `<uses-permission ... QUERY_ALL_PACKAGES>` line):
- If the drawer, search, badges and widget list are still complete, **remove the permission**. No declaration, no video,
  no review risk.
- If something is missing (e.g. badges for packages with no launcher icon), keep it and note exactly which feature in the
  text above, because the review asks why `<queries>` is not enough.
This was not testable here (no device, no Gradle network access).
