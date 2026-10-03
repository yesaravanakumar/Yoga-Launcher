package com.yoga.launcher   // <- change to your app's package name

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.ContactsContract
import android.provider.Settings
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

    /** Sends the installed-app list to the page (call after the page loads). */
    fun pushApps() {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = JSONArray()
        pm.queryIntentActivities(i, 0)
            .filter { it.activityInfo.packageName != act.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
            .forEach { list.put(JSONObject().put("n", it.loadLabel(pm).toString()).put("p", it.activityInfo.packageName)) }
        js("window.setApps(${list})")
    }

    // ---------- links & search ----------
    @JavascriptInterface fun openUrl(url: String) = ui {
        val u = Uri.parse(url)
        when (u.scheme?.lowercase()) {
            "http", "https" -> act.startActivity(Intent(Intent.ACTION_VIEW, u))
            "tel" -> act.startActivity(Intent(Intent.ACTION_DIAL, u))
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

    @JavascriptInterface fun suggest(q: String): String = try {
        val c = URL("https://suggestqueries.google.com/complete/search?client=firefox&q=" +
            URLEncoder.encode(q, "UTF-8")).openConnection() as HttpURLConnection
        c.connectTimeout = 1500; c.readTimeout = 1500
        val arr = JSONArray(c.inputStream.bufferedReader().readText()).getJSONArray(1)
        c.disconnect(); arr.toString()
    } catch (e: Exception) { "[]" }

    // ---------- contacts & badges ----------
    @JavascriptInterface fun searchContacts(q: String): String {
        if (act.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ui { act.requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 7) }
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
    @JavascriptInterface fun getBadges(): String = JSONObject(BadgeService.counts as Map<*, *>).toString()

    // ---------- saving & restoring ----------
    @JavascriptInterface fun saveSettings(json: String) { prefs.edit().putString("settings", json).apply() }
    @JavascriptInterface fun saveTheme(json: String) { prefs.edit().putString("theme", json).apply() }

    /** Re-applies saved settings/theme to the page (call after the page loads). */
    fun restore() {
        prefs.getString("settings", null)?.let { try { js("window.setSettings(${JSONObject(it)})") } catch (_: Exception) {} }
        prefs.getString("theme", null)?.let { try { js("window.setTheme(${JSONArray(it)})") } catch (_: Exception) {} }
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

/** Counts active notifications per app (for the dots on icons). */
class BadgeService : NotificationListenerService() {
    companion object { @Volatile var counts: Map<String, Int> = emptyMap() }
    private fun refresh() {
        counts = try { activeNotifications.groupingBy { it.packageName }.eachCount() } catch (_: Exception) { emptyMap() }
    }
    override fun onListenerConnected() = refresh()
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
