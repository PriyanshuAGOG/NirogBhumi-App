package com.nirogbhumi.app.health.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One day's step total as Health Connect reports it after removing overlap between apps. */
data class DailyStepTotal(val date: LocalDate, val steps: Long, val dayStart: Instant, val periodEnd: Instant)

data class StepDays(val accepted: List<DailySteps>, val rejectedDates: List<LocalDate>)

/**
 * What a believable imported value looks like. Anything outside these bounds is left out of the sync and counted as skipped,
 * so a faulty source app (or a test record somebody left in Health Connect) cannot put an impossible reading in a member's record.
 * The bounds match the Firestore rules' `sensibleValues`, so an accepted value is also one the rules will store.
 */
object ImportSanity {
    const val MAX_DAILY_STEPS = 60_000L
    const val MIN_SLEEP_MINUTES = 20
    const val MAX_SLEEP_MINUTES = 18 * 60
    private const val FUTURE_SLACK_SECONDS = 15 * 60L

    fun stepsOk(steps: Long) = steps in 1..MAX_DAILY_STEPS
    fun glucoseOk(mgDl: Double) = mgDl in 20.0..800.0
    fun bpOk(systolic: Double, diastolic: Double) = systolic in 60.0..260.0 && diastolic in 30.0..180.0 && diastolic < systolic
    fun weightOk(kg: Double) = kg in 20.0..300.0

    fun sleepOk(start: Instant, end: Instant, now: Instant): Boolean {
        val minutes = (end.epochSecond - start.epochSecond) / 60
        return minutes in MIN_SLEEP_MINUTES..MAX_SLEEP_MINUTES && end.epochSecond <= now.epochSecond + FUTURE_SLACK_SECONDS
    }

    fun timeOk(at: Instant, now: Instant) = at.epochSecond <= now.epochSecond + FUTURE_SLACK_SECONDS

    /** Turns daily totals into the per-day records to save, setting aside days with no steps or an impossible count. */
    fun stepDays(totals: List<DailyStepTotal>, zone: ZoneId): StepDays {
        val accepted = ArrayList<DailySteps>()
        val rejected = ArrayList<LocalDate>()
        for (t in totals.sortedBy { it.date }) {
            if (t.steps <= 0) continue                      // a quiet day is not an error and is not saved
            if (!stepsOk(t.steps)) { rejected += t.date; continue }
            accepted += DailySteps(t.date, t.steps, t.dayStart, t.periodEnd, t.date.atStartOfDay(zone).toInstant())
        }
        return StepDays(accepted, rejected)
    }

    /**
     * Two apps can record the same night. Where sessions overlap, only the longer one is kept, so a night is never counted twice.
     */
    fun <T> withoutOverlappingSleep(items: List<T>, start: (T) -> Instant, end: (T) -> Instant): List<T> {
        val kept = ArrayList<T>()
        for (item in items.sortedBy { start(it) }) {
            val last = kept.lastOrNull()
            if (last != null && start(item) < end(last)) {
                if (end(item).epochSecond - start(item).epochSecond > end(last).epochSecond - start(last).epochSecond) kept[kept.lastIndex] = item
            } else kept += item
        }
        return kept
    }
}

/** App names for the source line of the sync preview. Unknown apps are described plainly rather than by their package id. */
object HealthSources {
    private val known = mapOf(
        "com.google.android.apps.fitness" to "Google Fit",
        "com.sec.android.app.shealth" to "Samsung Health",
        "com.fitbit.FitbitMobile" to "Fitbit",
        "com.garmin.android.apps.connectmobile" to "Garmin Connect",
        "com.xiaomi.hm.health" to "Zepp",
        "com.mi.health" to "Mi Fitness",
        "com.huawei.health" to "Huawei Health",
        "com.oneplus.health" to "OnePlus Health",
        "com.google.android.apps.healthdata" to "Health Connect",
        "com.nirogbhumi.app" to "Nirog Bhumi",
        "in.nirogbhumi.app" to "Nirog Bhumi",
    )

    fun friendly(packageName: String): String = known[packageName] ?: "another app on this phone"

    fun describe(packageNames: Collection<String>): List<String> = packageNames.map(::friendly).distinct().sorted()
}

