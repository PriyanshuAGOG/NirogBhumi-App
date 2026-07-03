package com.nirogbhumi.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

object UpdateInstaller {
    /** SHA-256 of the downloaded file, lowercase hex - compared against the Firestore-supplied checksum before install. */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** True if the on-disk file's checksum matches what Firestore said it should be. An empty expected checksum (not yet backfilled by CI) skips verification rather than blocking every install. */
    fun verifyChecksum(file: File, expectedSha256: String): Boolean {
        if (expectedSha256.isBlank()) return true
        return sha256(file).equals(expectedSha256, ignoreCase = true)
    }

    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Settings screen where the user grants "install unknown apps" for this app specifically. */
    fun requestInstallPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /**
     * Launches the system package installer. This is the realistic ceiling for
     * a non-Play, non-device-owner install flow - the user still taps
     * "Install"/"Update" once on the OS's own confirmation screen; everything
     * before this point (check, download, checksum verify) is fully automatic.
     */
    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
