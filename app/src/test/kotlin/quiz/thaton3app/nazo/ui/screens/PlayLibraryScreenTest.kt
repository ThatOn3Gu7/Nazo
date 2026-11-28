package quiz.thaton3app.nazo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayLibraryScreenTest {
    @Test
    fun libraryOpensCuratedForNewPlayersAndSavedSetupsForReturningPlayers() {
        assertEquals(PlayLibrarySection.Curated, playLibraryInitialSection(collectionCount = 0))
        assertEquals(PlayLibrarySection.Collections, playLibraryInitialSection(collectionCount = 1))
        assertEquals(PlayLibrarySection.Collections, playLibraryInitialSection(collectionCount = 8))
    }
}
