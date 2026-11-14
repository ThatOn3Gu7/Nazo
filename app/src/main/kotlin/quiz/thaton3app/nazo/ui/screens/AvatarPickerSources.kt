package quiz.thaton3app.nazo.ui.screens

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlin.math.abs
import kotlin.random.Random

/** One picker tile. Generated URLs contain random seeds only, never profile data. */
internal sealed interface AvatarPreset {
    data class Emoji(val symbol: String) : AvatarPreset
    data class Generated(val id: String, val url: String) : AvatarPreset
}

/** Network-backed tabs are named up front, but fetched only when opened. */
internal enum class DynamicAvatarSource(val tabTitle: String) {
    JIKAN("Anime Faces"),
    WAIFU_IM("Anime Art"),
    RANDOM_USER("Real Photos"),
}

internal data class AvatarSourceResult(
    val items: List<AvatarPreset.Generated>,
    val unavailable: Boolean,
)

/**
 * Privacy and reliability boundary for the profile avatar catalogue.
 *
 * Opening the picker itself is offline: static tabs construct URLs locally and
 * dynamic APIs are called only after their tab is selected. API responses may
 * nominate images only on that provider's documented CDN; an upstream cannot
 * make the picker contact an arbitrary third-party host. Requests carry no
 * profile/device values and use a fixed app-purpose User-Agent rather than the
 * platform default, which can contain Android build/device details.
 */
internal object AvatarPickerSources {
    private const val USER_AGENT = "Nazo-Avatar-Picker/1.0"
    private const val MAX_JSON_BYTES = 1 * 1024 * 1024

    private val animeSeeds = listOf(
        "Aiko", "Botan", "Chiyo", "Daichi", "Emi", "Fuma",
        "Haruto", "Ichika", "Jun", "Kaida", "Lin", "Mio",
        "Noa", "Ren", "Suzu", "Tsumugi",
    )
    private val roboSeeds = listOf("Rex", "Volt", "Zed", "Kilo", "Nova", "Byte", "Gizmo", "Unit")
    private val emojiChoices = listOf(
        "⚡", "🔥", "🌙", "⭐", "🌸", "🍀", "👻", "✨",
        "🦊", "🐱", "🎮", "📚", "🎯", "🍜", "💤", "😎",
    )

    val dynamicSources: List<DynamicAvatarSource> = DynamicAvatarSource.entries

    private fun seeded(seed: String, nonce: String) = if (nonce.isBlank()) seed else "$seed-$nonce"

    private fun dicebearUrl(style: String, seed: String, nonce: String) =
        "https://api.dicebear.com/10.x/$style/png?seed=${seeded(seed, nonce)}" +
            "&size=256&backgroundType=gradientLinear"

    private fun robohashUrl(set: String, seed: String, nonce: String) =
        "https://robohash.org/${seeded(seed, nonce)}?set=$set&size=256x256"

    private fun pravatarUrl(seed: String, nonce: String): String {
        val n = abs(seeded(seed, nonce).hashCode() % 70) + 1
        return "https://i.pravatar.cc/256?img=$n"
    }

    fun staticCategories(nonce: String): List<Pair<String, List<AvatarPreset>>> = listOf(
        "Emoji" to emojiChoices.map { AvatarPreset.Emoji(it) },
        "Anime Style" to animeSeeds.map {
            AvatarPreset.Generated("adv-$it-$nonce", dicebearUrl("adventurer", it, nonce))
        },
        "Pixel Art" to animeSeeds.map {
            AvatarPreset.Generated("pix-$it-$nonce", dicebearUrl("pixel-art", it, nonce))
        },
        "Abstract" to animeSeeds.take(8).map {
            AvatarPreset.Generated("shp-$it-$nonce", dicebearUrl("shapes", it, nonce))
        },
        "Robots" to roboSeeds.map {
            AvatarPreset.Generated("rob-$it-$nonce", robohashUrl("set1", it, nonce))
        },
        "Monsters" to roboSeeds.map {
            AvatarPreset.Generated("mon-$it-$nonce", robohashUrl("set2", it, nonce))
        },
        "Cats" to roboSeeds.map {
            AvatarPreset.Generated("cat-$it-$nonce", robohashUrl("set4", it, nonce))
        },
        "Portraits" to (1..8).map { index ->
            AvatarPreset.Generated("prt-$index-$nonce", pravatarUrl("portrait$index", nonce))
        },
    )

    fun sourceForTab(tabIndex: Int, staticTabCount: Int): DynamicAvatarSource? =
        dynamicSources.getOrNull(tabIndex - staticTabCount)

    fun requestUrl(source: DynamicAvatarSource, jikanPage: Int = Random.nextInt(1, 40)): String =
        when (source) {
            DynamicAvatarSource.JIKAN ->
                "https://api.jikan.moe/v4/characters?page=${jikanPage.coerceIn(1, 39)}" +
                    "&order_by=members&sort=desc"
            DynamicAvatarSource.WAIFU_IM -> "https://api.waifu.im/images/?is_nsfw=false"
            DynamicAvatarSource.RANDOM_USER -> "https://randomuser.me/api/?results=10"
        }

    suspend fun fetch(
        source: DynamicAvatarSource,
        get: (String) -> String? = ::httpGet,
    ): AvatarSourceResult = withContext(Dispatchers.IO) {
        val body = runCatching { get(requestUrl(source)) }.getOrNull()
            ?: return@withContext AvatarSourceResult(emptyList(), unavailable = true)
        val items = runCatching { parse(source, body) }.getOrDefault(emptyList())
        AvatarSourceResult(items, unavailable = items.isEmpty())
    }

    internal fun parse(source: DynamicAvatarSource, body: String): List<AvatarPreset.Generated> {
        val root = JSONObject(body)
        val array = when (source) {
            DynamicAvatarSource.JIKAN -> root.optJSONArray("data")
            DynamicAvatarSource.WAIFU_IM -> root.optJSONArray("images")
            DynamicAvatarSource.RANDOM_USER -> root.optJSONArray("results")
        } ?: return emptyList()

        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = when (source) {
                    DynamicAvatarSource.JIKAN -> item.optJSONObject("images")
                        ?.optJSONObject("jpg")
                        ?.optString("image_url")
                    DynamicAvatarSource.WAIFU_IM -> item.optString("url")
                    DynamicAvatarSource.RANDOM_USER -> item.optJSONObject("picture")
                        ?.optString("medium")
                }?.takeIf { allowedImageUrl(source, it) } ?: continue
                add(AvatarPreset.Generated("${source.name.lowercase()}-$index-${url.hashCode()}", url))
            }
        }
    }

    internal fun allowedImageUrl(source: DynamicAvatarSource, raw: String): Boolean {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null) return false
        if (uri.port !in listOf(-1, 443)) return false
        val host = uri.host?.lowercase() ?: return false
        return when (source) {
            DynamicAvatarSource.JIKAN -> host == "cdn.myanimelist.net"
            DynamicAvatarSource.WAIFU_IM -> host == "cdn.waifu.im"
            DynamicAvatarSource.RANDOM_USER -> host == "randomuser.me"
        }
    }

    private fun httpGet(url: String): String? {
        val connection = (URL(url).openConnection() as? HttpURLConnection) ?: return null
        return try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) return null
            val declared = connection.contentLengthLong
            if (declared > MAX_JSON_BYTES) return null
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream(
                    if (declared in 1..MAX_JSON_BYTES.toLong()) declared.toInt() else 16 * 1024,
                )
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > MAX_JSON_BYTES) return null
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
