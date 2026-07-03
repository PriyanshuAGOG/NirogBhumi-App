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
