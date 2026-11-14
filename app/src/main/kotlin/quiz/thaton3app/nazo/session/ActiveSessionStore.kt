package quiz.thaton3app.nazo.session

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import quiz.thaton3app.nazo.data.Question
import quiz.thaton3app.nazo.modes.guessing_game.GuessPayload
import quiz.thaton3app.nazo.modes.guessing_game.GuessRoundResult

/** Persisted state for a game that has started but has not been quit or completed. */
sealed interface ActiveSessionSnapshot {
    val savedAtEpochMs: Long
}

data class ActiveQuizSession(
    override val savedAtEpochMs: Long,
    val destination: Destination,
    val mode: String,
    val questions: List<Question>,
    val userAnswers: List<String?>,
    val currentQuestionIndex: Int,
    val score: Int,
    val difficulty: String,
    val aiGenerated: Boolean,
    val daily: Boolean,
    val pendingAnswer: String?,
    val pendingTimeUp: Boolean,
    val questionRemainingMs: Long,
    val quizElapsedMs: Long,
    val survivalWrongs: Int,
    val blitzRemainingMs: Long,
    val versusStage: Int,
    val versusP1Score: Int,
    val versusP1Answers: List<String?>,
) : ActiveSessionSnapshot {
    enum class Destination { QUIZ, VERSUS_HANDOFF }
}

sealed interface SavedGuessPhase {
    data object Preparing : SavedGuessPhase
    data class Playing(val payload: GuessPayload, val imageUrl: String?) : SavedGuessPhase
    data class Error(val message: String, val offline: Boolean) : SavedGuessPhase
}

data class ActiveGuessSession(
    override val savedAtEpochMs: Long,
    val topic: String,
    val difficulty: String,
    val totalRounds: Int,
    val round: Int,
    val score: Int,
    val results: List<GuessRoundResult>,
    val roundResult: GuessRoundResult?,
    val phase: SavedGuessPhase,
    val roundTimerStarted: Boolean,
    val roundRemainingMs: Long,
) : ActiveSessionSnapshot

/**
 * Small private checkpoint store for process-death recovery.
 *
 * It is deliberately separate from backup data: an in-flight game is local,
 * short-lived state, not portable user data. Snapshots expire after [MAX_AGE_MS]
 * so reopening Nazo days later never resurrects an abandoned run.
 */
class ActiveSessionStore internal constructor(
    private val prefs: SharedPreferences,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE),
    )

    fun save(snapshot: ActiveSessionSnapshot) {
        prefs.edit().putString(KEY, ActiveSessionJson.encode(snapshot)).apply()
    }

    fun load(): ActiveSessionSnapshot? {
        val raw = try {
            prefs.getString(KEY, null)
        } catch (_: ClassCastException) {
            clear()
            return null
        } ?: return null

        val snapshot = try {
            ActiveSessionJson.decode(raw)
        } catch (_: JSONException) {
            clear()
            return null
        } catch (_: IllegalArgumentException) {
            clear()
            return null
        }
        val age = nowEpochMs() - snapshot.savedAtEpochMs
        if (age !in 0L..MAX_AGE_MS) {
            clear()
            return null
        }
        return snapshot
    }

    fun clear() {
        // Completion/quit must be durable before navigation leaves gameplay;
        // otherwise an immediate process kill could resurrect a finished run.
        prefs.edit().remove(KEY).commit()
    }

    companion object {
        internal const val STORE = "nazo_active_session"
        internal const val KEY = "active_session_v1"
        internal const val MAX_AGE_MS = 6L * 60L * 60L * 1_000L

        /** Remaining timer value after time spent outside the process. */
        fun remainingAfterRestore(
            remainingAtSaveMs: Long,
            savedAtEpochMs: Long,
            nowEpochMs: Long,
        ): Long = (remainingAtSaveMs - (nowEpochMs - savedAtEpochMs).coerceAtLeast(0L))
            .coerceAtLeast(0L)
    }
}

internal object ActiveSessionJson {
    private const val VERSION = 1
    private const val MAX_QUESTIONS = 200
    private const val MAX_TEXT = 20_000
    private const val MAX_TIMER_MS = ActiveSessionStore.MAX_AGE_MS

