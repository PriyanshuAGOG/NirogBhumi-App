package com.nirogbhumi.app.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.core.content.getSystemService
import java.io.File

/**
 * Thin wrapper around the system DownloadManager rather than a hand-rolled
 * OkHttp download: it already handles background continuation, retries on
 * transient network loss, and a system progress notification for free -
 * reimplementing that correctly (especially "continue in the background")
 * is exactly the kind of thing DownloadManager exists to avoid.
 */
object ApkDownloader {
    private const val SUBDIR = "updates"

    fun targetFile(context: Context, versionCode: Int): File =
        File(context.getExternalFilesDir(null), "$SUBDIR/nirog-bhumi-$versionCode.apk")

    /** Returns the DownloadManager request id, or null if the source URL is untrusted. */
    fun enqueue(context: Context, info: UpdateInfo): Long? {
        // Only ever download from the exact URL the app itself fetched from
        // Firestore moments ago (see UpdateRepository) - this check exists so
        // a future caller can never accidentally wire an arbitrary/user-
        // supplied URL through this same path. HTTPS-only, matching
        // usesCleartextTraffic="false" in the manifest.
        val uri = runCatching { Uri.parse(info.apkUrl) }.getOrNull() ?: return null
        if (uri.scheme != "https") return null

        val target = targetFile(context, info.latestVersionCode)
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()

        val manager = context.getSystemService<DownloadManager>() ?: return null
        val request = DownloadManager.Request(uri)
            .setTitle("Nirog Bhumi update")
            .setDescription("Downloading version ${info.latestVersionName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, "$SUBDIR/nirog-bhumi-${info.latestVersionCode}.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        return manager.enqueue(request)
    }

    /** One-shot progress snapshot - poll this from a loop while a download id is active. */
    fun queryProgress(context: Context, downloadId: Long): DownloadState {
        val manager = context.getSystemService<DownloadManager>() ?: return DownloadState.Failed("Download manager unavailable")
        val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId)) ?: return DownloadState.Failed("Download not found")
        cursor.use {
            if (!it.moveToFirst()) return DownloadState.Failed("Download not found")
            val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val downloadedIndex = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalIndex = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val reasonIndex = it.getColumnIndex(DownloadManager.COLUMN_REASON)
            val status = if (statusIndex >= 0) it.getInt(statusIndex) else DownloadManager.STATUS_FAILED
            val downloaded = if (downloadedIndex >= 0) it.getLong(downloadedIndex) else 0L
            val total = if (totalIndex >= 0) it.getLong(totalIndex) else 0L
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> DownloadState.ReadyToInstall("") // caller already knows the target path
                DownloadManager.STATUS_FAILED -> {
                    val reason = if (reasonIndex >= 0) it.getInt(reasonIndex) else -1
                    DownloadState.Failed(failureReasonText(reason))
                }
                else -> {
                    val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                    DownloadState.InProgress(percent, downloaded, total)
                }
            }
        }
    }

    private fun failureReasonText(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage space to download the update"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage is unavailable right now"
        DownloadManager.ERROR_HTTP_DATA_ERROR, DownloadManager.ERROR_UNKNOWN -> "The download link may have expired - try checking for updates again"
        DownloadManager.ERROR_CANNOT_RESUME -> "The download was interrupted and couldn't resume"
        else -> "The download failed - check your connection and try again"
    }
}
