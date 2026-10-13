# Publishing Yoga Launcher to Google Play

## What the build does now
- `targetSdk` and `compileSdk` are **36** (Play requires 36 for new apps and updates since 31 Aug 2026). AGP 8.9.1, Gradle 8.11.1.
- Back is already routed through `OnBackInvokedCallback` and edge-to-edge is already on, which are the two API 36 behaviour changes that matter here.
- Pushing a tag such as `v1.62.3` builds a **signed release APK and AAB** and creates a GitHub Release with both files.
- Pushes to `main` still build the debug APK. If the signing secrets are not set, only the debug APK is built.

## One-time setup
1. Make a keystore (keep it and its passwords safe; losing it means you cannot update the app unless you use Play App Signing):
   `keytool -genkeypair -v -keystore release.jks -alias yoga -keyalg RSA -keysize 2048 -validity 10000`
2. Add these GitHub repository secrets (Settings > Secrets and variables > Actions):
   - `YOGA_KEYSTORE_BASE64` : output of `base64 -w0 release.jks`
   - `YOGA_KEYSTORE_PASSWORD`
   - `YOGA_KEY_ALIAS` (e.g. `yoga`)
   - `YOGA_KEY_PASSWORD` (optional, defaults to the keystore password)
3. Release: `git tag v1.62.3 && git push origin v1.62.3`. Upload the `.aab` to Play Console (Play needs the AAB, not the APK).

## Play Console declarations to prepare
- **QUERY_ALL_PACKAGES**: needs the "core functionality" justification (a launcher must list every installed app). Launchers are a permitted use.
- **Accessibility service (LockService)**: Play requires an Accessibility API declaration and a short video showing why it is needed (screen lock). If it can be avoided, removing it makes review much easier.
- **Notification access (BadgeService)**, **READ/WRITE_CONTACTS**, **READ_CALENDAR**, **location**, **REQUEST_DELETE_PACKAGES**: each must be explained in the Data safety form and match what the app really does.
- **Privacy policy**: a public URL is required, covering location, contacts, calendar and notification access.
- Data safety form, content rating, and an app category (Personalization) are also needed.

## Not verified
The AGP / Gradle / SDK 36 bump could not be built here (no network). Run the workflow once on `main` and check it is green before tagging.
