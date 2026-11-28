package quiz.thaton3app.nazo.challenge

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import quiz.thaton3app.nazo.data.LocalQuestionBank
import quiz.thaton3app.nazo.data.Question

/** Visual identity used by the pack cards without coupling the catalog to Compose. */
enum class ChallengePackStyle {
    SHINOBI,
    GRAND_LINE,
    TITAN,
    HASHIRA,
}

/** One shipped, hand-selected offline challenge. Questions remain in authored order. */
data class ChallengePack(
    val id: String,
    val title: String,
    val eyebrow: String,
    val description: String,
    val curatorNote: String,
    val difficulty: String,
    val style: ChallengePackStyle,
    val questions: List<Question>,
) {
    val questionCount: Int get() = questions.size
    val series: List<String> get() = questions.map(Question::anime).distinct()
}

/**
 * Versioned-by-source curated catalog. Each exact question text is intentionally
 * named here rather than selected by a broad topic filter, so a pack cannot
 * silently drift when the larger local bank grows or changes ordering.
 */
object ChallengePackCatalog {
    private val questionByText = LocalQuestionBank.stablePool().associateBy(Question::text)

    private fun pack(
        id: String,
        title: String,
        eyebrow: String,
        description: String,
        curatorNote: String,
        difficulty: String,
        style: ChallengePackStyle,
        questionTexts: List<String>,
    ): ChallengePack = ChallengePack(
        id = id,
        title = title,
        eyebrow = eyebrow,
        description = description,
        curatorNote = curatorNote,
        difficulty = difficulty,
        style = style,
        questions = questionTexts.map { text ->
            requireNotNull(questionByText[text]) { "Missing curated question: $text" }
        },
    ).also(::validate)

    val all: List<ChallengePack> = listOf(
        pack(
            id = "shinobi-legacy-v1",
            title = "Shinobi Legacy",
            eyebrow = "HIDDEN LEAF ARCHIVES",
            description = "Techniques, mentors, and choices that shaped Naruto's ninja world.",
            curatorNote = "Eight hand-picked Naruto questions · balanced from fundamentals to lore",
            difficulty = "Medium",
            style = ChallengePackStyle.SHINOBI,
            questionTexts = listOf(
                "What is Naruto Uzumaki's signature jutsu?",
                "What is Naruto's favorite food?",
                "Who is the sensei of Team 7 (Naruto, Sasuke, Sakura)?",
                "Jiraiya is famously one of the Legendary what?",
                "What does Sage Mode absorb?",
                "What is the Mangekyo Sharingan's price?",
                "What is Pain's real name?",
                "Who ordered Itachi to carry out the Uchiha massacre?",
            ),
        ),
        pack(
            id = "grand-line-odyssey-v1",
            title = "Grand Line Odyssey",
            eyebrow = "PIRATE KING ROUTE",
            description = "Crew dreams, strange seas, Haki, and the history hidden in stone.",
            curatorNote = "Eight hand-picked One Piece questions · crew, world, and legacy",
            difficulty = "Medium",
            style = ChallengePackStyle.GRAND_LINE,
            questionTexts = listOf(
                "What is Luffy's ultimate dream?",
                "What is the major weakness of all Devil Fruit users?",
                "What was the name of the Straw Hats' first ship?",
                "Which Haki type lets a user sense others' presence?",
                "What are Poneglyphs made of?",
                "What is Zoro's ambition?",
                "What is the true name of the final island of the Grand Line?",
                "Which ancient weapon is a mermaid princess?",
            ),
        ),
        pack(
            id = "titan-frontlines-v1",
            title = "Titan Frontlines",
            eyebrow = "BEYOND THE WALLS",
            description = "The Scouts, the walls, and the truths that changed Paradis forever.",
            curatorNote = "Eight hand-picked Attack on Titan questions · people, places, and reveals",
            difficulty = "Hard",
            style = ChallengePackStyle.TITAN,
            questionTexts = listOf(
                "What gear lets soldiers fly?",
                "What is the middle wall?",
                "What title is Levi Ackerman known by?",
                "What is the role of the Survey Corps?",
                "What island do the heroes live on?",
                "Who is the Armored Titan?",
                "Who is the Colossal Titan?",
                "What is Historia's royal surname?",
            ),
        ),
        pack(
            id = "hashira-trial-v1",
            title = "Hashira Trial",
            eyebrow = "DEMON SLAYER CORPS",
            description = "Breathing styles, mentors, demons, and the swordsmen at the summit.",
            curatorNote = "Eight hand-picked Demon Slayer questions · a focused Corps trial",
            difficulty = "Medium",
            style = ChallengePackStyle.HASHIRA,
            questionTexts = listOf(
                "What Breathing Style does Rengoku use?",
                "What does Nezuko do instead of eating humans?",
                "What is the name of Tanjiro's teacher?",
                "What organization fights demons?",
                "Who is Tanjiro's sister turned into a demon?",
                "Who is the antagonist that created the demons?",
                "What are the Hashira?",
                "Who is Lower Moon Five?",
            ),
        ),
    ).also { packs ->
        require(packs.map(ChallengePack::id).toSet().size == packs.size) {
            "Duplicate challenge-pack id"
        }
    }

