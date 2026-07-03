package com.nirogbhumi.app.update

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nirogbhumi.app.MainActivity
import com.nirogbhumi.app.R

/**
 * Background backstop for UpdateManager.checkNow - the primary detection path
 * is the foreground loop (on launch/on resume/every 30 min while open); this
 * worker just makes sure a member who leaves the app open rarely (or never
 * opens it in the 30-min window) still hears about a new release within
 * BACKSTOP_INTERVAL_HOURS.
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val currentVersionCode = runCatching {
            applicationContext.packageManager
                .getPackageInfo(applicationContext.packageName, 0)
                .let { info ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt()
                    else @Suppress("DEPRECATION") info.versionCode
                }
        }.getOrNull() ?: return Result.success()

        val outcome = UpdateManager.checkNow(applicationContext, currentVersionCode)
        val info = outcome.getOrNull() ?: return Result.success()

        postNotification(applicationContext, info)
        return Result.success()
    }

    private fun postNotification(context: Context, info: UpdateInfo) {
        // "settings"/"profile" isn't in MainActivity's DEEP_LINK_ROUTES allowlist (that
        // list only covers routes reachable by external actors), so this points at the
        // dashboard like ReminderWorker does - the update dialog itself is what surfaces
        // once the app is open (see UpdateManager's foreground checks).
        val intent = Intent(context, MainActivity::class.java)
            .putExtra("route", "dashboard")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = android.app.PendingIntent.getActivity(
            context, 3000, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val body = "Version ${info.latestVersionName} is ready to install"
        val notification = NotificationCompat.Builder(context, "app_updates")
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentTitle("New update available")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        if (Build.VERSION.SDK_INT < 33 ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(3000, notification)
        }
    }
}
