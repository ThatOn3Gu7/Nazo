package quiz.thaton3app.nazo.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
)

suspend fun fetchLatestRelease(repo: String = GITHUB_REPO): GitHubRelease? =
    withContext(Dispatchers.IO) {
        try {
            val conn = (URL("https://api.github.com/repos/$repo/releases/latest")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10000
                readTimeout = 10000
            }
            if (conn.responseCode != 200) {
                conn.disconnect()
                return@withContext null
            }
            val text = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = JSONObject(text)
            val tag = json.optString("tag_name")
            val html = json.optString("html_url")
            val body = playerFacingNotes(json.optString("body"))

            var apkUrl: String? = null
            var apkSize = -1L
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        apkSize = asset.optLong("size", -1L)
                        break
                    }
                }
            }

            if (tag.isBlank()) null else GitHubRelease(tag, html, body, apkUrl, apkSize)
        } catch (_: Exception) {
            null
        }
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

fun isNewerVersion(latest: String, current: String): Boolean {
    fun split(v: String): Pair<List<Int>, String?> {
        val cleaned = v.trim().trimStart('v', 'V')
        val dash = cleaned.indexOf('-')
        val base = if (dash >= 0) cleaned.substring(0, dash) else cleaned
        val suffix = if (dash >= 0) cleaned.substring(dash + 1) else null
        return base.split('.').mapNotNull { it.toIntOrNull() } to suffix
    }
    val (a, aSuffix) = split(latest)
    val (b, bSuffix) = split(current)
    if (a.isEmpty() || b.isEmpty()) return false
    val n = maxOf(a.size, b.size)
    for (i in 0 until n) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return when {
        aSuffix == null && bSuffix == null -> false
        aSuffix == null -> true
        else -> false
    }
}

fun currentVersionName(context: Context): String? =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()
