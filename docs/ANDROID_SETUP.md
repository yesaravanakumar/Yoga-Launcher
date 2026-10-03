# Yoga Launcher: Android setup (3 steps)

## 1. Add the files
- Copy `YogaBridge.kt` into `app/src/main/java/<your package>/` and change the first line (`package ...`) to match.
- Put `yoga-launcher-v18_metal.html` in `app/src/main/assets/` and rename it `index.html`.
- Create `app/src/main/res/xml/lock_service.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/lock_desc"
    android:canRetrieveWindowContent="false" />
```
- In `res/values/strings.xml` add: `<string name="lock_desc">Lets the launcher lock the screen with a double tap.</string>`

## 2. AndroidManifest.xml
Inside `<manifest>` (before `<application>`):
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent>
</queries>
```
Inside `<application>`:
```xml
<service android:name=".BadgeService"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    android:exported="true">
    <intent-filter><action android:name="android.service.notification.NotificationListenerService" /></intent-filter>
</service>
<service android:name=".LockService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="true">
    <intent-filter><action android:name="android.accessibilityservice.AccessibilityService" /></intent-filter>
    <meta-data android:name="android.accessibilityservice" android:resource="@xml/lock_service" />
</service>
```
To make it your Home app, the launcher activity also needs:
```xml
<intent-filter>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.HOME" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.LAUNCHER" />
</intent-filter>
```

## 3. MainActivity
```kotlin
val web = WebView(this)
setContentView(web)
web.settings.javaScriptEnabled = true
web.settings.domStorageEnabled = true
web.settings.textZoom = 100   // stops the phone's "font size" setting from stretching the clock digits
val bridge = YogaBridge(this, web)
web.addJavascriptInterface(bridge, "Android")
web.webViewClient = object : WebViewClient() {
    override fun onPageFinished(v: WebView, url: String) { bridge.pushApps(); bridge.restore() }
}
web.loadUrl("file:///android_asset/index.html")
```

## One-time steps on the phone
- **Notification dots:** Settings > Notifications > Notification access > turn on the launcher.
- **Lock screen gesture:** Settings > Accessibility > turn on the launcher's lock service.
- **App shortcuts (long-press menu):** set the launcher as the default Home app.
- **Contacts in search:** tap Allow when asked.

## What each function does
| Page calls | Result |
|---|---|
| launch(pkg), appInfo(pkg), uninstall(pkg) | opens the app, its info page, or the uninstall prompt |
| getShortcuts(pkg), launchShortcut(pkg,id) | long-press shortcuts (Home app only) |
| openUrl(url), search(q) | opens http(s)/tel links, web search |
| searchContacts(q), suggest(q) | up to 4 contacts, up to 4 suggestions |
| getBadges() | notification counts per app |
| saveSettings(json), saveTheme(json) | stored on the phone, restored on next start |
| lockScreen() | locks the phone (Android 9+) |
