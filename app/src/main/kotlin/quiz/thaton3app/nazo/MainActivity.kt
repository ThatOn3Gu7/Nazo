package quiz.thaton3app.nazo

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import quiz.thaton3app.nazo.data.UpdatePrefs
import quiz.thaton3app.nazo.data.UpdateScheduler
import quiz.thaton3app.nazo.data.settings.ThemePreferences
import quiz.thaton3app.nazo.LauncherIconSwitcher
import quiz.thaton3app.nazo.ui.NazoApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Schedule background update checks per the saved frequency preference.
        UpdateScheduler.apply(this, UpdatePrefs(this).updateFrequency)
        setContent {
            NazoApp()
        }
    }

    override fun onStop() {
        super.onStop()
        // Don't swap during a config-change-driven restart (e.g. rotation).
        if (isChangingConfigurations) return
        syncIconToOsTheme()
    }

    // Silently sync the launcher icon to the current OS theme when leaving the app.
    // Never called while the app is visible, because disabling the alias that launched
    // the current session would kill that task.
    private fun syncIconToOsTheme() {
        val prefs = ThemePreferences(this)
        if (!prefs.iconFollowsOsTheme) return
        val desiredNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        if (LauncherIconSwitcher.appliedNight(this) != desiredNight) {
            LauncherIconSwitcher.apply(this, desiredNight)
            prefs.appliedLauncherNight = if (desiredNight) "dark" else "light"
        }
    }
}
