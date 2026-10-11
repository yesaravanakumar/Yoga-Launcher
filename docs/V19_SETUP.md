# v19 native setup

Copy `index.html` and `YogaNativeV19.kt` into the project. Change the package line in the Kotlin file.

## AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.READ_CALENDAR" />

<service
    android:name=".YogaNotificationListener"
    android:exported="true"
    android:label="Yoga Launcher"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

## MainActivity

```kotlin
private lateinit var native: YogaNativeV19

// after the WebView is created; `container` is the FrameLayout that holds it
native = YogaNativeV19(this, webView, container)
webView.addJavascriptInterface(native, "YogaNative")   // keep your existing "Android" interface as it is

override fun onStart() { super.onStart(); native.onStart() }
override fun onStop() { super.onStop(); native.onStop() }

override fun onActivityResult(req: Int, res: Int, data: Intent?) {
    if (!native.onActivityResult(req, res, data)) super.onActivityResult(req, res, data)
}
```

## First run on the phone

1. Settings, At a glance & notifications, tap **Notification access** and switch Yoga Launcher on.
2. Tap **Calendar access** and allow it.
3. Widgets page, **Add widget**.

## JavaScript hooks the page exposes

Native code can push data at any time. The page also polls the matching `get...` methods every 30 seconds.

| Call | Value |
|---|---|
| `setBadges({pkg: count})` | unread counts |
| `setNotifications(pkg, [{t, x, k}])` | preview lines |
| `setMedia({title, artist, playing, pkg, pos, dur})` or `null` | now playing |
| `setNextAlarm(ms)` | next alarm time |
| `setNextEvent({t, s, d})` or `null` | next event |
| `setCommute({dest, min, mode})` or `null` | commute |
| `onWidgetAdded({id, label})` / `onWidgetRemoved(id)` | widget list |
