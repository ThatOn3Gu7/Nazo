package quiz.thaton3app.nazo.ui.screens

import java.net.URI
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarPickerSourcesTest {

    @Test
    fun openingDefaultEmojiTabNeedsNoRemoteSource() {
        val staticTabs = AvatarPickerSources.staticCategories("")

        assertEquals("Emoji", staticTabs.first().first)
        assertNull(AvatarPickerSources.sourceForTab(tabIndex = 0, staticTabCount = staticTabs.size))
        assertEquals(
            DynamicAvatarSource.JIKAN,
            AvatarPickerSources.sourceForTab(staticTabs.size, staticTabs.size),
        )
    }

    @Test
    fun staticCatalogUsesOnlyTheThreeDeclaredGeneratorHosts() {
        val hosts = AvatarPickerSources.staticCategories("nonce")
            .flatMap { it.second }
            .filterIsInstance<AvatarPreset.Generated>()
            .map { URI(it.url).host }
            .toSet()

        assertEquals(setOf("api.dicebear.com", "robohash.org", "i.pravatar.cc"), hosts)
    }

    @Test
    fun dynamicRequestContainsOnlySourceControlsAndAnimeArtRequestsSfw() {
        assertTrue(AvatarPickerSources.requestUrl(DynamicAvatarSource.JIKAN, jikanPage = 7)
            .endsWith("characters?page=7&order_by=members&sort=desc"))
        assertTrue(AvatarPickerSources.requestUrl(DynamicAvatarSource.WAIFU_IM)
            .endsWith("/images/?is_nsfw=false"))
        assertTrue(AvatarPickerSources.requestUrl(DynamicAvatarSource.RANDOM_USER)
            .endsWith("/api/?results=10"))
    }

    @Test
    fun fetchCallsOnlyTheSelectedSourceAndKeepsAllowedCdnImages() = runBlocking {
        val requests = mutableListOf<String>()
        val body = """{
            "data": [
              {"images":{"jpg":{"image_url":"https://cdn.myanimelist.net/images/characters/1.jpg"}}},
              {"images":{"jpg":{"image_url":"https://tracker.example/face.jpg"}}}
            ]
        }"""

        val result = AvatarPickerSources.fetch(DynamicAvatarSource.JIKAN) { url ->
            requests += url
            body
        }

        assertEquals(1, requests.size)
        assertEquals("api.jikan.moe", URI(requests.single()).host)
        assertFalse(result.unavailable)
        assertEquals(1, result.items.size)
        assertEquals("cdn.myanimelist.net", URI(result.items.single().url).host)
    }

    @Test
    fun malformedUnavailableAndUntrustedResponsesFailClosed() = runBlocking {
        val malformed = AvatarPickerSources.fetch(DynamicAvatarSource.RANDOM_USER) { "{not-json" }
        val unavailable = AvatarPickerSources.fetch(DynamicAvatarSource.WAIFU_IM) { null }
        val injected = AvatarPickerSources.parse(
            DynamicAvatarSource.WAIFU_IM,
            """{"images":[
                null,
                {"url":"http://cdn.waifu.im/not-https.jpg"},
                {"url":"https://user@cdn.waifu.im/credential.jpg"},
                {"url":"https://evil.example/pixel.jpg"}
            ]}""",
        )

        assertTrue(malformed.unavailable)
        assertTrue(unavailable.unavailable)
        assertTrue(injected.isEmpty())
    }
}
