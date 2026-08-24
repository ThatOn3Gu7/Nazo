package quiz.thaton3app.nazo

import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import quiz.thaton3app.nazo.data.UpdatePrefs
import quiz.thaton3app.nazo.data.UpdateScheduler
import quiz.thaton3app.nazo.ui.NazoApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyLauncherIconForNightMode()
        // Schedule background update checks per the saved frequency preference.
        UpdateScheduler.apply(this, UpdatePrefs(this).updateFrequency)
        setContent {
            NazoApp()
        }
    }

    private fun applyLauncherIconForNightMode() {
        try {
            val pm = packageManager
            val main = ComponentName(this, "$packageName.MainActivity")
            val darkAlias = ComponentName(this, "$packageName.LauncherDark")
            val isNight = (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val (enabled, disabled) = if (isNight) {
                darkAlias to main
            } else {
                main to darkAlias
            }
            pm.setComponentEnabledSetting(
                enabled,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            pm.setComponentEnabledSetting(
                disabled,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        } catch (_: Exception) {
            // Non-fatal: themed launcher icon is best-effort.
        }
    }
}