    fun find(id: String?): ChallengePack? = all.firstOrNull { it.id == id }

    fun getRequired(id: String): ChallengePack =
        requireNotNull(find(id)) { "Unknown challenge pack" }

    private fun validate(pack: ChallengePack) {
        require(pack.id.matches(Regex("[a-z0-9-]{3,80}"))) { "Invalid challenge-pack id" }
        require(pack.title.isNotBlank() && pack.title.length <= 80)
        require(pack.eyebrow.isNotBlank() && pack.eyebrow.length <= 80)
        require(pack.description.isNotBlank() && pack.description.length <= 240)
        require(pack.curatorNote.isNotBlank() && pack.curatorNote.length <= 240)
        require(pack.difficulty in setOf("Easy", "Medium", "Hard", "Otaku Master"))
        require(pack.questions.size == 8) { "Curated packs must contain eight questions" }
        require(pack.questions.map(Question::identity).toSet().size == pack.questions.size) {
            "Duplicate question in challenge pack"
        }
        pack.questions.forEach { question ->
            require(question.text.isNotBlank())
            require(question.options.size == 4 && question.options.distinct().size == 4)
            require(question.correctAnswer in question.options)
            require(question.explanation.isNotBlank())
        }
    }
}

/** Durable progress for one catalog pack. */
data class ChallengePackProgress(
    val packId: String,
    val bestScore: Int,
    val questionCount: Int,
    val completionCount: Int,
    val lastScore: Int,
    val lastPlayedAtEpochMs: Long,
) {
    val mastered: Boolean get() = bestScore == questionCount
}

/** Result snapshot shown after a pack completion. */
data class ChallengePackResult(
    val pack: ChallengePack,
    val progress: ChallengePackProgress,
    val previousBest: Int?,
    val newBest: Boolean,
    val firstMastery: Boolean,
)

/** Small, strict local store for curated-pack bests and completion counts. */
class ChallengePackProgressStore internal constructor(
    private val prefs: SharedPreferences,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    @Synchronized
    fun get(): List<ChallengePackProgress> {
        val raw = try {
            prefs.getString(KEY, null)
        } catch (_: ClassCastException) {
            return repair()
        } ?: return emptyList()
        return try {
            ChallengePackProgressJson.decode(raw)
        } catch (_: JSONException) {
            repair()
        } catch (_: IllegalArgumentException) {
            repair()
        }
    }

    @Synchronized
    fun submit(packId: String, score: Int): ChallengePackResult {
        val pack = ChallengePackCatalog.getRequired(packId)
        require(score in 0..pack.questionCount) { "Invalid challenge-pack score" }
        val progress = get().toMutableList()
        val previous = progress.firstOrNull { it.packId == packId }
        val previousBest = previous?.bestScore
        val next = ChallengePackProgress(
            packId = packId,
            bestScore = maxOf(score, previousBest ?: 0),
            questionCount = pack.questionCount,
            completionCount = ((previous?.completionCount ?: 0) + 1).coerceAtMost(MAX_COMPLETIONS),
            lastScore = score,
            lastPlayedAtEpochMs = nowEpochMs().coerceAtLeast(1L),
        )
        if (previous != null) progress.remove(previous)
        progress += next
        persist(progress.sortedBy { item ->
            ChallengePackCatalog.all.indexOfFirst { it.id == item.packId }
        })
        return ChallengePackResult(
            pack = pack,
            progress = next,
            previousBest = previousBest,
            newBest = previous == null || score > previous.bestScore,
            firstMastery = score == pack.questionCount && previous?.mastered != true,
        )
    }

    private fun repair(): List<ChallengePackProgress> =
        emptyList<ChallengePackProgress>().also(::persist)

    private fun persist(progress: List<ChallengePackProgress>) {
        prefs.edit().putString(KEY, ChallengePackProgressJson.encode(progress)).apply()
    }

    internal companion object {
        const val PREFS_NAME = "nazo_challenge_packs"
        const val KEY = "challenge_pack_progress_v1"
        const val MAX_COMPLETIONS = 1_000_000
    }
}

