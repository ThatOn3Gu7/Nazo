package quiz.thaton3app.nazo.records

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Exact finite-Quiz setup a local rival belongs to. */
data class RivalGhostSetup(
    val topic: String,
    val difficulty: String,
    val questionCount: Int,
)

/** Best score-and-time profile saved locally for one setup. */
data class RivalGhostProfile(
    val setup: RivalGhostSetup,
    val correct: Int,
    val totalQuestions: Int,
    val elapsedMillis: Long,
    val recordedAtEpochMs: Long,
)

enum class RivalGhostVerdict {
    FirstGhost,
    PlayerWon,
    RivalWon,
    Draw,
}

/** Comparison shown after a run; [previous] is the ghost that was raced. */
data class RivalGhostResult(
    val previous: RivalGhostProfile?,
    val current: RivalGhostProfile,
    val verdict: RivalGhostVerdict,
    val savedAsBest: Boolean,
)

data class RivalGhostLiveRace(
    val progressFraction: Float,
    val projectedAnswered: Int,
    val projectedCorrect: Int,
    val targetMillisRemaining: Long,
)

/** Pure normalization, comparison, and live-race policy. */
object RivalGhostPolicy {
    const val MAX_PROFILES = 12
    const val MAX_TOPIC_LENGTH = 120
    private const val MAX_QUESTIONS = 50
    internal const val MAX_ELAPSED_MS = 6L * 60L * 60L * 1_000L
    private val difficulties = listOf("Easy", "Medium", "Hard", "Otaku Master", "Adaptive")

    fun normalize(setup: RivalGhostSetup): RivalGhostSetup {
        val topic = setup.topic.trim().replace(Regex("\\s+"), " ").take(MAX_TOPIC_LENGTH)
        val difficulty = difficulties.firstOrNull {
            it.equals(setup.difficulty.trim(), ignoreCase = true)
        } ?: throw IllegalArgumentException("Unknown rival difficulty")
        require(setup.questionCount in 1..MAX_QUESTIONS) { "Invalid rival question count" }
        return RivalGhostSetup(topic, difficulty, setup.questionCount)
    }

    fun matches(profile: RivalGhostProfile, setup: RivalGhostSetup): Boolean = try {
        canonicalKey(profile.setup) == canonicalKey(setup)
    } catch (_: IllegalArgumentException) {
        false
    }

    internal fun canonicalKey(setup: RivalGhostSetup): String {
        val normalized = normalize(setup)
        return listOf(
            normalized.topic.lowercase(Locale.ROOT),
            normalized.difficulty,
            normalized.questionCount.toString(),
        ).joinToString("\u001f")
    }

    fun isBetter(candidate: RivalGhostProfile, existing: RivalGhostProfile): Boolean {
        require(canonicalKey(candidate.setup) == canonicalKey(existing.setup)) {
            "Cannot compare ghosts from different setups"
        }
        return candidate.correct > existing.correct ||
            (candidate.correct == existing.correct && candidate.elapsedMillis < existing.elapsedMillis)
    }

    fun verdict(current: RivalGhostProfile, previous: RivalGhostProfile?): RivalGhostVerdict = when {
        previous == null -> RivalGhostVerdict.FirstGhost
        current.correct == previous.correct && current.elapsedMillis == previous.elapsedMillis ->
            RivalGhostVerdict.Draw
        isBetter(current, previous) -> RivalGhostVerdict.PlayerWon
        else -> RivalGhostVerdict.RivalWon
    }

    fun liveRace(profile: RivalGhostProfile, elapsedMillis: Long): RivalGhostLiveRace {
        val elapsed = elapsedMillis.coerceAtLeast(0L)
        val fraction = if (profile.elapsedMillis <= 0L) 1f else {
            (elapsed.toDouble() / profile.elapsedMillis.toDouble()).toFloat().coerceIn(0f, 1f)
        }
        return RivalGhostLiveRace(
            progressFraction = fraction,
            projectedAnswered = (profile.totalQuestions * fraction).toInt()
                .coerceIn(0, profile.totalQuestions),
            projectedCorrect = (profile.correct * fraction).toInt()
                .coerceIn(0, profile.correct),
            targetMillisRemaining = (profile.elapsedMillis - elapsed).coerceAtLeast(0L),
        )
    }

    internal fun validate(profile: RivalGhostProfile) {
        val normalized = normalize(profile.setup)
        require(normalized == profile.setup) { "Rival setup is not normalized" }
        require(profile.totalQuestions == profile.setup.questionCount) {
            "Rival question count does not match its setup"
        }
        require(profile.correct in 0..profile.totalQuestions) { "Invalid rival score" }
        require(profile.elapsedMillis in 1L..MAX_ELAPSED_MS) { "Invalid rival elapsed time" }
        require(profile.recordedAtEpochMs > 0L) { "Invalid rival timestamp" }
    }
}

