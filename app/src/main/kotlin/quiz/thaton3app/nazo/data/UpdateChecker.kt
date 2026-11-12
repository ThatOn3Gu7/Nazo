package quiz.thaton3app.nazo.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// GitHub repo for THIS app (used by the About-screen update check). Mirrors the
// structure of the reference project's UpdateChecker, but is self-contained: it only
// relies on HttpURLConnection + org.json, both already used elsewhere in the app.
const val GITHUB_REPO = "ThatOn3Gu7/Nazo"

// Markers written by .github/scripts/gen_release_notes.py around the curated,
// player-facing part of the notes. They are HTML comments, so GitHub shows
// nothing for them. Changing either string means changing that script too.
private const val NOTES_START_MARKER = "<!--NAZO_NOTES_START-->"
private const val NOTES_END_MARKER = "<!--NAZO_NOTES_END-->"

// Compiled once rather than per line.
private val COMMIT_LINE_RE = Regex("""^[-*]\s+[0-9a-f]{7,40}\s+""")
private val HEADING_RE = Regex("""^#{1,6}\s*""")
private val BULLET_RE = Regex("""^[-*]\s+""")
private val BOLD_RE = Regex("""\*\*(.+?)\*\*""")
private val CODE_RE = Regex("""`(.+?)`""")
private val BLANK_RUN_RE = Regex("""\n{3,}""")

data class GitHubRelease(
    val tag: String,
    val htmlUrl: String,
    val body: String,
    val apkUrl: String?,
    val apkSizeBytes: Long = -1L,
    val prerelease: Boolean = false,
)

/**
 * Fetches the newest release in the selected channel.
 *
 * GitHub's `/releases/latest` endpoint intentionally hides pre-releases, so the
 * default path remains a single stable-only request. Opted-in users use the
 * release list instead; drafts are discarded and semantic version ordering,
 * rather than GitHub's creation order, chooses between stable, beta and RC
 * builds.
 */
suspend fun fetchLatestRelease(
    repo: String = GITHUB_REPO,
    includePrereleases: Boolean = false,
): GitHubRelease? = withContext(Dispatchers.IO) {
    try {
        val endpoint = if (includePrereleases) {
            "https://api.github.com/repos/$repo/releases?per_page=30"
        } else {
            "https://api.github.com/repos/$repo/releases/latest"
        }
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10000
            readTimeout = 10000
        }
        try {
            if (conn.responseCode != 200) return@withContext null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            if (includePrereleases) {
                parseReleaseList(text, includePrereleases = true)
            } else {
                parseGitHubRelease(text)?.takeUnless { it.prerelease }
            }
        } finally {
            conn.disconnect()
        }
    } catch (_: Exception) {
        null
    }
}

/** Parses one GitHub release response. Drafts and malformed tags are ignored. */
internal fun parseGitHubRelease(rawJson: String): GitHubRelease? =
    parseGitHubRelease(JSONObject(rawJson))

/** Parses a GitHub release-list response and selects its highest allowed tag. */
internal fun parseReleaseList(
    rawJson: String,
    includePrereleases: Boolean,
): GitHubRelease? {
    val array = JSONArray(rawJson)
    val releases = buildList {
        for (index in 0 until array.length()) {
            parseGitHubRelease(array.getJSONObject(index))?.let(::add)
        }
    }
    return selectHighestRelease(releases, includePrereleases)
}

private fun parseGitHubRelease(json: JSONObject): GitHubRelease? {
    if (json.optBoolean("draft", false)) return null

    val tag = json.optString("tag_name").trim()
    val parsedVersion = parseVersion(tag) ?: return null

    val (apkUrl, apkSize) = preferredApk(json.optJSONArray("assets"))
    return GitHubRelease(
        tag = tag,
        htmlUrl = json.optString("html_url"),
        body = playerFacingNotes(json.optString("body")),
        apkUrl = apkUrl,
        apkSizeBytes = apkSize,
        // A suffixed tag is never allowed onto the stable channel, even if a
        // manually created GitHub release forgot to set its prerelease flag.
        prerelease = json.optBoolean("prerelease", false) || parsedVersion.prerelease != null,
    )
}

