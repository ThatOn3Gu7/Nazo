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
import quiz.thaton3app.nazo.vision.AnimeImageGate

/**
 * Resolves a direct image URL of the round's TARGET ENTITY using keyless
 * public sources, in order:
 *
 *   1. AniList — anime character search (keyless GraphQL; official character
 *      portraits — this is an anime game, so the anime databases lead; they
 *      also keep cosplay photos / random sketches out of character rounds)
 *   2. Jikan (MyAnimeList) — keyless character search, official MAL portraits
 *   3. Kitsu — keyless character search, official portraits (500x600)
 *   4. Wikimedia Commons — exact-phrase file search scoped to the franchise
 *   5. English Wikipedia — exact-phrase article search scoped to the franchise
 *   6. AniList media — anime COVER art when the target is a series, not a
 *      character (character search can't find "One Piece" itself)
 *   7. Openverse — keyless Creative-Commons image search (WordPress index)
 *   8. DuckDuckGo — image endpoint (i.js + vqd token), strictly gated
 *
 * The three anime databases (1-3) are how competitor quiz apps get a correct
 * anime image every time: a character row in a curated database carries its
 * official portrait, so for characters the web-search stages below almost
 * never run.
 *
 * Stages 1-5 are tried for every name variant: the target name, its
 * honorific-stripped form ("Dr. Vegapunk" -> "Vegapunk") and any AI alias
 * that shares a content word with the target. EVERY result is relevance-
 * gated: the file/article/character title must score >= 2 against a name
 * variant (all words = 3, strictly-more-than-half INCLUDING the longest
 * word = 2, longest word only = 1). A half-match on a 2-word name no longer
 * passes: for "Trafalgar Law" the articles "Trafalgar" or "Uzumaki" score 0
 * and are dropped. Titles that look like cosplay / fan art / figures /
 * statues are dropped even on a perfect name match, so real humans and
 * human-made art cannot win. A wrong image is treated as a miss.
 *
 * ON TOP of the title gates, every candidate from a non-anime source is
 * PIXEL-verified before it may win: the bytes are downloaded and run through
 * [AnimeImageGate] — photographs of the real world (cosplayers, conventions,
 * statues, live-action stills) are rejected and the search simply CONTINUES
 * with the next source, so a blocked cosplay photo automatically triggers a
 * fresh attempt at a real anime image. AniList CDN images are trusted as
 * official art and skip the check. Verified bytes are kept in a one-slot
 * cache that [fetchImageBytes] consumes, so the image is never downloaded
 * twice.
 *
 * FALLBACK LADDER (owner rule: a placeholder is the LAST resort — if no
 * verified anime image exists, show whatever matched the topic): candidates
 * rejected by the pixel gate, or whose verification download failed, are
 * remembered instead of discarded. When the whole search (or its time
 * budget) ends without an anime-verified winner, the best remembered
 * candidate is returned — it already passed every title/relevance gate, so
 * it IS the right character, just maybe not official art. Placeholder only
 * when no source returned anything relevant at all.
 *
 * The franchise suffix of [imageQuery] ("Satoru Gojo Jujutsu Kaisen anime
 * character" -> "Jujutsu Kaisen anime character") is appended to the
 * Commons / Wikipedia / Openverse / DuckDuckGo search strings so bare names
 * resolve to the right entity.
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
     * Titles containing these words are cosplay / fan art / merchandise,
     * not official art — dropped even when the name matches perfectly.
     */
    private val JUNK_TITLE = Regex(
        "cosplay|cosplayer|fan ?art|figur?e|statue|statuette|plush|doll|live.?action|costume|mask|sketch" +
            "|convention|comic ?con|comiket|fan ?expo|photograph",
        RegexOption.IGNORE_CASE,
    )

    /**
     * One-slot cache holding the bytes that passed pixel verification during
     * the URL search, keyed by URL. [fetchImageBytes] consumes it so the play
     * screen never downloads the same image twice.
     */
    @Volatile
    private var verifiedBytes: Pair<String, ByteArray>? = null

    /**
     * [target] is the round's target_entity; [aliases] are the AI's
     * alternative names for the same entity; [imageQuery] supplies the
     * franchise context appended to the later search stages.
     */
    suspend fun fetchImageUrl(
        target: String,
        aliases: List<String>,
        imageQuery: String = "",
    ): String? {
        if (target.isBlank()) return null
        val targetName = target.trim()
        val variants = nameVariants(targetName, aliases)
        val franchise = franchiseSuffix(targetName, imageQuery)
        return withContext(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + TOTAL_BUDGET_MS
            val fallback = Fallback()
            val url: String? = try {
                withTimeout(TOTAL_BUDGET_MS) {
                    var found: String? = null
                    for (v in variants) {
                        if (budgetLeftMs(deadline) < MIN_STAGE_BUDGET_MS) break
                        found = animeVerified(fromAniList(v, variants), fallback)
                            ?: animeVerified(fromJikan(v, variants), fallback)
                            ?: animeVerified(fromKitsu(v, variants), fallback)
                            ?: animeVerified(fromCommonsPhrase(v, franchise, variants), fallback)
                            ?: animeVerified(fromWikipediaPhrase(v, franchise, variants), fallback)
                        Log.i(TAG, "anime-dbs+commons+wiki('$v') -> ${found ?: "miss"}")
                        if (found != null) break
                    }
                    if (found == null && budgetLeftMs(deadline) >= MIN_STAGE_BUDGET_MS) {
                        // The target may be a SERIES rather than a character —
                        // try anime cover art before falling to web search.
                        found = animeVerified(fromAniListMedia(targetName, variants), fallback)
                        Log.i(TAG, "anilist-media -> ${found ?: "miss"}")
                    }
                    if (found == null && budgetLeftMs(deadline) >= MIN_STAGE_BUDGET_MS) {
                        found = animeVerified(fromOpenverse(targetName, franchise, variants), fallback)
                        Log.i(TAG, "openverse -> ${found ?: "miss"}")
                    }
                    if (found == null && budgetLeftMs(deadline) >= MIN_STAGE_BUDGET_MS) {
                        found = animeVerified(fromDuckDuckGo(targetName, franchise, imageQuery), fallback)
                        Log.i(TAG, "duckduckgo -> ${found ?: "miss"}")
                    }
                    found
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "image search timed out for '$targetName'")
                null
            } catch (e: Exception) {
                Log.w(TAG, "image search failed for '$targetName'", e)
                null
            }
            // No anime-verified winner (miss OR timeout): climb down the
            // fallback ladder before surrendering to the placeholder.
            val chosen = url ?: fallback.best()?.also { fb ->
                Log.i(TAG, "using on-topic fallback image for '$targetName' -> $fb")
            }
            if (chosen == null) {
                Log.w(TAG, "no relevant image for '$targetName' (variants: $variants)")
            } else {
                Log.i(TAG, "image for '$targetName' -> $chosen")
            }
            chosen
        }
    }

    private fun budgetLeftMs(deadline: Long): Long = deadline - System.currentTimeMillis()

    /**
     * Rejected-but-relevant candidates collected during one search, best
     * first: a gate-rejected image whose bytes we already hold (strong —
     * on-topic, instantly displayable) beats a candidate whose verification
     * download failed (weak — the play screen's own fetch may still work).
     * [best] promotes the strong candidate's bytes into the one-slot cache
     * so the play screen doesn't download it again.
     */
    private class Fallback {
        private var rejectedUrl: String? = null
        private var rejectedBytes: ByteArray? = null
        private var unfetchedUrl: String? = null

        fun rememberRejected(url: String, bytes: ByteArray) {
            if (rejectedUrl == null) {
                rejectedUrl = url
                rejectedBytes = bytes
            }
        }

        fun rememberUnfetched(url: String) {
            if (unfetchedUrl == null) unfetchedUrl = url
        }

        fun best(): String? {
            rejectedUrl?.let { u ->
                rejectedBytes?.let { b -> verifiedBytes = u to b }
                return u
            }
            return unfetchedUrl
        }
    }

    /**
     * Pixel gate between the stage searches and the winner: a candidate from
     * a non-anime source only survives if its actual pixels look like anime
     * artwork (see [AnimeImageGate]) — a cosplayer's photo is rejected here
     * and the `?:` chain / stage loop continues searching, which IS the
     * "request a new image" retry. AniList CDN art is official and trusted
     * without a download. Bytes that pass are cached for [fetchImageBytes];
     * candidates that fail are remembered in [fallback] so a fully-missed
     * search can still show SOMETHING on-topic instead of the placeholder.
     */
    private fun animeVerified(url: String?, fallback: Fallback): String? {
        if (url == null) return null
        if (isTrustedAnimeCdn(url)) return url
        val bytes = fetchImageBytes(url) ?: run {
            Log.w(TAG, "verification download failed, keeping as weak fallback: $url")
            fallback.rememberUnfetched(url)
            return null
        }
        if (AnimeImageGate.looksLikeRealPhoto(bytes)) {
            Log.w(TAG, "real-world photo (cosplay/human) — kept only as fallback, searching on: $url")
            fallback.rememberRejected(url, bytes)
            return null
        }
        verifiedBytes = url to bytes
        return url
    }

    /** Official-art CDNs of the anime databases — trusted without a pixel check. */
    private fun isTrustedAnimeCdn(url: String): Boolean =
        url.contains(".anilist.co/") ||
            url.contains("cdn.myanimelist.net/") ||
            url.contains("media.kitsu.app/") ||
            url.contains("media.kitsu.io/")

    /**
     * The part of [imageQuery] after the target name ("Satoru Gojo Jujutsu
     * Kaisen anime character" -> "Jujutsu Kaisen anime character"); blank
     * when the query doesn't start with the target. Scopes bare-name
     * searches to the right franchise.
     *
     * CAPPED at 4 words: the AI sometimes appends a whole scene description
     * ("One Piece anime character standing with three swords"), and feeding
     * all of that into an exact-search makes Commons/Wikipedia find NOTHING
     * (the Zoro-placeholder bug). Four words keep the franchise context and
     * drop the scene noise.
     */
    private fun franchiseSuffix(targetName: String, imageQuery: String): String {
        val q = imageQuery.trim()
        if (q.length <= targetName.length) return ""
        val suffix = if (q.lowercase().startsWith(targetName.lowercase())) {
            q.substring(targetName.length).trim()
        } else {
            ""
        }
        return suffix.split(Regex("\\s+")).take(4).joinToString(" ")
    }

    /**
     * Plain-HTTP download of the image bytes. Used by the play screen to
     * pre-warm the mystery image BEFORE the countdown starts, so the timer
     * only ever runs against pixels Coil can decode instantly (passed to
     * AsyncImage as a ByteArray model — no ImageRequest needed).
     *
     * When the URL search already downloaded and pixel-verified these exact
     * bytes, the one-slot cache is consumed instead of re-downloading.
     */
    fun fetchImageBytes(url: String): ByteArray? {
        verifiedBytes?.let { (cachedUrl, cached) ->
            if (cachedUrl == url) {
                verifiedBytes = null // one consumer per round; free the slot
                return cached
            }
        }
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
     * Commons: one generator=search call (exact name phrase + franchise
     * context, bitmaps, top 10) returns the file URLs directly; the best
     * clean title match wins.
     */
    private fun fromCommonsPhrase(phrase: String, franchise: String, variants: List<String>): String? {
        val search = if (franchise.isBlank()) "\"$phrase\" filetype:bitmap" else "\"$phrase\" $franchise filetype:bitmap"
        val json = getJson(
            "https://commons.wikimedia.org/w/api.php?action=query&format=json&redirects=1" +
                "&generator=search&gsrnamespace=6&gsrlimit=10&prop=imageinfo&iiprop=url&iiurlwidth=1024" +
                "&gsrsearch=${enc(search)}",
        ) ?: return null
        val pages = json.optJSONObject("query")?.optJSONObject("pages") ?: return null
        return bestByTitle(pages, variants) { page ->
            val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: return@bestByTitle null
            info.optString("thumburl", "").ifBlank { info.optString("url", "") }
        }
    }

    /**
     * Wikipedia: exact-phrase + franchise search, then the ARTICLE TITLE is
     * gated before any summary fetch — for target "Trafalgar Law" the
     * articles "Trafalgar" or "One Piece" fail the gate,
     * "Trafalgar Law (One Piece)" passes.
     */
    private fun fromWikipediaPhrase(phrase: String, franchise: String, variants: List<String>): String? {
        val search = if (franchise.isBlank()) "\"$phrase\"" else "\"$phrase\" $franchise"
        val titles = getJson(
            "https://en.wikipedia.org/w/api.php?action=query&format=json" +
                "&list=search&srsearch=${enc(search)}&srlimit=5",
        ) ?: return null
        val results = titles.optJSONObject("query")?.optJSONArray("search") ?: return null
        for (i in 0 until results.length()) {
            val title = results.getJSONObject(i).optString("title", "")
            if (title.isBlank() || !isCleanTitle(title)) continue
            if (titleRelevanceAny(title, variants) < MIN_RELEVANCE) continue
            val summary = getJson("https://en.wikipedia.org/api/rest_v1/page/summary/${enc(title)}") ?: continue
            val original = summary.optJSONObject("originalimage")?.optString("source", "") ?: ""
            val thumb = summary.optJSONObject("thumbnail")?.optString("source", "") ?: ""
            val url = original.ifBlank { thumb }
            if (isUsableImage(url)) return url
        }
        return null
    }

    /**
     * AniList: keyless GraphQL character search. NOTE the shape: the
     * top-level `Character` query returns a SINGLE character and does NOT
     * accept perPage — list searches must go through `Page { characters }`.
     * (The previous query used `Character(search:, perPage:) { nodes }`,
     * which the API rejects with HTTP 400 — this stage silently never
     * returned anything. Found via the Zoro-placeholder bug.)
     */
    private fun fromAniList(variant: String, variants: List<String>): String? {
        val query = "query (\$search: String) { Page(perPage: 5) { characters(search: \$search) " +
            "{ id name { full } image { large medium } } } }"
        val body = JSONObject()
            .put("query", query)
            .put("variables", JSONObject().put("search", variant))
        val json = postJson("https://graphql.anilist.co", body.toString()) ?: return null
        val nodes = json.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("characters")
            ?: return null
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
        return if (bestScore >= MIN_RELEVANCE) bestUrl else null
    }

    /**
     * AniList media search: anime COVER art for rounds whose target is a
     * series/film rather than a character ("guess the anime" style rounds).
     */
    private fun fromAniListMedia(name: String, variants: List<String>): String? {
        val query = "query (\$search: String) { Page(perPage: 5) { media(search: \$search, type: ANIME) " +
            "{ id title { romaji english } coverImage { extraLarge large } } } }"
        val body = JSONObject()
            .put("query", query)
            .put("variables", JSONObject().put("search", name))
        val json = postJson("https://graphql.anilist.co", body.toString()) ?: return null
        val nodes = json.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media")
            ?: return null
        var bestUrl: String? = null
        var bestScore = 0
        for (i in 0 until nodes.length()) {
            val node = nodes.getJSONObject(i)
            val title = node.optJSONObject("title") ?: continue
            val score = maxOf(
                titleRelevanceAny(title.optString("romaji", ""), variants),
                titleRelevanceAny(title.optString("english", ""), variants),
            )
            if (score < bestScore) continue
            val img = node.optJSONObject("coverImage") ?: continue
            val url = img.optString("extraLarge", "").ifBlank { img.optString("large", "") }
            if (!isUsableImage(url)) continue
            bestScore = score
            bestUrl = url
        }
        return if (bestScore >= MIN_RELEVANCE) bestUrl else null
    }

    /**
     * Jikan v4 (keyless MyAnimeList proxy): character search, official MAL
     * portraits from cdn.myanimelist.net. MAL sometimes writes names as
     * "Surname, Given" — the word-based relevance gate is order-independent
     * so that still scores. Placeholder "questionmark" images are skipped.
     */
    private fun fromJikan(variant: String, variants: List<String>): String? {
        val json = getJson(
            "https://api.jikan.moe/v4/characters?q=${enc(variant)}&limit=5",
        ) ?: return null
        val results = json.optJSONArray("data") ?: return null
        var bestUrl: String? = null
        var bestScore = 0
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            val score = titleRelevanceAny(r.optString("name", ""), variants)
            if (score < bestScore) continue
            val url = r.optJSONObject("images")?.optJSONObject("jpg")
                ?.optString("image_url", "") ?: ""
            if (!isUsableImage(url) || url.contains("questionmark")) continue
            bestScore = score
            bestUrl = url
        }
        return if (bestScore >= MIN_RELEVANCE) bestUrl else null
    }

    /**
     * Kitsu (keyless JSON:API): character search, official portraits from
     * media.kitsu.app (the `large` rendition is a clean 500x600 PORTRAIT —
     * ideal for the passport crop). Names come back canonical
     * ("Zoro Roronoa"), which the order-independent gate scores fine.
     */
    private fun fromKitsu(variant: String, variants: List<String>): String? {
        val json = getJson(
            "https://kitsu.io/api/edge/characters?filter%5Bname%5D=${enc(variant)}&page%5Blimit%5D=5",
        ) ?: return null
        val results = json.optJSONArray("data") ?: return null
        var bestUrl: String? = null
        var bestScore = 0
        for (i in 0 until results.length()) {
            val attrs = results.getJSONObject(i).optJSONObject("attributes") ?: continue
            val score = titleRelevanceAny(attrs.optString("canonicalName", ""), variants)
            if (score < bestScore) continue
            val img = attrs.optJSONObject("image") ?: continue
            val url = img.optString("large", "")
                .ifBlank { img.optString("original", "") }
                .ifBlank { img.optString("medium", "") }
            if (!isUsableImage(url)) continue
            bestScore = score
            bestUrl = url
        }
        return if (bestScore >= MIN_RELEVANCE) bestUrl else null
    }

    /**
     * Openverse: keyless Creative-Commons image search (WordPress index of
     * Flickr/Wikimedia/etc.). Results carry a title, so the same relevance
     * gate AND the junk-title filter apply (Flickr is cosplay-heavy).
     */
    private fun fromOpenverse(targetName: String, franchise: String, variants: List<String>): String? {
        val q = if (franchise.isBlank()) targetName else "$targetName $franchise"
        val json = getJson(
            "https://api.openverse.org/v1/images/?q=${enc(q)}&page_size=10",
        ) ?: return null
        val results = json.optJSONArray("results") ?: return null
        var bestUrl: String? = null
        var bestScore = 0
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            val title = r.optString("title", "")
            if (!isCleanTitle(title)) continue
            val score = titleRelevanceAny(title, variants)
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
     * are tried (numeric, then any token). Every hit must be HTTPS, must
     * mention the target, and must be a decodable format (Bing thumbnails
     * have no extension, so they are exempt from the format check only).
     * There is NO blind top-hit fallback: a miss beats a wrong face.
     */
    private fun fromDuckDuckGo(targetName: String, franchise: String, imageQuery: String): String? {
        val query = when {
            imageQuery.isNotBlank() &&
                imageQuery.trim().lowercase().startsWith(targetName.lowercase()) -> imageQuery.trim()
            franchise.isNotBlank() -> "$targetName $franchise"
            else -> targetName
        }
        val html = getText(
            "https://duckduckgo.com/?q=${enc(query)}&iax=1&ia=images",
            userAgent = BROWSER_USER_AGENT,
        ) ?: return null
        val vqd = listOf(
            Regex("""vqd=(-?\d+)"""),
            Regex("""vqd-0 value=(-?\d+)"""),
            Regex("""vqd=([^"\s]+)"""),
            Regex("""vqd-0 value=([^"\s]+)"""),
            Regex("""vqd=([\w-]+)"""),
        ).firstNotNullOfOrNull { it.find(html)?.groupValues?.get(1) } ?: return null
        val json = getJson(
            "https://duckduckgo.com/i.js?q=${enc(query)}&vqd=$vqd&o=json&p=1&l=wt-wt",
            userAgent = BROWSER_USER_AGENT,
        ) ?: return null
        val results = json.optJSONArray("results") ?: return null
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            val url = r.optString("image", "").ifBlank { r.optString("thumbnail", "") }
            if (!url.startsWith("https://")) continue
            if (!urlMatchesTarget(url, targetName)) continue
            if (isUsableImage(url) || url.contains("bing.net/th")) return url
        }
        return null
    }

    /**
     * Scans a Commons `pages` object, picks the highest-scoring usable
     * image with a clean title, requires >= [MIN_RELEVANCE].
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
            val title = page.optString("title", "")
            if (!isCleanTitle(title)) continue
            val score = titleRelevanceAny(title, variants)
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
     * 3 = every content word present, 2 = strictly more than half present
     * AND the longest word present, 1 = only the longest word present,
     * 0 = nothing. "Zoro (One Piece)" scores 3 against the alias variant
     * "Zoro" (kept); for "Trafalgar Law" the article "Trafalgar" now scores
     * 0 (one of two words is no longer enough) and is dropped; a
     * "Tokyo One Piece Tower" logo scores 0 (dropped).
     */
    private fun titleRelevance(title: String, name: String): Int {
        val words = targetWords(name)
        if (words.isEmpty()) return 0
        val t = title.lowercase()
        val present = words.count { t.contains(it) }
        val longest = words.maxByOrNull { it.length }!!
        return when {
            present == words.size -> 3
            present * 2 > words.size && t.contains(longest) -> 2
            t.contains(longest) -> 1
            else -> 0
        }
    }

    /** Best relevance of [title] against ANY name variant (target or alias). */
    private fun titleRelevanceAny(title: String, variants: List<String>): Int =
        variants.maxOfOrNull { titleRelevance(title, it) } ?: 0

    /** False for cosplay / fan-art / merchandise titles (see [JUNK_TITLE]). */
    private fun isCleanTitle(title: String): Boolean = !JUNK_TITLE.containsMatchIn(title)

    /**
     * DDG/Bing URLs are loose: Bing thumbnails (opaque, no slug to inspect)
     * are trusted, anything with a readable path must mention the target.
     * YouTube thumbnails are NOT trusted — they are usually video frames of
     * real people.
     */
    private fun urlMatchesTarget(url: String, targetName: String): Boolean {
        val words = targetWords(targetName)
        if (words.isEmpty()) return true
        val u = url.lowercase()
        if (u.contains(words.joinToString(" "))) return true
        if (u.contains("bing.net/th")) return true
        return u.contains(words.maxByOrNull { it.length }!!) || words.count { u.contains(it) } * 2 > words.size
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
            } else if (url.contains("kitsu.io")) {
                setRequestProperty("Accept", "application/vnd.api+json")
            } else if (url.contains("jikan.moe")) {
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
