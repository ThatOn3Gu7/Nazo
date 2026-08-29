package quiz.thaton3app.nazo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import android.graphics.drawable.ColorDrawable
import androidx.core.view.WindowCompat

// Theme now reacts to the user's Appearance-screen choices: a real dark "deep forest"
// theme, plus a selectable accent that applies a full palette. Dynamic color (Material You)
// is intentionally NOT wired up: the brief is to match the mockup's fixed palette.
@Composable
fun NazoTheme(
    darkTheme: Boolean,
    accentId: String,
    content: @Composable () -> Unit,
) {
    val palette = resolveAccent(accentId, darkTheme)
    // Publish the active palette so every screen's `NazoXxx` accessor reflects it.
    setNazoColors(palette)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.textSecondary,
            secondaryContainer = palette.pillUnselected,
            onSecondaryContainer = palette.textPrimary,
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.textSecondary,
            secondaryContainer = palette.pillUnselected,
            onSecondaryContainer = palette.textPrimary,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            // Edge-to-edge: let the app draw full-bleed (behind the status bar and into the
            // display cutout) so the startup popup's blur/scrim can reach the entire top,
            // including the camera cutout and status bar. Screens already apply
            // statusBarsPadding()/navigationBarsPadding(), so their content stays clear of
            // the system bars — only the background (blurred when the popup is up) shows through.
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            // statusBarColor / navigationBarColor setters are deprecated on API 35+,
            // but remain required to keep the bars transparent on the minSdk (26) we support.
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            // Transparent nav bar too, so the app's full-bleed background (blurred while a
            // popup is shown) bleeds behind the bottom swipe-gesture hint instead of a solid
            // bar. The system still draws its own hint line on top.
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()
            // Keep the Activity's own window background in sync with the in-app theme.
            // This prevents the white system window from bleeding through during the
            // AnimatedContent crossfade (especially in forced-dark mode) — see handoff log.
            window.setBackgroundDrawable(ColorDrawable(palette.background.toArgb()))
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = !darkTheme
            insets.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NazoTypography,
        content = content,
    )
}
