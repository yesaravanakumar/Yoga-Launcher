# Bug check of Yoga-Launcher-merged

Checked: the page (all 31 scripts: syntax, undefined names, duplicate ids), the page-to-Android contract (every Android.xxx call and every window.xxx callback), the manifest and Kotlin files, the project's own smoke / e2e tests, and about 1,800 random taps, swipes, long-presses, Back / Home / screen-off events in headless Chromium with a mocked Android bridge. The page itself had no script errors.

Fixed:
- **Reset all** called `Android.clearData()`, which did not exist. The phone kept its native copy of settings, theme and lock-over and could bring the old look back after the restart. `clearData()` now exists in YogaBridge.kt.
- **Notification access check** matched any package name that merely contained this app's name. It now compares the exact package.
- **Notification dots / texts** stayed on screen after notification access was switched off. BadgeService now clears them when it disconnects.
- **Leaks on close**: the torch callback and the app-scan / suggestion threads were never stopped. `YogaBridge.release()` (called from MainActivity.onDestroy) stops them.
- **tools/smoke/e2e.js** still expected `cycleMode` to be gone, but the merge restored the display modes. The test now checks the right thing (Smart Modes stay removed).

Not changed: the Business home panel is still in the page but switched off on purpose (`sync()` has `on=false`). It is dead code for the planned cleanup stage.
Not tested: the Kotlin could not be compiled here (no Android SDK / Gradle access). The GitHub Actions build will confirm.