internal object ChallengePackProgressJson {
    private const val VERSION = 1

    fun encode(progress: List<ChallengePackProgress>): String {
        validate(progress)
        return JSONObject().apply {
            put("version", VERSION)
            put("packs", JSONArray().apply {
                progress.forEach { item ->
                    put(
                        JSONObject()
                            .put("id", item.packId)
                            .put("bestScore", item.bestScore)
                            .put("questionCount", item.questionCount)
                            .put("completionCount", item.completionCount)
                            .put("lastScore", item.lastScore)
                            .put("lastPlayedAt", item.lastPlayedAtEpochMs),
                    )
                }
            })
        }.toString()
    }

    fun decode(raw: String): List<ChallengePackProgress> {
        require(raw.isNotBlank()) { "Challenge-pack progress is blank" }
        val root = JSONObject(raw)
        require(strictInt(root, "version") == VERSION) { "Unsupported challenge-pack version" }
        val array = root.get("packs") as? JSONArray
            ?: throw IllegalArgumentException("Challenge-pack progress must be an array")
        require(array.length() <= ChallengePackCatalog.all.size) { "Too many challenge-pack entries" }
        return List(array.length()) { index ->
            val item = array.get(index) as? JSONObject
                ?: throw IllegalArgumentException("Challenge-pack entry must be an object")
            ChallengePackProgress(
                packId = strictString(item, "id"),
                bestScore = strictInt(item, "bestScore"),
                questionCount = strictInt(item, "questionCount"),
                completionCount = strictInt(item, "completionCount"),
                lastScore = strictInt(item, "lastScore"),
                lastPlayedAtEpochMs = strictLong(item, "lastPlayedAt"),
            )
        }.also(::validate)
    }

    private fun validate(progress: List<ChallengePackProgress>) {
        require(progress.size <= ChallengePackCatalog.all.size)
        require(progress.map(ChallengePackProgress::packId).toSet().size == progress.size) {
            "Duplicate challenge-pack progress"
        }
        progress.forEach { item ->
            val pack = ChallengePackCatalog.find(item.packId)
                ?: throw IllegalArgumentException("Unknown challenge pack")
            require(item.questionCount == pack.questionCount)
            require(item.bestScore in 0..item.questionCount)
            require(item.lastScore in 0..item.questionCount)
            require(item.completionCount in 1..ChallengePackProgressStore.MAX_COMPLETIONS)
            require(item.lastPlayedAtEpochMs > 0L)
        }
    }

    private fun strictString(o: JSONObject, key: String): String =
        (o.get(key) as? String)
            ?: throw IllegalArgumentException("Challenge-pack '$key' must be a string")

    private fun strictInt(o: JSONObject, key: String): Int {
        val value = strictLong(o, key)
        require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        return value.toInt()
    }

    private fun strictLong(o: JSONObject, key: String): Long = when (val value = o.get(key)) {
        is Byte -> value.toLong()
        is Short -> value.toLong()
        is Int -> value.toLong()
        is Long -> value
        else -> throw IllegalArgumentException("Challenge-pack '$key' must be a whole number")
    }
}
