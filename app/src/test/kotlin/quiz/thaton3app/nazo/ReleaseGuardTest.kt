package quiz.thaton3app.nazo

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end policy tests for the script Gradle runs before tag release assembly. */
class ReleaseGuardTest {
    private val repoRoot: File = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, ".github/scripts/validate_release_build.py").isFile }
    private val script = File(repoRoot, ".github/scripts/validate_release_build.py")

    @Test
    fun exactVersionTagWithCompleteSigningInputsPasses() {
        val keystore = Files.createTempFile("nazo-release", ".jks").toFile().apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        try {
            val result = runGuard(
                tag = "v14.0-beta01",
                version = "14.0-beta01",
                keystore = keystore,
                credentialsPresent = true,
            )
            assertEquals(result.output, 0, result.code)
        } finally {
            keystore.delete()
        }
    }

    @Test
    fun tagMustBeSupportedAndExactlyMatchVersionName() {
        val keystore = Files.createTempFile("nazo-release", ".jks").toFile().apply { writeText("x") }
        try {
            val mismatch = runGuard("v14.0-beta02", "14.0-beta01", keystore, true)
            assertNotEquals(0, mismatch.code)
            assertTrue(mismatch.output.contains("does not match"))

            val malformed = runGuard("vnext", "next", keystore, true)
            assertNotEquals(0, malformed.code)
            assertTrue(malformed.output.contains("not a supported"))
        } finally {
            keystore.delete()
        }
    }

    @Test
    fun officialTagCannotBuildWithoutEverySigningInputAndKeystore() {
        val missingCredentials = runGuard(
            tag = "v14.0-beta01",
            version = "14.0-beta01",
            keystore = File(repoRoot, "missing-release-key.jks"),
            credentialsPresent = false,
        )

        assertNotEquals(0, missingCredentials.code)
        assertTrue(missingCredentials.output.contains("signing properties"))
        assertTrue(missingCredentials.output.contains("keystore is missing"))
    }

    @Test
    fun unsignedReleaseAssemblyRemainsAvailableForOrdinaryPrChecks() {
        val result = runGuard(
            tag = "arena/branch",
            version = "14.0-beta01",
            keystore = File(repoRoot, "missing-release-key.jks"),
            credentialsPresent = false,
            refType = "branch",
        )

        assertEquals(result.output, 0, result.code)
    }

    private fun runGuard(
        tag: String,
        version: String,
        keystore: File,
        credentialsPresent: Boolean,
        refType: String = "tag",
    ): Result {
        val command = mutableListOf(
            "python3",
            script.absolutePath,
            "--version", version,
            "--version-code", "14",
            "--keystore", keystore.absolutePath,
        )
        if (credentialsPresent) command += "--credentials-present"
        val process = ProcessBuilder(command)
            .directory(repoRoot)
            .redirectErrorStream(true)
            .apply {
                environment()["GITHUB_ACTIONS"] = "true"
                environment()["GITHUB_REF_TYPE"] = refType
                environment()["GITHUB_REF_NAME"] = tag
            }
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return Result(process.waitFor(), output)
    }

    private data class Result(val code: Int, val output: String)
}
