# Yoga Launcher

An Android home-screen launcher built as a single HTML page (`app/src/main/assets/index.html`) inside a small Kotlin WebView app.

- Rotating dial clock, dock and the Arc Wave app drawer (v1.51: rotation, edge-to-edge, reduce motion, TalkBack entry points; see `docs/V1.51_NOTES.md`)
- Widgets page, search, themes and gestures
- Smart Modes, Holo Glass launcher and the Business home screen (see `docs/BUSINESS_HOME_PLAN.md`)
- Debug APK built by GitHub Actions on every push to `main` (Actions tab, artifact `yoga-launcher-debug-apk`)

## Build and test

```bash
# 1. Build the debug APK (needs JDK 17 and the Android SDK; CI does this for you)
chmod +x ./gradlew
./gradlew assembleDebug --console=plain
#    -> app/build/outputs/apk/debug/app-debug.apk

# 2. Install on a connected phone
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. Read the launcher's error lines (the page logs to Logcat as "YogaPage",
#    native bridge failures as "YogaBridge")
adb logcat -s YogaPage YogaBridge

# 4. Quick page checks without a phone (needs Node 18+)
cd tools/smoke && npm install
npm run load      # loads index.html, prints any script errors
npm run monkey    # taps every control with a fake Android bridge, prints the page error log
```

## Notes
- Gradle only reads `app/src/main/AndroidManifest.xml`.
- See `docs/` for per-version notes, `PHASE8_*` for Smart Modes and `docs/DEBUG_REPORT.md` for the latest debugging pass.
