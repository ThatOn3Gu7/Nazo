package quiz.thaton3app.nazo

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import quiz.thaton3app.nazo.data.UpdatePrefs
import quiz.thaton3app.nazo.data.UpdateScheduler
import quiz.thaton3app.nazo.data.settings.ThemePreferences
import quiz.thaton3app.nazo.LauncherIconSwitcher
import quiz.thaton3app.nazo.ui.NazoApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // MUST be the first statement (before super.onCreate) so the system splash
        // window is installed and handed off before any content is drawn.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Schedule background update checks per the saved frequency preference.
        UpdateScheduler.apply(this, UpdatePrefs(this).updateFrequency)
        setContent {
            NazoApp(launchDailyChallenge = intent?.action == ACTION_DAILY)
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

    companion object {
        /** Launcher long-press shortcut (res/xml/shortcuts.xml) → jump into the Daily Challenge. */
        const val ACTION_DAILY = "quiz.thaton3app.nazo.action.DAILY"
    }
}
