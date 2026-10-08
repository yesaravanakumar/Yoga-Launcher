package com.yoga.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

/** Bridge from the launcher to the page. MainActivity sets [js] while it is alive. */
object LauncherHook {
    @Volatile var js: ((String) -> Unit)? = null
}

/**
 * Other apps (yours, or Tasker) send a broadcast and a card appears on the widgets page.
 *
 *   Intent("com.yoga.launcher.PUSH").setPackage("com.yoga.launcher")
 *       .putExtra("id", "water").putExtra("title", "Water")
 *       .putExtra("value", "5 / 8").putExtra("sub", "glasses today")
 *
 * Add .putExtra("remove", true) to delete the card.
 * Only apps signed with the same key can send (see the permission in AndroidManifest.xml).
 */
class PushReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getStringExtra("id")?.take(40) ?: return
        val hook = LauncherHook.js ?: return
        val q = JSONObject.quote(id)
        if (i.getBooleanExtra("remove", false)) {
            hook("window.setWidgetData&&setWidgetData($q,null)")
            return
        }
        val o = JSONObject()
            .put("title", (i.getStringExtra("title") ?: id).take(40))
            .put("value", (i.getStringExtra("value") ?: "").take(40))
            .put("sub", (i.getStringExtra("sub") ?: "").take(120))
        hook("window.setWidgetData&&setWidgetData($q,$o)")
    }
}
