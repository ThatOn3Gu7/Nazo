package quiz.thaton3app.nazo

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * One selectable launcher icon.
 *
 * [alias] is the activity-alias suffix declared in AndroidManifest.xml (".LauncherX").
 * Exactly ONE alias is ever enabled at a time; the real MainActivity is never
 * disabled, so the app always stays launchable.
 */
data class AppIconOption(
    val id: String,
    val alias: String,
    val label: String,
    val blurb: String,
    /** Adaptive-icon background colors (start, end) used to draw the in-app preview. */
    val startColor: Long,
    val endColor: Long,
    /** True for the two aliases that back the "match system theme" mode. */
    val themed: Boolean = false,
)

/**
 * Swaps which launcher activity-alias is enabled, i.e. which app icon the
 * launcher shows.
 *
 * Two modes coexist:
 *  - "match system theme" (ThemePreferences.iconFollowsOsTheme): the classic
 *    light/dark green pair, swapped silently when the app is backgrounded.
 *  - a user-picked icon from [OPTIONS]: applied immediately, which makes Android
 *    kill the app's task (documented behaviour when the launching component is
 *    disabled) — the UI warns about this and the icon updates on the home screen.
 */
object LauncherIconSwitcher {

    /** The classic light/dark pair (used by the follow-OS-theme mode). */
    const val ID_LIGHT = "light"
    const val ID_DARK = "dark"

    val OPTIONS: List<AppIconOption> = listOf(
        AppIconOption(
            id = ID_LIGHT,
            alias = "LauncherLight",
            label = "Classic Green",
            blurb = "The original Nazo mark on mint green",
            startColor = 0xFF36A06F,
            endColor = 0xFF36A06F,
            themed = true,
        ),
        AppIconOption(
            id = ID_DARK,
            alias = "LauncherDark",
            label = "Deep Green",
            blurb = "Darker take on the classic, easy on OLED",
            startColor = 0xFF246D4C,
            endColor = 0xFF246D4C,
            themed = true,
        ),
        AppIconOption(
            id = "sakura",
            alias = "LauncherSakura",
            label = "Sakura",
            blurb = "Soft pink blossom gradient",
            startColor = 0xFFE86A9A,
            endColor = 0xFFB84A78,
        ),
        AppIconOption(
            id = "indigo",
            alias = "LauncherIndigo",
            label = "Indigo",
            blurb = "Cool night-blue gradient",
            startColor = 0xFF5C6BC0,
            endColor = 0xFF3949AB,
        ),
        AppIconOption(
            id = "bronze",
            alias = "LauncherBronze",
            label = "Bronze",
            blurb = "Warm metallic amber",
            startColor = 0xFFC98A3C,
            endColor = 0xFF8C5A22,
        ),
        AppIconOption(
            id = "midnight",
            alias = "LauncherMidnight",
            label = "Midnight",
            blurb = "Near-black stealth icon",
            startColor = 0xFF1B1F24,
            endColor = 0xFF0B0D10,
        ),
        AppIconOption(
            id = "ocean",
            alias = "LauncherOcean",
            label = "Ocean",
            blurb = "Teal-to-deep-sea gradient",
            startColor = 0xFF2CA8B8,
            endColor = 0xFF11707F,
        ),
    )

    fun option(id: String): AppIconOption =
        OPTIONS.firstOrNull { it.id == id } ?: OPTIONS.first()

    private fun component(context: Context, option: AppIconOption): ComponentName =
        ComponentName(context.packageName, "${context.packageName}.${option.alias}")

    private fun isSystemNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun apply(context: Context) {
        apply(context, isSystemNight(context))
    }

    fun apply(context: Context, isNight: Boolean) {
        select(context, if (isNight) ID_DARK else ID_LIGHT)
    }

    /**
     * Enables the alias for [id] and disables every other one.
     *
     * The new alias is enabled BEFORE the old ones are disabled so the launcher
     * never observes zero launcher entries (which can drop the home-screen
     * shortcut entirely).
     */
    fun select(context: Context, id: String) {
        try {
            val pm = context.packageManager
            val target = option(id)
            if (currentId(context) == target.id) return
            pm.setComponentEnabledSetting(
                component(context, target),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            OPTIONS.filter { it.id != target.id }.forEach {
                pm.setComponentEnabledSetting(
                    component(context, it),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        } catch (_: Exception) {
            // Best-effort: a custom launcher icon is non-critical.
        }
    }

    /** The id of the currently enabled alias, or null if the state is ambiguous. */
    fun currentId(context: Context): String? {
        val pm = context.packageManager
        val enabled = OPTIONS.filter {
            effectiveEnabled(pm, component(context, it), it.id == ID_LIGHT)
        }
        return enabled.singleOrNull()?.id
    }

    /** Which OS-theme variant is currently enabled, or null if it's neither/ambiguous. */
    fun appliedNight(context: Context): Boolean? = when (currentId(context)) {
        ID_DARK -> true
        ID_LIGHT -> false
        else -> null
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
