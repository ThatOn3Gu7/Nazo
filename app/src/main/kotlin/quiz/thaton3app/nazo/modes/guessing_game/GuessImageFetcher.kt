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
 * public sources, in order:
 *
 *   1. Wikimedia Commons — exact-phrase file search (bitmaps, top 10)
 *   2. English Wikipedia — exact-phrase article search, summary image
 *   3. AniList — character search (keyless GraphQL; anime character
 *      portraits — this is an anime game, so this is a strong source)
 *   4. Openverse — keyless Creative-Commons image search (WordPress index)
 *   5. DuckDuckGo — image endpoint (i.js + vqd token) as last resort
 *
 * Stages 1-2 are tried for every name variant: the target name, its
 * honorific-stripped form ("Dr. Vegapunk" -> "Vegapunk") and any AI alias
 * that shares a content word with the target. EVERY result is relevance-
 * gated: the file/article/character title must score >= 2 against a name
 * variant (all words = 3, half+ = 2, longest word only = 1). A topic-
 * branded logo or an unrelated first hit scores 0 and is dropped — a wrong
 * image is treated as a miss.
 *
 * The whole search runs under a total time budget AND each stage re-checks
 * the remaining budget, so a slow network burns the early stages but the
 * later ones still get a fair shot. Returns null on ANY failure; the UI
 * then shows its drawn placeholder (with the image_query) instead.
 */
object GuessImageFetcher {

    private const val TAG = "NazoGuessImage"
    private const val USER_AGENT = "NazoQuizApp/4.0 (Android quiz app; https://github.com/ThatOn3Gu7/Nazo)"
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val TOTAL_BUDGET_MS = 20_000L

    /** Minimum title relevance for a hit to be considered (see [titleRelevance]). */
    private const val MIN_RELEVANCE = 2

    /** A stage only starts when at least this much budget remains. */
    private const val MIN_STAGE_BUDGET_MS = 2_000L

    /**
     * [target] is the round's target_entity; [aliases] are the AI's
     * alternative names for the same entity; [imageQuery] is only used to
     * enrich the DuckDuckGo query when it starts with the target name.
     */
    suspend fun fetchImageUrl(target: String, aliases: List<String>, imageQuery: String = ""): String? {
        if (target.isBlank()) return null
        val targetName = target.trim()
        val variants = nameVariants(targetName, aliases)
        return withContext(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + TOTAL_BUDGET_MS
            val url: String? = try {
                withTimeout(TOTAL_BUDGET_MS) {
                    var found: String? = null
                    for (v in variants) {
                        if (budgetLeftMs(deadline) < MIN_STAGE_BUDGET_MS) break
                        found = fromCommonsPhrase(v, variants) ?: fromWikipediaPhrase(v, variants)
                        Log.i(TAG, "commons+wiki('$v') -> ${found ?: "miss"}")
                        if (found != null) break
                    }
                    if (found == null && budgetLeftMs(deadline) >= MIN_STAGE_BUDGET_MS) {
                        found = fromAniList(variants)
                        Log.i(TAG, "anilist -> ${found ?: "miss"}")
                    }
                    if (found == null && budgetLeftMs(deadline) >= MIN_STAGE_BUDGET_MS) {
                        found = fromOpenverse(targetName, variants)
                        Log.i(TAG, "openverse -> ${found ?: "miss"}")
                    }
                    if (found == null && budgetLeftMs(deadline) >= MIN_STAGE_BUDGET_MS) {
                        found = fromDuckDuckGo(targetName, imageQuery)
                        Log.i(TAG, "duckduckgo -> ${found ?: "miss"}")
                    }
                    found
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "image search timed out for \"$targetName\"")
                null
            } catch (e: Exception) {
                Log.w(TAG, "image search failed for \"$targetName\"", e)
                null
            }
            if (url == null) {
                Log.w(TAG, "no relevant image for \"$targetName\" (variants: $variants)")
            } else {
                Log.i(TAG, "image for \"$targetName\" -> $url")
            }
            url
        }
    }

