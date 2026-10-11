# Debugging pass

## Method
1. Compiled all Kotlin against the Android 34 API (kotlinc 1.9.24): no errors.
2. Linted every inline script of index.html together (undefined names, redeclarations, bad assignments).
3. Loaded the full page headless with a fake Android bridge, then tapped 467 controls and read the page's own error log.
4. Re-ran the Business home screen tests and the Smart Modes checks after the fixes.

## Fixed
1. **Holo Glass never opened installed apps.** `findApp`, `launchApp` and `openUrl` lived inside one Smart Modes block, but Holo Glass and the home strip test for them as globals, so they always saw "not defined". Fix: the Smart Modes block now shares them.
2. **Website shortcuts showed a false "Allow pop-ups" toast** (same cause, `openUrl` missing). They now go through the Android bridge.
3. **Apps with several launcher entries appeared twice** in the app list. `pushApps` now keeps one entry per package.
4. **Bridge errors were swallowed silently.** `ui {}` still stops crashes but now logs the reason to Logcat (tag `YogaBridge`).
5. **Quick call dialled raw contact text** (spaces, brackets). Numbers are cleaned and go straight to the dialer.
6. **Business screen stayed stale after allowing calendar access.** It now refreshes on permission change and on resume.
7. Housekeeping: removed a stale duplicate root `AndroidManifest.xml` and an empty `testwrite` file; README pointed to missing docs.

## Not changed, worth knowing
- Theme and icon-pack preview taps are slow in a desktop simulator (about 0.4 s each); not measured on a phone.
- Calendar card cannot hide declined meetings (Android data used does not include response status).
- Gradle build and phone behaviour could not be run here; use CI or the commands in README.
