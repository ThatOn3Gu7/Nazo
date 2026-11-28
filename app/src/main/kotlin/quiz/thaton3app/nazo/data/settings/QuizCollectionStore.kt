package quiz.thaton3app.nazo.data.settings

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/** One reusable Home setup before it receives its stable local identifier. */
data class QuizCollectionSetup(
    val topic: String,
    val mode: String,
    val difficulty: String,
    /** Questions for Quiz/Versus, rounds for Guessing, and zero for timed/endless modes. */
    val amount: Int,
)

/** A player-saved topic, mode, difficulty, and length combination. */
data class QuizCollection(
    val id: String,
    val topic: String,
    val mode: String,
    val difficulty: String,
    val amount: Int,
) {
    val setup: QuizCollectionSetup
        get() = QuizCollectionSetup(topic, mode, difficulty, amount)
}

/** Validation and identity rules shared by persistence and Home selection state. */
object QuizCollectionPolicy {
    const val MAX_COLLECTIONS = 8
    private const val MAX_TOPIC_LENGTH = 120

    private val modes = setOf("QUIZ", "GUESSING", "SURVIVAL", "BLITZ", "VERSUS")
    private val difficulties = listOf("Easy", "Medium", "Hard", "Otaku Master", "Adaptive")

    fun normalize(setup: QuizCollectionSetup): QuizCollectionSetup {
        val mode = setup.mode.trim().uppercase(Locale.ROOT)
        require(mode in modes) { "Unknown collection mode" }
        val difficulty = difficulties.firstOrNull {
            it.equals(setup.difficulty.trim(), ignoreCase = true)
        } ?: throw IllegalArgumentException("Unknown collection difficulty")
        require(difficulty != "Adaptive" || mode == "QUIZ") {
            "Adaptive collections are Quiz-only"
        }
        val amount = when (mode) {
            "QUIZ", "VERSUS" -> setup.amount.also {
                require(it in setOf(5, 10, 15)) { "Invalid question count" }
            }
            "GUESSING" -> setup.amount.also {
                require(it in setOf(1, 3, 5)) { "Invalid round count" }
            }
            else -> 0
        }
        return QuizCollectionSetup(
            topic = setup.topic.trim().replace(Regex("\\s+"), " ").take(MAX_TOPIC_LENGTH),
            mode = mode,
            difficulty = difficulty,
            amount = amount,
        )
    }

    fun matches(collection: QuizCollection, setup: QuizCollectionSetup): Boolean = try {
        canonicalKey(collection.setup) == canonicalKey(setup)
    } catch (_: IllegalArgumentException) {
        false
    }

    internal fun canonicalKey(setup: QuizCollectionSetup): String {
        val normalized = normalize(setup)
        return listOf(
            normalized.topic.lowercase(Locale.ROOT),
            normalized.mode,
            normalized.difficulty,
            normalized.amount.toString(),
        ).joinToString("\u001f")
    }
}

/**
 * Durable, backup-friendly storage for the player's favorite Home setups.
 *
 * Saving the same normalized setup again moves it to the front rather than
 * creating a duplicate. The list is deliberately small; at eight setups the
 * player chooses what to remove, so a favorite is never retired silently.
 */
