package quiz.thaton3app.nazo.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityPreferencesTest {
    @Test
    fun defaultsPreserveExistingPresentationAndFeedback() {
        val prefs = AccessibilityPreferences(FakeSharedPreferences())

        assertFalse(prefs.reduceMotion)
        assertFalse(prefs.highContrast)
        assertEquals(AccessibilityTextScale.STANDARD, prefs.textScale)
        assertEquals(TimerAssist.STANDARD, prefs.timerAssist)
        assertTrue(prefs.hapticsEnabled)
        assertFalse(prefs.classicHomeLayout)
    }

    @Test
    fun everyChoiceRoundTrips() {
        val storage = FakeSharedPreferences()
        val prefs = AccessibilityPreferences(storage)

        prefs.reduceMotion = true
        prefs.highContrast = true
        prefs.textScale = AccessibilityTextScale.EXTRA_LARGE
        prefs.timerAssist = TimerAssist.EXTENDED
        prefs.hapticsEnabled = false
        prefs.classicHomeLayout = true

        val restored = AccessibilityPreferences(storage)
        assertTrue(restored.reduceMotion)
        assertTrue(restored.highContrast)
        assertEquals(AccessibilityTextScale.EXTRA_LARGE, restored.textScale)
        assertEquals(TimerAssist.EXTENDED, restored.timerAssist)
        assertFalse(restored.hapticsEnabled)
        assertTrue(restored.classicHomeLayout)
    }

    @Test
    fun malformedAndFutureValuesFallBackSafely() {
        val storage = FakeSharedPreferences()
        storage.edit()
            .putString(AccessibilityPreferences.KEY_REDUCE_MOTION, "not a boolean")
            .putString(AccessibilityPreferences.KEY_CLASSIC_HOME_LAYOUT, "not a boolean")
            .putString(AccessibilityPreferences.KEY_TEXT_SCALE, "future_scale")
            .putString(AccessibilityPreferences.KEY_TIMER_ASSIST, "future_timer")
            .commit()

        val prefs = AccessibilityPreferences(storage)

        assertFalse(prefs.reduceMotion)
        assertFalse(prefs.classicHomeLayout)
        assertEquals(AccessibilityTextScale.STANDARD, prefs.textScale)
        assertEquals(TimerAssist.STANDARD, prefs.timerAssist)
    }

    @Test
    fun timerAssistScalesEveryBaseClockDeterministically() {
        assertEquals(20, AccessibilityPolicy.seconds(20, TimerAssist.STANDARD))
        assertEquals(30, AccessibilityPolicy.seconds(20, TimerAssist.RELAXED))
        assertEquals(40, AccessibilityPolicy.seconds(20, TimerAssist.EXTENDED))
        assertEquals(90_000L, AccessibilityPolicy.millis(60_000L, TimerAssist.RELAXED))
        assertEquals(120_000L, AccessibilityPolicy.millis(60_000L, TimerAssist.EXTENDED))
    }

    @Test
    fun eitherSystemOrAppCanRequireReducedMotion() {
        assertFalse(AccessibilityPolicy.reduceMotion(false, false))
        assertTrue(AccessibilityPolicy.reduceMotion(true, false))
        assertTrue(AccessibilityPolicy.reduceMotion(false, true))
        assertTrue(AccessibilityPolicy.reduceMotion(true, true))
    }
}
