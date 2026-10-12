package quiz.thaton3app.nazo.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * True while the system's "Remove animations" accessibility setting is on.
 *
 * Reads `Settings.Global.ANIMATOR_DURATION_SCALE` — the framework knob that
 * every animation setting ultimately honours (0 = animations removed). No new
 * dependencies: it is a plain content-resolver read.
 *
 * The value is re-checked on every resume, so turning the setting on in system
 * settings is picked up when the user comes back to the app instead of
 * requiring a restart. Decorative motion (the ambient background, celebration
 * confetti) consults this and stands down; functional transitions — navigation,
 * dialogs, progress — keep running so the app remains operable.
 */
@Composable
fun rememberSystemReduceMotion(): Boolean {
    val context = LocalContext.current
    var disabled by remember { mutableStateOf(animationsDisabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                disabled = animationsDisabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return disabled
}

private fun animationsDisabled(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
}.getOrDefault(false)
