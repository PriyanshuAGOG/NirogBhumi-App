package com.nirogbhumi.app.notifications

import android.content.Context
import android.content.SharedPreferences
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** A single locally-scheduled health reminder the user can toggle on the Notification Settings screen. */
enum class ReminderType(
    val prefKey: String,
    val workName: String,
    val intervalHours: Long,
    val title: String,
    val body: String
) {
    FASTING_SUGAR("reminder_sugar", "nirog_reminder_sugar", 24, "Fasting sugar check-in", "Log today's fasting sugar reading before breakfast."),
    BP("reminder_bp", "nirog_reminder_bp", 24, "Blood pressure check-in", "A quiet moment to measure and log your blood pressure."),
    WALK("reminder_walk", "nirog_reminder_walk", 24, "Evening walk or activity", "A 15-minute walk after dinner can help your sugar overnight."),
    SLEEP("reminder_sleep", "nirog_reminder_sleep", 24, "Wind down for sleep", "Start winding down soon for consistent, restful sleep."),
    // Timed via scheduleSmart(), not the generic elapsed-interval schedule()
    // every other type uses - see there for why.
    DAILY_CHECKIN("reminder_checkin", "nirog_reminder_checkin", 24, "Daily check-in", "A gentle nudge around your usual time to log today's numbers.")
}

/**
 * Reminders are scheduled with WorkManager (on-device, works offline) instead of
 * server-push, since these are personal habit nudges rather than account events.
 * Quiet hours are read from SharedPreferences at fire-time by ReminderWorker itself,
 * since a Worker has no access to Compose state.
 */
object ReminderScheduler {
    private const val PREFS = "nirog_reminders"
    private const val KEY_QUIET_START = "quiet_start"
    private const val KEY_QUIET_END = "quiet_end"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context, type: ReminderType): Boolean =
        prefs(context).getBoolean(type.prefKey, false)

    fun setQuietHours(context: Context, start: String, end: String) {
        prefs(context).edit().putString(KEY_QUIET_START, start).putString(KEY_QUIET_END, end).apply()
    }

    fun quietHoursStart(context: Context): String = prefs(context).getString(KEY_QUIET_START, "21:00") ?: "21:00"
    fun quietHoursEnd(context: Context): String = prefs(context).getString(KEY_QUIET_END, "07:00") ?: "07:00"

    fun setEnabled(context: Context, type: ReminderType, enabled: Boolean) {
        prefs(context).edit().putBoolean(type.prefKey, enabled).apply()
        if (enabled) schedule(context, type) else cancel(context, type)
    }

    private fun schedule(context: Context, type: ReminderType) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(type.intervalHours, TimeUnit.HOURS)
            .setInputData(ReminderWorker.inputFor(type))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            type.workName, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    /**
     * DAILY_CHECKIN only: instead of firing every 24h from whenever the toggle was
     * flipped (schedule()'s behavior, used as the fallback until this resolves),
     * align the first fire - and so every fire after it, since it's still a 24h
     * periodic request - to just before the hour the member actually tends to
     * check in (users/{uid}.checkinHourHint, learned in
     * HealthRepository.recordCheckinCompletion). Reads no Firestore itself - the
     * caller (which already has repository access) passes the hint in, keeping
     * this object free of the cloud boundary like every other on-device piece here.
     */
    fun scheduleSmart(context: Context, hourHint: Int?) {
        val targetHour = (hourHint ?: 19).coerceIn(0, 23)
        val now = java.util.Calendar.getInstance()
        val target = (now.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, targetHour)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.MINUTE, -15) // nudge a little before their usual time, not at it
        }
        if (!target.after(now)) target.add(java.util.Calendar.DAY_OF_YEAR, 1)
        val initialDelayMs = target.timeInMillis - now.timeInMillis
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(ReminderType.DAILY_CHECKIN.intervalHours, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setInputData(ReminderWorker.inputFor(ReminderType.DAILY_CHECKIN))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ReminderType.DAILY_CHECKIN.workName, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    private fun cancel(context: Context, type: ReminderType) {
        WorkManager.getInstance(context).cancelUniqueWork(type.workName)
    }

    /** Re-applies persisted on/off state to WorkManager, e.g. after an app update or reboot. */
    fun rescheduleAllEnabled(context: Context) {
        ReminderType.entries.forEach { type -> if (isEnabled(context, type)) schedule(context, type) }
    }

    // Server-driven update categories have no on-device schedule to manage, but are
    // still cached locally so the settings screen shows the user's real last choice
    // immediately instead of always resetting to a blank form on every visit.
    fun isUpdateCategoryEnabled(context: Context, key: String): Boolean =
        prefs(context).getBoolean("update_$key", true)

    fun setUpdateCategoryEnabled(context: Context, key: String, enabled: Boolean) {
        prefs(context).edit().putBoolean("update_$key", enabled).apply()
    }
}
