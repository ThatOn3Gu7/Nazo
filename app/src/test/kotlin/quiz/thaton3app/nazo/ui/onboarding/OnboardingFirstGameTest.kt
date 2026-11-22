package quiz.thaton3app.nazo.ui.onboarding

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import quiz.thaton3app.nazo.data.LocalQuestionBank

class OnboardingFirstGameTest {

    @Test
    fun enteredTopicIsTrimmedAndNeverReplaced() {
        repeat(16) { seed ->
            assertEquals(
                "Fullmetal Alchemist",
                resolveOnboardingFirstGameTopic(
                    topicInput = "  Fullmetal Alchemist  ",
                    random = Random(seed),
                ),
            )
        }
    }

    @Test
    fun blankTopicAlwaysResolvesToAPlayablePopularAnime() {
        val available = LocalQuestionBank.suggestions().toSet()
        ONBOARDING_POPULAR_TOPICS.forEach { topic ->
            assertTrue("onboarding default has no local questions: $topic", topic in available)
            assertEquals(
                "onboarding default cannot start a five-question game: $topic",
                5,
                LocalQuestionBank.getQuestions(count = 5, topic = topic).size,
            )
        }

        repeat(64) { seed ->
            val topic = resolveOnboardingFirstGameTopic("   ", Random(seed))
            assertTrue("unexpected onboarding default: $topic", topic in ONBOARDING_POPULAR_TOPICS)
        }
    }

    @Test
    fun blankTopicDoesNotGiveEveryNewPlayerTheSameAnime() {
        val sampledTopics = (0 until 64)
            .map { seed -> resolveOnboardingFirstGameTopic("", Random(seed)) }
            .toSet()

        assertTrue("expected randomized onboarding topics", sampledTopics.size > 1)
    }
}
