package quiz.thaton3app.nazo

/**
 * Per-icon launcher entry points.
 *
 * ## Why these exist
 * The very first frame of a cold start is the **starting window** (a.k.a. the
 * "preview window"): the system draws it from the launched component's
 * *manifest* theme BEFORE the app process is even forked. Nothing in Kotlin can
 * influence it — by the time `onCreate` runs, that window is already on screen.
 *
 * `<activity-alias>` cannot declare `android:theme`; the starting window falls
 * back to the *target* activity's theme, which is why every icon variant used to
 * flash the same green. A real `<activity>`, however, can — so each icon variant
 * gets a trivial [MainActivity] subclass whose manifest entry carries its own
 * `Theme.Nazo.Splash.*`. That makes the launch color part of the component the
 * launcher starts, so it is correct from the very first frame.
 *
 * These classes intentionally add no behaviour: they inherit everything
 * (splash handoff, intent handling, the Daily Challenge shortcut action) from
 * [MainActivity]. Only the manifest theme differs.
 *
 * Registered in [LauncherIconSwitcher.OPTIONS] via `AppIconOption.alias`; exactly
 * one is enabled at a time. Adding a new icon means: an adaptive icon, a
 * `Theme.Nazo.Splash.*` style, a subclass here, a manifest `<activity>`, and one
 * `OPTIONS` entry.
 */
class LauncherSakura : MainActivity()

class LauncherMidnight : MainActivity()

class LauncherOcean : MainActivity()

class LauncherLantern : MainActivity()

class LauncherBrush : MainActivity()

class LauncherPixel : MainActivity()
