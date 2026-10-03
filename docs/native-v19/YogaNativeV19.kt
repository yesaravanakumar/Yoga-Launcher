package com.yoga.launcher // change to your package name

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.CalendarContract
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import org.json.JSONArray
import org.json.JSONObject

/* ---------- shared state, filled by the notification listener ---------- */

data class Note(val key: String, val title: String, val text: String)

object NotifStore {
    @Volatile var counts: Map<String, Int> = emptyMap()
    @Volatile var notes: Map<String, List<Note>> = emptyMap()
    @Volatile var media: JSONObject? = null
    @Volatile var controller: MediaController? = null
    @Volatile var onChange: (() -> Unit)? = null
    @Volatile var service: NotificationListenerService? = null
}

class YogaNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() { NotifStore.service = this; refresh() }
    override fun onListenerDisconnected() { if (NotifStore.service === this) NotifStore.service = null }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val list: Array<StatusBarNotification> = try { activeNotifications ?: return } catch (e: Exception) { return }
        val counts = HashMap<String, Int>()
        val notes = HashMap<String, MutableList<Note>>()
        for (sbn in list) {
            val n = sbn.notification
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            if (sbn.isOngoing) continue
            if (sbn.packageName == packageName) continue
            val ex = n.extras
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = (ex.getCharSequence(Notification.EXTRA_TEXT)
                ?: ex.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
            counts[sbn.packageName] = (counts[sbn.packageName] ?: 0) + 1
            notes.getOrPut(sbn.packageName) { mutableListOf() }.add(Note(sbn.key, title, text))
        }
        NotifStore.counts = counts
        NotifStore.notes = notes
        NotifStore.media = pickMedia()
        NotifStore.onChange?.invoke()
    }

    private fun pickMedia(): JSONObject? = try {
        val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val sessions = msm.getActiveSessions(ComponentName(this, YogaNotificationListener::class.java))
        val c = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull()
        NotifStore.controller = c
        val md = c?.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (c == null || md == null || title.isNullOrEmpty()) null else JSONObject().apply {
            put("title", title)
            put("artist", md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "")
            put("playing", c.playbackState?.state == PlaybackState.STATE_PLAYING)
            put("pkg", c.packageName)
            put("pos", c.playbackState?.position ?: 0L)
            put("dur", md.getLong(MediaMetadata.METADATA_KEY_DURATION))
        }
    } catch (e: Exception) { null }
}

/* ---------- JavaScript interface, registered as "YogaNative" ---------- */

