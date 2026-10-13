package quiz.thaton3app.nazo

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.annotation.StyleRes

/**
 * One selectable launcher icon.
 *
 * [alias] is the launcher component's class name in AndroidManifest.xml
 * (".LauncherX"). The themed green pair are `<activity-alias>` entries; the custom
 * variants are real [MainActivity] subclasses (see LauncherActivities.kt) so they
 * can each declare their own splash theme. Exactly ONE is ever enabled at a time,
 * and MainActivity itself is never disabled, so the app always stays launchable.
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
    /**
     * Splash theme for this icon. Declared on the variant's own launcher activity in
     * the manifest (so it colors even the pre-process starting window) and re-applied
     * in code for non-icon entry points like the Daily shortcut. Null for the
     * [themed] pair, which keeps the day/night-aware default theme.
     */
    @StyleRes val splashTheme: Int? = null,
    /**
     * Flat background used by the in-app intro overlay, matching [splashTheme]'s
     * color (the midpoint of the icon gradient). Null for the [themed] pair,
     * which keeps its own light/dark greens.
     */
    val splashColor: Long? = null,
    /**
     * Silhouette drawable used by the intro zoom-through (BlendMode.DstOut needs a
     * flat alpha mask, not full-color art) — defaults to the 謎 kanji foreground.
     */
    @DrawableRes val introMark: Int = R.drawable.ic_launcher_foreground,
    /** Which launch animation this icon plays. See [IntroStyle]. */
    val introStyle: IntroStyle = IntroStyle.WARP,
    /**
     * Full-color art shown in the Appearance picker (and nothing else). Defaults to
     * the 謎 kanji; illustrated variants point at their color mark so the list shows
     * what actually lands on the home screen.
     */
    @DrawableRes val previewMark: Int = R.drawable.ic_launcher_foreground,
)

/**
 * Launch animation played by `IntroOverlay` after the system splash hands off.
 * Each illustrated mark gets its own so the cold start feels specific to the icon.
 */
enum class IntroStyle {
    /** Original: the mark is punched out of the ground and warps past the camera. */
    WARP,
    /** Lantern: the mark glows brighter, sways, then the light floods the screen. */
    LANTERN_GLOW,
    /** N marks: a line rises and traces itself into the letter, then warps away. */
    N_STROKE,
    /** Pixel "?": snaps through coarse mosaic steps before resolving and zooming. */
    PIXEL_RESOLVE,
}

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
            splashTheme = R.style.Theme_Nazo_Splash_Sakura,
            splashColor = 0xFFD05A89,
        ),
        AppIconOption(
            id = "midnight",
            alias = "LauncherMidnight",
            label = "Midnight",
            blurb = "Near-black stealth icon",
            startColor = 0xFF1B1F24,
            endColor = 0xFF0B0D10,
            splashTheme = R.style.Theme_Nazo_Splash_Midnight,
            splashColor = 0xFF13161A,
        ),
        AppIconOption(
            id = "ocean",
            alias = "LauncherOcean",
            label = "Ocean",
            blurb = "Teal-to-deep-sea gradient",
            startColor = 0xFF2CA8B8,
            endColor = 0xFF11707F,
            splashTheme = R.style.Theme_Nazo_Splash_Ocean,
            splashColor = 0xFF1E8C9C,
        ),
        AppIconOption(
            id = "lantern",
            alias = "LauncherLantern",
            label = "Paper Lantern",
            blurb = "Illustrated chochin glowing in the dark",
            startColor = 0xFF2B1D2E,
            endColor = 0xFF1A1020,
            splashTheme = R.style.Theme_Nazo_Splash_Lantern,
            splashColor = 0xFF221729,
            introMark = R.drawable.ic_mark_lantern_silhouette,
            introStyle = IntroStyle.LANTERN_GLOW,
            previewMark = R.drawable.ic_mark_lantern,
        ),
        AppIconOption(
            id = "nbrush",
            alias = "LauncherBrush",
            label = "Brush N",
            blurb = "Inked initial on warm paper",
            startColor = 0xFFF0E4CA,
            endColor = 0xFFD9C7A4,
            splashTheme = R.style.Theme_Nazo_Splash_Brush,
            splashColor = 0xFFE8DCC2,
            introMark = R.drawable.ic_mark_n_brush_silhouette,
            introStyle = IntroStyle.N_STROKE,
            previewMark = R.drawable.ic_mark_n_brush,
        ),
        AppIconOption(
            id = "npixel",
            alias = "LauncherPixel",
            label = "Pixel N",
            blurb = "8-bit initial resolving into focus",
            startColor = 0xFF2C3A55,
            endColor = 0xFF161E2E,
            splashTheme = R.style.Theme_Nazo_Splash_Pixel,
            splashColor = 0xFF212C42,
            introMark = R.drawable.ic_mark_n_pixel_silhouette,
            introStyle = IntroStyle.PIXEL_RESOLVE,
            previewMark = R.drawable.ic_mark_n_pixel,
        ),
    )

    fun option(id: String): AppIconOption =
        OPTIONS.firstOrNull { it.id == id } ?: OPTIONS.first()

    /**
     * Repairs a saved preference that names an icon which no longer exists (the
     * Indigo/Bronze color variants were retired in favour of the illustrated
     * marks). Their manifest components are gone, so a stale pref would leave the
     * user with no enabled launcher entry after the next swap. Falls back to the
     * classic light icon and re-applies it.
     *
     * Returns the id actually in effect.
     */
    fun sanitize(context: Context, savedId: String): String {
        if (OPTIONS.any { it.id == savedId }) return savedId
        select(context, ID_LIGHT)
        return ID_LIGHT
    }

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
