package quiz.thaton3app.nazo.data

import kotlin.math.roundToInt

/** A confidence-oriented view of one anime/topic, derived from persisted quiz stats. */
data class TopicMastery(
    val topic: String,
    val answered: Int,
    val correct: Int,
    val accuracyPercent: Int,
    val recentAccuracyPercent: Int,
    val confidencePercent: Int,
    val trendDeltaPercent: Int,
    val status: TopicMasteryStatus,
)

enum class TopicMasteryStatus {
    Strong,
    Improving,
    NeedsPractice,
    Building,
}

/**
 * Turns the aggregate counters and bounded recent outcome windows into a stable
 * mastery signal. Accuracy supplies quality; repeated exposure supplies
 * confidence; the recent-vs-previous window supplies direction.
 */
fun QuizStats.topicMastery(): List<TopicMastery> = animeAnswered.mapNotNull { (storedTopic, answered) ->
    if (answered <= 0) return@mapNotNull null
    val correct = animeCorrect[storedTopic].orZero().coerceIn(0, answered)
    val accuracy = percent(correct, answered)
    val history = animeRecentOutcomes[storedTopic].orEmpty()
        .filter { it == '0' || it == '1' }
        .takeLast(QuizStats.RECENT_TOPIC_OUTCOME_LIMIT)
    val recentWindow = history.takeLast(4)
    val recentAccuracy = if (recentWindow.isEmpty()) accuracy else outcomePercent(recentWindow)
    val comparisonSize = minOf(4, history.length / 2)
    val previousWindow = if (comparisonSize == 0) "" else {
        history.dropLast(comparisonSize).takeLast(comparisonSize)
    }
    val trendDelta = if (previousWindow.isEmpty()) 0 else {
        outcomePercent(history.takeLast(comparisonSize)) - outcomePercent(previousWindow)
    }

    val evidence = answered.coerceAtMost(8) / 8f
    val blendedAccuracy = if (history.isEmpty()) {
        accuracy.toFloat()
    } else {
        accuracy * 0.55f + recentAccuracy * 0.45f
    }
    val confidence = (
        blendedAccuracy * (0.55f + evidence * 0.45f)
    ).roundToInt().coerceIn(0, 100)

    val status = when {
        comparisonSize >= 2 && trendDelta >= 20 && recentAccuracy >= 60 ->
            TopicMasteryStatus.Improving
        answered >= 5 && accuracy >= 75 && recentAccuracy >= 70 && confidence >= 65 ->
            TopicMasteryStatus.Strong
        answered >= 3 && (accuracy < 55 || recentAccuracy < 60) ->
            TopicMasteryStatus.NeedsPractice
        else -> TopicMasteryStatus.Building
    }

    TopicMastery(
        topic = if (storedTopic == "Unknown") "Mixed topics" else storedTopic,
        answered = answered,
        correct = correct,
        accuracyPercent = accuracy,
        recentAccuracyPercent = recentAccuracy,
        confidencePercent = confidence,
        trendDeltaPercent = trendDelta,
        status = status,
    )
}.sortedWith(
    compareByDescending<TopicMastery> { it.confidencePercent }
        .thenByDescending { it.answered }
        .thenBy { it.topic.lowercase() }
)

/** Keeps the card compact while ensuring every available signal is represented. */
fun topicMasteryHighlights(
    mastery: List<TopicMastery>,
    limit: Int = 6,
): List<TopicMastery> {
    if (limit <= 0 || mastery.isEmpty()) return emptyList()
    val featured = mutableListOf<TopicMastery>()
    listOf(
        TopicMasteryStatus.Strong,
        TopicMasteryStatus.Improving,
        TopicMasteryStatus.NeedsPractice,
        TopicMasteryStatus.Building,
    ).forEach { status ->
        mastery.firstOrNull { it.status == status }?.let(featured::add)
    }
    mastery.asSequence()
        .filterNot { candidate -> featured.any { it.topic == candidate.topic } }
        .forEach { featured.add(it) }
    return featured.take(limit)
}

private fun Int?.orZero(): Int = this ?: 0

private fun percent(part: Int, whole: Int): Int =
    if (whole <= 0) 0 else (
        part.toLong() * 100L / whole.toLong()
    ).toInt().coerceIn(0, 100)

private fun outcomePercent(outcomes: String): Int =
    percent(outcomes.count { it == '1' }, outcomes.length)
