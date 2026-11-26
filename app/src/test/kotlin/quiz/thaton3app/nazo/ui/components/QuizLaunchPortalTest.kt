package quiz.thaton3app.nazo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class QuizLaunchPortalTest {

    private val origin = QuizLaunchOrigin(
        left = 24f,
        top = 600f,
        right = 376f,
        bottom = 658f,
    )

    @Test
    fun portalStartsAtButtonAndCoversTheExactViewport() {
        val start = quizLaunchPortalFrame(
            origin = origin,
            viewportWidth = 400f,
            viewportHeight = 800f,
            terminalDiameter = 58f,
            progress = 0f,
            initialCornerRadius = 18f,
        )
        assertEquals(24f, start.left, 0f)
        assertEquals(600f, start.top, 0f)
        assertEquals(352f, start.width, 0f)
        assertEquals(58f, start.height, 0f)
        assertEquals(18f, start.cornerRadius, 0f)

        val covered = quizLaunchPortalFrame(
            origin = origin,
            viewportWidth = 400f,
            viewportHeight = 800f,
            terminalDiameter = 58f,
            progress = 1f,
            initialCornerRadius = 18f,
        )
        assertEquals(0f, covered.left, 0f)
        assertEquals(0f, covered.top, 0f)
        assertEquals(400f, covered.width, 0f)
        assertEquals(800f, covered.height, 0f)
        assertEquals(0f, covered.cornerRadius, 0f)
    }

    @Test
    fun coveredPortalFoldsIntoDestinationCentreBeforeDisappearing() {
        val end = quizLaunchPortalFrame(
            origin = origin,
            viewportWidth = 400f,
            viewportHeight = 800f,
            terminalDiameter = 58f,
            progress = 2f,
            initialCornerRadius = 18f,
        )

        assertEquals(171f, end.left, 0f)
        assertEquals(371f, end.top, 0f)
        assertEquals(58f, end.width, 0f)
        assertEquals(58f, end.height, 0f)
        assertEquals(29f, end.cornerRadius, 0f)
        assertEquals(0f, end.alpha, 0f)
    }

    @Test
    fun generatedLaunchWaitsForQuestionButDirectLaunchDoesNot() {
        var generated = nextQuizLaunchHandoff(
            QuizLaunchHandoffState.Idle,
            QuizLaunchHandoffEvent.PortalStarted,
        )
        generated = nextQuizLaunchHandoff(generated, QuizLaunchHandoffEvent.LoadingReached)
        assertEquals(QuizLaunchHandoffState.AwaitingQuestion, generated)
        generated = nextQuizLaunchHandoff(generated, QuizLaunchHandoffEvent.QuestionReached)
        assertEquals(QuizLaunchHandoffState.RevealingQuestion, generated)
        generated = nextQuizLaunchHandoff(generated, QuizLaunchHandoffEvent.RevealFinished)
        assertEquals(QuizLaunchHandoffState.Idle, generated)

        var direct = nextQuizLaunchHandoff(
            QuizLaunchHandoffState.Idle,
            QuizLaunchHandoffEvent.PortalStarted,
        )
        direct = nextQuizLaunchHandoff(direct, QuizLaunchHandoffEvent.QuestionReached)
        assertEquals(QuizLaunchHandoffState.Idle, direct)
    }

    @Test
    fun cancellingLoadingCannotLeakARevealIntoTheNextQuiz() {
        var state = nextQuizLaunchHandoff(
            QuizLaunchHandoffState.Idle,
            QuizLaunchHandoffEvent.LoadingReached,
        )
        state = nextQuizLaunchHandoff(state, QuizLaunchHandoffEvent.Cancelled)
        state = nextQuizLaunchHandoff(state, QuizLaunchHandoffEvent.QuestionReached)

        assertEquals(QuizLaunchHandoffState.Idle, state)
    }
}
