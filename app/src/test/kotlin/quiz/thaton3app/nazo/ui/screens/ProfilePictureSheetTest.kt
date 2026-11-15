package quiz.thaton3app.nazo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfilePictureSheetTest {

    @Test
    fun emojiSelectionKeepsTheStoredAvatarContract() {
        assertEquals(
            "emoji:🦊",
            AvatarPreset.Emoji("🦊").storedProfilePictureValue(),
        )
    }

    @Test
    fun generatedSelectionKeepsTheRemoteUrlContract() {
        val url = "https://api.dicebear.com/10.x/adventurer/png?seed=Aiko"
        assertEquals(
            url,
            AvatarPreset.Generated(id = "aiko", url = url).storedProfilePictureValue(),
        )
    }
}