class YogaNativeV19(
    private val act: Activity,
    private val web: WebView,
    private val overlay: FrameLayout // the FrameLayout that holds the WebView
) {
    companion object { const val REQ_PICK = 7101; const val REQ_CONFIG = 7102; const val REQ_CAL = 7103 }

    private val host = AppWidgetHost(act, 1024)
    private val mgr = AppWidgetManager.getInstance(act)
    private val views = HashMap<Int, AppWidgetHostView>()
    private val density get() = act.resources.displayMetrics.density

    init { NotifStore.onChange = { push() } }

    private fun js(code: String) = web.post { web.evaluateJavascript(code, null) }
    private fun ui(block: () -> Unit) = act.runOnUiThread(block)

    private fun push() {
        js("window.setBadges&&setBadges(${badgesJson()})")
        js("window.setMedia&&setMedia(${NotifStore.media ?: "null"})")
    }

    private fun badgesJson() = JSONObject().also { o -> NotifStore.counts.forEach { (k, v) -> o.put(k, v) } }.toString()

    /* lifecycle: call these from your Activity */
    fun onStart() { try { host.startListening() } catch (_: Exception) {} }
    fun onStop() { try { host.stopListening() } catch (_: Exception) {} }

    /* ---------- notifications ---------- */

    @JavascriptInterface fun hasNotificationAccess(): Boolean {
        val s = Settings.Secure.getString(act.contentResolver, "enabled_notification_listeners") ?: return false
        return s.contains(act.packageName)
    }

    @JavascriptInterface fun requestNotificationAccess() {
        act.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    @JavascriptInterface fun getBadges(): String = badgesJson().toString()

    @JavascriptInterface fun getNotifications(pkg: String): String {
        val a = JSONArray()
        NotifStore.notes[pkg].orEmpty().take(3).forEach {
            a.put(JSONObject().put("k", it.key).put("t", it.title).put("x", it.text))
        }
        return a.toString()
    }

    @JavascriptInterface fun openNotification(key: String) {
        val n = NotifStore.service?.activeNotifications?.firstOrNull { it.key == key } ?: return
        try { n.notification.contentIntent?.send() } catch (_: Exception) {}
    }

    /* ---------- media ---------- */

    @JavascriptInterface fun getMedia(): String = NotifStore.media?.toString() ?: "null"

    @JavascriptInterface fun mediaControl(cmd: String) {
        val t = NotifStore.controller?.transportControls ?: return
        when (cmd) {
            "prev" -> t.skipToPrevious()
            "next" -> t.skipToNext()
            "toggle" -> if (NotifStore.controller?.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
        }
    }

    /* ---------- alarm, calendar ---------- */

    @JavascriptInterface fun getNextAlarm(): String {
        val am = act.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return (am.nextAlarmClock?.triggerTime ?: 0L).toString()
    }

    @JavascriptInterface fun requestCalendar() {
        act.requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), REQ_CAL)
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
            act.contentResolver.query(b.build(), cols, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(3) == 1 || c.getLong(2) <= now) continue
                    return JSONObject().put("t", c.getString(0) ?: "")
                        .put("s", c.getLong(1)).put("d", c.getLong(2) - c.getLong(1)).toString()
                }
            }
        } catch (_: Exception) {}
        return "null"
    }

    @JavascriptInterface fun openNext(kind: String) {
        val i = when (kind) {
            "alarm" -> Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
            "event" -> Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
            else -> return
        }
        try { act.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }

    /* ---------- Android widgets ---------- */

    @JavascriptInterface fun pickWidget() = ui {
        val id = host.allocateAppWidgetId()
        val i = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        act.startActivityForResult(i, REQ_PICK)
    }

    /** Call from Activity.onActivityResult. Returns true when the result belonged to widgets. */
    fun onActivityResult(req: Int, res: Int, data: Intent?): Boolean {
        if (req != REQ_PICK && req != REQ_CONFIG) return false
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        if (res != Activity.RESULT_OK || id == -1) { if (id != -1) host.deleteAppWidgetId(id); return true }
        val info = mgr.getAppWidgetInfo(id)
        if (info == null) { host.deleteAppWidgetId(id); js("toast&&toast('That widget could not be added')"); return true }
        if (req == REQ_PICK && info.configure != null) {
            host.startAppWidgetConfigureActivityForResult(act, id, 0, REQ_CONFIG, null)
        } else {
            val label = info.loadLabel(act.packageManager)
            js("window.onWidgetAdded&&onWidgetAdded({id:$id,label:${JSONObject.quote(label)}})")
        }
        return true
    }

    @JavascriptInterface fun placeWidget(idStr: String, x: Int, y: Int, w: Int, h: Int) = ui {
        val id = idStr.toIntOrNull() ?: return@ui
        val info = mgr.getAppWidgetInfo(id)
        if (info == null) { host.deleteAppWidgetId(id); js("window.onWidgetRemoved&&onWidgetRemoved($id)"); return@ui }
        val v = views.getOrPut(id) { host.createView(act.applicationContext, id, info).also { overlay.addView(it) } }
        val d = density
        val lp = FrameLayout.LayoutParams((w * d).toInt(), (h * d).toInt())
        lp.leftMargin = (x * d).toInt() + web.left
        lp.topMargin = (y * d).toInt() + web.top
        v.layoutParams = lp
        v.updateAppWidgetSize(null, w, h, w, h)
        v.visibility = View.VISIBLE
    }

    @JavascriptInterface fun hideWidget(idStr: String) = ui {
        views[idStr.toIntOrNull() ?: return@ui]?.visibility = View.GONE
    }

    @JavascriptInterface fun removeWidget(idStr: String) = ui {
        val id = idStr.toIntOrNull() ?: return@ui
        views.remove(id)?.let { overlay.removeView(it) }
        host.deleteAppWidgetId(id)
    }
}
