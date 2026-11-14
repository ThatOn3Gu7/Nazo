package quiz.thaton3app.nazo.modes.guessing_game

import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

/**
 * Strict Safe Search request construction plus a defensive local result gate.
 *
 * DuckDuckGo documents `kp=1` as strict Safe Search. Its reverse-engineered
 * image endpoint uses `p=1`; Nazo sends both so the token page and i.js request
 * agree. This is still not a parental-control guarantee, so marked explicit
 * results, malformed entries, non-HTTPS URLs and results with no target
 * evidence are also rejected locally before any image bytes are downloaded.
 */
internal object DuckDuckGoImagePolicy {
    private val explicitMarker = Regex(
        "(?:^|[^a-z0-9])(?:nsfw|porn(?:ography)?|hentai|ecchi|nude|nudity|explicit|" +
            "rule[ ._-]?34|r34|xxx|sex(?:ual)?|booru)(?:[^a-z0-9]|$)",
        RegexOption.IGNORE_CASE,
    )

    fun tokenUrl(query: String): String =
        "https://duckduckgo.com/?q=${encode(query)}&iax=1&ia=images&kp=1"

    fun resultsUrl(query: String, vqd: String): String =
        "https://duckduckgo.com/i.js?q=${encode(query)}&vqd=${encode(vqd)}" +
            "&o=json&p=1&kp=1&l=wt-wt"

    fun selectCandidate(json: JSONObject, targetName: String): String? {
        val results = json.optJSONArray("results") ?: return null
        for (index in 0 until results.length()) {
            val result = results.optJSONObject(index) ?: continue
            val image = result.optString("image", "")
            val thumbnail = result.optString("thumbnail", "")
            val candidate = image.ifBlank { thumbnail }
            val title = result.optString("title", "")
            val source = result.optString("source", "")
            val pageUrl = result.optString("url", "")
            if (listOf(candidate, title, source, pageUrl).any(::looksExplicit)) continue
            if (!isSafeImageUrl(candidate)) continue
            if (!hasTargetEvidence("$title $pageUrl $candidate", targetName)) continue
            return candidate
        }
        return null
    }

    internal fun looksExplicit(value: String): Boolean = explicitMarker.containsMatchIn(value)

    private fun isSafeImageUrl(raw: String): Boolean {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null) return false
        if (uri.port !in listOf(-1, 443)) return false
        val host = uri.host?.lowercase() ?: return false
        val path = uri.path.orEmpty().lowercase()
        return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") ||
            path.endsWith(".webp") || path.endsWith(".gif") ||
            (host.endsWith("bing.net") && path.startsWith("/th"))
    }

    private fun hasTargetEvidence(value: String, targetName: String): Boolean {
        val words = targetWords(targetName)
        if (words.isEmpty()) return false
        val normalized = value.lowercase()
        val present = words.count { normalized.contains(it) }
        val longest = words.maxByOrNull { it.length } ?: return false
        return present == words.size ||
            (present * 2 > words.size && normalized.contains(longest))
    }

    private fun targetWords(value: String): List<String> = value.lowercase()
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length >= 3 }

    private fun encode(raw: String): String = URLEncoder.encode(raw, "UTF-8").replace("+", "%20")
}
