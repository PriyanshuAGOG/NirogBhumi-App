package com.nirogbhumi.app.update

/** Mirrors an appUpdates/{channel} Firestore doc - see firebase/firestore.rules. */
data class UpdateInfo(
    val channel: String,
    val latestVersionCode: Int,
    val latestVersionName: String,
    val minSupportedVersionCode: Int,
    val apkUrl: String,
    val checksum: String,
    val releaseNotes: String,
    val forceUpdate: Boolean,
    val fileSizeBytes: Long?,
) {
    companion object {
        fun fromMap(channel: String, values: Map<String, Any?>): UpdateInfo? {
            val versionCode = (values["latestVersionCode"] as? Number)?.toInt() ?: return null
            val versionName = values["latestVersionName"] as? String ?: return null
            val apkUrl = values["apkUrl"] as? String ?: return null
            val checksum = values["checksum"] as? String ?: ""
            if (apkUrl.isBlank() || versionCode <= 0) return null
            return UpdateInfo(
                channel = channel,
                latestVersionCode = versionCode,
                latestVersionName = versionName,
                minSupportedVersionCode = (values["minSupportedVersionCode"] as? Number)?.toInt() ?: 0,
                apkUrl = apkUrl,
                checksum = checksum,
                releaseNotes = (values["releaseNotes"] as? String).orEmpty(),
                forceUpdate = values["forceUpdate"] == true,
                fileSizeBytes = (values["fileSizeBytes"] as? Number)?.toLong(),
            )
        }
    }
}

enum class UpdateChannelOption(val id: String, val label: String) {
    PRODUCTION("production", "Production"),
    TESTING("testing", "Testing"),
    DEVELOPMENT("development", "Development");

    companion object {
        fun fromId(id: String?): UpdateChannelOption = entries.firstOrNull { it.id == id } ?: PRODUCTION
    }
}

sealed interface DownloadState {
    data object Idle : DownloadState
    data class InProgress(val progressPercent: Int, val downloadedBytes: Long, val totalBytes: Long) : DownloadState
    data class Verifying(val filePath: String) : DownloadState
    data class ReadyToInstall(val filePath: String) : DownloadState
    data class Failed(val message: String) : DownloadState
}
