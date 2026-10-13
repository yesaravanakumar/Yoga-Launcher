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
    private fun ui(block: () -> Unit) = act.runOnUiThread {
        try { block() } catch (e: Exception) { android.util.Log.w("YogaBridge", "bridge call failed", e) }   // never crash the launcher, but leave the reason in Logcat
    }
    private fun js(code: String) = ui { web.evaluateJavascript(code, null) }

    /** Runs [block] on the UI thread and returns its result to the page (false/default if it throws or takes > 1.5 s). */
    private fun <T> uiResult(def: T, block: () -> T): T {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            return try { block() } catch (e: Exception) { android.util.Log.w("YogaBridge", "bridge call failed", e); def }
        }
        val box = arrayOfNulls<Any>(1)
        val latch = java.util.concurrent.CountDownLatch(1)
        act.runOnUiThread {
            try { box[0] = block() } catch (e: Exception) { android.util.Log.w("YogaBridge", "bridge call failed", e) } finally { latch.countDown() }
        }
        latch.await(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
        @Suppress("UNCHECKED_CAST") return (box[0] as T?) ?: def
    }
    private fun toast(msg: String) = Toast.makeText(act, msg, Toast.LENGTH_SHORT).show()

    /** The page sends its error log lines here so they also show up in Logcat. */
    @JavascriptInterface fun logError(msg: String) { android.util.Log.e("YogaPage", msg.take(300)) }

    // ---------- haptic detents (v1.48): real tick / click effects; returns false so the page can fall back to navigator.vibrate ----------
    private val hapticMotor: android.os.Vibrator? by lazy {
        try { act.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator } catch (_: Exception) { null }
    }
    /** kind: 0 = tick (light), 1 = click, 2 = heavy click (end stop).
     *  Android 10+ uses the system's predefined effects; Android 8-9 a short one-shot.
     *  If the phone's "touch feedback" switch is off we stay quiet but still answer true, so the page does not fall back to navigator.vibrate. */
    @JavascriptInterface fun haptic(kind: Int): Boolean {
        val v = hapticMotor ?: return false
        if (!v.hasVibrator()) return false
        if (!touchFeedbackOn()) return true
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val id = when (kind) {
                    0 -> android.os.VibrationEffect.EFFECT_TICK
                    2 -> android.os.VibrationEffect.EFFECT_HEAVY_CLICK
                    else -> android.os.VibrationEffect.EFFECT_CLICK
                }
                v.vibrate(android.os.VibrationEffect.createPredefined(id))
            } else {
                val ms = longArrayOf(6L, 10L, 22L)[kind.coerceIn(0, 2)]
                v.vibrate(android.os.VibrationEffect.createOneShot(ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            }
            true
        } catch (_: Exception) { false }
    }

    private fun touchFeedbackOn(): Boolean = try {
        Settings.System.getInt(act.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
    } catch (_: Exception) { true }

    // ---------- v1.51 platform hooks (docs/V1.51_NOTES.md). Every one is optional for the page: it checks typeof first. ----------
    @JavascriptInterface fun isDefaultLauncher(): Boolean = isDefaultHome()
    @JavascriptInterface fun requestDefaultLauncher() = ui { openHomeSettings() }
    @JavascriptInterface fun isPowerSave(): Boolean = try {
        (act.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isPowerSaveMode
    } catch (_: Exception) { false }
    /** true when the user turned "Remove animations" on (animator scale 0) */
    @JavascriptInterface fun getReduceMotion(): Boolean = try {
        Settings.Global.getFloat(act.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (_: Exception) { false }

    /** Android 10+ gesture navigation steals edge swipes. The page lists the rects (CSS px) that must keep their touches, e.g. the A-Z rail. */
    @JavascriptInterface fun setGestureExclusion(json: String) {
        if (Build.VERSION.SDK_INT < 29) return
        val d = act.resources.displayMetrics.density
        val rects = ArrayList<android.graphics.Rect>()
        try {
            val a = JSONArray(json)
            for (i in 0 until minOf(a.length(), 16)) {
                val r = a.getJSONArray(i)
                rects.add(android.graphics.Rect((r.getInt(0) * d).toInt(), (r.getInt(1) * d).toInt(), (r.getInt(2) * d).toInt(), (r.getInt(3) * d).toInt()))
            }
        } catch (_: Exception) { return }
        ui { web.systemGestureExclusionRects = rects }
    }

    /** Light page theme -> dark status / navigation bar icons (and back). Android 11+; older versions keep the black bars. */
    @JavascriptInterface fun setBarsLight(light: Boolean) = ui {
        if (Build.VERSION.SDK_INT >= 30) {
            val m = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            act.window.insetsController?.setSystemBarsAppearance(if (light) m else 0, m)
        }
    }

    // ---------- real Android widgets (hosted by YogaWidgets, set from MainActivity) ----------
    var widgets: YogaWidgets? = null
    @JavascriptInterface fun pickWidget() = ui { widgets?.pick() }
    @JavascriptInterface fun placeWidget(id: String, x: Int, y: Int, w: Int, h: Int) { widgets?.place(id, x, y, w, h) }
    @JavascriptInterface fun hideWidget(id: String) { widgets?.hide(id) }
    @JavascriptInterface fun removeWidget(id: String) { widgets?.remove(id) }

    // ---------- apps ----------
    /** true if the app was started; otherwise a toast tells the user why and the page gets false. */
    @JavascriptInterface fun launch(pkg: String): Boolean = uiResult(false) {
        val i = pm.getLaunchIntentForPackage(pkg)
        if (i == null) { toast("That app can't be opened (removed or disabled)"); false }
        else try { act.startActivity(i); true } catch (e: Exception) { toast("Could not open that app"); false }
    }

    @JavascriptInterface fun appInfo(pkg: String): Boolean = uiResult(false) {
        try { act.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))); true }
        catch (e: Exception) { toast("Could not open app info"); false }
    }

    @JavascriptInterface fun uninstall(pkg: String): Boolean = uiResult(false) {
        val ai = try { pm.getApplicationInfo(pkg, 0) } catch (e: Exception) { null }
        if (ai == null) { toast("That app is not installed"); return@uiResult false }
        val system = (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 &&
            (ai.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
        if (system) { toast("This is a system app. Android doesn't allow uninstalling it"); return@uiResult false }
        try { act.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))); true }
        catch (e: Exception) { toast("Could not start the uninstall"); false }
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

    @JavascriptInterface fun launchShortcut(pkg: String, id: String): Boolean = uiResult(false) {
        try {
            act.getSystemService(LauncherApps::class.java).startShortcut(pkg, id, null, null, Process.myUserHandle()); true
        } catch (e: Exception) { toast("That shortcut isn't available. Make Yoga Launcher your default Home app"); false }
    }

    /** One background thread for the app scan. It used to run on the UI thread inside onPageFinished
     *  (queryIntentActivities + loadLabel for every installed app), which delays the first touch on slow phones. */
    private val appScan = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "yoga-appscan").also { it.isDaemon = true } }

    /** Sends the installed-app list to the page (call after the page loads), then the real icons.
     *  [after] runs on the same thread right after the list is sent, so "apps first, then saved settings" keeps its order. */
    fun pushApps(after: (() -> Unit)? = null) {
        try {
            appScan.execute {
                try { pushAppsNow() } catch (e: Exception) { android.util.Log.w("YogaBridge", "app scan failed", e) }
                try { after?.invoke() } catch (e: Exception) { android.util.Log.w("YogaBridge", "after-scan step failed", e) }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {}
    }

    @Suppress("DEPRECATION")
    private fun pushAppsNow() {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val labelled = pm.queryIntentActivities(i, 0)
            .filter { it.activityInfo.packageName != act.packageName }
            .distinctBy { it.activityInfo.packageName }      // apps with several launcher entries would otherwise show twice
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
        if (activePack.isNotEmpty()) runPack(activePack)   // the list above carries the apps' own icons; draw the pack's again
    }

    // ---------- real app icons ----------
    private companion object { const val ICON_PX = 96; const val ICON_QUALITY = 82 }
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
                if (activePack.isNotEmpty()) runPack(activePack)
            } catch (_: InterruptedException) {}
        }.also { it.isDaemon = true; it.start() }
    }

    // ---------- installed icon packs (ADW / Nova / Apex style, appfilter.xml) ----------
    @Volatile private var activePack: String = ""
    @Volatile private var packJob: Thread? = null

    /** Icon packs installed on the phone, as [{"p":package,"n":label}]. */
    @Suppress("DEPRECATION")
    @JavascriptInterface fun getIconPacks(): String = try {
        val seen = LinkedHashMap<String, String>()
        val probes = listOf(
            Intent("org.adw.launcher.THEMES"), Intent("com.novalauncher.THEME"), Intent("com.anddoes.launcher.THEME"),
            Intent(Intent.ACTION_MAIN).addCategory("com.anddoes.launcher.THEME"))
        for (i in probes) for (ri in pm.queryIntentActivities(i, 0)) {
            val p = ri.activityInfo.packageName
            if (p != act.packageName) seen.putIfAbsent(p, ri.loadLabel(pm).toString())
        }
        val a = JSONArray(); seen.forEach { (p, n) -> a.put(JSONObject().put("p", p).put("n", n)) }; a.toString()
    } catch (_: Throwable) { "[]" }

    /** Uses [pkg]'s icons where the pack has one; other apps keep their own. Empty string switches back to the apps' own icons. */
    @JavascriptInterface fun applyIconPack(pkg: String) { activePack = pkg; runPack(pkg) }

    private fun sendIcons(m: Map<String, String>) {
        var b = JSONObject(); var n = 0
        for ((k, v) in m) { b.put(k, v); if (++n % 24 == 0) { val x = b; b = JSONObject(); js("window.setIconPack($x)") } }
        if (b.length() > 0) js("window.setIconPack($b)")
    }

    @Suppress("DEPRECATION")
    private fun runPack(pkg: String) {
        packJob?.interrupt()
        packJob = Thread {
            try {
                if (pkg.isEmpty()) { sendIcons(HashMap(iconCache)); return@Thread }
                val res = pm.getResourcesForApplication(pkg)
                val byComp = HashMap<String, String>(); val byPkg = HashMap<String, String>()
                parseAppFilter(pkg, res, byComp, byPkg)
                if (byComp.isEmpty() && byPkg.isEmpty()) return@Thread
                val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val byApp = pm.queryIntentActivities(launcher, 0).filter { it.activityInfo.packageName != act.packageName }
                    .groupBy { it.activityInfo.packageName }
                val out = LinkedHashMap<String, String>()
                for ((app, list) in byApp) {
                    if (Thread.currentThread().isInterrupted) return@Thread
                    val name = list.firstNotNullOfOrNull { byComp[app + "/" + it.activityInfo.name] } ?: byPkg[app]
                    val uri = name?.let { n ->
                        try {
                            var id = res.getIdentifier(n, "drawable", pkg)
                            if (id == 0) id = res.getIdentifier(n, "mipmap", pkg)
                            if (id != 0) toDataUri(res.getDrawable(id, null)) else null
                        } catch (_: Throwable) { null }
                    }
                    (uri ?: iconCache[app])?.let { out[app] = it }
                }
                if (activePack == pkg) sendIcons(out)
            } catch (_: InterruptedException) {
            } catch (e: Throwable) { android.util.Log.w("YogaBridge", "icon pack failed", e) }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun parseAppFilter(pkg: String, res: android.content.res.Resources, byComp: MutableMap<String, String>, byPkg: MutableMap<String, String>) {
        val xp: org.xmlpull.v1.XmlPullParser = try {
            val id = res.getIdentifier("appfilter", "xml", pkg)
            if (id != 0) res.getXml(id) else throw IllegalStateException()
        } catch (_: Throwable) {
            try { org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser().also { it.setInput(res.assets.open("appfilter.xml"), null) } }
            catch (_: Throwable) { return }
        }
        val re = Regex("ComponentInfo\\{([^/}]+)/([^}]*)\\}")
        var ev = xp.eventType
        while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (ev == org.xmlpull.v1.XmlPullParser.START_TAG && xp.name == "item") {
                val comp = xp.getAttributeValue(null, "component"); val dr = xp.getAttributeValue(null, "drawable")
                val m = if (comp != null && !dr.isNullOrEmpty()) re.find(comp) else null
                if (m != null) {
                    val ap = m.groupValues[1]; var cls = m.groupValues[2]
                    if (cls.startsWith(".")) cls = ap + cls
                    byComp.putIfAbsent("$ap/$cls", dr!!); byPkg.putIfAbsent(ap, dr)
                }
            }
            ev = try { xp.next() } catch (_: Throwable) { break }
        }
    }

    /** 96 px WebP instead of the old 128 px PNG: a smaller base64 payload for the page to decode. */
    @Suppress("DEPRECATION")
    private fun toDataUri(d: Drawable, size: Int = ICON_PX): String {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, size, size); d.draw(c)   // works for plain and adaptive icons (background + foreground layers)
        val out = ByteArrayOutputStream()
        val fmt = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        bmp.compress(fmt, ICON_QUALITY, out)
        bmp.recycle()
        return "data:image/webp;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    // ---------- links & search ----------
    @JavascriptInterface fun openUrl(url: String): Boolean = uiResult(false) {
        val u = Uri.parse(url)
        val i = when (u.scheme?.lowercase()) {
            "http", "https" -> Intent(Intent.ACTION_VIEW, u)
            "tel" -> Intent(Intent.ACTION_DIAL, u)
            "sms", "smsto" -> Intent(Intent.ACTION_SENDTO, u)
            else -> { toast("Can't open that kind of link"); return@uiResult false }
        }
        try { act.startActivity(i); true } catch (e: Exception) { toast("No app can open that link"); false }
    }

    // ---------- edit / delete a contact (looked up by phone number) ----------
    private fun contactLookup(number: String): Pair<Long, String>? {
        if (!hasContactsPerm()) return null
        return try {
            var out: Pair<Long, String>? = null
            act.contentResolver.query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY), null, null, null)?.use {
                if (it.moveToFirst()) out = Pair(it.getLong(0), it.getString(1) ?: "")
            }
            out
        } catch (_: Exception) { null }
    }
    /** Opens the phone's own contact editor for this number. */
    @JavascriptInterface fun editContact(number: String): Boolean = uiResult(false) {
        val c = contactLookup(number)
        if (c == null) { toast("Contact not found"); return@uiResult false }
        try {
            act.startActivity(Intent(Intent.ACTION_EDIT).setDataAndType(ContactsContract.Contacts.getLookupUri(c.first, c.second), ContactsContract.Contacts.CONTENT_ITEM_TYPE))
            true
        } catch (e: Exception) { toast("No app can edit contacts"); false }
    }
    /** Deletes the contact from the phone. Needs "Contacts" write permission; the page asks the person to confirm first. */
    @JavascriptInterface fun deleteContact(number: String): Boolean = uiResult(false) {
        if (act.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            askRuntime(arrayOf(Manifest.permission.WRITE_CONTACTS), 8)
            toast("Allow Contacts, then tap delete again")
            return@uiResult false
        }
        val c = contactLookup(number)
        if (c == null) { toast("Contact not found"); return@uiResult false }
        try {
            act.contentResolver.delete(Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, c.second), null, null) > 0
        } catch (e: Exception) { toast("Could not delete"); false }
    }

    // ---------- WhatsApp shortcuts for a contact (no WhatsApp data is read) ----------
    private fun waDigits(num: String): String {
        val raw = num.filter { it.isDigit() || it == '+' }
        val e164 = try { android.telephony.PhoneNumberUtils.formatNumberToE164(raw, java.util.Locale.getDefault().country) } catch (_: Exception) { null }
        return (e164 ?: raw).filter { it.isDigit() }
    }
    private fun waIntent(i: Intent): Boolean {
        for (pkg in arrayOf("com.whatsapp", "com.whatsapp.w4b")) {
            try { act.startActivity(Intent(i).setPackage(pkg)); return true } catch (_: Exception) {}
        }
        return false
    }
    /** Opens the WhatsApp chat with this number. */
    @JavascriptInterface fun waChat(number: String): Boolean = uiResult(false) {
        val d = waDigits(number)
        if (d.length < 6) { toast("No valid number"); return@uiResult false }
        val ok = waIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$d")))
        if (!ok) toast("WhatsApp is not installed")
        ok
    }
    /** Starts a WhatsApp voice call to this contact. Falls back to opening the chat when WhatsApp has no call entry for the contact. */
    @JavascriptInterface fun waCall(number: String): Boolean = uiResult(false) {
        if (hasContactsPerm()) {
            try {
                var cid = -1L
                act.contentResolver.query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                    arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { if (it.moveToFirst()) cid = it.getLong(0) }
                if (cid >= 0) {
                    val mime = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
                    var did = -1L
                    act.contentResolver.query(ContactsContract.Data.CONTENT_URI, arrayOf(ContactsContract.Data._ID),
                        "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?", arrayOf(cid.toString(), mime), null)?.use { if (it.moveToFirst()) did = it.getLong(0) }
                    if (did >= 0 && waIntent(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://com.android.contacts/data/$did"), mime))) return@uiResult true
                }
            } catch (_: Exception) {}
        }
        toast("No WhatsApp call found for this contact, opening chat")
        waChat(number)
    }

    // ---------- clipboard, sharing, saving files ----------
    /** Puts text on the clipboard. Returns true when Android accepted it. */
    @JavascriptInterface fun copyText(text: String): Boolean = uiResult(false) {
        val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Yoga Launcher", text)); true
    }

    /** Opens Android's share sheet with plain text. */
    @JavascriptInterface fun shareText(text: String): Boolean = uiResult(false) {
        try {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            act.startActivity(Intent.createChooser(send, "Share via")); true
        } catch (e: Exception) { toast("Nothing to share with"); false }
    }

    private var pendingSave: Triple<String, String, String>? = null
    private val REQ_SAVE = 7210
    /** Android's "Save as" screen. The page is told the outcome through window.onFileSaved(ok, name). Returns true if the screen opened. */
    @JavascriptInterface fun saveFile(name: String, mime: String, content: String): Boolean = uiResult(false) {
        if (content.length > 4_000_000) { toast("That file is too large to save from here"); return@uiResult false }
        val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).ifBlank { "yoga-launcher.json" }
        pendingSave = Triple(safe, mime.ifBlank { "application/octet-stream" }, content)
        try {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(pendingSave!!.second).putExtra(Intent.EXTRA_TITLE, safe)
            act.startActivityForResult(i, REQ_SAVE); true
        } catch (e: Exception) { pendingSave = null; toast("No file manager is available to save to"); false }
    }

    /** Called from MainActivity.onActivityResult. Returns true if the result was ours. */
    fun onSaveResult(code: Int, resultCode: Int, data: Intent?): Boolean {
        if (code != REQ_SAVE) return false
        val p = pendingSave; pendingSave = null
        val uri = if (resultCode == Activity.RESULT_OK) data?.data else null
        if (p == null || uri == null) { js("window.onFileSaved&&window.onFileSaved(false,${JSONObject.quote(p?.first ?: "")},true)"); return true }
        Thread {
            val ok = try {
                act.contentResolver.openOutputStream(uri, "wt")?.use { it.write(p.third.toByteArray(Charsets.UTF_8)); true } ?: false
            } catch (e: Exception) { false }
            ui { toast(if (ok) "Saved " + p.first else "Could not save the file") }
            js("window.onFileSaved&&window.onFileSaved($ok,${JSONObject.quote(p.first)},false)")
        }.start()
        return true
    }

    @JavascriptInterface fun search(q: String) = ui {
        try {
            act.startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q))
        } catch (e: Exception) {
            openUrl("https://www.google.com/search?q=" + URLEncoder.encode(q, "UTF-8"))
        }
    }

    /** Non-blocking version: the network call runs on its own thread and the result is pushed back to window.__sug(id, [...]). */
    // search suggestions: one request at a time (was a new Thread per keystroke); a request the user has already typed past is
    // answered with [] at once, which is what the page's own 2.5 s timeout would have done
    private val sugPool = java.util.concurrent.ThreadPoolExecutor(1, 1, 0L, java.util.concurrent.TimeUnit.SECONDS,
        java.util.concurrent.LinkedBlockingQueue<Runnable>(), { r -> Thread(r, "yoga-suggest").also { it.isDaemon = true } })
    @Volatile private var sugLatest = 0
    @JavascriptInterface fun suggestAsync(q: String, id: Int) {
        sugLatest = id
        try {
            sugPool.execute {
                val r = if (id != sugLatest) "[]" else suggest(q)
                js("window.__sug&&window.__sug($id,$r)")
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { js("window.__sug&&window.__sug($id,[])") }
    }

    @JavascriptInterface fun suggest(q: String): String {
        var c: HttpURLConnection? = null
        return try {
            c = URL("https://suggestqueries.google.com/complete/search?client=firefox&q=" +
                URLEncoder.encode(q, "UTF-8")).openConnection() as HttpURLConnection
            c.connectTimeout = 1500; c.readTimeout = 1500
            JSONArray(c.inputStream.bufferedReader().use { it.readText() }).getJSONArray(1).toString()
        } catch (e: Exception) { "[]" }
        finally { try { c?.disconnect() } catch (_: Exception) {} }   // used to leak the socket whenever the request failed
    }

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
    @JavascriptInterface fun askContacts() = ui { askRuntime(arrayOf(Manifest.permission.READ_CONTACTS), 7) }

    /** Whole address book for the app drawer's Contacts group: [{"n":"Name","p":"number"}], one entry per person+number. */
    @JavascriptInterface fun getContacts(): String {
        val out = JSONArray()
        if (!hasContactsPerm()) return out.toString()
        try {
            act.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI),
                null, null, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC")?.use { c ->
                val seen = HashSet<String>()
                while (c.moveToNext() && out.length() < 3000) {
                    val n = c.getString(0)?.trim() ?: continue
                    val p = c.getString(1)?.trim() ?: ""
                    val u = c.getString(2) ?: ""
                    if (n.isNotEmpty() && seen.add(n + "|" + p.filter { it.isDigit() || it == '+' })) out.put(JSONObject().put("n", n).put("p", p).put("u", u))
                }
            }
        } catch (_: Exception) {}
        return out.toString()
    }

    /** Contact photo (the one stored on the phone, e.g. synced from Google) as a small data: URI, or "" if there is none.
     *  Only content://com.android.contacts/ addresses are accepted. Nothing is sent over the internet. */
    @JavascriptInterface fun getContactPhoto(uri: String): String {
        if (!hasContactsPerm() || !uri.startsWith("content://com.android.contacts/")) return ""
        return try {
            val opt = android.graphics.BitmapFactory.Options().apply { inSampleSize = 1 }
            val src = act.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { android.graphics.BitmapFactory.decodeStream(it, null, opt) } ?: return ""
            val side = 128
            val bmp = Bitmap.createScaledBitmap(src, side, side, true)
            val bo = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 82, bo)
            if (bmp !== src) bmp.recycle()
            src.recycle()                                   // these bitmaps were left for the GC: a long contact list piled them up
            "data:image/jpeg;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) { "" }
    }

    private var askedContacts = false
    @JavascriptInterface fun searchContacts(q: String): String {
        if (act.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            // ask once per session, not on every keystroke
            if (!askedContacts) { askedContacts = true; ui { askRuntime(arrayOf(Manifest.permission.READ_CONTACTS), 7) } }
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

    @JavascriptInterface fun hasCalendarPerm(): Boolean = granted(Manifest.permission.READ_CALENDAR)

    @JavascriptInterface fun requestCalendar() = ui {
        if (granted(Manifest.permission.READ_CALENDAR)) js("window.onCalendarGranted&&window.onCalendarGranted()")
        else askRuntime(arrayOf(Manifest.permission.READ_CALENDAR), 7208)
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
        // the setting is "pkg/Class:pkg/Class": compare the package exactly (a plain contains() also matched e.g. com.yoga.launcher.beta)
        return s.split(':').any { android.content.ComponentName.unflattenFromString(it)?.packageName == act.packageName }
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
            if (small !== sq) small.recycle()
            if (sq !== bmp) sq.recycle()                    // the metadata's own bitmap (bmp) is not ours to recycle
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

    /** "Reset all" in Settings: forget the native copies of settings, theme and lock-over, so restore() does not bring the old look back after the page reloads. */
    @JavascriptInterface fun clearData() {
        prefs.edit().remove("settings").remove("theme").remove("lockover").apply()
        ui { applyLockOver(act, false) }
    }

    /** Called from MainActivity.onDestroy: stop background work and listeners that would otherwise keep this Activity alive. */
    fun release() {
        try { (act.getSystemService(Context.CAMERA_SERVICE) as CameraManager).unregisterTorchCallback(torchCb) } catch (_: Exception) {}
        iconJob?.interrupt()
        packJob?.interrupt()
        try { appScan.shutdownNow() } catch (_: Exception) {}
        try { sugPool.shutdownNow() } catch (_: Exception) {}
    }

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
                act.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 7206)   // not 7204: that is the widget picker's code
                return
            }
        }
        // role dialog unavailable (older Android, some OEM builds): Home settings, then the default-apps list, then Settings
        for (a in arrayOf(Settings.ACTION_HOME_SETTINGS, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, Settings.ACTION_SETTINGS)) {
            try { act.startActivity(Intent(a)); return } catch (_: Exception) {}
        }
        web.evaluateJavascript("window.YPLAT&&window.YPLAT.homeHelp()", null)   // nothing opened: the page shows the manual steps
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
            "calendar" -> if (granted(Manifest.permission.READ_CALENDAR)) appDetails() else askRuntime(arrayOf(Manifest.permission.READ_CALENDAR), 7208)
            else -> appDetails()
        }
    }

    @Volatile private var torchOn = false
    private var torchId: String? = null
    private val torchCb = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { if (cameraId == torchId) torchOn = enabled }
    }
    init {
        // keep torchOn equal to the real flashlight state (it can be switched off from the quick settings too)
        try {
            val cm = act.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            torchId = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            cm.registerTorchCallback(torchCb, android.os.Handler(android.os.Looper.getMainLooper()))
        } catch (_: Exception) {}
    }

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
                "camera" -> if (uiResult(false) { act.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); true }) "ok" else "fail"
                "torch" -> {
                    val cm = act.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val id = torchId
                    if (id == null) "fail" else { val want = !torchOn; cm.setTorchMode(id, want); torchOn = want; if (want) "on" else "off" }
                }
                else -> "fail"
            }
        } catch (_: Exception) { "fail" }
    }

    // ---------- lock screen ----------
    @JavascriptInterface fun lockScreen() = ui {
        val svc = LockService.inst
        if (Build.VERSION.SDK_INT < 28) {
            Toast.makeText(act, "Locking from the launcher needs Android 9 or newer", Toast.LENGTH_LONG).show()
        } else if (svc != null) {
            if (!svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) Toast.makeText(act, "Android refused to lock the screen", Toast.LENGTH_SHORT).show()
        } else {
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
    override fun onListenerDisconnected() {
        if (inst === this) { inst = null; counts = emptyMap(); notes = emptyMap() }   // access switched off: no stale dots or texts
    }
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
