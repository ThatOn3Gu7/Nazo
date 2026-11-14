package quiz.thaton3app.nazo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NazoQuitDialogTest {
    @Test
    fun `stay resumes the timer and accumulates the exact paused duration`() {
        var now = 1_000L
        val state = GameplayQuitPauseState(nowMillis = { now })

        state.requestQuit()
        assertTrue(state.dialogVisible)
        assertTrue(state.timerPaused)

        now = 4_250L
        assertEquals(3_250L, state.stay())
        assertFalse(state.dialogVisible)
        assertFalse(state.timerPaused)
        assertEquals(3_250L, state.accumulatedPauseMillis)

        now = 8_000L
        state.requestQuit()
        now = 8_750L
        assertEquals(750L, state.stay())
        assertEquals(4_000L, state.accumulatedPauseMillis)
    }

    @Test
    fun `confirming quit hides the dialog without briefly resuming gameplay`() {
        var now = 10_000L
        val state = GameplayQuitPauseState(nowMillis = { now })

        state.requestQuit()
        now = 15_000L
        state.hideForQuit()

        assertFalse(state.dialogVisible)
        assertTrue(state.timerPaused)
        assertEquals(0L, state.accumulatedPauseMillis)

        // A second request during the dialog exit must not reset the pause.
        state.requestQuit()
        assertFalse(state.dialogVisible)
        assertTrue(state.timerPaused)
    }

    @Test
    fun `elapsed gameplay excludes only pauses completed after this timer started`() {
        assertEquals(
            4_000L,
            elapsedGameplayMillis(
                startedAtMillis = 10_000L,
                nowMillis = 16_000L,
                accumulatedPauseMillis = 5_000L,
                pauseMillisAtStart = 3_000L,
            ),
        )
        assertEquals(
            0L,
            elapsedGameplayMillis(
                startedAtMillis = 20_000L,
                nowMillis = 19_000L,
                accumulatedPauseMillis = 0L,
                pauseMillisAtStart = 0L,
            ),
        )
    }
}
