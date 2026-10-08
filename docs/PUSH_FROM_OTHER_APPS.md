# Sending cards from other apps

```kotlin
sendBroadcast(Intent("com.yoga.launcher.PUSH").setPackage("com.yoga.launcher")
    .putExtra("id", "water").putExtra("title", "Water")
    .putExtra("value", "5 / 8").putExtra("sub", "glasses today"))
```

Add `.putExtra("remove", true)` to delete the card.

By default only apps signed with your own key can send. To let Tasker or other apps send,
change `protectionLevel="signature"` to `"normal"` in `AndroidManifest.xml`. Then any app
installed on the phone can post a text card.

Cards are kept in memory, so they disappear when the launcher restarts. Resend on a schedule.
