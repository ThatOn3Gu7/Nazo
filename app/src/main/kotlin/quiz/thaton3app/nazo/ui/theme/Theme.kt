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
// theme, plus a selectable accent that overrides `primary`. Dynamic color (Material You)
// is intentionally NOT wired up: the brief is to match the mockup's fixed palette.
@Composable
fun NazoTheme(
    darkTheme: Boolean,
    accentColor: Color,
    content: @Composable () -> Unit,
) {
    val brand = (if (darkTheme) DarkNazoColors else LightNazoColors).withPrimary(accentColor)
    // Publish the active palette so every screen's `NazoXxx` accessor reflects it.
    setNazoColors(brand)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = brand.primary,
            onPrimary = brand.onPrimary,
            background = brand.background,
            onBackground = brand.textPrimary,
            surface = brand.surface,
            onSurface = brand.textPrimary,
            surfaceVariant = brand.surfaceVariant,
            onSurfaceVariant = brand.textSecondary,
            secondaryContainer = brand.pillUnselected,
            onSecondaryContainer = brand.textPrimary,
        )
    } else {
        lightColorScheme(
            primary = brand.primary,
            onPrimary = brand.onPrimary,
            background = brand.background,
            onBackground = brand.textPrimary,
            surface = brand.surface,
            onSurface = brand.textPrimary,
            surfaceVariant = brand.surfaceVariant,
            onSurfaceVariant = brand.textSecondary,
            secondaryContainer = brand.pillUnselected,
            onSecondaryContainer = brand.textPrimary,
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
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = brand.background.toArgb()
            // Keep the Activity's own window background in sync with the in-app theme.
            // This prevents the white system window from bleeding through during the
            // AnimatedContent crossfade (especially in forced-dark mode) — see handoff log.
            window.setBackgroundDrawable(ColorDrawable(brand.background.toArgb()))
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NazoTypography,
        content = content,
    )
}
