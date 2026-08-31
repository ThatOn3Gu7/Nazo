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
 * Resolves a direct image URL for a search query using keyless public sources,
 * in order:
 *   1. Wikimedia Commons file search (best anime-art source, bitmaps only),
 *   2. English Wikipedia page images (2x-size summary thumbnail),
 *   3. DuckDuckGo's image endpoint as a last resort (finds obscure targets the
 *      wikis don't carry, e.g. niche jutsu or side characters).
 *
 * The query is tried as-is and then with trailing qualifiers dropped, so
 * "Satoru Gojo Jujutsu Kaisen character portrait" also tries
 * "Satoru Gojo Jujutsu Kaisen character". The whole search is capped by a
 * total time budget so a slow network can never stall a round.
 *
 * Returns null on ANY failure; the UI falls back to its drawn placeholder.
 */
object GuessImageFetcher {

    private const val TAG = "NazoGuessImage"
    private const val USER_AGENT = "NazoQuizApp/4.0 (Android quiz app; https://github.com/ThatOn3Gu7/Nazo)"
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val TOTAL_BUDGET_MS = 20_000L

    suspend fun fetchImageUrl(query: String): String? {
        if (query.isBlank()) return null
        return withContext(Dispatchers.IO) {
            val variants = queryVariants(query)
            val primary = variants.first()
            val url: String? = try {
                withTimeout(TOTAL_BUDGET_MS) {
                    fromCommons(primary)
                        ?: fromWikipedia(primary)
                        ?: variants.getOrNull(1)?.let { fromCommons(it) ?: fromWikipedia(it) }
                        ?: fromDuckDuckGo(primary)
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "image search timed out for \"$query\"")
                null
            } catch (e: Exception) {
                Log.w(TAG, "image search failed for \"$query\"", e)
                null
            }
            if (url == null) {
                Log.w(TAG, "no image found for \"$query\" (tried $variants, +DuckDuckGo)")
            } else {
                Log.i(TAG, "image for \"$query\" -> $url")
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

    /** Full query first, then progressively shorter prefixes (drop trailing qualifiers). */
    private fun queryVariants(query: String): List<String> {
        val out = mutableListOf<String>()
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        for (n in words.size downTo 2) {
            val v = words.take(n).joinToString(" ")
            if (v.isNotBlank() && v !in out) out.add(v)
            if (out.size >= 3) break
        }
        if (out.isEmpty()) out.add(query.trim())
        return out
    }

    /** Commons search returns actual files (bitmaps only), so it's the best anime-art source. */
    private fun fromCommons(query: String): String? {
        val titles = getJson(
            "https://commons.wikimedia.org/w/api.php?action=query&format=json" +
                "&list=search&srsearch=${enc(query)}+filetype:bitmap&srnamespace=6&srlimit=5",
        ) ?: return null
        val results = titles.optJSONObject("query")?.optJSONArray("search") ?: return null
        for (i in 0 until results.length()) {
            val title = results.getJSONObject(i).optString("title", "")
            if (title.isBlank()) continue
            val json = getJson(
                "https://commons.wikimedia.org/w/api.php?action=query&format=json&redirects=1" +
                    "&titles=${enc(title)}&prop=imageinfo&iiprop=url&iiurlwidth=1024",
            ) ?: continue
            val pages = json.optJSONObject("query")?.optJSONObject("pages") ?: continue
            for (key in pages.keys()) {
                val p = pages.optJSONObject(key) ?: continue
                val info = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val url = info.optString("thumburl", "").ifBlank { info.optString("url", "") }
                if (isUsableImage(url)) return url
            }
        }
        return null
    }

    /** Wikipedia fallback: the REST summary's `originalimage` is a 2x-size
     *  thumbnail — far lighter than the full-resolution original. */
    private fun fromWikipedia(query: String): String? {
        val titles = getJson(
            "https://en.wikipedia.org/w/api.php?action=query&format=json" +
                "&list=search&srsearch=${enc(query)}&srlimit=3",
        ) ?: return null
        val results = titles.optJSONObject("query")?.optJSONArray("search") ?: return null
        for (i in 0 until results.length()) {
            val title = results.getJSONObject(i).optString("title", "")
            if (title.isBlank()) continue
            val json = getJson("https://en.wikipedia.org/api/rest_v1/page/summary/${enc(title)}") ?: continue
            val original = json.optJSONObject("originalimage")?.optString("source", "") ?: ""
            val thumb = json.optJSONObject("thumbnail")?.optString("source", "") ?: ""
            val url = original.ifBlank { thumb }
            if (isUsableImage(url)) return url
        }
        return null
    }

    /**
     * Last resort: DuckDuckGo's image endpoint. GET the image-search HTML page
     * (which embeds a per-session `vqd` token), then hit the JSON i.js API with
     * it. URL shape is checked loosely — DDG often serves Bing-hosted
     * thumbnails without a file extension, which Coil still decodes fine.
     */
    private fun fromDuckDuckGo(query: String): String? {
        val html = getText(
            "https://duckduckgo.com/?q=${enc(query)}&iax=1&ia=images",
            userAgent = BROWSER_USER_AGENT,
        ) ?: return null
        val vqd = listOf(
            Regex("""vqd="(-?\d+)"""),
            Regex("""data-vqd="(-?\d+)"""),
            Regex("""vqd-0" value="(-?\d+)"""),
            Regex("""vqd=(-?\d+)"""),
        ).firstNotNullOfOrNull { it.find(html)?.groupValues?.get(1) } ?: return null
        val json = getJson(
            "https://duckduckgo.com/i.js?q=${enc(query)}&vqd=$vqd&p=1",
            userAgent = BROWSER_USER_AGENT,
        ) ?: return null
        val results = json.optJSONArray("results") ?: return null
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            val url = r.optString("image", "").ifBlank { r.optString("thumbnail", "") }
            if (url.startsWith("https://") || url.startsWith("http://")) return url
        }
        return null
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
