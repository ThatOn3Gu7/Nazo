package quiz.thaton3app.nazo.data

/**
 * Static content for the About section: the changelog, the licence inventory and
 * the credits.
 *
 * These are deliberately hand-maintained constants rather than anything
 * generated at runtime:
 *
 *  - The **changelog** is written for players, in the same voice as
 *    `RELEASE_NOTES_<version>.md`. The raw commit log is not a changelog — most
 *    of the commits between 8.0 and 9.0, for instance, were failed attempts at
 *    one bug that cancelled each other out. Entries here describe what actually
 *    changed for someone using the app.
 *  - The **licence list** mirrors `gradle/libs.versions.toml`. Nazo does not use
 *    a licence-scanning plugin (AboutLibraries and friends pull in a Gradle
 *    plugin plus a generated asset), so this list must be updated by hand when a
 *    dependency is added. It is short enough that this is honest work, and it
 *    lets us state real versions instead of a vague list.
 *
 * When cutting a release: add a [ChangelogEntry] here as well as the
 * `RELEASE_NOTES_<version>.md` file.
 */

/** One shipped version, newest first in [NAZO_CHANGELOG]. */
data class ChangelogEntry(
    val version: String,
    val date: String,
    /** Short player-facing lines. Keep them concrete; no commit hashes. */
    val changes: List<String>,
)

/**
 * Every released version, newest first.
 *
 * Dates are the GitHub release dates. Summaries are condensed from each
 * release's own notes and commit range.
 */
val NAZO_CHANGELOG: List<ChangelogEntry> = listOf(
    ChangelogEntry(
        version = "9.0",
        date = "10 Sep 2026",
        changes = listOf(
            "Landscape mode across the whole app, with a vertical nav rail and two-pane quiz, results, guessing and settings layouts.",
            "Fixed badly discoloured guessing-game images — the app was downloading a low-quality thumbnail instead of the original artwork.",
            "Reveal effects are drawn over the mystery image instead of being baked into it, so the picture is no longer damaged.",
            "Auto-crop is gentler: it frames the character rather than zooming into the face.",
            "Bottom sheets scroll properly in landscape instead of cutting off their lower options.",
            "The Settings back button now leaves in one press instead of replaying every screen you visited.",
            "In-app update notes show a written summary instead of a raw commit list.",
        ),
    ),
    ChangelogEntry(
        version = "8.0",
        date = "6 Sep 2026",
        changes = listOf(
            "Offline mode is now a real switch you control, and it always reflects the actual state.",
            "Quiz, Survival and Versus fall back to the local question bank when the network is down instead of failing.",
            "Removed the connectivity popup that appeared on startup.",
            "Releases are restricted to an author allowlist.",
        ),
    ),
    ChangelogEntry(
        version = "7.0",
        date = "2 Sep 2026",
        changes = listOf(
            "Redesigned the floating bottom navigation bar.",
            "Fixed share-card stat rows overflowing the card and colliding with the footer.",
            "Added the screenshot gallery to the project README.",
        ),
    ),
    ChangelogEntry(
        version = "6.0",
        date = "1 Sep 2026",
        changes = listOf(
            "Much better guessing-game images: franchise-aware character matching, plus AniList, Jikan and Kitsu as sources.",
            "Cosplay and real-person photos are rejected by an on-device image check.",
            "Questions no longer repeat within a session, in either game mode.",
            "On-topic fallback images replace blank placeholders.",
        ),
    ),
    ChangelogEntry(
        version = "5.0",
        date = "31 Aug 2026",
        changes = listOf(
            "Guessing games now feed the statistics screen and your level.",
            "Pixel Reveal starts fully obscured instead of briefly showing the answer.",
            "Smaller release builds through minification and resource shrinking.",
        ),
    ),
    ChangelogEntry(
        version = "4.0",
        date = "29 Aug 2026",
        changes = listOf(
            "Ambient background redesigned with seven distinct particle shapes.",
            "Statistics screen animates its numbers with count-ups.",
            "Provider cards show animated status.",
            "Subtle outlines added to cards across About, Appearance and the nav bar.",
        ),
    ),
    ChangelogEntry(
        version = "3.0",
        date = "28 Aug 2026",
        changes = listOf(
            "Real backup and restore to a file you choose, plus scheduled automatic backups.",
            "Backup files are validated before anything is overwritten.",
            "Version numbers shown in the app are now taken from the build itself.",
        ),
    ),
    ChangelogEntry(
        version = "2.0",
        date = "26 Aug 2026",
        changes = listOf(
            "System back now pops one screen at a time, with double-back-to-exit on Home.",
            "Floating navigation bar, with a toggle to turn it off.",
            "Refined the floating-particles background.",
        ),
    ),
    ChangelogEntry(
        version = "1.0",
        date = "26 Aug 2026",
        changes = listOf(
            "First release.",
            "Accent colours are full palettes that theme the entire app.",
            "AI-generated quizzes using your own provider key, with a local question bank as a fallback.",
        ),
    ),
)

