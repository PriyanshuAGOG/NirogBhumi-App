package com.nirogbhumi.app.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Orchestrates the version check + dismissal logic (checkNow) and the
 * background backstop (schedulePeriodicCheck). Deliberately does NOT own the
 * "every 30 minutes while the app is open" timer itself - that's a UI-layer
 * concern (a lifecycle-scoped coroutine loop that pauses while backgrounded),
 * since WorkManager's periodic jobs aren't meant for tight in-foreground
 * intervals and have a 15-minute minimum anyway. This object is the single
 * place both the foreground loop and the background worker call into, so
 * the actual "is there an update, and should we show it" logic only exists
 * once.
 */
object UpdateManager {
    private const val WORK_NAME = "nirog_update_backstop_check"
    private const val BACKSTOP_INTERVAL_HOURS = 6L

    /**
     * Returns the UpdateInfo to show, or null if there's nothing worth
     * surfacing right now (already up to date, or the member already tapped
     * "Later" on this exact version and it isn't a forced update).
     */
    suspend fun checkNow(context: Context, currentVersionCode: Int): Result<UpdateInfo?> {
        UpdatePrefs.recordCheckNow(context)
        val channel = UpdatePrefs.channel(context)
        val result = UpdateRepository.fetchLatest(channel)
        val info = result.getOrElse { return Result.failure(it) }

        if (!VersionChecker.isNewer(info.latestVersionName, currentVersionCode.toString())) {
            // Fall through to the numeric versionCode comparison below - version
            // NAME comparison above is skipped in practice since versionCode is
            // the authoritative, always-numeric source of truth; versionName is
            // only ever used for display.
        }
        if (info.latestVersionCode <= currentVersionCode) return Result.success(null)

        val mandatory = currentVersionCode < info.minSupportedVersionCode || info.forceUpdate
        if (!mandatory && UpdatePrefs.dismissedVersionCode(context) >= info.latestVersionCode) {
            return Result.success(null)
        }
        return Result.success(info)
    }

    fun isMandatory(context: Context, info: UpdateInfo, currentVersionCode: Int): Boolean =
        info.forceUpdate || currentVersionCode < info.minSupportedVersionCode

    /**
     * Backstop only - the on-launch/on-foreground/every-30-min-while-open
     * checks are the primary path. Called unconditionally from
     * Application.onCreate, so this must not throw: WorkManager.getInstance
     * throws IllegalStateException if the library's auto-init ContentProvider
     * never ran (e.g. under Robolectric's default test environment, which
     * doesn't wire up WorkManagerInitializer) - losing this background
     * backstop silently is far better than crashing app startup.
     */
    fun schedulePeriodicCheck(context: Context) {
        runCatching {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(BACKSTOP_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
