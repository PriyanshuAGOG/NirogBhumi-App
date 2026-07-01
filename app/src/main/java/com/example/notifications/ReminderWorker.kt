package com.nirogbhumi.app.notifications

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nirogbhumi.app.MainActivity
import com.nirogbhumi.app.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val typeName = inputData.getString(KEY_TYPE) ?: return Result.success()
        val type = ReminderType.entries.firstOrNull { it.name == typeName } ?: return Result.success()

        // The user may have turned this reminder off since it was scheduled but before
        // this periodic tick fired - honor that instead of a stale enqueue.
        if (!ReminderScheduler.isEnabled(applicationContext, type)) return Result.success()

        if (isWithinQuietHours(applicationContext)) return Result.success()

        postNotification(applicationContext, type)
        return Result.success()
    }

    private fun isWithinQuietHours(context: Context): Boolean {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val start = runCatching { LocalTime.parse(ReminderScheduler.quietHoursStart(context), formatter) }.getOrNull() ?: return false
        val end = runCatching { LocalTime.parse(ReminderScheduler.quietHoursEnd(context), formatter) }.getOrNull() ?: return false
        val now = LocalTime.now()
        return if (start.isBefore(end)) now in start..end else now >= start || now <= end
    }

    private fun postNotification(context: Context, type: ReminderType) {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra("route", "dashboard")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = android.app.PendingIntent.getActivity(
            context, type.ordinal, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, "health_reminders")
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentTitle(type.title)
            .setContentText(type.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(type.body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        if (Build.VERSION.SDK_INT < 33 ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(2000 + type.ordinal, notification)
            NotificationLog.record("reminder", type.title, type.body, "dashboard")
        }
    }

    companion object {
        private const val KEY_TYPE = "reminder_type"
        fun inputFor(type: ReminderType): Data = Data.Builder().putString(KEY_TYPE, type.name).build()
    }
}