/** Durable, bounded local history of automatically saved personal-best ghosts. */
class RivalGhostStore internal constructor(
    private val prefs: SharedPreferences,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    @Synchronized
    fun get(): List<RivalGhostProfile> {
        val raw = try {
            prefs.getString(KEY, null)
        } catch (_: ClassCastException) {
            return repair()
        } ?: return emptyList()
        return try {
            RivalGhostJson.decode(raw)
        } catch (_: JSONException) {
            repair()
        } catch (_: IllegalArgumentException) {
            repair()
        }
    }

    @Synchronized
    fun find(setup: RivalGhostSetup): RivalGhostProfile? =
        get().firstOrNull { RivalGhostPolicy.matches(it, setup) }

    @Synchronized
    fun submit(
        setup: RivalGhostSetup,
        correct: Int,
        totalQuestions: Int,
        elapsedMillis: Long,
    ): RivalGhostResult {
        val normalized = RivalGhostPolicy.normalize(
            setup.copy(questionCount = totalQuestions),
        )
        val current = RivalGhostProfile(
            setup = normalized,
            correct = correct.coerceIn(0, totalQuestions),
            totalQuestions = totalQuestions,
            elapsedMillis = elapsedMillis.coerceIn(1L, RivalGhostPolicy.MAX_ELAPSED_MS),
            recordedAtEpochMs = nowEpochMs(),
        )
        RivalGhostPolicy.validate(current)

        val profiles = get().toMutableList()
        val previous = profiles.firstOrNull { RivalGhostPolicy.matches(it, normalized) }
        val verdict = RivalGhostPolicy.verdict(current, previous)
        val savedAsBest = previous == null || RivalGhostPolicy.isBetter(current, previous)
        if (savedAsBest) {
            if (previous != null) profiles.remove(previous)
            profiles.add(0, current)
            persist(
                profiles.sortedByDescending(RivalGhostProfile::recordedAtEpochMs)
                    .take(RivalGhostPolicy.MAX_PROFILES),
            )
        }
        return RivalGhostResult(previous, current, verdict, savedAsBest)
    }

    private fun repair(): List<RivalGhostProfile> = emptyList<RivalGhostProfile>().also(::persist)

    private fun persist(profiles: List<RivalGhostProfile>) {
        prefs.edit().putString(KEY, RivalGhostJson.encode(profiles)).apply()
    }

    internal companion object {
        const val PREFS_NAME = "nazo_rival_ghosts"
        const val KEY = "rival_ghosts_v1"
    }
}

internal object RivalGhostJson {
    private const val VERSION = 1

    fun encode(profiles: List<RivalGhostProfile>): String {
        validate(profiles)
        return JSONObject().apply {
            put("version", VERSION)
            put(
                "profiles",
                JSONArray().apply {
                    profiles.forEach { profile ->
                        put(
                            JSONObject()
                                .put("topic", profile.setup.topic)
                                .put("difficulty", profile.setup.difficulty)
                                .put("questionCount", profile.setup.questionCount)
                                .put("correct", profile.correct)
                                .put("totalQuestions", profile.totalQuestions)
                                .put("elapsedMillis", profile.elapsedMillis)
                                .put("recordedAt", profile.recordedAtEpochMs),
                        )
                    }
                },
            )
        }.toString()
    }

    fun decode(raw: String): List<RivalGhostProfile> {
        require(raw.isNotBlank()) { "Rival ghosts are blank" }
        val root = JSONObject(raw)
        require(strictInt(root, "version") == VERSION) { "Unsupported rival-ghost version" }
        val array = root.get("profiles") as? JSONArray
            ?: throw IllegalArgumentException("Rival profiles must be an array")
        require(array.length() <= RivalGhostPolicy.MAX_PROFILES) { "Too many rival profiles" }
        val profiles = List(array.length()) { index ->
            val item = array.get(index) as? JSONObject
                ?: throw IllegalArgumentException("Rival profile must be an object")
            RivalGhostProfile(
                setup = RivalGhostSetup(
                    topic = strictString(item, "topic"),
                    difficulty = strictString(item, "difficulty"),
                    questionCount = strictInt(item, "questionCount"),
                ),
                correct = strictInt(item, "correct"),
                totalQuestions = strictInt(item, "totalQuestions"),
                elapsedMillis = strictLong(item, "elapsedMillis"),
                recordedAtEpochMs = strictLong(item, "recordedAt"),
            )
        }
        validate(profiles)
        return profiles
    }

    private fun validate(profiles: List<RivalGhostProfile>) {
        require(profiles.size <= RivalGhostPolicy.MAX_PROFILES) { "Too many rival profiles" }
        require(profiles.map { RivalGhostPolicy.canonicalKey(it.setup) }.toSet().size == profiles.size) {
            "Duplicate rival setup"
        }
        profiles.forEach(RivalGhostPolicy::validate)
    }

    private fun strictString(o: JSONObject, key: String): String =
        (o.get(key) as? String) ?: throw IllegalArgumentException("Rival '$key' must be a string")

    private fun strictInt(o: JSONObject, key: String): Int {
        val value = strictLong(o, key)
        require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            "Rival '$key' is outside the integer range"
        }
        return value.toInt()
    }

    private fun strictLong(o: JSONObject, key: String): Long = when (val value = o.get(key)) {
        is Byte -> value.toLong()
        is Short -> value.toLong()
        is Int -> value.toLong()
        is Long -> value
        else -> throw IllegalArgumentException("Rival '$key' must be a whole number")
    }
}

fun rivalGhostTimeLabel(elapsedMillis: Long): String {
    val totalSeconds = (elapsedMillis.coerceAtLeast(0L) + 500L) / 1_000L
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
