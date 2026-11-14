package quiz.thaton3app.nazo.modes.guessing_game

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DuckDuckGoImagePolicyTest {

    @Test
    fun bothDuckDuckGoRequestsExplicitlySelectStrictSafeSearch() {
        val token = DuckDuckGoImagePolicy.tokenUrl("Satoru Gojo")
        val results = DuckDuckGoImagePolicy.resultsUrl("Satoru Gojo", "3-abc")

        assertTrue(token.contains("q=Satoru%20Gojo"))
        assertTrue(token.contains("kp=1"))
        assertTrue(results.contains("p=1"))
        assertTrue(results.contains("kp=1"))
        assertTrue(results.contains("vqd=3-abc"))
    }

    @Test
    fun selectorSkipsMalformedExplicitAndUnrelatedEntries() {
        val json = JSONObject(
            """{"results":[
              null,
              "not an object",
              {"title":"Satoru Gojo explicit hentai","image":"https://images.example/gojo.jpg"},
              {"title":"Naruto Uzumaki","image":"https://images.example/naruto.jpg"},
              {"title":"Satoru Gojo portrait","image":"http://images.example/gojo.jpg"},
              {"title":"Satoru Gojo official art","image":"https://images.example/gojo.png"}
            ]}"""
        )

        assertEquals(
            "https://images.example/gojo.png",
            DuckDuckGoImagePolicy.selectCandidate(json, "Satoru Gojo"),
        )
    }

    @Test
    fun opaqueBingThumbnailStillNeedsTargetEvidenceInMetadata() {
        val unrelated = JSONObject(
            """{"results":[{
              "title":"Unrelated person", "thumbnail":"https://tse1.mm.bing.net/th?id=123"
            }]}"""
        )
        val related = JSONObject(
            """{"results":[{
              "title":"Satoru Gojo anime character", "thumbnail":"https://tse1.mm.bing.net/th?id=456"
            }]}"""
        )

        assertNull(DuckDuckGoImagePolicy.selectCandidate(unrelated, "Satoru Gojo"))
        assertEquals(
            "https://tse1.mm.bing.net/th?id=456",
            DuckDuckGoImagePolicy.selectCandidate(related, "Satoru Gojo"),
        )
    }

    @Test
    fun missingOrMalformedResultCollectionsFailClosed() {
        assertNull(DuckDuckGoImagePolicy.selectCandidate(JSONObject(), "Nami"))
        assertNull(
            DuckDuckGoImagePolicy.selectCandidate(
                JSONObject("""{"results":[{"title":"Nami","image":"file:///secret.png"}]}"""),
                "Nami",
            )
        )
    }
}
