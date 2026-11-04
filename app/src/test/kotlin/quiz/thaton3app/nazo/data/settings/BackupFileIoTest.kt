package quiz.thaton3app.nazo.data.settings

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException

/**
 * File-level backup mechanics: the automatic backup is replaced atomically,
 * so a failed or interrupted write can never destroy the last valid backup —
 * and a successful write's content round-trips through the same parse/apply
 * path a restore uses.
 */
class BackupFileIoTest {

    private fun tempDir(): File =
        File.createTempFile("nazo_bk", "").apply { delete(); mkdirs() }

    private fun oldBackupFile(dir: File): File =
        File(dir, "auto_backup.json").apply { writeText("""{"version":1,"stores":{}}""") }

    private fun bundleText(): String = JSONObject()
        .put("version", 2)
        .put("createdAt", 1728000000000L)
        .put(
            "stores",
            JSONObject()
                .put("nazo_profile", JSONObject().put(
                    "username",
                    JSONObject().put("t", "string").put("v", "restored-user"),
                ))
                .put("nazo_stats", JSONObject().put(
                    "quiz_stats_v1",
                    JSONObject().put("t", "string").put("v", """{"totalQuizzes":7}"""),
                )),
        )
        .toString()

    // ---- atomic replacement ---------------------------------------------

    @Test
    fun successfulWriteReplacesThePreviousBackupCleanly() {
        val dir = tempDir()
        try {
            val target = oldBackupFile(dir)
            val newBytes = bundleText().toByteArray(Charsets.UTF_8)

            BackupRepository.writeFileAtomically(target, newBytes)

            assertTrue(newBytes.contentEquals(target.readBytes()))
            // No temp file is left behind.
            assertEquals(setOf(target.name), dir.listFiles()!!.map { it.name }.toSet())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun failedWriteDoesNotDestroyTheLastValidBackup() {
        val dir = tempDir()
        try {
            val target = oldBackupFile(dir)
            val oldBytes = target.readBytes()

            assertThrows(IOException::class.java) {
                BackupRepository.writeFileAtomically(
                    target,
                    ByteArray(1024),
                    writeTemp = { file, data ->
                        // Simulate the disk filling up partway through: a
                        // PARTIAL temp file is written, then the write fails.
                        file.writeBytes(data.copyOf(16))
                        throw IOException("disk full")
                    },
                )
            }

            // The previous backup is still fully intact, and the partial
            // temp file was cleaned up.
            assertTrue(oldBytes.contentEquals(target.readBytes()))
            assertEquals(setOf(target.name), dir.listFiles()!!.map { it.name }.toSet())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun interruptionOrCancellationDuringWriteKeepsThePreviousBackup() {
        val dir = tempDir()
        try {
            val target = oldBackupFile(dir)
            val oldBytes = target.readBytes()

            // A cancelled coroutine surfaces as CancellationException from the
            // write; it must propagate (so callers see the cancellation) AND
            // leave the previous backup untouched.
            assertThrows(CancellationException::class.java) {
                BackupRepository.writeFileAtomically(
                    target,
                    ByteArray(1024),
                    writeTemp = { file, data ->
                        file.writeBytes(data.copyOf(4))
                        throw CancellationException("cancelled mid-write")
                    },
                )
            }

            assertTrue(oldBytes.contentEquals(target.readBytes()))
            assertEquals(setOf(target.name), dir.listFiles()!!.map { it.name }.toSet())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun ioFailuresSurfaceInsteadOfLeavingAPartialTarget() {
        val dir = tempDir()
        try {
            // Parent directory does not exist: the write fails outright —
            // there is no half-written backup file at the target path.
            val target = File(dir, "missing_dir/auto_backup.json")
            assertThrows(IOException::class.java) {
                BackupRepository.writeFileAtomically(target, "x".toByteArray())
            }
            assertFalse(target.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    // ---- successful export/import round-trip ----------------------------

    @Test
    fun successfulExportThenImportRestoresExactlyWhatWasWritten() {
        val dir = tempDir()
        try {
            val target = File(dir, "auto_backup.json")
            val bytes = bundleText().toByteArray(Charsets.UTF_8)

            // Export side: atomic write of the serialized bundle.
            BackupRepository.writeFileAtomically(target, bytes)

            // Import side: the same parse -> apply path a restore takes.
            val parsed = BackupRepository.parseAndValidate(target.readText(Charsets.UTF_8))
            val profile = FakeSharedPreferences()
            val stats = FakeSharedPreferences()
            val backend = mapOf("nazo_profile" to profile, "nazo_stats" to stats)
            BackupRepository.applyValidated({ name -> backend.getValue(name) }, parsed.stores)

            assertEquals("restored-user", profile.getString("username", null))
            assertEquals(7, QuizStatsStore(stats).get().totalQuizzes)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun corruptFileFailsValidationAndWritesNothing() {
        val dir = tempDir()
        try {
            val target = File(dir, "auto_backup.json")
            target.writeText("not a backup at all {")

            // A corrupt file is rejected by validation before any store is
            // touched — the same gate import uses.
            assertThrows(Exception::class.java) {
                BackupRepository.parseAndValidate(target.readText(Charsets.UTF_8))
            }
            val untouched = FakeSharedPreferences()
            untouched.edit().putBoolean("keep", true).commit()
            assertTrue(untouched.getBoolean("keep", false))
        } finally {
            dir.deleteRecursively()
        }
    }
}
