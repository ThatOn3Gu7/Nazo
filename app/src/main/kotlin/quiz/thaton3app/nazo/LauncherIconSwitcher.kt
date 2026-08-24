package quiz.thaton3app.nazo

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Swaps the launcher entry between the light-green and dark-green adaptive icons
 * to match the device's OS dark/light mode (not the in-app Appearance setting).
 *
 * Implemented with two launcher activity-aliases (LauncherLight / LauncherDark);
 * the real MainActivity is never disabled, so the app stays launchable and the
 * home shortcut can't point at a dead component.
 */
object LauncherIconSwitcher {
    private fun isSystemNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun apply(context: Context) {
        apply(context, isSystemNight(context))
    }

    fun apply(context: Context, isNight: Boolean) {
        try {
            val pm = context.packageManager
            val pkg = context.packageName
            val light = ComponentName(pkg, "$pkg.LauncherLight")
            val dark = ComponentName(pkg, "$pkg.LauncherDark")
            val darkEff = effectiveEnabled(pm, dark, manifestDefaultEnabled = false)
            val lightEff = effectiveEnabled(pm, light, manifestDefaultEnabled = true)
            // Already in the right state — avoid needless re-broadcasts to the launcher.
            if (darkEff == isNight && lightEff == !isNight) return
            val (enable, disable) = if (isNight) dark to light else light to dark
            // Enable the new entry before disabling the old one so the launcher never
            // sees a moment with zero (or, briefly, two) launcher entries.
            pm.setComponentEnabledSetting(
                enable,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            pm.setComponentEnabledSetting(
                disable,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        } catch (_: Exception) {
            // Best-effort: a themed launcher icon is non-critical.
        }
    }

    private fun effectiveEnabled(
        pm: PackageManager,
        component: ComponentName,
        manifestDefaultEnabled: Boolean,
    ): Boolean = when (pm.getComponentEnabledSetting(component)) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> false
        else -> manifestDefaultEnabled
    }
}