    private fun budgetLeftMs(deadline: Long): Long = deadline - System.currentTimeMillis()

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
     * Search phrases: the target, its honorific-stripped form, then AI
     * aliases that share a content word with the target (a shared word is
     * what makes an alias safe to search by). Max 3.
     */
    private fun nameVariants(target: String, aliases: List<String>): List<String> {
        val out = mutableListOf(target.trim())
        stripHonorific(target)?.let { if (it !in out) out.add(it) }
        val targetWords = targetWords(target).toSet()
        for (a in aliases) {
            if (out.size >= 3) break
            val av = a.trim()
            if (av.length < 3 || av in out) continue
            if (targetWords.intersect(targetWords(av)).isEmpty()) continue
            out.add(av)
        }
        return out
    }

    /** "Dr. Vegapunk" -> "Vegapunk"; null when the name has no honorific. */
    private fun stripHonorific(name: String): String? {
        val m = Regex(
            "^(?:dr|mr|mrs|ms|the|lord|lady|king|queen|princess|captain|professor)\\.?\\s+",
            RegexOption.IGNORE_CASE,
        ).find(name) ?: return null
        val rest = name.substring(m.value.length).trim()
        return if (rest.length >= 3) rest else null
    }

    /**
     * Commons: one generator=search call (exact phrase, bitmaps, top 10)
     * returns the file URLs directly; the best title match wins.
     */
    private fun fromCommonsPhrase(phrase: String, variants: List<String>): String? {
        val json = getJson(
            "https://commons.wikimedia.org/w/api.php?action=query&format=json&redirects=1" +
                "&generator=search&gsrnamespace=6&gsrlimit=10&prop=imageinfo&iiprop=url&iiurlwidth=1024" +
                "&gsrsearch=${enc("\"$phrase\" filetype:bitmap")}",
        ) ?: return null
        val pages = json.optJSONObject("query")?.optJSONObject("pages") ?: return null
        return bestByTitle(pages, variants) { page ->
            val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: return@bestByTitle null
            info.optString("thumburl", "").ifBlank { info.optString("url", "") }
        }
    }

    /**
     * Wikipedia: exact-phrase search, then the ARTICLE TITLE is gated before
     * any summary fetch — for target "Trafalgar Law" the articles
     * "Trafalgar" or "One Piece" fail the gate, "Trafalgar Law (One Piece)"
     * passes.
     */
    private fun fromWikipediaPhrase(phrase: String, variants: List<String>): String? {
        val titles = getJson(
            "https://en.wikipedia.org/w/api.php?action=query&format=json" +
                "&list=search&srsearch=${enc("\"$phrase\"")}&srlimit=5",
        ) ?: return null
        val results = titles.optJSONObject("query")?.optJSONArray("search") ?: return null
        for (i in 0 until results.length()) {
            val title = results.getJSONObject(i).optString("title", "")
            if (title.isBlank() || titleRelevanceAny(title, variants) < MIN_RELEVANCE) continue
            val summary = getJson("https://en.wikipedia.org/api/rest_v1/page/summary/${enc(title)}") ?: continue
            val original = summary.optJSONObject("originalimage")?.optString("source", "") ?: ""
            val thumb = summary.optJSONObject("thumbnail")?.optString("source", "") ?: ""
            val url = original.ifBlank { thumb }
            if (isUsableImage(url)) return url
        }
        return null
    }

    /**
     * AniList: keyless GraphQL character search. Anime character portraits
     * live on its CDN, so this is the best source for characters the wikis
     * don't cover well (and it keeps cosplay photos out of major rounds).
     */
    private fun fromAniList(variants: List<String>): String? {
        val query = "query (\$search: String) { Character(search: \$search, perPage: 5) " +
            "{ nodes { id name { full } image { large medium } } } }"
        for (v in variants) {
            val body = JSONObject()
                .put("query", query)
                .put("variables", JSONObject().put("search", v))
            val json = postJson("https://graphql.anilist.co", body.toString()) ?: continue
            val nodes = json.optJSONObject("data")?.optJSONObject("Character")?.optJSONArray("nodes")
                ?: continue
            var bestUrl: String? = null
            var bestScore = 0
            for (i in 0 until nodes.length()) {
                val node = nodes.getJSONObject(i)
                val name = node.optJSONObject("name")?.optString("full", "") ?: ""
                val score = titleRelevanceAny(name, variants)
                if (score < bestScore) continue
                val img = node.optJSONObject("image") ?: continue
                val url = img.optString("large", "").ifBlank { img.optString("medium", "") }
                if (!isUsableImage(url)) continue
                bestScore = score
                bestUrl = url
            }
            if (bestScore >= MIN_RELEVANCE && bestUrl != null) return bestUrl
        }
        return null
    }

    /**
     * Openverse: keyless Creative-Commons image search (WordPress index of
     * Flickr/Wikimedia/etc.). Results carry a title, so the same relevance
     * gate applies.
     */
    private fun fromOpenverse(targetName: String, variants: List<String>): String? {
        val json = getJson(
            "https://api.openverse.org/v1/images/?q=${enc(targetName)}&page_size=10",
        ) ?: return null
        val results = json.optJSONArray("results") ?: return null
        var bestUrl: String? = null
        var bestScore = 0
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            val score = titleRelevanceAny(r.optString("title", ""), variants)
            if (score < bestScore) continue
            val url = r.optString("url", "")
            if (!isUsableImage(url)) continue
            bestScore = score
            bestUrl = url
        }
        return if (bestScore >= MIN_RELEVANCE) bestUrl else null
    }

    /**
     * Last resort: DuckDuckGo's image endpoint. GET the image-search HTML
     * (browser UA) for the per-session `vqd` token, then the JSON i.js API.
     * The token format has changed over time, so several extraction shapes
     * are tried (numeric, then any token). Bing often serves thumbnails
     * without file extensions, so URL shape is checked loosely; opaque
     * hosts (YouTube thumbs) are trusted, slugs must mention the target.
     */
    private fun fromDuckDuckGo(targetName: String, imageQuery: String): String? {
        val query = if (imageQuery.isNotBlank() &&
            imageQuery.trim().lowercase().startsWith(targetName.lowercase())
        ) imageQuery.trim() else targetName
        val html = getText(
            "https://duckduckgo.com/?q=${enc(query)}&iax=1&ia=images",
            userAgent = BROWSER_USER_AGENT,
        ) ?: return null
        val vqd = listOf(
            Regex("""vqd="(-?\d+)""""),
            Regex("""vqd-0" value="(-?\d+)""""),
            Regex("""vqd="([^"\s]+)""""),
            Regex("""vqd-0" value="([^"\s]+)""""),
            Regex("""vqd=([\w-]+)"""),
        ).firstNotNullOfOrNull { it.find(html)?.groupValues?.get(1) } ?: return null
        val json = getJson(
            "https://duckduckgo.com/i.js?q=${enc(query)}&vqd=$vqd&o=json&p=1&l=wt-wt",
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
        // Last resort of last resorts: the top ranked hit for the target.
        return first
    }

    /**
     * Scans a Commons `pages` object, picks the highest-scoring usable
     * image, requires >= [MIN_RELEVANCE].
     */
    private fun bestByTitle(
        pages: JSONObject,
        variants: List<String>,
        urlFor: (JSONObject) -> String?,
    ): String? {
        var bestUrl: String? = null
        var bestScore = 0
        for (key in pages.keys()) {
            val page = pages.optJSONObject(key) ?: continue
            val score = titleRelevanceAny(page.optString("title", ""), variants)
            if (score < bestScore) continue
            val url = urlFor(page) ?: continue
            if (!isUsableImage(url)) continue
            bestScore = score
            bestUrl = url
        }
        return if (bestScore >= MIN_RELEVANCE) bestUrl else null
    }

    /** Normalized content words (letters/digits, length >= 3) of a name. */
    private fun targetWords(name: String): List<String> =
        name.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 }

    /**
     * 3 = every content word present, 2 = at least half present,
     * 1 = only the longest word present, 0 = nothing. "Zoro (One Piece)"
     * scores 2 for target "Roronoa Zoro" (kept); a "Tokyo One Piece Tower"
     * logo scores 0 (dropped).
     */
    private fun titleRelevance(title: String, name: String): Int {
        val words = targetWords(name)
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

    /** Best relevance of [title] against ANY name variant (target or alias). */
    private fun titleRelevanceAny(title: String, variants: List<String>): Int =
        variants.maxOfOrNull { titleRelevance(title, it) } ?: 0

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

    private fun postJson(url: String, body: String): JSONObject? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 6_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use {
                JSONObject(it.readText())
            }
        } catch (e: Exception) {
            Log.w(TAG, "POST failed: $url", e)
            null
        } finally {
            connection.disconnect()
        }
    }
}
