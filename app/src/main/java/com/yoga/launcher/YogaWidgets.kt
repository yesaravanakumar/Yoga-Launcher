package com.yoga.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import org.json.JSONObject

/** Hosts real Android widgets on top of the page.
 *  The page decides where each widget goes (placeWidget) and this class puts the real widget view there. */
class YogaWidgets(
    private val act: Activity,
    private val web: WebView,
    private val root: FrameLayout          // the FrameLayout that holds the WebView
) {
    companion object { const val REQ_PICK = 7204; const val REQ_CONFIG = 7205 }

    private val host = AppWidgetHost(act, 1024)
    private val mgr = AppWidgetManager.getInstance(act)
    private val views = HashMap<Int, AppWidgetHostView>()
    private var pending = -1               // widget id being picked or configured right now

    private fun js(code: String) = web.post { web.evaluateJavascript(code, null) }

    fun onStart() { try { host.startListening() } catch (_: Exception) {} }
    fun onStop() { try { host.stopListening() } catch (_: Exception) {} }

    /** Open Android's widget chooser. */
    fun pick() {
        try {
            drop()
            pending = host.allocateAppWidgetId()
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending)
                .putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, ArrayList())
                .putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, ArrayList())
            act.startActivityForResult(i, REQ_PICK)
        } catch (e: Exception) {
            drop()
            js("window.toast&&toast('Could not open the widget picker')")
        }
    }

    /** Call from Activity.onActivityResult. Returns true when the result belonged to widgets. */
    fun onActivityResult(req: Int, res: Int, @Suppress("UNUSED_PARAMETER") data: Intent?): Boolean {
        if (req != REQ_PICK && req != REQ_CONFIG) return false
        val id = pending
        if (id == -1) return true
        if (res != Activity.RESULT_OK) { drop(); return true }
        val info = mgr.getAppWidgetInfo(id)
        if (info == null) { drop(); js("window.toast&&toast('That widget could not be added')"); return true }
        if (req == REQ_PICK && info.configure != null) {
            try { host.startAppWidgetConfigureActivityForResult(act, id, 0, REQ_CONFIG, null) }
            catch (e: Exception) { added(id, info.loadLabel(act.packageManager)) }
        } else {
            added(id, info.loadLabel(act.packageManager))
        }
        return true
    }

    private fun added(id: Int, label: String) {
        pending = -1
        js("window.onWidgetAdded&&onWidgetAdded({id:$id,label:${JSONObject.quote(label)}})")
    }

    private fun drop() {
        if (pending != -1) { try { host.deleteAppWidgetId(pending) } catch (_: Exception) {} }
        pending = -1
    }

    /** x, y, w, h are page pixels, which equal dp. */
    fun place(idStr: String, x: Int, y: Int, w: Int, h: Int) {
        act.runOnUiThread {
            try {
                val id = idStr.toIntOrNull() ?: return@runOnUiThread
                val info = mgr.getAppWidgetInfo(id)
                if (info == null) {
                    views.remove(id)?.let { root.removeView(it) }
                    host.deleteAppWidgetId(id)
                    js("window.onWidgetRemoved&&onWidgetRemoved($id)")
                    return@runOnUiThread
                }
                val v = views.getOrPut(id) { host.createView(act.applicationContext, id, info).also { root.addView(it) } }
                val d = act.resources.displayMetrics.density
                val lp = FrameLayout.LayoutParams((w * d).toInt(), (h * d).toInt())
                lp.leftMargin = (x * d).toInt() + web.left
                lp.topMargin = (y * d).toInt() + web.top
                v.layoutParams = lp
                v.updateAppWidgetSize(null, w, h, w, h)
                v.visibility = View.VISIBLE
            } catch (_: Exception) {}
        }
    }

    fun hide(idStr: String) {
        act.runOnUiThread { views[idStr.toIntOrNull() ?: return@runOnUiThread]?.visibility = View.GONE }
    }

    fun remove(idStr: String) {
        act.runOnUiThread {
            val id = idStr.toIntOrNull() ?: return@runOnUiThread
            views.remove(id)?.let { root.removeView(it) }
            try { host.deleteAppWidgetId(id) } catch (_: Exception) {}
        }
    }
}