    fun encode(snapshot: ActiveSessionSnapshot): String = JSONObject().apply {
        put("version", VERSION)
        put("savedAt", snapshot.savedAtEpochMs)
        when (snapshot) {
            is ActiveQuizSession -> {
                put("kind", "quiz")
                put("destination", snapshot.destination.name)
                put("mode", snapshot.mode)
                put("questions", JSONArray().apply { snapshot.questions.forEach { put(questionJson(it)) } })
                put("answers", nullableStrings(snapshot.userAnswers))
                put("index", snapshot.currentQuestionIndex)
                put("score", snapshot.score)
                put("difficulty", snapshot.difficulty)
                put("ai", snapshot.aiGenerated)
                put("daily", snapshot.daily)
                put("pendingAnswer", snapshot.pendingAnswer ?: JSONObject.NULL)
                put("pendingTimeUp", snapshot.pendingTimeUp)
                put("questionRemainingMs", snapshot.questionRemainingMs)
                put("quizElapsedMs", snapshot.quizElapsedMs)
                put("survivalWrongs", snapshot.survivalWrongs)
                put("blitzRemainingMs", snapshot.blitzRemainingMs)
                put("versusStage", snapshot.versusStage)
                put("versusP1Score", snapshot.versusP1Score)
                put("versusP1Answers", nullableStrings(snapshot.versusP1Answers))
            }
            is ActiveGuessSession -> {
                put("kind", "guess")
                put("topic", snapshot.topic)
                put("difficulty", snapshot.difficulty)
                put("totalRounds", snapshot.totalRounds)
                put("round", snapshot.round)
                put("score", snapshot.score)
                put("results", JSONArray().apply { snapshot.results.forEach { put(resultJson(it)) } })
                put("roundResult", snapshot.roundResult?.let(::resultJson) ?: JSONObject.NULL)
                put("roundTimerStarted", snapshot.roundTimerStarted)
                put("roundRemainingMs", snapshot.roundRemainingMs)
                put("phase", phaseJson(snapshot.phase))
            }
        }
    }.toString()

    fun decode(raw: String): ActiveSessionSnapshot {
        val o = JSONObject(raw)
        require(o.strictInt("version") == VERSION) { "Unsupported active-session version" }
        val savedAt = o.strictLong("savedAt")
        require(savedAt > 0L) { "Invalid active-session timestamp" }
        return when (o.strictString("kind")) {
            "quiz" -> decodeQuiz(o, savedAt).also(::validateQuiz)
            "guess" -> decodeGuess(o, savedAt).also(::validateGuess)
            else -> throw IllegalArgumentException("Unknown active-session kind")
        }
    }

    private fun decodeQuiz(o: JSONObject, savedAt: Long) = ActiveQuizSession(
        savedAtEpochMs = savedAt,
        destination = ActiveQuizSession.Destination.valueOf(o.strictString("destination")),
        mode = o.strictString("mode"),
        questions = o.strictArray("questions").objects(::questionFromJson),
        userAnswers = nullableStringList(o.strictArray("answers")),
        currentQuestionIndex = o.strictInt("index"),
        score = o.strictInt("score"),
        difficulty = o.strictString("difficulty"),
        aiGenerated = o.strictBoolean("ai"),
        daily = o.strictBoolean("daily"),
        pendingAnswer = o.nullableString("pendingAnswer"),
        pendingTimeUp = o.strictBoolean("pendingTimeUp"),
        questionRemainingMs = o.strictLong("questionRemainingMs"),
        quizElapsedMs = o.strictLong("quizElapsedMs"),
        survivalWrongs = o.strictInt("survivalWrongs"),
        blitzRemainingMs = o.strictLong("blitzRemainingMs"),
        versusStage = o.strictInt("versusStage"),
        versusP1Score = o.strictInt("versusP1Score"),
        versusP1Answers = nullableStringList(o.strictArray("versusP1Answers")),
    )

    private fun decodeGuess(o: JSONObject, savedAt: Long) = ActiveGuessSession(
        savedAtEpochMs = savedAt,
        topic = o.strictString("topic"),
        difficulty = o.strictString("difficulty"),
        totalRounds = o.strictInt("totalRounds"),
        round = o.strictInt("round"),
        score = o.strictInt("score"),
        results = o.strictArray("results").objects(::resultFromJson),
        roundResult = if (o.isNull("roundResult")) null else resultFromJson(o.strictObject("roundResult")),
        phase = phaseFromJson(o.strictObject("phase")),
        roundTimerStarted = o.strictBoolean("roundTimerStarted"),
        roundRemainingMs = o.strictLong("roundRemainingMs"),
    )

