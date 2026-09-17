package com.nirogbhumi.app.update

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateInstallerSecurityTest {
    @Test
    fun verifyChecksum_acceptsMatchingSha256() {
        val file = File.createTempFile("nirog-update", ".apk").apply {
            writeText("known test payload")
            deleteOnExit()
        }
        val checksum = UpdateInstaller.sha256(file)

        assertTrue(UpdateInstaller.verifyChecksum(file, checksum))
        assertTrue(UpdateInstaller.verifyChecksum(file, checksum.uppercase()))
    }

    @Test
    fun verifyChecksum_rejectsMissingMalformedAndMismatchedChecksums() {
        val file = File.createTempFile("nirog-update", ".apk").apply {
            writeText("known test payload")
            deleteOnExit()
        }

        assertFalse(UpdateInstaller.verifyChecksum(file, ""))
        assertFalse(UpdateInstaller.verifyChecksum(file, "abc123"))
        assertFalse(UpdateInstaller.verifyChecksum(file, "0".repeat(64)))
    }

    @Test
    fun updateInfo_rejectsReleaseMetadataWithoutValidChecksum() {
        val base = mapOf<String, Any?>(
            "latestVersionCode" to 2,
            "latestVersionName" to "1.0.1",
            "apkUrl" to "https://example.com/app.apk",
            "minSupportedVersionCode" to 1,
            "releaseNotes" to "Security update",
            "forceUpdate" to false,
        )

        assertTrue(UpdateInfo.fromMap("development", base + ("checksum" to "a".repeat(64))) != null)
        assertTrue(UpdateInfo.fromMap("development", base) == null)
        assertTrue(UpdateInfo.fromMap("development", base + ("checksum" to "abc123")) == null)
    }
}
