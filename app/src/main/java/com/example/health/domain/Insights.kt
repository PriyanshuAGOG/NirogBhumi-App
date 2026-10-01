package com.nirogbhumi.app.health.domain

import java.time.LocalDate
import java.time.ZoneId

/*
 * Pattern cards ("after shorter nights your fasting sugar averaged ...") computed from the canonical
 * entries, so they work for typed-in and imported data alike. Descriptive pattern-matching over the
 * member's own small sample, never a clinical claim: each returns null unless there is enough data AND
 * a difference large enough to be worth saying.
 */

data class SleepGlucoseInsight(val shortSleepAvg: Double, val longSleepAvg: Double, val shortNights: Int, val longNights: Int)
data class MedicationGlucoseInsight(val takenAvg: Double, val missedAvg: Double, val takenDays: Int, val missedDays: Int)
data class MealTimingInsight(val mealLabel: String, val mealAvg: Double, val otherAvg: Double, val mealCount: Int)
enum class WeeklyTone { POSITIVE, NEUTRAL, CAUTION }
enum class SugarDirection { IMPROVING, RISING, STABLE }
data class WeeklySummary(val headline: String, val detail: String, val tone: WeeklyTone)

object Insights {
    private const val SHORT_SLEEP_THRESHOLD_MINUTES = 6 * 60
    private const val MIN_NIGHTS_PER_BUCKET = 3
    private const val MIN_MEANINGFUL_DIFFERENCE_MGDL = 8.0
    private const val MIN_DAYS_PER_MED_BUCKET = 3
    private const val MIN_READINGS_PER_MEAL_BUCKET = 4
    private const val MIN_MEANINGFUL_MEAL_DIFFERENCE_MGDL = 15.0

    private fun day(millis: Long, zone: ZoneId): LocalDate = HealthStateBuilder.dateOf(millis, zone)
    private fun fasting(readings: List<GlucoseEntry>) = readings.filter { it.kind == GlucoseKind.FASTING }

    /**
     * A fasting reading is compared with the night that just ended: sleep whose wake-up fell on the same
     * calendar day. (Sleep is stored against its wake-up time, so this is the night before the reading.)
     */
    fun sleepGlucose(sleep: List<SleepEntry>, glucose: List<GlucoseEntry>, zone: ZoneId): SleepGlucoseInsight? {
        val minutesByWakeDay = sleep.filter { !it.isSuspect }.groupBy { day(it.measuredAtMillis, zone) }.mapValues { (_, l) -> l.maxOf { it.durationMinutes } }
        if (minutesByWakeDay.isEmpty()) return null
        val short = mutableListOf<Double>(); val long = mutableListOf<Double>()
        for (reading in fasting(glucose)) {
            val minutes = minutesByWakeDay[day(reading.measuredAtMillis, zone)] ?: continue
            if (minutes < SHORT_SLEEP_THRESHOLD_MINUTES) short += reading.value else long += reading.value
        }
        if (short.size < MIN_NIGHTS_PER_BUCKET || long.size < MIN_NIGHTS_PER_BUCKET) return null
        if (short.average() - long.average() < MIN_MEANINGFUL_DIFFERENCE_MGDL) return null
        return SleepGlucoseInsight(short.average(), long.average(), short.size, long.size)
    }

    /** Same-day: fasting sugar on days the last medicine log said "taken" versus "missed". */
    fun medicationGlucose(medication: List<MedicationEntry>, glucose: List<GlucoseEntry>, zone: ZoneId): MedicationGlucoseInsight? {
        val takenByDay = medication.sortedBy { it.measuredAtMillis }.associate { day(it.measuredAtMillis, zone) to it.taken }
        if (takenByDay.isEmpty()) return null
        val taken = mutableListOf<Double>(); val missed = mutableListOf<Double>()
        for (reading in fasting(glucose)) when (takenByDay[day(reading.measuredAtMillis, zone)]) {
            true -> taken += reading.value
            false -> missed += reading.value
            null -> Unit
        }
        if (taken.size < MIN_DAYS_PER_MED_BUCKET || missed.size < MIN_DAYS_PER_MED_BUCKET) return null
        if (missed.average() - taken.average() < MIN_MEANINGFUL_DIFFERENCE_MGDL) return null
        return MedicationGlucoseInsight(taken.average(), missed.average(), taken.size, missed.size)
    }

