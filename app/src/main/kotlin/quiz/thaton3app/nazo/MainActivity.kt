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
import quiz.thaton3app.nazo.data.settings.ThemePreferences
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
            // Mirror NazoApp's effective theme: Appearance "dark"/"light" win, otherwise
            // fall back to the system night mode. This keeps the launcher icon in sync with
            // the in-app theme the user actually controls.
            val mode = ThemePreferences(this).mode
            val isNight = when (mode) {
                "dark" -> true
                "light" -> false
                else -> (resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
            val main = ComponentName(this, MainActivity::class.java)
            val darkAlias = ComponentName(main.packageName, "${main.packageName}.LauncherDark")
            val (enabled, disabled) = if (isNight) darkAlias to main else main to darkAlias
            val pm = packageManager
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