/**
 * Picks the installable release APK rather than the alphabetically first APK.
 * Debug assets sort before release assets and are signed with the wrong key for
 * upgrading a normal installation, so an explicit release build always wins.
 */
private fun preferredApk(assets: JSONArray?): Pair<String?, Long> {
    if (assets == null) return null to -1L

    var releaseAsset: Pair<String, Long>? = null
    var nonDebugAsset: Pair<String, Long>? = null
    var lastResortAsset: Pair<String, Long>? = null

    for (index in 0 until assets.length()) {
        val asset = assets.getJSONObject(index)
        val name = asset.optString("name")
        if (!name.endsWith(".apk", ignoreCase = true)) continue

        val candidate = asset.optString("browser_download_url") to asset.optLong("size", -1L)
        val lowerName = name.lowercase()
        when {
            lowerName.contains("release") -> releaseAsset = candidate
            !lowerName.contains("debug") && nonDebugAsset == null -> nonDebugAsset = candidate
            lastResortAsset == null -> lastResortAsset = candidate
        }
    }

    return releaseAsset ?: nonDebugAsset ?: lastResortAsset ?: (null to -1L)
}

/**
 * Extracts the player-facing part of a GitHub release body.
 *
 * `gen_release_notes.py` wraps a short curated "What's New" list in HTML-comment
 * markers, then appends the full commit log inside a <details> block. GitHub
 * renders that nicely, but the app was showing the *whole* body verbatim — so
 * players saw raw `<details>`/`<summary>` tags, commit hashes and every trivial
 * `chore:`/`ci:` commit.
 *
 * This keeps only the marked section and strips the Markdown that has no
 * meaning in a plain [androidx.compose.material3.Text].
 *
 * Releases published before the markers existed have none, so we fall back to
 * sanitising the whole body: drop HTML blocks and hash-prefixed commit lines.
 */
fun playerFacingNotes(rawBody: String): String {
    if (rawBody.isBlank()) return ""

    val start = rawBody.indexOf(NOTES_START_MARKER)
    val end = rawBody.indexOf(NOTES_END_MARKER)
    val section = if (start >= 0 && end > start) {
        rawBody.substring(start + NOTES_START_MARKER.length, end)
    } else {
        // Legacy release (published before the markers existed): keep only the
        // commit SUBJECTS and drop the rest. Everything indented belongs to a
        // <details> body, so indentation is the reliable signal here — the
        // subject lines all start at column 0.
        rawBody
            .lineSequence()
            .filter { line ->
                val t = line.trim()
                when {
                    t.isEmpty() -> false
                    // Anything indented is commit-description detail.
                    line != line.trimStart() -> false
                    t.startsWith("<") -> false            // <details>, <summary>
                    t.startsWith("**Full Changelog**") -> false
                    t.startsWith("#") -> false            // "## What's Changed"
                    else -> true
                }
            }
            // "- a1b2c3d feat(home): subject" -> "feat(home): subject"
            .map { it.trim().replace(COMMIT_LINE_RE, "• ") }
            .joinToString("\n")
    }

    return section
        .lineSequence()
        .map { line ->
            line.trim()
                // Headings: "## What's New" / "### Fixed" -> plain text.
                .replace(HEADING_RE, "")
                // Bullets: normalise "-" and "*" to a real bullet.
                .replace(BULLET_RE, "• ")
                // Inline emphasis/code markers that would show as literals.
                .replace(BOLD_RE, "\$1")
                .replace(CODE_RE, "\$1")
        }
        .joinToString("\n")
        .replace(BLANK_RUN_RE, "\n\n")
        .trim()
}

private sealed interface VersionPart {
    data class Number(val value: Int) : VersionPart
    data class Text(val value: String) : VersionPart
}

