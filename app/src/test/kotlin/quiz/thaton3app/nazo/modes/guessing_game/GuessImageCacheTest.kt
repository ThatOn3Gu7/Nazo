package quiz.thaton3app.nazo.modes.guessing_game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Unit tests for the guessing-game image byte cache and the download size
 * cap. These cover the machine-checkable parts of the one-download-per-image
 * contract: the play screen's fetch must be served from the URL search's /
 * prefetch's bytes without a second network read (counted via an injected
 * downloader), cache entries are keyed to their exact URL so one round can
 * never see another round's image, and [GuessImageFetcher.readBounded]
 * refuses oversized responses both when the length is declared and when the
 * stream lies or declares nothing.
 */
class GuessImageCacheTest {

    // ---- One-slot cache keying -------------------------------------------

    @Test
    fun `cache only returns bytes for the exact url that stored them`() {
        val cache = OneSlotBytesCache()
        val u1 = "https://example.test/round1.jpg"
        val u2 = "https://example.test/round2.jpg"
        val b1 = byteArrayOf(1, 1, 1)
        val b2 = byteArrayOf(2, 2, 2)

        cache.put(u1, b1)
        assertSame(b1, cache.peek(u1))
        // A different URL must miss — it can never surface the old round's bytes.
        assertNull(cache.peek(u2))
        // Near-miss URLs are different keys.
        assertNull(cache.peek("https://example.test/round1.jpg "))
        assertNull(cache.peek("https://example.test/round1.png"))

        cache.put(u2, b2)
        assertSame(b2, cache.peek(u2))
        // Replacement drops the old entry entirely.
        assertNull(cache.peek(u1))
    }

    // ---- fetchImageBytes reuse (mock request counter) --------------------

    @Test
    fun `second fetch of the same url is served from cache without a second download`() {
        val url = "https://example.test/reuse.jpg"
        var downloads = 0
        val download: (String) -> ByteArray? = {
            downloads++
            byteArrayOf(7, 7, 7)
        }

        val first = GuessImageFetcher.fetchImageBytes(url, download)!!
        val second = GuessImageFetcher.fetchImageBytes(url, download)!!

        assertEquals(1, downloads)
        assertArrayEquals(first, second)
    }

    @Test
    fun `each url downloads once and never returns another urls bytes`() {
        val u1 = "https://example.test/first-of-two.jpg"
        val u2 = "https://example.test/second-of-two.jpg"
        var downloads = 0
        val payloads = mapOf(u1 to byteArrayOf(1), u2 to byteArrayOf(2, 2))
        val download: (String) -> ByteArray? = {
            downloads++
            payloads[it]
        }

        assertArrayEquals(payloads[u1], GuessImageFetcher.fetchImageBytes(u1, download))
        assertArrayEquals(payloads[u2], GuessImageFetcher.fetchImageBytes(u2, download))
        assertEquals(2, downloads)

        // The slot now holds u2: fetching u1 again must refetch (one-slot
        // cache) but must still return u1's bytes — never u2's.
        val refetched = GuessImageFetcher.fetchImageBytes(u1, download)!!
        assertEquals(3, downloads)
        assertArrayEquals(payloads[u1], refetched)
    }

    @Test
    fun `failed downloads return null and are retried not cached`() {
        val url = "https://example.test/flaky.jpg"
        var attempts = 0
        val flaky: (String) -> ByteArray? = {
            attempts++
            if (attempts == 1) throw IOException("boom") else byteArrayOf(9)
        }

        assertNull(GuessImageFetcher.fetchImageBytes(url, flaky))
        // A failure must not poison the cache: the next fetch retries.
        assertArrayEquals(byteArrayOf(9), GuessImageFetcher.fetchImageBytes(url, flaky))
        assertEquals(2, attempts)
    }

    @Test
    fun `null downloads return null and are not cached`() {
        val url = "https://example.test/empty.jpg"
        var attempts = 0
        val missing: (String) -> ByteArray? = {
            attempts++
            null
        }

        assertNull(GuessImageFetcher.fetchImageBytes(url, missing))
        assertNull(GuessImageFetcher.fetchImageBytes(url, missing))
        assertEquals(2, attempts)
    }

    // ---- Bounded reads ---------------------------------------------------

    private class CountingStream(data: ByteArray) : ByteArrayInputStream(data) {
        var bytesRead = 0
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) bytesRead += n
            return n
        }
    }

    @Test
    fun `readBounded returns normal responses intact`() {
        val payload = ByteArray(300) { it.toByte() }
        // Known length and unknown length both round-trip.
        assertArrayEquals(
            payload,
            GuessImageFetcher.readBounded(ByteArrayInputStream(payload), payload.size.toLong(), 1024),
        )
        assertArrayEquals(
            payload,
            GuessImageFetcher.readBounded(ByteArrayInputStream(payload), -1L, 1024),
        )
    }

    @Test
    fun `readBounded accepts a response of exactly the limit`() {
        val payload = ByteArray(1024) { it.toByte() }
        assertArrayEquals(
            payload,
            GuessImageFetcher.readBounded(ByteArrayInputStream(payload), 1024L, 1024),
        )
        assertArrayEquals(
            payload,
            GuessImageFetcher.readBounded(ByteArrayInputStream(payload), -1L, 1024),
        )
    }

    @Test
    fun `declared oversize is rejected before the body is buffered`() {
        val stream = CountingStream(ByteArray(64))
        val e = assertThrows(IOException::class.java) {
            GuessImageFetcher.readBounded(stream, 5_000L, 1024)
        }
        assertTrue(e.message!!.contains("too large"))
        // Fail-fast: the declared length alone must reject, without reading the body.
        assertEquals(0, stream.bytesRead)
    }

    @Test
    fun `streaming limit rejects oversize when the length is unknown`() {
        val payload = ByteArray(2_000) { it.toByte() }
        val e = assertThrows(IOException::class.java) {
            // Content-Length unknown (chunked): only the streaming cap applies.
            GuessImageFetcher.readBounded(ByteArrayInputStream(payload), -1L, 1024)
        }
        assertTrue(e.message!!.contains("too large"))
    }

    @Test
    fun `streaming limit rejects a stream larger than its lying declared length`() {
        val payload = ByteArray(2_000) { it.toByte() }
        assertThrows(IOException::class.java) {
            // Declared tiny to sneak past the first check — the stream cap must stop it.
            GuessImageFetcher.readBounded(ByteArrayInputStream(payload), 512L, 1024)
        }
    }

    @Test
    fun `oversized downloads fail the fetch and are never cached`() {
        val url = "https://example.test/huge.jpg"
        var attempts = 0
        val huge: (String) -> ByteArray? = {
            attempts++
            // The default downloader runs readBounded; simulate its rejection.
            GuessImageFetcher.readBounded(ByteArrayInputStream(ByteArray(2_000)), -1L, 1024)
        }

        assertNull(GuessImageFetcher.fetchImageBytes(url, huge))
        assertNull(GuessImageFetcher.fetchImageBytes(url, huge))
        assertEquals(2, attempts)
    }
}
