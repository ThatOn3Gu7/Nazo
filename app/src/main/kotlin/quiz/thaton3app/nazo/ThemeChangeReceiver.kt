package quiz.thaton3app.nazo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-evaluates the launcher icon whenever the device's UI mode (dark/light) changes.
 * Registered both in the manifest (best-effort on some devices) and dynamically from
 * MainActivity (so it fires while the app is running).
 */
class ThemeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        LauncherIconSwitcher.apply(context)
    }
}