// ---------------------------------------------------------------------------
// Licences
// ---------------------------------------------------------------------------

/** Nazo's own licence, shown as the highlighted card on the Licenses screen. */
const val NAZO_LICENSE_ID = "GPL-3.0"

/**
 * The standard GPL "this program is free software" paragraph. The full text
 * lives in `LICENSE` at the repository root, which the screen links to.
 */
const val NAZO_LICENSE_SUMMARY: String =
    "Nazo is free software: you can redistribute it and/or modify it under the " +
        "terms of the GNU General Public License as published by the Free Software " +
        "Foundation, either version 3 of the License, or (at your option) any " +
        "later version."

/** A third-party dependency, as declared in gradle/libs.versions.toml. */
data class ThirdPartyLibrary(
    val name: String,
    val version: String,
    val license: String,
    val url: String,
)

/**
 * Third-party libraries Nazo ships.
 *
 * Versions that come from the Compose BOM are shown as the BOM's version, since
 * that is what actually pins them. Keep in sync with libs.versions.toml.
 */
val NAZO_LIBRARIES: List<ThirdPartyLibrary> = listOf(
    ThirdPartyLibrary(
        "Activity Compose", "1.11.0", "Apache-2.0",
        "https://developer.android.com/jetpack/androidx/releases/activity",
    ),
    ThirdPartyLibrary(
        "Coil", "2.7.0", "Apache-2.0",
        "https://github.com/coil-kt/coil",
    ),
    ThirdPartyLibrary(
        "Compose BOM", "2025.10.01", "Apache-2.0",
        "https://developer.android.com/jetpack/compose/bom",
    ),
    ThirdPartyLibrary(
        "Compose Material 3", "2025.10.01", "Apache-2.0",
        "https://developer.android.com/jetpack/androidx/releases/compose-material3",
    ),
    ThirdPartyLibrary(
        "Compose UI", "2025.10.01", "Apache-2.0",
        "https://developer.android.com/jetpack/androidx/releases/compose-ui",
    ),
    ThirdPartyLibrary(
        "Core KTX", "1.17.0", "Apache-2.0",
        "https://developer.android.com/jetpack/androidx/releases/core",
    ),
    ThirdPartyLibrary(
        "Core SplashScreen", "1.2.0", "Apache-2.0",
        "https://developer.android.com/jetpack/androidx/releases/core",
    ),
    ThirdPartyLibrary(
        "Kotlin Standard Library", "2.1.0", "Apache-2.0",
        "https://github.com/JetBrains/kotlin",
    ),
    ThirdPartyLibrary(
        "Kotlin Coroutines", "2.1.0", "Apache-2.0",
        "https://github.com/Kotlin/kotlinx.coroutines",
    ),
    ThirdPartyLibrary(
        "Lifecycle Runtime", "2.9.2", "Apache-2.0",
        "https://developer.android.com/jetpack/androidx/releases/lifecycle",
    ),
    ThirdPartyLibrary(
        "Material Icons Extended", "2025.10.01", "Apache-2.0",
        "https://developer.android.com/jetpack/androidx/releases/compose-material",
    ),
    ThirdPartyLibrary(
        "WorkManager", "2.9.1", "Apache-2.0",
        "https://developer.android.com/jetpack/androidx/releases/work",
    ),
)

// ---------------------------------------------------------------------------
// Credits
// ---------------------------------------------------------------------------

/** Someone who worked on Nazo. */
data class Contributor(
    val name: String,
    val role: String,
    val detail: String,
    /** Null for entries with nowhere to link (e.g. a tool rather than a person). */
    val url: String?,
)

/**
 * Everyone who has contributed. Short by design — this is the honest list, not
 * a padded one.
 */
val NAZO_CONTRIBUTORS: List<Contributor> = listOf(
    Contributor(
        name = "ThatOn3Gu7",
        role = "Lead developer",
        detail = "Created Nazo; design, direction and the majority of the app.",
        url = "https://github.com/ThatOn3Gu7",
    ),
    Contributor(
        name = "Arena AI",
        role = "Coding agent",
        detail = "Paired on features, bug fixes and refactors from v5.0 onwards.",
        url = "https://arena.ai",
    ),
)

/** Services the app can talk to. Shown under the contributors list. */
val NAZO_SERVICES: List<String> = listOf(
    "Google Gemini, OpenRouter and OpenCode Zen — optional AI question generation",
    "AniList, Jikan, Kitsu, Fandom, Wikimedia and Openverse — guessing-game artwork",
    "GitHub — update checks and releases",
)