    private fun validateQuiz(s: ActiveQuizSession) {
        require(s.mode in setOf("normal", "survival", "blitz", "versus"))
        require(s.questions.isNotEmpty() && s.questions.size <= MAX_QUESTIONS)
        require(s.currentQuestionIndex in s.questions.indices)
        require(s.userAnswers.size == s.currentQuestionIndex)
        require(s.userAnswers.size <= s.questions.size)
        require(s.score == correctCount(s.questions, s.userAnswers))
        require(s.score in 0..s.userAnswers.size)
        require(s.difficulty.isNotBlank() && s.difficulty.length <= 100)
        require(s.pendingAnswer == null || s.pendingAnswer in s.questions[s.currentQuestionIndex].options)
        require(!(s.pendingTimeUp && s.pendingAnswer != null))
        require(s.questionRemainingMs in 0L..MAX_TIMER_MS)
        require(s.quizElapsedMs in 0L..MAX_TIMER_MS)
        require(s.survivalWrongs in 0..3)
        require(s.blitzRemainingMs in 0L..MAX_TIMER_MS)
        require(s.versusStage in 1..2)
        require(s.versusP1Answers.size <= s.questions.size)
        require(s.versusP1Score == correctCount(s.questions, s.versusP1Answers))
        require(s.destination != ActiveQuizSession.Destination.VERSUS_HANDOFF ||
            (s.mode == "versus" && s.versusStage == 2 && s.currentQuestionIndex == 0))
        s.questions.forEach(::validateQuestion)
    }

    private fun validateGuess(s: ActiveGuessSession) {
        require(s.totalRounds in 1..15)
        require(s.round in 1..s.totalRounds)
        require(s.results.size <= s.round)
        require(s.results.map { it.round }.distinct().size == s.results.size)
        require(s.results.all { it.round in 1..s.round })
        require(s.score == s.results.sumOf { it.points })
        require(s.score >= 0)
        require(s.roundResult == null || s.results.lastOrNull() == s.roundResult)
        require(s.roundRemainingMs in 0L..MAX_TIMER_MS)
        require(s.roundTimerStarted || s.roundRemainingMs == 0L)
        require(s.topic.length <= 1_000 && s.difficulty.length <= 100)
        s.results.forEach(::validateResult)
        s.roundResult?.let(::validateResult)
        if (s.phase is SavedGuessPhase.Playing) validatePayload(s.phase.payload)
    }

    private fun correctCount(questions: List<Question>, answers: List<String?>): Int =
        answers.indices.count { answers[it] != null && answers[it] == questions[it].correctAnswer }

    private fun validateQuestion(q: Question) {
        require(q.text.isNotBlank() && q.text.length <= MAX_TEXT)
        require(q.options.size == 4 && q.options.all { it.isNotBlank() && it.length <= MAX_TEXT })
        require(q.options.distinct().size == 4 && q.correctAnswer in q.options)
        require(q.anime.length <= MAX_TEXT && q.theme.length <= MAX_TEXT)
        require(q.difficulty.length <= 100 && q.explanation.length <= MAX_TEXT)
    }

    private fun validatePayload(p: GuessPayload) {
        require(p.targetEntity.isNotBlank() && p.targetEntity.length <= MAX_TEXT)
        require(p.aliases.size <= 20 && p.easyMediumOptions.size <= 20 && p.hardAutocompletePool.size <= 100)
        require((p.aliases + p.easyMediumOptions + p.hardAutocompletePool).all { it.length <= MAX_TEXT })
        require(p.imageQuery.length <= MAX_TEXT)
    }

    private fun validateResult(r: GuessRoundResult) {
        require(r.round in 1..15 && r.target.isNotBlank() && r.target.length <= MAX_TEXT)
        require(r.aliases.size <= 20 && r.aliases.all { it.length <= MAX_TEXT })
        require(r.imageQuery.length <= MAX_TEXT && (r.answerText?.length ?: 0) <= MAX_TEXT)
        require(r.points >= 0 && r.remainingFraction in 0f..1f)
    }

    private fun questionJson(q: Question) = JSONObject().apply {
        put("id", q.id); put("anime", q.anime); put("theme", q.theme); put("difficulty", q.difficulty)
        put("text", q.text); put("options", JSONArray(q.options)); put("correct", q.correctAnswer)
        put("explanation", q.explanation)
    }

    private fun questionFromJson(o: JSONObject) = Question(
        id = o.strictInt("id"), anime = o.strictString("anime"), theme = o.strictString("theme"),
        difficulty = o.strictString("difficulty"), text = o.strictString("text"),
        options = o.strictArray("options").strings(), correctAnswer = o.strictString("correct"),
        explanation = o.strictString("explanation"),
    )

    private fun payloadJson(p: GuessPayload) = JSONObject().apply {
        put("target", p.targetEntity); put("aliases", JSONArray(p.aliases)); put("query", p.imageQuery)
        put("options", JSONArray(p.easyMediumOptions)); put("pool", JSONArray(p.hardAutocompletePool))
    }