class QuizCollectionStore internal constructor(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    @Synchronized
    fun get(): List<QuizCollection> {
        val raw = try {
            prefs.getString(KEY, null)
        } catch (_: ClassCastException) {
            return repair()
        } ?: return emptyList()
        return try {
            QuizCollectionJson.decode(raw)
        } catch (_: JSONException) {
            repair()
        } catch (_: IllegalArgumentException) {
            repair()
        }
    }

    @Synchronized
    fun save(setup: QuizCollectionSetup): QuizCollection {
        val normalized = QuizCollectionPolicy.normalize(setup)
        val collections = get().toMutableList()
        val existing = collections.firstOrNull { QuizCollectionPolicy.matches(it, normalized) }
        if (existing != null) collections.remove(existing)
        check(existing != null || collections.size < QuizCollectionPolicy.MAX_COLLECTIONS) {
            "Collection limit reached"
        }
        val saved = existing ?: QuizCollection(
            id = UUID.randomUUID().toString(),
            topic = normalized.topic,
            mode = normalized.mode,
            difficulty = normalized.difficulty,
            amount = normalized.amount,
        )
        collections.add(0, saved)
        persist(collections)
        return saved
    }

    @Synchronized
    fun delete(id: String): Boolean {
        if (id.isBlank()) return false
        val collections = get().toMutableList()
        val changed = collections.removeAll { it.id == id }
        if (changed) persist(collections)
        return changed
    }

    private fun repair(): List<QuizCollection> = emptyList<QuizCollection>().also(::persist)

    private fun persist(collections: List<QuizCollection>) {
        prefs.edit().putString(KEY, QuizCollectionJson.encode(collections)).apply()
    }

    internal companion object {
        const val PREFS_NAME = "nazo_collections"
        const val KEY = "quiz_collections_v1"
    }
}

internal object QuizCollectionJson {
    private const val VERSION = 1
    private const val MAX_ID_LENGTH = 128

    fun encode(collections: List<QuizCollection>): String {
        validate(collections)
        return JSONObject().apply {
            put("version", VERSION)
            put(
                "collections",
                JSONArray().apply {
                    collections.forEach { collection ->
                        put(
                            JSONObject()
                                .put("id", collection.id)
                                .put("topic", collection.topic)
                                .put("mode", collection.mode)
                                .put("difficulty", collection.difficulty)
                                .put("amount", collection.amount),
                        )
                    }
                },
            )
        }.toString()
    }

    fun decode(raw: String): List<QuizCollection> {
        require(raw.isNotBlank()) { "Collections are blank" }
        val root = JSONObject(raw)
        require(strictInt(root, "version") == VERSION) { "Unsupported collections version" }
        val array = root.get("collections") as? JSONArray
            ?: throw IllegalArgumentException("Collections must be an array")
        require(array.length() <= QuizCollectionPolicy.MAX_COLLECTIONS) { "Too many collections" }
        val collections = List(array.length()) { index ->
            val item = array.get(index) as? JSONObject
                ?: throw IllegalArgumentException("Collection must be an object")
            val storedSetup = QuizCollectionSetup(
                topic = strictString(item, "topic"),
                mode = strictString(item, "mode"),
                difficulty = strictString(item, "difficulty"),
                amount = strictInt(item, "amount"),
            )
            val setup = QuizCollectionPolicy.normalize(storedSetup)
            require(storedSetup == setup) { "Collection is not normalized" }
            QuizCollection(
                id = strictString(item, "id"),
                topic = setup.topic,
                mode = setup.mode,
                difficulty = setup.difficulty,
                amount = setup.amount,
            )
        }
        validate(collections)
        return collections
    }

    private fun validate(collections: List<QuizCollection>) {
        require(collections.size <= QuizCollectionPolicy.MAX_COLLECTIONS) { "Too many collections" }
        require(collections.map { it.id }.toSet().size == collections.size) {
            "Duplicate collection id"
        }
        require(
            collections.map { QuizCollectionPolicy.canonicalKey(it.setup) }.toSet().size == collections.size,
        ) { "Duplicate collection setup" }
        collections.forEach { collection ->
            require(collection.id.isNotBlank() && collection.id.length <= MAX_ID_LENGTH) {
                "Invalid collection id"
            }
            require(QuizCollectionPolicy.normalize(collection.setup) == collection.setup) {
                "Collection is not normalized"
            }
        }
    }

    private fun strictString(o: JSONObject, key: String): String =
        (o.get(key) as? String) ?: throw IllegalArgumentException("Collection '$key' must be a string")

    private fun strictInt(o: JSONObject, key: String): Int {
        val value = o.get(key)
        val long = when (value) {
            is Byte -> value.toLong()
            is Short -> value.toLong()
            is Int -> value.toLong()
            is Long -> value
            else -> throw IllegalArgumentException("Collection '$key' must be a whole number")
        }
        require(long in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            "Collection '$key' is outside the integer range"
        }
        return long.toInt()
    }
}
