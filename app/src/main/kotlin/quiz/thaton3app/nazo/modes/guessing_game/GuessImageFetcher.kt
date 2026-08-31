package quiz.thaton3app.nazo.modes.guessing_game

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Resolves a direct image URL of the round's TARGET ENTITY using keyless
 * public sources. Relevance is the whole game here: the first hit of a plain
 * keyword search is often a topic-branded logo or an unrelated article image
 * (a "Tokyo One Piece Tower" logo for a Roronoa Zoro round), so every source
 * is searched by EXACT PHRASE and its results are gated by how closely the
 * file/article title mentions the target name:
 *   1. Wikimedia Commons phrase search (bitmap files, title must match),
 *   2. English Wikipedia phrase search (article title must match),
 *   3. the same two stages with the AI's image_query as search phrase,
 *   4. DuckDuckGo's image endpoint (query = target name, loose URL check).
 * The whole search is capped by a total time budget so a slow network can
 * never stall a round. Returns null on ANY failure or when nothing relevant
 * is found; the UI then shows its drawn placeholder (with the query) instead
 * of a wrong image.
 */
object GuessImageFetcher {

    private const val TAG = "NazoGuessImage"
    private const val USER_AGENT = "NazoQuizApp/4.0 (Android quiz app; https://github.com/ThatOn3Gu7/Nazo)"
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val TOTAL_BUDGET_MS = 20_000L

    /** Minimum title/article relevance for a hit to be considered (see [titleRelevance]). */
    private const val MIN_RELEVANCE = 2

    suspend fun fetchImageUrl(query: String, target: String): String? {
        if (target.isBlank()) return null
        val targetName = target.trim()
        return withContext(Dispatchers.IO) {
            val url: String? = try {
                withTimeout(TOTAL_BUDGET_MS) {
                    val byTarget = fromCommons(targetName, targetName)
                        ?: fromWikipedia(targetName, targetName)
                    val byQuery = if (query.isNotBlank() && query.trim().lowercase() != targetName.lowercase())
                        fromCommons(query.trim(), targetName) ?: fromWikipedia(query.trim(), targetName)
                    else null
                    byTarget ?: byQuery ?: fromDuckDuckGo(targetName)
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "image search timed out for \"$targetName\"")
                null
            } catch (e: Exception) {
                Log.w(TAG, "image search failed for \"$targetName\"", e)
                null
            }
            if (url == null) {
                Log.w(TAG, "no relevant image for \"$targetName\" (query: \"$query\")")
            } else {
                Log.i(TAG, "image for \"$targetName\" -> $url")
            }
            url
        }
    }

