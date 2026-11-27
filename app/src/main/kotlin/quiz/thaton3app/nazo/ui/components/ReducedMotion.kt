package quiz.thaton3app.nazo.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** App-level override supplied by [quiz.thaton3app.nazo.ui.theme.NazoTheme]. */
internal val LocalNazoReduceMotion = staticCompositionLocalOf { false }

/** System Remove animations OR the in-app Accessibility Studio preference. */
@Composable
fun rememberSystemReduceMotion(): Boolean =
    LocalNazoReduceMotion.current || rememberSystemAnimationsDisabled()

/**
 * Reads only Android's animation scale; the app root combines it with its preference.
 * The value is checked again on resume so a system Settings change needs no restart.
 */
@Composable
internal fun rememberSystemAnimationsDisabled(): Boolean {
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
