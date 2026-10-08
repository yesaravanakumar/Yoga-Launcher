package com.yoga.launcher   // <- change to your app's package name

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.role.RoleManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.MediaStore
import android.app.KeyguardManager
import android.app.AlarmManager
import android.app.Notification
import android.app.SearchManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import java.io.ByteArrayOutputStream
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognizerIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.accessibility.AccessibilityEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Everything the launcher page calls through `Android.xxx(...)`. Register with
 *  webView.addJavascriptInterface(YogaBridge(this, webView), "Android") */
class YogaBridge(private val act: Activity, private val web: WebView) {

    private val prefs = act.getSharedPreferences("yoga", Context.MODE_PRIVATE)
    private val pm = act.packageManager
    private fun ui(block: () -> Unit) = act.runOnUiThread { try { block() } catch (_: Exception) {} }
    private fun js(code: String) = ui { web.evaluateJavascript(code, null) }

    // ---------- haptic detents (v1.48): real tick / click effects; returns false so the page can fall back to navigator.vibrate ----------
    private val hapticMotor: android.os.Vibrator? by lazy {
        try { act.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator } catch (_: Exception) { null }
    }
    /** kind: 0 = tick (light), 1 = click, 2 = heavy click (end stop). Needs Android 10+. */
    @JavascriptInterface fun haptic(kind: Int): Boolean {
        val v = hapticMotor ?: return false
        if (Build.VERSION.SDK_INT < 29 || !v.hasVibrator()) return false
        return try {
            val id = when (kind) {
                0 -> android.os.VibrationEffect.EFFECT_TICK
                2 -> android.os.VibrationEffect.EFFECT_HEAVY_CLICK
                else -> android.os.VibrationEffect.EFFECT_CLICK
            }
            v.vibrate(android.os.VibrationEffect.createPredefined(id))
            true
        } catch (_: Exception) { false }
    }

    // ---------- real Android widgets (hosted by YogaWidgets, set from MainActivity) ----------
    var widgets: YogaWidgets? = null
    @JavascriptInterface fun pickWidget() = ui { widgets?.pick() }
    @JavascriptInterface fun placeWidget(id: String, x: Int, y: Int, w: Int, h: Int) { widgets?.place(id, x, y, w, h) }
    @JavascriptInterface fun hideWidget(id: String) { widgets?.hide(id) }
    @JavascriptInterface fun removeWidget(id: String) { widgets?.remove(id) }

    // ---------- apps ----------
    @JavascriptInterface fun launch(pkg: String) = ui {
        pm.getLaunchIntentForPackage(pkg)?.let { act.startActivity(it) }
    }

    @JavascriptInterface fun appInfo(pkg: String) = ui {
        act.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
    }

    @JavascriptInterface fun uninstall(pkg: String) = ui {
        act.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
    }

    @JavascriptInterface fun getShortcuts(pkg: String): String = try {
        val la = act.getSystemService(LauncherApps::class.java)
        val q = LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        val out = JSONArray()
        la.getShortcuts(q, Process.myUserHandle())?.take(4)?.forEach {
            out.put(JSONObject().put("id", it.id).put("l", it.shortLabel?.toString() ?: it.id))
        }
        out.toString()
    } catch (e: Exception) { "[]" }   // only works once this app is the default Home app

    @JavascriptInterface fun launchShortcut(pkg: String, id: String) = ui {
        act.getSystemService(LauncherApps::class.java)
            .startShortcut(pkg, id, null, null, Process.myUserHandle())
    }

