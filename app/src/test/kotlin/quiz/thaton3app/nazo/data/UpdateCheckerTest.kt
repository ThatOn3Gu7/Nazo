package quiz.thaton3app.nazo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private fun release(tag: String, prerelease: Boolean = false) = GitHubRelease(
        tag = tag,
        htmlUrl = "https://example.test/$tag",
        body = "notes",
        apkUrl = "https://example.test/$tag.apk",
        prerelease = prerelease,
    )

    @Test
    fun versionOrderingHandlesStableBetaAndRcChannels() {
        assertTrue(isNewerVersion("13.0", "12.0"))
        assertFalse(isNewerVersion("13.0.0", "v13.0"))
        assertTrue(isNewerVersion("v14.0-beta01", "13.0"))
        assertTrue(isNewerVersion("14.0-beta02", "14.0-beta01"))
        assertTrue(isNewerVersion("14.0-rc01", "14.0-beta12"))
        assertTrue(isNewerVersion("14.0", "14.0-rc09"))
        assertFalse(isNewerVersion("14.0-beta01", "14.0"))
        assertFalse(isNewerVersion("13.0", "14.0-beta01"))
    }

    @Test
    fun prereleaseNumbersAreNumericNotLexicographic() {
        assertTrue(isNewerVersion("14.0-beta10", "14.0-beta2"))
        assertEquals(0, compareVersionTags("14.0-beta01", "v14.0-beta1"))
        assertTrue(isNewerVersion("14.0.1-beta01", "14.0"))
    }

    @Test
    fun malformedVersionsNeverBecomeUpdates() {
        assertFalse(isNewerVersion("nightly", "13.0"))
        assertFalse(isNewerVersion("14.0", "unknown"))
        assertNull(compareVersionTags("14..0", "13.0"))
    }

    @Test
    fun stableChannelExcludesPrereleasesWhileOptInIncludesThem() {
        val releases = listOf(
            release("v14.0-beta02", prerelease = true),
            release("v13.0"),
            release("v14.0-beta01", prerelease = true),
        )

        assertEquals("v13.0", selectHighestRelease(releases, includePrereleases = false)?.tag)
        assertEquals("v14.0-beta02", selectHighestRelease(releases, includePrereleases = true)?.tag)
    }

    @Test
    fun finalReleaseOutranksItsPrereleasesForOptedInUsers() {
        val releases = listOf(
            release("v14.0-beta03", prerelease = true),
            release("v14.0-rc01", prerelease = true),
            release("v14.0"),
        )

        assertEquals("v14.0", selectHighestRelease(releases, includePrereleases = true)?.tag)
    }

    @Test
    fun releaseListFiltersDraftsAndPicksTheSignedReleaseApk() {
        val raw = """
            [
              {
                "tag_name": "v99.0-beta01",
                "html_url": "https://example.test/draft",
                "body": "draft",
                "draft": true,
                "prerelease": true,
                "assets": []
              },
              {
                "tag_name": "v14.0-beta02",
                "html_url": "https://example.test/v14.0-beta02",
                "body": "<!--NAZO_NOTES_START-->## What's New\n- Beta polish<!--NAZO_NOTES_END-->",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {
                    "name": "Nazo-debug-14.0-beta02.apk",
                    "browser_download_url": "https://example.test/debug.apk",
                    "size": 300
                  },
                  {
                    "name": "Nazo-release-14.0-beta02.apk",
                    "browser_download_url": "https://example.test/release.apk",
                    "size": 100
                  }
                ]
              },
              {
                "tag_name": "v13.0",
                "html_url": "https://example.test/v13.0",
                "body": "stable",
                "draft": false,
                "prerelease": false,
                "assets": []
              }
            ]
        """.trimIndent()

        val stable = parseReleaseList(raw, includePrereleases = false)
        assertEquals("v13.0", stable?.tag)

        val beta = parseReleaseList(raw, includePrereleases = true)
        assertEquals("v14.0-beta02", beta?.tag)
        assertTrue(beta?.prerelease == true)
        assertEquals("https://example.test/release.apk", beta?.apkUrl)
        assertEquals(100L, beta?.apkSizeBytes)
        assertEquals("What's New\n• Beta polish", beta?.body)
    }
}
