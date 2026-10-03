package com.yoga.launcher   // <- must match "namespace" in app/build.gradle.kts

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient

/** The Home screen. Opens index.html in a WebView and wires it to YogaBridge. */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var bridge: YogaBridge
    private var loaded = false
    private val handler = Handler(Looper.getMainLooper())
    private val refreshApps = Runnable { if (loaded) bridge.pushApps() }

    /** Refresh the app list when something is installed, updated or removed. */
    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            handler.removeCallbacks(refreshApps)
            handler.postDelayed(refreshApps, 700)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        web = WebView(this)
        web.setBackgroundColor(Color.BLACK)
        web.overScrollMode = WebView.OVER_SCROLL_NEVER
        setContentView(web)

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
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, url: String) {
                loaded = true
                bridge.pushApps()
                bridge.restore()
            }
        }
        web.loadUrl("file:///android_asset/index.html")
    }

    override fun onStart() {
        super.onStart()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED)
        else registerReceiver(pkgReceiver, f)
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(pkgReceiver) } catch (_: Exception) {}
    }

    /** Home button pressed while the launcher is already open: close drawers and go to the clock. */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.hasCategory(Intent.CATEGORY_HOME) == true) {
            web.evaluateJavascript("window.onHome&&window.onHome()", null)
        }
    }

    /** A launcher never quits on Back: it just closes whatever is open. */
    @Deprecated("Back button")
    override fun onBackPressed() {
        web.evaluateJavascript("window.onBack&&window.onBack()", null)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        web.removeJavascriptInterface("Android")
        web.destroy()
        super.onDestroy()
    }
}
