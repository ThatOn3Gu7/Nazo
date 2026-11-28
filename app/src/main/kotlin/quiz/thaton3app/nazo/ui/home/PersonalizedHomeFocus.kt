package quiz.thaton3app.nazo.ui.home

import quiz.thaton3app.nazo.data.QuizStats
import quiz.thaton3app.nazo.data.TopicMastery
import quiz.thaton3app.nazo.data.TopicMasteryStatus
import quiz.thaton3app.nazo.data.topicMastery

/** The single best next action surfaced near the top of Home. */
enum class HomeFocusAction {
    Daily,
    Practice,
    TopicQuiz,
}

data class PersonalizedHomeFocus(
    val action: HomeFocusAction,
    val title: String,
    val reason: String,
    val topic: String = "",
    val difficulty: String = "Adaptive",
    val questionCount: Int = 5,
)

/**
 * Deterministic recommendation order:
 *  1. protect a live streak when the last play was yesterday;
 *  2. turn outstanding mistakes into the next learning action;
 *  3. offer today's Daily Ladder;
 *  4. revisit a recent weak topic, then any recent topic;
 *  5. use existing mastery as a legacy fallback, then offer a mixed Adaptive quiz.
 */
fun personalizedHomeFocus(
    stats: QuizStats,
    dailyCompleted: Boolean,
    practiceCount: Int,
    todayEpochDay: Long = QuizStats.localEpochDay(),
): PersonalizedHomeFocus {
    val streakAtRisk = !dailyCompleted &&
        stats.currentStreakDays > 0 &&
        stats.lastStreakActivityEpochDay() == todayEpochDay - 1
    if (streakAtRisk) {
        return PersonalizedHomeFocus(
            action = HomeFocusAction.Daily,
            title = "Keep your ${stats.currentStreakDays}-day streak moving",
            reason = "Today's five-question ladder is ready and works offline.",
        )
    }

    if (practiceCount > 0) {
        return PersonalizedHomeFocus(
            action = HomeFocusAction.Practice,
            title = "Turn $practiceCount ${if (practiceCount == 1) "miss" else "misses"} into progress",
            reason = "Retry what tripped you up—no generation or network needed.",
        )
    }

    if (!dailyCompleted) {
        return PersonalizedHomeFocus(
            action = HomeFocusAction.Daily,
            title = "Climb today's Daily Ladder",
            reason = "Warm-up, Climb, then Summit—five questions with bonus XP.",
        )
    }

    val mastery = stats.topicMastery()
    val recentMastery = stats.recentTopics.mapNotNull { recent ->
        mastery.firstOrNull { it.topic.equals(recent, ignoreCase = true) }
    }
    val weakRecent = recentMastery.firstOrNull {
        it.status == TopicMasteryStatus.NeedsPractice
    }
    if (weakRecent != null) return weakTopicFocus(weakRecent)

    val recent = stats.recentTopics.firstOrNull()
    if (recent != null) {
        val signal = recentMastery.firstOrNull {
            it.topic.equals(recent, ignoreCase = true)
        }
        return PersonalizedHomeFocus(
            action = HomeFocusAction.TopicQuiz,
            title = when (signal?.status) {
                TopicMasteryStatus.Strong -> "Push further in $recent"
                TopicMasteryStatus.Improving -> "Keep $recent moving"
                else -> "Continue with $recent"
            },
            reason = "A five-question Adaptive run picks up where you left off.",
            topic = recent,
        )
    }

    // Older statistics predate the explicit recency trail. Give those players
    // a useful mastery-based action immediately; their next run establishes
    // the ordered recent-topic signal used above.
    val familiar = mastery.firstOrNull {
        it.topic != "Mixed topics" && it.status == TopicMasteryStatus.NeedsPractice
    } ?: mastery.firstOrNull { it.topic != "Mixed topics" }
    if (familiar != null) {
        return if (familiar.status == TopicMasteryStatus.NeedsPractice) {
            weakTopicFocus(familiar)
        } else {
            PersonalizedHomeFocus(
                action = HomeFocusAction.TopicQuiz,
                title = "Revisit ${familiar.topic}",
                reason = "A five-question Adaptive run builds on your existing mastery.",
                topic = familiar.topic,
            )
        }
    }

    return PersonalizedHomeFocus(
        action = HomeFocusAction.TopicQuiz,
        title = "Find your next specialty",
        reason = "Start a mixed five-question Adaptive quiz and build a new signal.",
    )
}

private fun weakTopicFocus(mastery: TopicMastery) = PersonalizedHomeFocus(
    action = HomeFocusAction.TopicQuiz,
    title = "Strengthen ${mastery.topic}",
    reason = "${mastery.recentAccuracyPercent}% recent accuracy—Adaptive will meet you where you are.",
    topic = mastery.topic,
)