    /** Sends the installed-app list to the page (call after the page loads), then the real icons. */
    @Suppress("DEPRECATION")
    fun pushApps() {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val labelled = pm.queryIntentActivities(i, 0)
            .filter { it.activityInfo.packageName != act.packageName }
            .map { it to it.loadLabel(pm).toString() }
            .sortedBy { it.second.lowercase() }
        val infos = labelled.map { it.first }
        val list = JSONArray()
        labelled.forEach { (it, label) ->
            val pkg = it.activityInfo.packageName
            val o = JSONObject().put("n", label).put("p", pkg)
            val ai = it.activityInfo.applicationInfo
            val kind = when {
                (ai.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0 -> "Games"
                else -> when (ai.category) {
                    android.content.pm.ApplicationInfo.CATEGORY_GAME -> "Games"
                    android.content.pm.ApplicationInfo.CATEGORY_AUDIO,
                    android.content.pm.ApplicationInfo.CATEGORY_VIDEO,
                    android.content.pm.ApplicationInfo.CATEGORY_IMAGE -> "Media"
                    android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                    android.content.pm.ApplicationInfo.CATEGORY_NEWS -> "Read & Learn"
                    android.content.pm.ApplicationInfo.CATEGORY_MAPS -> "Travel"
                    android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Work"
                    else -> ""
                }
            }
            if (kind.isNotEmpty()) o.put("k", kind)
            iconCache[pkg]?.let { ic -> o.put("i", ic) }       // already rendered: send right away
            list.put(o)
        }
        js("window.setApps(${list})")
        pushIcons(infos.map { it.activityInfo.packageName to it })
    }

    // ---------- real app icons ----------
    private val iconCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    @Volatile private var iconJob: Thread? = null

    /** Renders each app's own launcher icon to a small PNG data-URI (off the UI thread)
     *  and sends them to the page in batches via window.setIconPack({pkg: dataUri}). */
    private fun pushIcons(infos: List<Pair<String, android.content.pm.ResolveInfo>>) {
        iconJob?.interrupt()
        val todo = infos.filter { !iconCache.containsKey(it.first) }
        if (todo.isEmpty()) return
        iconJob = Thread {
            try {
                var batch = JSONObject(); var n = 0
                for ((pkg, ri) in todo) {
                    if (Thread.currentThread().isInterrupted) return@Thread
                    val uri = try { toDataUri(ri.loadIcon(pm)) } catch (_: Throwable) { null } ?: continue
                    iconCache[pkg] = uri
                    batch.put(pkg, uri)
                    if (++n % 24 == 0) { val b = batch; batch = JSONObject(); js("window.setIconPack($b)") }
                }
                if (batch.length() > 0) js("window.setIconPack($batch)")
            } catch (_: InterruptedException) {}
        }.also { it.isDaemon = true; it.start() }
    }

    private fun toDataUri(d: Drawable, size: Int = 128): String {
        val bmp = if (d is BitmapDrawable && d.bitmap != null) Bitmap.createScaledBitmap(d.bitmap, size, size, true)
        else Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { b ->
            val c = Canvas(b); d.setBounds(0, 0, size, size); d.draw(c)   // adaptive icons: background + foreground layers
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    // ---------- links & search ----------
    @JavascriptInterface fun openUrl(url: String) = ui {
        val u = Uri.parse(url)
        when (u.scheme?.lowercase()) {
            "http", "https" -> act.startActivity(Intent(Intent.ACTION_VIEW, u))
            "tel" -> act.startActivity(Intent(Intent.ACTION_DIAL, u))
            "sms", "smsto" -> act.startActivity(Intent(Intent.ACTION_SENDTO, u))
            else -> {}
        }
    }

    @JavascriptInterface fun search(q: String) = ui {
        try {
            act.startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q))
        } catch (e: Exception) {
            openUrl("https://www.google.com/search?q=" + URLEncoder.encode(q, "UTF-8"))
        }
    }

    /** Non-blocking version: the network call runs on its own thread and the result is pushed back to window.__sug(id, [...]). */
    @JavascriptInterface fun suggestAsync(q: String, id: Int) {
        Thread {
            val r = suggest(q)
            js("window.__sug&&window.__sug($id,$r)")
        }.start()
    }

    @JavascriptInterface fun suggest(q: String): String = try {
        val c = URL("https://suggestqueries.google.com/complete/search?client=firefox&q=" +
            URLEncoder.encode(q, "UTF-8")).openConnection() as HttpURLConnection
        c.connectTimeout = 1500; c.readTimeout = 1500
        val arr = JSONArray(c.inputStream.bufferedReader().readText()).getJSONArray(1)
        c.disconnect(); arr.toString()
    } catch (e: Exception) { "[]" }

    // ---------- contacts & badges ----------
    /** Voice search through Android's own speech dialog (WebView has no SpeechRecognition). Result arrives in MainActivity.onActivityResult. */
    @JavascriptInterface fun startVoice() = ui {
        try {
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            act.startActivityForResult(i, 7203)
        } catch (e: Exception) {
            Toast.makeText(act, "Voice search isn't available on this phone", Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface fun hasContactsPerm(): Boolean =
        act.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Shows Android's Contacts permission dialog. MainActivity tells the page when it is granted (window.onContactsGranted). */
    @JavascriptInterface fun askContacts() = ui { act.requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 7) }

    /** Whole address book for the app drawer's Contacts group: [{"n":"Name","p":"number"}], one entry per person+number. */
    @JavascriptInterface fun getContacts(): String {
        val out = JSONArray()
        if (!hasContactsPerm()) return out.toString()
        try {
            act.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC")?.use { c ->
                val seen = HashSet<String>()
                while (c.moveToNext() && out.length() < 3000) {
                    val n = c.getString(0)?.trim() ?: continue
                    val p = c.getString(1)?.trim() ?: ""
                    if (n.isNotEmpty() && seen.add(n + "|" + p.filter { it.isDigit() || it == '+' })) out.put(JSONObject().put("n", n).put("p", p))
                }
            }
        } catch (_: Exception) {}
        return out.toString()
    }

    private var askedContacts = false
    @JavascriptInterface fun searchContacts(q: String): String {
        if (act.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            // ask once per session, not on every keystroke
            if (!askedContacts) { askedContacts = true; ui { act.requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 7) } }
            return "[]"
        }
        val out = JSONArray()
        try {
            act.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$q%"),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC")?.use { c ->
                val seen = HashSet<String>()
                while (c.moveToNext() && out.length() < 4) {
                    val n = c.getString(0) ?: continue
                    if (seen.add(n)) out.put(JSONObject().put("n", n).put("p", c.getString(1) ?: ""))
                }
            }
        } catch (_: Exception) {}
        return out.toString()
    }

