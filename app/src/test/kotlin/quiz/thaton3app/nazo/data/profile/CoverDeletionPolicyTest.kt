package quiz.thaton3app.nazo.data.profile

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Canonical ownership boundary for destructive custom-cover cleanup. */
class CoverDeletionPolicyTest {

    @Test
    fun legitimateCoverFileInsideTheOwnedDirectoryIsDeleted() {
        withFixture { root, covers ->
            val nested = File(covers, "accepted").apply { mkdirs() }
            val cover = File(nested, "cover_1.jpg").apply { writeText("cover") }

            assertTrue(ProfileImageStore.deleteCoverFile(covers, cover.toURI().toString()))
            assertFalse(cover.exists())
            assertTrue(covers.exists())
            assertTrue(root.exists())
        }
    }

    @Test
    fun malformedAndNonFileUrisAreRejectedWithoutDeletingAnything() {
        withFixture { _, covers ->
            val cover = File(covers, "cover_2.mp4").apply { writeText("cover") }
            val rejected = listOf<String?>(
                null,
                "",
                "not a uri %%%",
                "content://media/external/video/2",
                "https://example.com/cover.jpg",
                "file://remote-host/data/cover.jpg",
                "file:///%ZZ",
                cover.toURI().toString() + "?query=delete",
            )

            rejected.forEach { uri ->
                assertFalse(ProfileImageStore.deleteCoverFile(covers, uri))
            }
            assertTrue(cover.exists())
        }
    }

    @Test
    fun traversalPrefixSiblingAndUnexpectedAbsolutePathsAreRejected() {
        withFixture { root, covers ->
            val outside = File(root, "outside.txt").apply { writeText("outside") }
            val prefixSibling = File(root, "profile_covers_evil").apply { mkdirs() }
            val siblingFile = File(prefixSibling, "cover.jpg").apply { writeText("sibling") }
            val traversal = covers.toURI().toString() + "../outside.txt"

            assertFalse(ProfileImageStore.deleteCoverFile(covers, traversal))
            assertFalse(ProfileImageStore.deleteCoverFile(covers, outside.toURI().toString()))
            assertFalse(ProfileImageStore.deleteCoverFile(covers, siblingFile.toURI().toString()))
            assertFalse(ProfileImageStore.deleteCoverFile(covers, covers.toURI().toString()))

            assertTrue(outside.exists())
            assertTrue(siblingFile.exists())
            assertTrue(covers.exists())
        }
    }

    @Test
    fun symlinkInsideCoverDirectoryCannotEscapeCanonicalBoundary() {
        withFixture { root, covers ->
            val outside = File(root, "outside-video.mp4").apply { writeText("outside") }
            val link = File(covers, "cover_link.mp4")
            Files.createSymbolicLink(link.toPath(), outside.toPath())

            assertNull(
                ProfileImageStore.resolveOwnedCoverFile(covers, link.toURI().toString())
            )
            assertFalse(ProfileImageStore.deleteCoverFile(covers, link.toURI().toString()))
            assertTrue(outside.exists())
            assertTrue(link.exists())
        }
    }

    private fun withFixture(block: (root: File, covers: File) -> Unit) {
        val root = Files.createTempDirectory("nazo_cover_delete").toFile()
        val covers = File(root, "profile_covers").apply { mkdirs() }
        try {
            block(root, covers)
        } finally {
            root.deleteRecursively()
        }
    }
}