    private fun payloadFromJson(o: JSONObject) = GuessPayload(
        targetEntity = o.strictString("target"), aliases = o.strictArray("aliases").strings(),
        imageQuery = o.strictString("query"), easyMediumOptions = o.strictArray("options").strings(),
        hardAutocompletePool = o.strictArray("pool").strings(),
    )

    private fun resultJson(r: GuessRoundResult) = JSONObject().apply {
        put("round", r.round); put("target", r.target); put("aliases", JSONArray(r.aliases))
        put("query", r.imageQuery); put("correct", r.correct)
        put("answer", r.answerText ?: JSONObject.NULL); put("points", r.points)
        put("remaining", r.remainingFraction.toDouble())
    }

    private fun resultFromJson(o: JSONObject) = GuessRoundResult(
        round = o.strictInt("round"), target = o.strictString("target"),
        aliases = o.strictArray("aliases").strings(), imageQuery = o.strictString("query"),
        correct = o.strictBoolean("correct"), answerText = o.nullableString("answer"),
        points = o.strictInt("points"), remainingFraction = o.strictFloat("remaining"),
    )

    private fun phaseJson(phase: SavedGuessPhase) = JSONObject().apply {
        when (phase) {
            SavedGuessPhase.Preparing -> put("kind", "preparing")
            is SavedGuessPhase.Playing -> {
                put("kind", "playing"); put("payload", payloadJson(phase.payload))
                put("imageUrl", phase.imageUrl ?: JSONObject.NULL)
            }
            is SavedGuessPhase.Error -> {
                put("kind", "error"); put("message", phase.message); put("offline", phase.offline)
            }
        }
    }

    private fun phaseFromJson(o: JSONObject): SavedGuessPhase = when (o.strictString("kind")) {
        "preparing" -> SavedGuessPhase.Preparing
        "playing" -> SavedGuessPhase.Playing(
            payloadFromJson(o.strictObject("payload")), o.nullableString("imageUrl")
        )
        "error" -> SavedGuessPhase.Error(o.strictString("message"), o.strictBoolean("offline"))
        else -> throw IllegalArgumentException("Unknown guessing phase")
    }

    private fun nullableStrings(values: List<String?>) = JSONArray().apply {
        values.forEach { put(it ?: JSONObject.NULL) }
    }

    private fun nullableStringList(a: JSONArray): List<String?> =
        (0 until a.length()).map { if (a.isNull(it)) null else a.strictString(it) }

    private fun JSONArray.strings(): List<String> =
        (0 until length()).map { index -> strictString(index) }

    private fun <T> JSONArray.objects(read: (JSONObject) -> T): List<T> =
        (0 until length()).map { read(strictObject(it)) }

    private fun JSONObject.strictArray(key: String): JSONArray =
        get(key) as? JSONArray ?: throw IllegalArgumentException("'$key' must be an array")

    private fun JSONObject.strictObject(key: String): JSONObject =
        get(key) as? JSONObject ?: throw IllegalArgumentException("'$key' must be an object")

    private fun JSONObject.strictString(key: String): String =
        get(key) as? String ?: throw IllegalArgumentException("'$key' must be a string")

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else strictString(key)

    private fun JSONObject.strictBoolean(key: String): Boolean =
        get(key) as? Boolean ?: throw IllegalArgumentException("'$key' must be a boolean")

    private fun JSONObject.strictInt(key: String): Int {
        val value = get(key)
        require(value is Int || value is Long) { "'$key' must be an integer" }
        val long = value.toString().toLongOrNull() ?: throw IllegalArgumentException("Invalid '$key'")
        require(long in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "'$key' is out of range" }
        return long.toInt()
    }

    private fun JSONObject.strictLong(key: String): Long {
        val value = get(key)
        require(value is Int || value is Long) { "'$key' must be an integer" }
        return value.toString().toLongOrNull() ?: throw IllegalArgumentException("Invalid '$key'")
    }

    private fun JSONObject.strictFloat(key: String): Float {
        val value = get(key) as? Number ?: throw IllegalArgumentException("'$key' must be numeric")
        val float = value.toFloat()
        require(float.isFinite()) { "'$key' must be finite" }
        return float
    }

    private fun JSONArray.strictString(index: Int): String =
        get(index) as? String ?: throw IllegalArgumentException("Array value must be a string")

    private fun JSONArray.strictObject(index: Int): JSONObject =
        get(index) as? JSONObject ?: throw IllegalArgumentException("Array value must be an object")
}