private data class ParsedVersion(
    val core: List<Int>,
    val prerelease: List<VersionPart>?,
)

private val VERSION_PART_RE = Regex("[A-Za-z]+|\\d+")
private val PRE_RELEASE_RANK = mapOf(
    "dev" to 0,
    "snapshot" to 0,
    "alpha" to 1,
    "a" to 1,
    "beta" to 2,
    "b" to 2,
    "preview" to 2,
    "rc" to 3,
)

private fun parseVersion(raw: String): ParsedVersion? {
    val cleaned = raw.trim().trimStart('v', 'V').substringBefore('+')
    if (cleaned.isBlank()) return null

    val dash = cleaned.indexOf('-')
    val coreText = if (dash >= 0) cleaned.substring(0, dash) else cleaned
    val suffixText = if (dash >= 0) cleaned.substring(dash + 1) else null
    val coreParts = coreText.split('.')
    if (coreParts.isEmpty() || coreParts.any { it.isBlank() || it.toIntOrNull() == null }) return null

    val prerelease = suffixText?.let { suffix ->
        if (suffix.isBlank()) return null
        VERSION_PART_RE.findAll(suffix).map { match ->
            match.value.toIntOrNull()?.let(VersionPart::Number)
                ?: VersionPart.Text(match.value.lowercase())
        }.toList().takeIf { it.isNotEmpty() } ?: return null
    }
    return ParsedVersion(coreParts.map { it.toInt() }, prerelease)
}

private fun compareParsedVersions(left: ParsedVersion, right: ParsedVersion): Int {
    val coreSize = maxOf(left.core.size, right.core.size)
    for (index in 0 until coreSize) {
        val comparison = left.core.getOrElse(index) { 0 }
            .compareTo(right.core.getOrElse(index) { 0 })
        if (comparison != 0) return comparison
    }

    val leftPre = left.prerelease
    val rightPre = right.prerelease
    if (leftPre == null || rightPre == null) {
        return when {
            leftPre == null && rightPre == null -> 0
            leftPre == null -> 1
            else -> -1
        }
    }

    val partCount = minOf(leftPre.size, rightPre.size)
    for (index in 0 until partCount) {
        val leftPart = leftPre[index]
        val rightPart = rightPre[index]
        val comparison = when {
            leftPart is VersionPart.Number && rightPart is VersionPart.Number ->
                leftPart.value.compareTo(rightPart.value)
            leftPart is VersionPart.Text && rightPart is VersionPart.Text -> {
                val leftRank = PRE_RELEASE_RANK[leftPart.value]
                val rightRank = PRE_RELEASE_RANK[rightPart.value]
                when {
                    leftRank != null && rightRank != null -> leftRank.compareTo(rightRank)
                    leftRank != null -> -1
                    rightRank != null -> 1
                    else -> leftPart.value.compareTo(rightPart.value)
                }
            }
            leftPart is VersionPart.Number -> -1
            else -> 1
        }
        if (comparison != 0) return comparison
    }
    return leftPre.size.compareTo(rightPre.size)
}

/** Returns null when either tag is not a version. */
internal fun compareVersionTags(left: String, right: String): Int? {
    val parsedLeft = parseVersion(left) ?: return null
    val parsedRight = parseVersion(right) ?: return null
    return compareParsedVersions(parsedLeft, parsedRight)
}

internal fun selectHighestRelease(
    releases: List<GitHubRelease>,
    includePrereleases: Boolean,
): GitHubRelease? = releases
    .asSequence()
    .filter { includePrereleases || !it.prerelease }
    .mapNotNull { release -> parseVersion(release.tag)?.let { release to it } }
    .maxWithOrNull(Comparator { left, right -> compareParsedVersions(left.second, right.second) })
    ?.first

fun isNewerVersion(latest: String, current: String): Boolean =
    compareVersionTags(latest, current)?.let { it > 0 } ?: false

fun currentVersionName(context: Context): String? =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()
