package com.nirogbhumi.app.health

import com.nirogbhumi.app.data.CloudDocument
import java.util.Calendar
import java.util.TimeZone

data class SleepGlucoseInsight(
    val shortSleepAvg: Double,
    val longSleepAvg: Double,
    val shortNights: Int,
    val longNights: Int,
)

private const val SHORT_SLEEP_THRESHOLD_HOURS = 6.0
private const val MIN_NIGHTS_PER_BUCKET = 3
// Deliberately conservative: this is descriptive pattern-matching over a
// member's own small sample, not a clinical claim - a difference has to be
// large enough to be worth surfacing at all, not just non-zero.
private const val MIN_MEANINGFUL_DIFFERENCE_MGDL = 8.0

private fun docTimeMillis(values: Map<String, Any?>): Long? {
    val measuredAt = values["measuredAt"] as? com.google.firebase.Timestamp
    val createdAt = values["createdAt"] as? com.google.firebase.Timestamp
    return (measuredAt ?: createdAt)?.toDate()?.time
}

private fun dayKey(millis: Long): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
    cal.timeInMillis = millis
    return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
}

private fun previousDayKey(millis: Long): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
    cal.timeInMillis = millis
    cal.add(Calendar.DAY_OF_YEAR, -1)
    return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
}

/**
 * For each fasting glucose reading, looks up sleep logged the previous
 * calendar day (Asia/Kolkata) and buckets the reading into "short sleep"
 * (<6h) or "longer sleep" (>=6h). Returns null when either bucket doesn't
 * have enough nights to say anything honest, or when the two buckets'
 * averages aren't meaningfully different.
 */
fun computeSleepGlucoseInsight(sleepLogs: List<CloudDocument>, glucoseReadings: List<CloudDocument>): SleepGlucoseInsight? {
    val sleepByDay = HashMap<String, Double>()
    for (log in sleepLogs) {
        val millis = docTimeMillis(log.values) ?: continue
        val hours = (log.values["duration"] as? Number)?.toDouble() ?: continue
        sleepByDay[dayKey(millis)] = hours
    }
    if (sleepByDay.isEmpty()) return null

    val shortSleepReadings = mutableListOf<Double>()
    val longSleepReadings = mutableListOf<Double>()
    for (reading in glucoseReadings) {
        if (reading.values["readingType"] != "fasting") continue
        val millis = docTimeMillis(reading.values) ?: continue
        val value = (reading.values["value"] as? Number)?.toDouble() ?: continue
        val sleepHours = sleepByDay[previousDayKey(millis)] ?: continue
        if (sleepHours < SHORT_SLEEP_THRESHOLD_HOURS) shortSleepReadings.add(value) else longSleepReadings.add(value)
    }

    if (shortSleepReadings.size < MIN_NIGHTS_PER_BUCKET || longSleepReadings.size < MIN_NIGHTS_PER_BUCKET) return null

    val shortAvg = shortSleepReadings.average()
    val longAvg = longSleepReadings.average()
    if (shortAvg - longAvg < MIN_MEANINGFUL_DIFFERENCE_MGDL) return null

    return SleepGlucoseInsight(
        shortSleepAvg = shortAvg,
        longSleepAvg = longAvg,
        shortNights = shortSleepReadings.size,
        longNights = longSleepReadings.size,
    )
}

data class MedicationGlucoseInsight(
    val takenAvg: Double,
    val missedAvg: Double,
    val takenDays: Int,
    val missedDays: Int,
)

private const val MIN_DAYS_PER_MED_BUCKET = 3
private const val MIN_MEANINGFUL_MED_DIFFERENCE_MGDL = 8.0

/**
 * Same shape as computeSleepGlucoseInsight but same-day rather than
 * previous-day: buckets fasting glucose readings by whether that day's
 * medication log said "taken" or "missed" (the last medication log of the
 * day wins if there were more than one). Null unless both buckets have
 * enough days and the difference is large enough to be worth surfacing.
 */
fun computeMedicationGlucoseInsight(medicationLogs: List<CloudDocument>, glucoseReadings: List<CloudDocument>): MedicationGlucoseInsight? {
    val takenByDay = HashMap<String, Boolean>()
    for (log in medicationLogs) {
        val millis = docTimeMillis(log.values) ?: continue
        val taken = log.values["taken"] as? Boolean ?: continue
        takenByDay[dayKey(millis)] = taken
    }
    if (takenByDay.isEmpty()) return null

    val takenReadings = mutableListOf<Double>()
    val missedReadings = mutableListOf<Double>()
    for (reading in glucoseReadings) {
        if (reading.values["readingType"] != "fasting") continue
        val millis = docTimeMillis(reading.values) ?: continue
        val value = (reading.values["value"] as? Number)?.toDouble() ?: continue
        when (takenByDay[dayKey(millis)]) {
            true -> takenReadings.add(value)
            false -> missedReadings.add(value)
            null -> Unit
        }
    }
    if (takenReadings.size < MIN_DAYS_PER_MED_BUCKET || missedReadings.size < MIN_DAYS_PER_MED_BUCKET) return null

    val takenAvg = takenReadings.average()
    val missedAvg = missedReadings.average()
    if (missedAvg - takenAvg < MIN_MEANINGFUL_MED_DIFFERENCE_MGDL) return null

    return MedicationGlucoseInsight(takenAvg, missedAvg, takenReadings.size, missedReadings.size)
}

data class MealTimingInsight(
    val mealLabel: String,
    val mealAvg: Double,
    val otherAvg: Double,
    val mealCount: Int,
)

