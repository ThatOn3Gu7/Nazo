package quiz.thaton3app.nazo.data

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkflowArtifactInstallerTest {

    @Test
    fun extractsTheOnlyApkWithoutTrustingItsArchivePath() {
        withTempDirectory { directory ->
            val bytes = ByteArray(2_048) { index -> (index % 251).toByte() }
            val archive = zip(directory, "artifact.zip", mapOf("outputs/app-release.apk" to bytes))
            val destination = File(directory, "workflow.apk")

            assertEquals(bytes.size.toLong(), extractSingleApk(archive, destination, 4_096L))
            assertArrayEquals(bytes, destination.readBytes())
        }
    }

    @Test
    fun rejectsMultipleApksAndDeletesThePartialDestination() {
        withTempDirectory { directory ->
            val archive = zip(
                directory,
                "multiple.zip",
                mapOf(
                    "app-release.apk" to ByteArray(8),
                    "other.apk" to ByteArray(8),
                ),
            )
            val destination = File(directory, "workflow.apk")

            assertThrows(IOException::class.java) {
                extractSingleApk(archive, destination, 4_096L)
            }
            assertFalse(destination.exists())
        }
    }

    @Test
    fun rejectsTraversalAndOversizedApkEntries() {
        withTempDirectory { directory ->
            val traversal = zip(
                directory,
                "traversal.zip",
                mapOf("../app-release.apk" to ByteArray(8)),
            )
            val destination = File(directory, "workflow.apk")
            assertThrows(IOException::class.java) {
                extractSingleApk(traversal, destination, 4_096L)
            }

            val oversized = zip(
                directory,
                "oversized.zip",
                mapOf("app-release.apk" to ByteArray(5_000)),
            )
            assertThrows(IOException::class.java) {
                extractSingleApk(oversized, destination, 4_096L)
            }
            assertFalse(destination.exists())
        }
    }

    private fun zip(directory: File, name: String, entries: Map<String, ByteArray>): File {
        val file = File(directory, name)
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (entryName, bytes) ->
                output.putNextEntry(ZipEntry(entryName))
                output.write(bytes)
                output.closeEntry()
            }
        }
        return file
    }

    private fun withTempDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("nazo-workflow-artifact").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
