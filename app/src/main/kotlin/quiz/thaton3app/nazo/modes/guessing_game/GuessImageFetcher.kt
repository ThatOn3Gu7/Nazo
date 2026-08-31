package quiz.thaton3app.nazo.modes.guessing_game

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Resolves a direct image URL for a search query using keyless public APIs —
 * Wikimedia Commons file search first, then the English Wikipedia page images
 * — so the guessing game needs no image-search API key.
 *
 * Returns null on ANY failure (network error, no results, unusable format);
 * the UI falls back to its drawn placeholder, so a bad fetch never blocks a round.
 */
object GuessImageFetcher {

    private const val TAG = "NazoGuessImage"
    private const val USER_AGENT = "NazoQuizApp/4.0 (Android quiz app; https://github.com/ThatOn3Gu7/Nazo)"

    suspend fun fetchImageUrl(query: String): String? {
        if (query.isBlank()) return null
        return withContext(Dispatchers.IO) {
            val url = runCatching {
                fromCommons(query) ?: fromWikipedia(query)
            }.getOrNull()
            if (url == null) Log.w(TAG, "no image found for \"$query\"")
            url
        }
    }

    /** Commons search returns actual files, so it's the best anime-art source. */
    private fun fromCommons(query: String): String? {
        val titles = getJson(
            "https://commons.wikimedia.org/w/api.php?action=query&format=json" +
                "&list=search&srsearch=${enc(query)}&srnamespace=6&srlimit=5",
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
            for ((_, page) in pages) {
                val p = page as? JSONObject ?: continue
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
            val original = json.optJSONObject("originalimage")?.optString("source", "")
            val thumb = json.optJSONObject("thumbnail")?.optString("source", "")
            val url = original.ifBlank { thumb }
            if (isUsableImage(url)) return url
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

    private fun getJson(url: String): JSONObject? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } catch (e: Exception) {
            Log.w(TAG, "GET failed: $url", e)
            null
        } finally {
            connection.disconnect()
        }
    }
}