    /**
     * Plain-HTTP download of the image bytes. Used by the play screen to
     * pre-warm the mystery image BEFORE the countdown starts, so the timer
     * only ever runs against pixels Coil can decode instantly (passed to
     * AsyncImage as a ByteArray model — no ImageRequest needed).
     */
    fun fetchImageBytes(url: String): ByteArray? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            Log.w(TAG, "image byte fetch failed: $url", e)
            null
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Commons: one generator=search call (phrase, bitmaps, top 10) returns
     * the file URLs directly; the best title match wins.
     */
    private fun fromCommons(searchPhrase: String, targetName: String): String? {
        val json = getJson(
            "https://commons.wikimedia.org/w/api.php?action=query&format=json&redirects=1" +
                "&generator=search&gsrnamespace=6&gsrlimit=10&prop=imageinfo&iiprop=url&iiurlwidth=1024" +
                "&gsrsearch=${enc("\"$searchPhrase\" filetype:bitmap")}",
        ) ?: return null
        val pages = json.optJSONObject("query")?.optJSONObject("pages") ?: return null
        var bestUrl: String? = null
        var bestScore = 0
        for (key in pages.keys()) {
            val page = pages.optJSONObject(key) ?: continue
            val title = page.optString("title", "")
            val score = titleRelevance(title, targetName)
            if (score < bestScore) continue
            val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            val url = info.optString("thumburl", "").ifBlank { info.optString("url", "") }
            if (!isUsableImage(url)) continue
            bestScore = score
            bestUrl = url
        }
        return if (bestScore >= MIN_RELEVANCE) bestUrl else null
    }

    /**
     * Wikipedia: phrase search, then the ARTICLE TITLE is gated before any
     * summary fetch — for target "Trafalgar Law" the articles "Trafalgar"
     * or "One Piece" fail the gate, "Trafalgar Law (One Piece)" passes.
     */
    private fun fromWikipedia(searchPhrase: String, targetName: String): String? {
        val titles = getJson(
            "https://en.wikipedia.org/w/api.php?action=query&format=json" +
                "&list=search&srsearch=${enc("\"$searchPhrase\"")}&srlimit=5",
        ) ?: return null
        val results = titles.optJSONObject("query")?.optJSONArray("search") ?: return null
        for (i in 0 until results.length()) {
            val title = results.getJSONObject(i).optString("title", "")
            if (title.isBlank() || titleRelevance(title, targetName) < MIN_RELEVANCE) continue
            val summary = getJson("https://en.wikipedia.org/api/rest_v1/page/summary/${enc(title)}") ?: continue
            val original = summary.optJSONObject("originalimage")?.optString("source", "") ?: ""
            val thumb = summary.optJSONObject("thumbnail")?.optString("source", "") ?: ""
            val url = original.ifBlank { thumb }
            if (isUsableImage(url)) return url
        }
        return null
    }

    /**
     * Last resort: DuckDuckGo's image endpoint, queried with the target name
     * itself. GET the image-search HTML (browser UA) for the per-session
     * `vqd` token, then the JSON i.js API. Bing often serves thumbnails
     * without file extensions, so URL shape is checked loosely; opaque
     * hosts (YouTube thumbs) are trusted, slugs must mention the target.
     */
    private fun fromDuckDuckGo(targetName: String): String? {
        val html = getText(
            "https://duckduckgo.com/?q=${enc(targetName)}&iax=1&ia=images",
            userAgent = BROWSER_USER_AGENT,
        ) ?: return null
        val vqd = listOf(
            Regex("""vqd="(-?\d+)""""),
            Regex("""data-vqd="(-?\d+)""""),
            Regex("""vqd-0" value="(-?\d+)""""),
            Regex("""vqd=(-?\d+)"""),
        ).firstNotNullOfOrNull { it.find(html)?.groupValues?.get(1) } ?: return null
        val json = getJson(
            "https://duckduckgo.com/i.js?q=${enc(targetName)}&vqd=$vqd&p=1",
            userAgent = BROWSER_USER_AGENT,
        ) ?: return null
        val results = json.optJSONArray("results") ?: return null
        var first: String? = null
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            val url = r.optString("image", "").ifBlank { r.optString("thumbnail", "") }
            if (!(url.startsWith("https://") || url.startsWith("http://"))) continue
            if (first == null) first = url
            if (urlMatchesTarget(url, targetName)) return url
        }
        // Last resort of last resorts: the top ranked hit for the exact
        // target name — better than a placeholder, never a topic keyword.
        return first
    }

    /** Normalized content words (letters/digits, length >= 3) of the target name. */
    private fun targetWords(targetName: String): List<String> =
        targetName.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 }

    /**
     * 3 = every content word present, 2 = at least half present,
     * 1 = only the longest word present, 0 = nothing. "Zoro (One Piece)"
     * scores 2 for target "Roronoa Zoro" (kept); a "Tokyo One Piece Tower"
     * logo scores 0 (dropped).
     */
    private fun titleRelevance(title: String, targetName: String): Int {
        val words = targetWords(targetName)
        if (words.isEmpty()) return 0
        val t = title.lowercase()
        val present = words.count { t.contains(it) }
        return when {
            present == words.size -> 3
            present * 2 >= words.size -> 2
            t.contains(words.maxByOrNull { it.length }!!) -> 1
            else -> 0
        }
    }

    /**
     * DDG/Bing URLs are loose: opaque image hosts (no slug to inspect) are
     * trusted, anything with a readable path must mention the target.
     */
    private fun urlMatchesTarget(url: String, targetName: String): Boolean {
        val words = targetWords(targetName)
        if (words.isEmpty()) return true
        val u = url.lowercase()
        if (u.contains(words.joinToString(" "))) return true
        if (u.contains("ytimg.com") || u.contains("bing.net/th")) return true
        return u.contains(words.maxByOrNull { it.length }!!) || words.count { u.contains(it) } * 2 >= words.size
    }

    /** HTTPS only, and a format Coil can decode (no SVG — the svg module isn't included). */
    private fun isUsableImage(url: String): Boolean {
        if (!url.startsWith("https://")) return false
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") ||
            path.endsWith(".webp") || path.endsWith(".gif")
    }

    private fun enc(raw: String): String = try {
        URLEncoder.encode(raw, "UTF-8").replace("+", "%20")
    } catch (e: UnsupportedEncodingException) {
        raw
    }

    private fun getJson(url: String, userAgent: String = USER_AGENT): JSONObject? {
        val raw = getText(url, userAgent) ?: return null
        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            Log.w(TAG, "JSON parse failed: $url", e)
            null
        }
    }

    private fun getText(url: String, userAgent: String = USER_AGENT): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", userAgent)
            if (url.contains("api.php") || url.contains("rest_v1") || url.contains("i.js")) {
                setRequestProperty("Accept", "application/json")
            }
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "GET failed: $url", e)
            null
        } finally {
            connection.disconnect()
        }
    }
}
