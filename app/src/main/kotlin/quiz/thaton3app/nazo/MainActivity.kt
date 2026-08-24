package quiz.thaton3app.nazo

import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import quiz.thaton3app.nazo.data.UpdatePrefs
import quiz.thaton3app.nazo.data.UpdateScheduler
import quiz.thaton3app.nazo.ui.NazoApp

class MainActivity : ComponentActivity() {
    private val themeChangeReceiver = ThemeChangeReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Match the launcher icon to the device's OS dark/light mode.
        LauncherIconSwitcher.apply(this)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_UI_MODE_CHANGED)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }
        ContextCompat.registerReceiver(
            this,
            themeChangeReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Schedule background update checks per the saved frequency preference.
        UpdateScheduler.apply(this, UpdatePrefs(this).updateFrequency)
        setContent {
            NazoApp()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(themeChangeReceiver) }
    }
}
