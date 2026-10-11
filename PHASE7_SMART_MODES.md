# Yoga Launcher — Phase 7: Smart Mode Dashboards & Context Automation

This phase is an additive update to the existing Yoga Launcher project. Existing launcher UI, settings, themes, app drawer, widgets, custom modes, contact shortcuts, media controls, compass, and the previous time-of-day schedule are preserved.

## What's new

- Mode-specific dashboard headers and quick-action cards for Drive, Business, Music, Entertainment, Travel, Study, Fitness, Home, Finance, Creator, Gaming, Zen, Shopping, Comms, Outdoor, Kitchen, and Night.
- Live local time, date, battery percentage / charging state when the Android bridge can provide it, and network online/offline state.
- Quick actions try to launch matching apps already installed on the device. Maps and payments have safe web fallbacks; the launcher does not fabricate installed apps.
- Optional context automation rules for charging, battery at or below 20%, offline/online network, morning, work hours, evening, and night.
- Automation is off by default. Rules are stored locally, are evaluated while the launcher is running, and can be removed individually.
- Manual mode selection temporarily pauses context switching for 30 minutes.
- Automation never places calls, sends messages, or makes payments automatically.

## Build

1. Open the `Yoga-Launcher` folder in Android Studio.
2. Allow Android Studio to sync Gradle and install the configured Android SDK if prompted.
3. Select **Build > Make Project** or run `./gradlew assembleDebug`.
4. The debug APK is normally written to `app/build/outputs/apk/debug/app-debug.apk`.

The build was not executable in the current offline environment because the Gradle 8.7 distribution was not cached and `services.gradle.org` could not be reached. The Phase 7 JavaScript was checked with Node.js syntax validation; please run a Gradle build in Android Studio or another environment with Gradle available before installing.

## Notes and limitations

- Context rules run in the WebView while Yoga Launcher is active. Android may pause the WebView in the background, so this is not a guaranteed background service.
- Bluetooth car-connection detection is intentionally not included yet; the current native bridge does not expose a Bluetooth connection state.
- App matching depends on app names reported by Android and may vary by device/manufacturer.
