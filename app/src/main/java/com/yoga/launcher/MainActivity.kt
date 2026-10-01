package com.yoga.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var wv: WebView
    private val prefs by lazy { getSharedPreferences("yoga", MODE_PRIVATE) }

    private val pkgRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val p = i.data?.schemeSpecificPart ?: return
            if (i.action == Intent.ACTION_PACKAGE_REMOVED) {
                if (!i.getBooleanExtra(Intent.EXTRA_REPLACING, false))
                    js("window.onPackageRemoved&&window.onPackageRemoved(${JSONObject.quote(p)})")
            } else {
                appJson(p)?.let { js("window.onPackageAdded&&window.onPackageAdded($it)") }
            }
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        wv = WebView(this)
        wv.setBackgroundColor(Color.BLACK)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.addJavascriptInterface(Bridge(), "Android")
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView?, u: String?) { pushState(); pushApps() }
        }
        setContentView(wv)
        wv.loadUrl("file:///android_asset/index.html")
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        }
        registerReceiver(pkgRx, f)
    }

    private fun js(code: String) = runOnUiThread { wv.evaluateJavascript(code, null) }

    private fun pushState() {
        prefs.getString("settings", null)?.let { js("window.setSettings&&window.setSettings($it)") }
        prefs.getString("theme", null)?.let { js("window.setTheme&&window.setTheme($it)") }
    }

    private fun iconData(d: Drawable): String {
        val s = 72
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, s, s); d.draw(Canvas(bmp))
        val o = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.PNG, 90, o)
        return "data:image/png;base64," + Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP)
    }

    private fun appJson(p: String): JSONObject? {
        if (p == packageName) return null
        val pm = packageManager
        val li = pm.getLaunchIntentForPackage(p) ?: return null
        val ri = pm.resolveActivity(li, 0) ?: return null
        return JSONObject().put("p", p).put("n", ri.loadLabel(pm).toString()).put("i", iconData(ri.loadIcon(pm)))
    }

    private fun pushApps() = thread {
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val arr = JSONArray()
        pm.queryIntentActivities(q, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
            .forEach { ri ->
                arr.put(JSONObject().put("p", ri.activityInfo.packageName)
                    .put("n", ri.loadLabel(pm).toString()).put("i", iconData(ri.loadIcon(pm))))
            }
        js("window.setApps&&window.setApps($arr)")
    }

    inner class Bridge {
        @JavascriptInterface fun launch(p: String) {
            runOnUiThread { packageManager.getLaunchIntentForPackage(p)?.let { startActivity(it) } }
        }
        @JavascriptInterface fun getShortcuts(p: String): String = try {
            val la = getSystemService(LauncherApps::class.java)
            val q = LauncherApps.ShortcutQuery().setPackage(p).setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            val arr = JSONArray()
            la.getShortcuts(q, Process.myUserHandle())?.take(4)?.forEach {
                arr.put(JSONObject().put("id", it.id).put("l", (it.shortLabel ?: it.id).toString()))
            }
            arr.toString()
        } catch (e: Exception) { "[]" }
        @JavascriptInterface fun launchShortcut(p: String, id: String) {
            try { getSystemService(LauncherApps::class.java).startShortcut(p, id, null, null, Process.myUserHandle()) }
            catch (e: Exception) { }
        }
        @JavascriptInterface fun search(q: String) {
            runOnUiThread { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))) }
        }
        @JavascriptInterface fun appInfo(p: String) {
            runOnUiThread { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$p"))) }
        }
        @JavascriptInterface fun uninstall(p: String) {
            runOnUiThread { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$p"))) }
        }
        @JavascriptInterface fun saveSettings(j: String) { prefs.edit().putString("settings", j).apply() }
        @JavascriptInterface fun saveTheme(j: String) { prefs.edit().putString("theme", j).apply() }
    }

    override fun onNewIntent(i: Intent) { super.onNewIntent(i); js("window.onHome&&window.onHome()") }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { js("window.onBack&&window.onBack()") }

    override fun onDestroy() { try { unregisterReceiver(pkgRx) } catch (e: Exception) { }; super.onDestroy() }
}
