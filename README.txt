YOGA LAUNCHER - get it on your Android phone (no coding)

A) Build the APK online (free, easiest)
1. Make a free account at github.com and create a new repository (any name).
2. Upload everything in this folder to it, keeping the folders (including the hidden ".github" folder).
3. Open the repository's "Actions" tab, choose "Build APK", press "Run workflow".
4. When it finishes (about 5 minutes), open the run and download "yoga-launcher-apk". Unzip it to get app-debug.apk.

B) Install on the phone
1. Copy app-debug.apk to the phone and open it. Allow "install unknown apps" when asked.
2. Press the Home button, choose "Yoga Launcher", then "Always".
3. To go back to your old launcher: Settings > Apps > Default apps > Home app.

To change the launcher later, replace app/src/main/assets/index.html with the new file and build again.
