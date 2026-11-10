package quiz.thaton3app.nazo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTopicInputTest {

    private val available = listOf(
        "Characters",
        "Jujutsu Kaisen",
        "Naruto",
        "One Piece",
        "Powers",
        "Story",
        "Naruto Shippuden",
        "One-Punch Man",
        "Pokémon",
        "Sailor Moon",
        "Spy x Family",
        "Sports",
        "Studio Ghibli",
    )

    @Test
    fun blankTopicOffersCuratedStartersInAStableOrder() {
        assertEquals(
            listOf("Naruto", "One Piece", "Jujutsu Kaisen", "Characters"),
            homeTopicSuggestions("   ", available),
        )
    }

    @Test
    fun typedTopicMatchesCaseInsensitivelyAndCapsTheTray() {
        assertEquals(
            listOf("Naruto", "Naruto Shippuden"),
            homeTopicSuggestions("  NARUTO ", available),
        )
        assertEquals(8, homeTopicSuggestions("s", available).size)
    }

    @Test
    fun curatedStartersAreOnlyOfferedWhenTheyExist() {
        assertEquals(
            listOf("Naruto", "Characters"),
            homeTopicSuggestions("", listOf("Characters", "Naruto")),
        )
    }
}
