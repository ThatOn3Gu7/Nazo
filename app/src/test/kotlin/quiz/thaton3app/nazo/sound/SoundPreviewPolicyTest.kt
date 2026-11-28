package quiz.thaton3app.nazo.sound

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundPreviewPolicyTest {

    @Test
    fun regularEffectsRemainSilentWhileTheMasterSwitchIsOff() {
        assertFalse(shouldPlaySound(soundEnabled = false, isPreview = false))
    }

    @Test
    fun pickerPreviewsPlayWhileTheMasterSwitchIsOff() {
        assertTrue(shouldPlaySound(soundEnabled = false, isPreview = true))
    }

    @Test
    fun enabledSoundAllowsBothRegularEffectsAndPreviews() {
        assertTrue(shouldPlaySound(soundEnabled = true, isPreview = false))
        assertTrue(shouldPlaySound(soundEnabled = true, isPreview = true))
    }
}