    /** After-meal readings by time of day (there is no "which meal" field, only when it was logged). */
    fun mealTiming(glucose: List<GlucoseEntry>, zone: ZoneId): MealTimingInsight? {
        val buckets = linkedMapOf("Breakfast" to mutableListOf<Double>(), "Lunch" to mutableListOf(), "Dinner" to mutableListOf())
        for (reading in glucose.filter { it.kind == GlucoseKind.POST_MEAL }) {
            val hour = HealthLabels.zoned(reading.measuredAtMillis, zone).hour
            val label = when (hour) { in 5..10 -> "Breakfast"; in 11..15 -> "Lunch"; in 16..22 -> "Dinner"; else -> null } ?: continue
            buckets.getValue(label) += reading.value
        }
        val eligible = buckets.filterValues { it.size >= MIN_READINGS_PER_MEAL_BUCKET }
        if (eligible.size < 2) return null
        val (topLabel, topValues) = eligible.entries.maxBy { it.value.average() }
        val otherAvg = eligible.filterKeys { it != topLabel }.values.flatten().average()
        if (topValues.average() - otherAvg < MIN_MEANINGFUL_MEAL_DIFFERENCE_MGDL) return null
        return MealTimingInsight(topLabel, topValues.average(), otherAvg, topValues.size)
    }

    /**
     * Whether blood sugar is moving down, up or sideways over the last 30 days: the recent half of the
     * readings against the older half. Null with fewer than 4 readings, so it never guesses.
     */
    fun sugarDirection(state: HealthUiState): SugarDirection? {
        val first = state.today.date.minusDays(29)
        val values = state.sugarReadings
            .filter { day(it.measuredAtMillis, state.zone).let { d -> !d.isBefore(first) && !d.isAfter(state.today.date) } }
            .map { it.value } // newest first
        if (values.size < 4) return null
        val half = values.size / 2
        val recent = values.take(half).average()
        val older = values.drop(half).average()
        return when {
            recent < older - 3 -> SugarDirection.IMPROVING
            recent > older + 3 -> SugarDirection.RISING
            else -> SugarDirection.STABLE
        }
    }

    /**
     * One plain sentence about the last 7 days. Framed around logging consistency and the share of
     * readings in the usual range, not a verdict. Null only when nothing at all was logged this week.
     */
    fun weeklySummary(state: HealthUiState): WeeklySummary? {
        val week = state.week
        val zone = state.zone
        val today = state.today.date
        val first = today.minusDays(6)
        fun inWeek(millis: Long) = day(millis, zone).let { !it.isBefore(first) && !it.isAfter(today) }
        val loggedDays = buildSet {
            state.sugarReadings.filter { inWeek(it.measuredAtMillis) }.forEach { add(day(it.measuredAtMillis, zone)) }
            state.bp.filter { inWeek(it.measuredAtMillis) }.forEach { add(day(it.measuredAtMillis, zone)) }
            state.weight.filter { inWeek(it.measuredAtMillis) }.forEach { add(day(it.measuredAtMillis, zone)) }
            state.sleep.filter { !it.isSuspect && inWeek(it.measuredAtMillis) }.forEach { add(day(it.measuredAtMillis, zone)) }
        }.size
        if (loggedDays == 0) return null
        val sugarNormalPercent = week.glucoseInRangePercent
        val bpConcern = state.bp.any { inWeek(it.measuredAtMillis) && (it.systolic >= 140 || it.diastolic >= 90) }
        val avgSleepHours = week.sleepAverageMinutes?.let { it / 60.0 }
        val logging = "Logged $loggedDays of 7 days"
        return when {
            bpConcern || (sugarNormalPercent != null && sugarNormalPercent < 50) -> WeeklySummary(
                "A tougher week",
                logging + (if (bpConcern) " - one BP reading ran high" else " - sugar ran outside range more than usual") + ". Worth a check-in with your doctor if the pattern continues.",
                WeeklyTone.CAUTION,
            )
            loggedDays >= 5 && (sugarNormalPercent == null || sugarNormalPercent >= 70) -> WeeklySummary(
                "A great week",
                logging + (sugarNormalPercent?.let { ", sugar stayed in range $it% of the time" } ?: "") + (avgSleepHours?.let { ", averaging %.1fh sleep".format(java.util.Locale.US, it) } ?: "") + ".",
                WeeklyTone.POSITIVE,
            )
            loggedDays >= 3 -> WeeklySummary("A steady week", "$logging. Keep it up - consistency is what turns into a real trend.", WeeklyTone.NEUTRAL)
            else -> WeeklySummary("Building your rhythm", "$logging. A few more logs this week will start showing real patterns.", WeeklyTone.NEUTRAL)
        }
    }
}
