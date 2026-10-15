package com.yoga.launcher   // <- must match "namespace" in app/build.gradle.kts

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowInsets
import android.view.WindowManager
import android.net.Uri
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.app.AlertDialog
import android.widget.EditText
import android.webkit.ValueCallback
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import org.json.JSONObject

/** The Home screen. Opens index.html in a WebView and wires it to YogaBridge. */
class MainActivity : Activity() {

    private companion object {
        const val REQ_GEO = 7201
        const val REQ_FILE = 7202
        const val REQ_VOICE = 7203
    }

    private lateinit var web: WebView
    private lateinit var bridge: YogaBridge
    private lateinit var widgets: YogaWidgets
    private var loaded = false
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var geoOrigin: String? = null
    private var geoCb: GeolocationPermissions.Callback? = null
    private val handler = Handler(Looper.getMainLooper())
    // Android 13+ with enableOnBackInvokedCallback="true" (see the manifest) no longer calls onBackPressed() for a plain Activity,
    // so Back would have left the launcher without ever reaching the page. This callback sends it to the page on every version.
    private var backCb: Any? = null
    private val refreshApps = Runnable { if (loaded) bridge.pushApps() }

    // Yoga lock screen: tell the page when the screen turns off, and whether the launcher was in front
    private var resumed = false
    private var pausedAt = 0L
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == Intent.ACTION_SCREEN_OFF && loaded) {
                val fg = resumed || System.currentTimeMillis() - pausedAt < 1500
                web.evaluateJavascript("window.onScreenOff&&window.onScreenOff($fg,${System.currentTimeMillis()})", null)
            }
        }
    }

    // keeps the battery percentage in the page exact (the WebView's own battery API goes stale)
    private var lastBatt = ""
    private val battReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (!loaded) return
            val b = bridge.getBattery()
            if (b == lastBatt) return                 // voltage / temperature ticks: nothing the page shows has changed
            lastBatt = b
            web.evaluateJavascript("window.setBattery&&window.setBattery($b)", null)
        }
    }

    /** Refresh the app list when something is installed, updated or removed. */
    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            i?.data?.schemeSpecificPart?.let { bridge.dropIcon(it) }
            handler.removeCallbacks(refreshApps)
            handler.postDelayed(refreshApps, 700)
        }
    }

    // Battery Saver and the system "Remove animations" switch are pushed to the page (it calms its decorative loops)
    private val psReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { pushPlatform() }
    }
    private val animObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) { pushPlatform() }
    }
    private fun pushPlatform() {
        if (!loaded) return
        web.evaluateJavascript("window.setPowerSave&&window.setPowerSave(${bridge.isPowerSave()});window.setReduceMotion&&window.setReduceMotion(${bridge.getReduceMotion()})", null)
    }

    // Edge-to-edge (Android 11+): the page is drawn behind the bars and told the insets in dp. Older versions keep black bars.
    private var insetsJs = ""
    private fun setupEdgeToEdge(root: FrameLayout) {
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        if (Build.VERSION.SDK_INT < 30) return
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        window.setDecorFitsSystemWindows(false)
        root.setBackgroundColor(Color.BLACK)
        root.setOnApplyWindowInsetsListener { v, ins ->
            val bars = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime = ins.getInsets(WindowInsets.Type.ime()).bottom
            // adjustResize no longer resizes the window when it is edge-to-edge, so the keyboard shrinks the WebView here instead
            v.setPadding(0, 0, 0, ime)
            val d = resources.displayMetrics.density
            val bottom = if (ime > 0) 0 else bars.bottom           // the keyboard already covers the navigation bar
            insetsJs = "window.setInsets&&window.setInsets({top:${bars.top / d},bottom:${bottom / d},left:${bars.left / d},right:${bars.right / d}})"
            if (loaded) web.evaluateJavascript(insetsJs, null)
            ins
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        web.setBackgroundColor(Color.BLACK)
        web.overScrollMode = WebView.OVER_SCROLL_NEVER
        val root = FrameLayout(this)
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        setupEdgeToEdge(root)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            textZoom = 100                       // phone font-size setting must not stretch the clock
            setSupportZoom(false)
            builtInZoomControls = false
            mediaPlaybackRequiresUserGesture = false
        }

        bridge = YogaBridge(this, web)
        web.addJavascriptInterface(bridge, "Android")
        widgets = YogaWidgets(this, web, root)
        bridge.widgets = widgets
        // Yoga lock screen above Android's lock screen: restore the last choice right away, before the page loads
        applyLockOver(this, getSharedPreferences("yoga", MODE_PRIVATE).getBoolean("lockover", false))
        // long-press must reach the page (drag to home screen), not Android's own long-click handling
        web.setOnLongClickListener { true }
        web.isLongClickable = false
        web.isHapticFeedbackEnabled = false
        // lets other apps push cards to the widgets page (see PushReceiver)
        LauncherHook.js = { code -> web.post { if (loaded) web.evaluateJavascript(code, null) } }
        // weather by location, and the wallpaper / settings-import file pickers
        web.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                val ok = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (ok) {
                    callback.invoke(origin, true, false)
                } else {
                    geoOrigin = origin
                    geoCb = callback
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), REQ_GEO)
                }
            }

            // The WebView shows nothing for alert() / confirm() / prompt() unless these are implemented
            // (without them confirm() silently returns false, so every "Are you sure?" action did nothing).
            override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                if (isFinishing) { result.cancel(); return true }
                AlertDialog.Builder(this@MainActivity).setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                    .setOnCancelListener { result.cancel() }.show()
                return true
            }

            override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                if (isFinishing) { result.cancel(); return true }
                AlertDialog.Builder(this@MainActivity).setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }.show()
                return true
            }

            override fun onJsPrompt(view: WebView, url: String?, message: String?, defaultValue: String?, result: JsPromptResult): Boolean {
                if (isFinishing) { result.cancel(); return true }
                val input = EditText(this@MainActivity).apply { setText(defaultValue ?: ""); setSingleLine() }
                AlertDialog.Builder(this@MainActivity).setMessage(message).setView(input)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm(input.text.toString()) }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }.show()
                return true
            }

            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCb?.onReceiveValue(null)
                fileCb = callback
                return try {
                    startActivityForResult(params.createIntent(), REQ_FILE)
                    true
                } catch (e: Exception) {
                    fileCb = null
                    false
                }
            }
        }
        web.webViewClient = object : WebViewClient() {
            // The page's renderer process died (memory pressure or a crash). Unhandled, Android kills the whole app and it
            // restarts from scratch with the boot logo. Rebuild the screen instead.
            override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
                loaded = false
                handler.post { if (!isFinishing && !isDestroyed) recreate() }
                return true
            }

            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.scheme == "file" && u.path?.startsWith("/android_asset/") == true) return false
                if (u.scheme == "http" || u.scheme == "https") {
                    try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) {}
                }
                return true
            }

            override fun onPageFinished(v: WebView, url: String) {
                loaded = true
                if (insetsJs.isNotEmpty()) web.evaluateJavascript(insetsJs, null)
                pushPlatform()
                bridge.pushApps { bridge.restore() }   // app scan runs off the UI thread; saved settings follow the app list, as before
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            val cb = OnBackInvokedCallback { web.evaluateJavascript("window.onBack&&window.onBack()", null) }
            backCb = cb
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
        }
        web.loadUrl("file:///android_asset/index.html")

        val off = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, off, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenReceiver, off)

        val bt = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(battReceiver, bt, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(battReceiver, bt)

        val pk = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, pk, Context.RECEIVER_EXPORTED)
        else registerReceiver(pkgReceiver, pk)
    }

    override fun onStart() {
        super.onStart()
        widgets.onStart()
        web.onResume()
        val ps = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(psReceiver, ps, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(psReceiver, ps)
        contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, animObserver)
        pushPlatform()
    }

    override fun onStop() {
        try { unregisterReceiver(psReceiver) } catch (_: Exception) {}
        try { contentResolver.unregisterContentObserver(animObserver) } catch (_: Exception) {}
        web.onPause()                       // another app is in front: stop rendering and page work
        widgets.onStop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        // the screen just came back: let the page show the Yoga lock screen / refresh the battery
        if (loaded) web.evaluateJavascript("window.refreshBatt&&window.refreshBatt();window.onLauncherResume&&window.onLauncherResume();window.onPermsChanged&&window.onPermsChanged()", null)
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        pausedAt = System.currentTimeMillis()
    }

    /** Home button pressed while the launcher is already open: close every panel, leave edit modes, go to the clock. */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) setIntent(intent)
        if (intent?.hasCategory(Intent.CATEGORY_HOME) == true) {
            web.evaluateJavascript("window.onHome&&window.onHome()", null)
        }
    }

    /** A launcher never quits on Back: it just closes whatever is open. */
    @Deprecated("Back button")
    override fun onBackPressed() {
        web.evaluateJavascript("window.onBack&&window.onBack()", null)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_GEO) {
            val ok = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
            geoCb?.invoke(geoOrigin, ok, false)
            geoCb = null
            geoOrigin = null
        } else if (requestCode == 7 && grantResults.any { it == PackageManager.PERMISSION_GRANTED } && loaded) {
            web.evaluateJavascript("window.onContactsGranted&&window.onContactsGranted()", null)
        } else if (requestCode == 7208 && grantResults.any { it == PackageManager.PERMISSION_GRANTED } && loaded) {
            web.evaluateJavascript("window.onCalendarGranted&&window.onCalendarGranted()", null)
        }
        if (loaded) web.evaluateJavascript("window.onPermsChanged&&window.onPermsChanged()", null)
    }

    @Deprecated("Activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (widgets.onActivityResult(requestCode, resultCode, data)) return
        if (bridge.onSaveResult(requestCode, resultCode, data)) return
        if (requestCode == 7206) {   // default-home role request
            if (loaded) web.evaluateJavascript("window.onPermsChanged&&window.onPermsChanged()", null)
        } else if (requestCode == REQ_VOICE) {
            val said = if (resultCode == RESULT_OK) data?.getStringArrayListExtra("android.speech.extra.RESULTS")?.firstOrNull() else null
            if (!said.isNullOrBlank() && loaded) web.evaluateJavascript("window.__voice&&window.__voice(${JSONObject.quote(said)})", null)
        } else if (requestCode == REQ_FILE) {
            fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCb = null
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        LauncherHook.js = null
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(pkgReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(battReceiver) } catch (_: Exception) {}
        if (Build.VERSION.SDK_INT >= 33) (backCb as? OnBackInvokedCallback)?.let { try { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) } catch (_: Exception) {} }
        try { unregisterReceiver(psReceiver) } catch (_: Exception) {}
        try { contentResolver.unregisterContentObserver(animObserver) } catch (_: Exception) {}
        fileCb?.onReceiveValue(null); fileCb = null          // a file chooser left open would block every later one
        geoCb = null
        web.removeJavascriptInterface("Android")
        try { bridge.release() } catch (_: Exception) {}
        (web.parent as? ViewGroup)?.removeView(web)          // detach before destroy(), as WebView requires
        web.destroy()
        super.onDestroy()
    }
}