private const val MIN_READINGS_PER_MEAL_BUCKET = 4
private const val MIN_MEANINGFUL_MEAL_DIFFERENCE_MGDL = 15.0

/**
 * Buckets post-meal glucose readings by hour-of-day into breakfast/lunch/
 * dinner windows (there's no explicit "which meal" field logged, only a
 * timestamp) and surfaces it only when at least two windows have enough
 * readings to compare and the highest-averaging window clears the other
 * windows by a meaningful margin - otherwise null, same conservative bar as
 * the other two correlation insights.
 */
fun computeMealTimingInsight(glucoseReadings: List<CloudDocument>): MealTimingInsight? {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
    val buckets = linkedMapOf<String, MutableList<Double>>("Breakfast" to mutableListOf(), "Lunch" to mutableListOf(), "Dinner" to mutableListOf())
    for (reading in glucoseReadings) {
        if (reading.values["readingType"] != "post_meal") continue
        val millis = docTimeMillis(reading.values) ?: continue
        val value = (reading.values["value"] as? Number)?.toDouble() ?: continue
        cal.timeInMillis = millis
        val label = when (cal.get(Calendar.HOUR_OF_DAY)) {
            in 5..10 -> "Breakfast"
            in 11..15 -> "Lunch"
            in 16..22 -> "Dinner"
            else -> null
        } ?: continue
        buckets.getValue(label).add(value)
    }
    val eligible = buckets.filterValues { it.size >= MIN_READINGS_PER_MEAL_BUCKET }
    if (eligible.size < 2) return null

    val (topLabel, topValues) = eligible.entries.maxBy { it.value.average() }
    val topAvg = topValues.average()
    val otherAvg = eligible.filterKeys { it != topLabel }.values.flatten().average()
    if (topAvg - otherAvg < MIN_MEANINGFUL_MEAL_DIFFERENCE_MGDL) return null

    return MealTimingInsight(topLabel, topAvg, otherAvg, topValues.size)
}

enum class WeeklyTone { POSITIVE, NEUTRAL, CAUTION }

data class WeeklySummary(val headline: String, val detail: String, val tone: WeeklyTone)

private const val WEEK_WINDOW_DAYS = 7

/**
 * A single plain-language rollup of the last 7 days for the Today screen,
 * not raw numbers - built from the same glucose/BP/sleep collections
 * already listened elsewhere in the app. Deliberately framed around
 * logging consistency and typical-range percentages rather than a clinical
 * verdict (this isn't a diagnosis), and returns null only when nothing at
 * all was logged this week - no fabricated praise for an empty week.
 */
fun computeWeeklySummary(
    glucoseReadings: List<CloudDocument>,
    bpReadings: List<CloudDocument>,
    sleepLogs: List<CloudDocument>,
    nowMillis: Long,
): WeeklySummary? {
    val cutoffMillis = nowMillis - (WEEK_WINDOW_DAYS - 1).toLong() * 86_400_000L
    fun withinWeek(values: Map<String, Any?>): Boolean {
        val millis = docTimeMillis(values) ?: return false
        return millis >= cutoffMillis
    }

    val weekGlucose = glucoseReadings.filter { withinWeek(it.values) && it.values["readingType"] != "hba1c" }
    val weekBp = bpReadings.filter { withinWeek(it.values) }
    val weekSleep = sleepLogs.filter { withinWeek(it.values) }

    val loggedDays = (weekGlucose.mapNotNull { docTimeMillis(it.values) } +
        weekBp.mapNotNull { docTimeMillis(it.values) } +
        weekSleep.mapNotNull { docTimeMillis(it.values) })
        .map { dayKey(it) }
        .toSet()
        .size
    if (loggedDays == 0) return null

    val sugarValues = weekGlucose.mapNotNull { (it.values["value"] as? Number)?.toDouble() }
    val normalSugarCount = sugarValues.count { it in 80.0..130.0 }
    val sugarNormalPercent = if (sugarValues.isNotEmpty()) normalSugarCount * 100 / sugarValues.size else null

    val bpConcern = weekBp.any { doc ->
        val s = (doc.values["systolic"] as? Number)?.toInt() ?: 0
        val d = (doc.values["diastolic"] as? Number)?.toInt() ?: 0
        s >= 140 || d >= 90
    }

    val avgSleepHours = weekSleep.mapNotNull { (it.values["duration"] as? Number)?.toDouble() }.takeIf { it.isNotEmpty() }?.average()

    val loggingClause = "Logged $loggedDays of 7 days"
    return when {
        bpConcern || (sugarNormalPercent != null && sugarNormalPercent < 50) -> WeeklySummary(
            "A tougher week",
            "$loggingClause" + (if (bpConcern) " - one BP reading ran high" else " - sugar ran outside range more than usual") + ". Worth a check-in with your doctor if the pattern continues.",
            WeeklyTone.CAUTION,
        )
        loggedDays >= 5 && (sugarNormalPercent == null || sugarNormalPercent >= 70) -> WeeklySummary(
            "A great week",
            "$loggingClause" +
                (sugarNormalPercent?.let { ", sugar stayed in range $it% of the time" } ?: "") +
                (avgSleepHours?.let { ", averaging %.1fh sleep".format(it) } ?: "") + ".",
            WeeklyTone.POSITIVE,
        )
        loggedDays >= 3 -> WeeklySummary(
            "A steady week",
            "$loggingClause. Keep it up - consistency is what turns into a real trend.",
            WeeklyTone.NEUTRAL,
        )
        else -> WeeklySummary(
            "Building your rhythm",
            "$loggingClause. A few more logs this week will start showing real patterns.",
            WeeklyTone.NEUTRAL,
        )
    }
}
