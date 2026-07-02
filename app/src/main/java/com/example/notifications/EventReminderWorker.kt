package com.nirogbhumi.app.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nirogbhumi.app.MainActivity
import com.nirogbhumi.app.R
import java.util.concurrent.TimeUnit

/**
 * One-off "Remind me" for a specific Care+ program calendar event (as opposed to
 * [ReminderWorker], which is a fixed set of recurring personal-habit reminders).
 * Scheduled with an initial delay computed from the event's start time - no
 * server round-trip needed, matches how the fixed reminders already work offline.
 */
class EventReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val title = inputData.getString(KEY_TITLE) ?: return Result.success()
    val body = inputData.getString(KEY_BODY) ?: ""
    postNotification(applicationContext, title, body)
    return Result.success()
  }

  private fun postNotification(context: Context, title: String, body: String) {
    val intent = Intent(context, MainActivity::class.java)
      .putExtra("route", "program_calendar")
      .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
    val pending = PendingIntent.getActivity(
      context, title.hashCode(), intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, "health_reminders")
      .setSmallIcon(R.mipmap.ic_launcher_monochrome)
      .setContentTitle(title)
      .setContentText(body)
      .setStyle(NotificationCompat.BigTextStyle().bigText(body))
      .setAutoCancel(true)
      .setContentIntent(pending)
      .build()
    if (Build.VERSION.SDK_INT < 33 ||
      ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    ) {
      NotificationManagerCompat.from(context).notify(title.hashCode(), notification)
    }
  }

  companion object {
    private const val KEY_TITLE = "event_title"
    private const val KEY_BODY = "event_body"

    /** Schedules a one-off reminder to fire at [triggerAtMillis]. No-ops if that time has already passed. */
    fun schedule(context: Context, eventId: String, title: String, body: String, triggerAtMillis: Long) {
      val delay = triggerAtMillis - System.currentTimeMillis()
      if (delay <= 0) return
      val request = OneTimeWorkRequestBuilder<EventReminderWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setInputData(Data.Builder().putString(KEY_TITLE, title).putString(KEY_BODY, body).build())
        .addTag("event_reminder_$eventId")
        .build()
      WorkManager.getInstance(context).enqueue(request)
    }
  }
}