    /** {"com.pkg": count}. Needs "Notification access" turned on for this app once. */
    // ---------- next alarm, next calendar event, notification access ----------

    @JavascriptInterface fun getNextAlarm(): String = try {
        val am = act.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        (am.nextAlarmClock?.triggerTime ?: 0L).toString()
    } catch (_: Exception) { "0" }

    @JavascriptInterface fun requestCalendar() = ui {
        act.requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 7203)
    }

    @JavascriptInterface fun getNextEvent(): String {
        if (act.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return "null"
        val now = System.currentTimeMillis()
        val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(b, now)
        ContentUris.appendId(b, now + 24 * 3600 * 1000L)
        val cols = arrayOf(
            CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY
        )
        try {
            act.contentResolver.query(b.build(), cols, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(3) == 1 || c.getLong(2) <= now) continue
                    return JSONObject().put("t", c.getString(0) ?: "")
                        .put("s", c.getLong(1)).put("d", c.getLong(2) - c.getLong(1)).toString()
                }
            }
        } catch (_: Exception) {}
        return "null"
    }

    @JavascriptInterface fun openNext(kind: String) = ui {
        val i = when (kind) {
            "alarm" -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            "event" -> Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
            else -> return@ui
        }
        act.startActivity(i)
    }

    @JavascriptInterface fun hasNotificationAccess(): Boolean {
        val s = Settings.Secure.getString(act.contentResolver, "enabled_notification_listeners") ?: return false
        return s.contains(act.packageName)
    }

    @JavascriptInterface fun requestNotificationAccess() = ui {
        act.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    @JavascriptInterface fun getNotifications(pkg: String): String {
        val a = JSONArray()
        BadgeService.notes[pkg].orEmpty().take(3).forEach {
            a.put(JSONObject().put("k", it.first).put("t", it.second).put("x", it.third))
        }
        return a.toString()
    }

    @JavascriptInterface fun openNotification(key: String) = ui {
        BadgeService.inst?.activeNotifications?.firstOrNull { it.key == key }?.notification?.contentIntent?.send()
    }

    /** Called when an app is updated or removed, so its icon is drawn again next time. */
    fun dropIcon(pkg: String) { iconCache.remove(pkg) }

    @JavascriptInterface fun getBadges(): String = JSONObject(BadgeService.counts as Map<*, *>).toString()

    // ---------- now playing (needs "Notification access" for this app, same as badges) ----------
    private fun mediaCtl(): android.media.session.MediaController? = try {
        val msm = act.getSystemService(Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
        val list = msm.getActiveSessions(android.content.ComponentName(act, BadgeService::class.java))
        // whatever is playing in any app wins; otherwise a paused/buffering one; otherwise the first with a title
        list.firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_BUFFERING }
            ?: list.firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PAUSED }
            ?: list.firstOrNull()
    } catch (_: Exception) {
        // access was given but Android has not (re)connected our listener: ask it to reconnect
        try {
            if (hasNotificationAccess() && BadgeService.inst == null)
                NotificationListenerService.requestRebind(android.content.ComponentName(act, BadgeService::class.java))
        } catch (_: Exception) {}
        null
    }

    private var artKey = ""
    private var artData = ""
    /** Small square cover as a data: URL (cached, so it is only encoded when the song changes). */
    private fun artFor(md: android.media.MediaMetadata, base: String): String {
        val bmp: Bitmap? = try {
            md.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
                ?: md.getBitmap(android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        } catch (_: Exception) { null }
        val key = base + "|" + (bmp != null)
        if (key == artKey) return artData
        artKey = key
        artData = ""
        if (bmp == null) return ""
        try {
            val side = minOf(bmp.width, bmp.height)
            val sq = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
            val small = Bitmap.createScaledBitmap(sq, 96, 96, true)
            val out = ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.JPEG, 80, out)
            artData = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {}
        return artData
    }

    /** {"title","artist","playing","pkg","pos","dur","art"} of the current song, or "null". */
    @JavascriptInterface fun getMedia(): String {
        return try {
            val c = mediaCtl() ?: return "null"
            val md = c.metadata ?: return "null"
            val title = md.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
                ?: md.getString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return "null"
            val artist = md.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
                ?: md.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: md.getString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
            val st = c.playbackState
            val playing = st?.state == android.media.session.PlaybackState.STATE_PLAYING
            var pos = st?.position ?: 0L
            if (st != null && playing) pos += ((android.os.SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
            val pkg = c.packageName ?: ""
            val o = JSONObject()
                .put("title", title)
                .put("artist", artist)
                .put("playing", playing)
                .put("pkg", pkg)
                .put("pos", pos)
                .put("dur", md.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION))
            val art = artFor(md, "$title|$artist|$pkg")
            if (art.isNotEmpty()) o.put("art", art)
            o.toString()
        } catch (_: Exception) { "null" }
    }

    private fun mediaKey(code: Int) {
        val am = act.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code))
    }

    /** cmd = "toggle", "next" or "prev". Falls back to the phone's media buttons if no session is found. */
    @JavascriptInterface fun mediaControl(cmd: String) {
        val code = when (cmd) {
            "toggle" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
            "prev" -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return
        }
        try {
            val c = mediaCtl()
            if (c == null) { mediaKey(code); return }
            val t = c.transportControls
            when (cmd) {
                "toggle" -> if (c.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING) t.pause() else t.play()
                "next" -> t.skipToNext()
                "prev" -> t.skipToPrevious()
            }
        } catch (_: Exception) {
            try { mediaKey(code) } catch (_: Exception) {}
        }
    }

    // ---------- saving & restoring ----------
    @JavascriptInterface fun saveSettings(json: String) { prefs.edit().putString("settings", json).apply() }
    @JavascriptInterface fun saveTheme(json: String) { prefs.edit().putString("theme", json).apply() }

    /** Re-applies saved settings/theme to the page (call after the page loads). */
    fun restore() {
        prefs.getString("settings", null)?.let { try { js("window.setSettings(${JSONObject(it)})") } catch (_: Exception) {} }
        prefs.getString("theme", null)?.let { try { js("window.setTheme(${JSONArray(it)})") } catch (_: Exception) {} }
    }

    // ---------- battery (real value: the WebView battery API goes stale while the screen is off) ----------
    /** {"p":57,"c":true,"f":false,"pl":true} = percent, charging, full, plugged in. "null" if unknown. */
    @JavascriptInterface fun getBattery(): String = try {
        val i = act.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lv = i?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val sc = i?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
        val st = i?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pl = i?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        if (lv < 0 || sc <= 0) "null"
        else JSONObject()
            .put("p", Math.round(lv * 100f / sc))
            .put("c", st == android.os.BatteryManager.BATTERY_STATUS_CHARGING)
            .put("f", st == android.os.BatteryManager.BATTERY_STATUS_FULL && pl != 0)
            .put("pl", pl != 0)
            .toString()
    } catch (_: Exception) { "null" }

    // ---------- Yoga lock screen above Android's own lock screen ----------
    /** Lets the launcher sit above Android's lock screen (so Yoga's lock screen is what you see first). */
    @JavascriptInterface fun setLockOver(on: Boolean) {
        prefs.edit().putBoolean("lockover", on).apply()
        ui { applyLockOver(act, on) }
    }

    @JavascriptInterface fun isKeyguardLocked(): Boolean = try {
        (act.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
    } catch (_: Exception) { false }

    /** Swipe-up on the Yoga lock screen: Android shows its PIN / pattern / fingerprint (or just unlocks if there is none). */
    @JavascriptInterface fun dismissKeyguard() = ui {
        val km = act.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isKeyguardLocked) { js("window.onKeyguardResult&&window.onKeyguardResult(true)"); return@ui }
        km.requestDismissKeyguard(act, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { js("window.onKeyguardResult&&window.onKeyguardResult(true)") }
            override fun onDismissCancelled() { js("window.onKeyguardResult&&window.onKeyguardResult(false)") }
            override fun onDismissError() { js("window.onKeyguardResult&&window.onKeyguardResult(false)") }
        })
    }

    // ---------- permissions & system actions (Settings > Permissions & system, and the gestures) ----------
    private fun granted(p: String) = act.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun isDefaultHome(): Boolean = try {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == act.packageName
    } catch (_: Exception) { false }

    /** {"home":true,"notif":false,...} for the Permissions & system list. */
    @JavascriptInterface fun getPerms(): String = JSONObject()
        .put("home", isDefaultHome())
        .put("notif", hasNotificationAccess())
        .put("access", LockService.inst != null)
        .put("location", granted(Manifest.permission.ACCESS_COARSE_LOCATION) || granted(Manifest.permission.ACCESS_FINE_LOCATION))
        .put("contacts", hasContactsPerm())
        .put("calendar", granted(Manifest.permission.READ_CALENDAR))
        .toString()

    private fun appDetails() = act.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + act.packageName)))

    /** Ask for a runtime permission. If Android will no longer show its dialog (denied twice), open this app's settings page instead. */
    private fun askRuntime(perms: Array<String>, code: Int) {
        val asked = prefs.getBoolean("asked$code", false)
        if (asked && perms.none { act.shouldShowRequestPermissionRationale(it) }) {
            Toast.makeText(act, "Open Permissions and allow it there", Toast.LENGTH_LONG).show()
            appDetails()
        } else {
            prefs.edit().putBoolean("asked$code", true).apply()
            act.requestPermissions(perms, code)
        }
    }

    private fun openHomeSettings() {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = act.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                act.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 7204)
                return
            }
        }
        try { act.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        catch (_: Exception) { act.startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    /** Every row in Permissions & system: ask if it is off, open the right settings page if it is already on. */
    @JavascriptInterface fun openPerm(k: String) = ui {
        val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        when (k) {
            "home" -> openHomeSettings()
            "notif" -> act.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            "access" -> act.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            "location" -> if (granted(coarse) || granted(fine)) appDetails() else askRuntime(arrayOf(coarse, fine), 7205)
            "contacts" -> if (hasContactsPerm()) appDetails() else askRuntime(arrayOf(Manifest.permission.READ_CONTACTS), 7)
            "calendar" -> if (granted(Manifest.permission.READ_CALENDAR)) appDetails() else askRuntime(arrayOf(Manifest.permission.READ_CALENDAR), 7203)
            else -> appDetails()
        }
    }

    private var torchOn = false

    /** Gesture actions: shade, qs, recents, shot (need the Accessibility service), torch, camera. Returns ok / on / off / need:access / fail. */
    @JavascriptInterface fun sysAction(name: String): String {
        fun glob(a: Int): String {
            val svc = LockService.inst ?: return "need:access"
            return if (svc.performGlobalAction(a)) "ok" else "fail"
        }
        return try {
            when (name) {
                "shade" -> glob(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                "qs" -> glob(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                "recents" -> glob(AccessibilityService.GLOBAL_ACTION_RECENTS)
                "shot" -> if (Build.VERSION.SDK_INT >= 28) glob(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) else "fail"
                "camera" -> { ui { act.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }; "ok" }
                "torch" -> {
                    val cm = act.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                    if (id == null) "fail" else { torchOn = !torchOn; cm.setTorchMode(id, torchOn); if (torchOn) "on" else "off" }
                }
                else -> "fail"
            }
        } catch (_: Exception) { "fail" }
    }

    // ---------- lock screen ----------
    @JavascriptInterface fun lockScreen() = ui {
        val svc = LockService.inst
        if (Build.VERSION.SDK_INT >= 28 && svc != null) svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        else {
            Toast.makeText(act, "Turn on \"Yoga Launcher lock\" in Accessibility", Toast.LENGTH_LONG).show()
            act.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}

/** Show this activity above the lock screen (or not). Safe on every Android version we support. */
@Suppress("DEPRECATION")
fun applyLockOver(a: Activity, on: Boolean) {
    if (Build.VERSION.SDK_INT >= 27) a.setShowWhenLocked(on)
    else if (on) a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
    else a.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
}

/** Counts active notifications per app (for the dots on icons). */
class BadgeService : NotificationListenerService() {
    companion object {
        @Volatile var counts: Map<String, Int> = emptyMap()
        @Volatile var notes: Map<String, List<Triple<String, String, String>>> = emptyMap()
        @Volatile var inst: BadgeService? = null
    }
    private fun refresh() {
        try {
            val list = activeNotifications
                .filter { !it.isOngoing && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            counts = list.groupingBy { it.packageName }.eachCount()
            notes = list.groupBy({ it.packageName }) {
                val ex = it.notification.extras
                Triple(
                    it.key,
                    ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                    ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                )
            }
        } catch (_: Exception) {
            counts = emptyMap()
            notes = emptyMap()
        }
    }
    override fun onListenerConnected() { inst = this; refresh() }
    override fun onListenerDisconnected() { if (inst === this) inst = null }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()
}

/** Lets the "lock screen" gesture lock the phone (Android 9+). */
class LockService : AccessibilityService() {
    companion object { @Volatile var inst: LockService? = null }
    override fun onServiceConnected() { inst = this }
    override fun onUnbind(intent: Intent?): Boolean { inst = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
