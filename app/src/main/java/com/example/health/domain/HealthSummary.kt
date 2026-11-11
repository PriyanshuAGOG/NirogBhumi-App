package com.nirogbhumi.app.health.domain

import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Whether a change moves toward a healthy range, away from it, or is just a number going up or down. */
enum class ChangeTone { BETTER, WORSE, NEUTRAL, NONE }

/** [direction] is +1 for up, -1 for down, 0 when unchanged or unknown. */
data class PeriodChange(val text: String, val tone: ChangeTone, val direction: Int = 0) {
    companion object { val NONE = PeriodChange("", ChangeTone.NONE) }
}

data class SummaryLine(val label: String, val value: String)

data class SummaryMetric(
    val metric: TrendMetric,
    val title: String,
    /** The one number to read first. "Not logged in this period" when there is nothing. */
    val headline: String,
    val lines: List<SummaryLine>,
    val change: PeriodChange,
    /** Daily values for the small chart, oldest first. Empty when there is not enough to draw. */
    val spark: List<Double>,
    val hasData: Boolean,
)

/**
 * The single health summary behind the Health File screen, its PDF and the weekly report: one definition of every number
 * so a doctor's printout, the app and the chart always agree. Replaces the separate "vitals summary" and "doctor visit report".
 * Blood sugar is always broken out by what was measured (fasting, after a meal, other, HbA1c): "blood sugar" is the
 * measurement, "fasting" is one kind of reading, and a bare "sugar" is never used.
 */
data class HealthSummary(
    val periodDays: Int,
    val periodName: String,
    val loggedDays: Int,
    val metrics: List<SummaryMetric>,
) {
    val hasData: Boolean get() = metrics.any { it.hasData }
}

object HealthSummaryBuilder {
    fun build(state: HealthUiState, periodDays: Int, nowMillis: Long): HealthSummary {
        val zone = state.zone
        val today = HealthStateBuilder.dateOf(nowMillis, zone)
        val first = today.minusDays(periodDays - 1L)
        val prevLast = first.minusDays(1)
        val prevFirst = prevLast.minusDays(periodDays - 1L)
        fun day(millis: Long): LocalDate = HealthStateBuilder.dateOf(millis, zone)
        fun cur(millis: Long) = day(millis).let { !it.isBefore(first) && !it.isAfter(today) }
        fun prev(millis: Long) = day(millis).let { !it.isBefore(prevFirst) && !it.isAfter(prevLast) }
        val periodName = if (periodDays == 7) "week" else if (periodDays == 30) "month" else "$periodDays days"
        val previousName = "the previous ${if (periodDays == 7) "7 days" else if (periodDays == 30) "30 days" else "$periodDays days"}"
        val range = if (periodDays <= 7) TrendRange.WEEK else TrendRange.MONTH
        fun spark(metric: TrendMetric): List<Double> =
            TrendBuilder.build(state, metric, range, nowMillis).primary?.points?.map { it.value }.orEmpty().let { if (it.size >= 2) it else emptyList() }

        val sugar = state.sugarReadings.filter { cur(it.measuredAtMillis) }
        val sugarPrev = state.sugarReadings.filter { prev(it.measuredAtMillis) }
        val bp = state.bp.filter { cur(it.measuredAtMillis) }
        val bpPrev = state.bp.filter { prev(it.measuredAtMillis) }
        val weight = state.weight.filter { cur(it.measuredAtMillis) }.sortedBy { it.measuredAtMillis }
        val weightPrev = state.weight.filter { prev(it.measuredAtMillis) }.maxByOrNull { it.measuredAtMillis }
        val sleep = state.sleep.filter { !it.isSuspect && cur(it.measuredAtMillis) }
        val sleepPrev = state.sleep.filter { !it.isSuspect && prev(it.measuredAtMillis) }
        val activity = state.activity.filter { cur(it.measuredAtMillis) }
        val activityPrev = state.activity.filter { prev(it.measuredAtMillis) }

        val loggedDays = buildSet {
            sugar.forEach { add(day(it.measuredAtMillis)) }
            bp.forEach { add(day(it.measuredAtMillis)) }
            weight.forEach { add(day(it.measuredAtMillis)) }
            sleep.forEach { add(day(it.measuredAtMillis)) }
            activity.filter { !it.source.isImported }.forEach { add(day(it.measuredAtMillis)) }
        }.size

        val metrics = listOf(
            glucose(state, sugar, sugarPrev, previousName, spark(TrendMetric.GLUCOSE), zone, nowMillis),
            bloodPressure(bp, bpPrev, previousName, spark(TrendMetric.BP), zone),
            weightMetric(weight, weightPrev, previousName, spark(TrendMetric.WEIGHT)),
            sleepMetric(sleep, sleepPrev, previousName, spark(TrendMetric.SLEEP), zone),
            walking(activity, activityPrev, previousName, spark(TrendMetric.WALKING), zone),
        )
        return HealthSummary(periodDays, periodName, loggedDays, metrics)
    }

    // ---- blood sugar ----------------------------------------------------------------------------------------------

    private fun glucose(state: HealthUiState, cur: List<GlucoseEntry>, prev: List<GlucoseEntry>, prevName: String, spark: List<Double>, zone: java.time.ZoneId, nowMillis: Long): SummaryMetric {
        val lines = ArrayList<SummaryLine>()
        fun kindLine(label: String, list: List<GlucoseEntry>) {
            if (list.isNotEmpty()) lines += SummaryLine(label, "${fmt0(list.map { it.value }.average())} mg/dL · ${count(list.size, "reading")}")
        }
        val fasting = cur.filter { it.kind == GlucoseKind.FASTING }
        val afterMeal = cur.filter { it.kind == GlucoseKind.POST_MEAL }
        val other = cur.filter { it.kind == GlucoseKind.RANDOM || it.kind == GlucoseKind.DEVICE }
        kindLine("Fasting (before eating)", fasting)
        kindLine("After a meal", afterMeal)
        kindLine("Other times", other)
        if (cur.isNotEmpty()) {
            val inRange = cur.count { it.status() == GlucoseStatus.NORMAL } * 100 / cur.size
            lines += SummaryLine("In the usual range", "$inRange% of ${count(cur.size, "reading")}")
        }
        state.latestHbA1c?.let { lines += SummaryLine("Latest HbA1c (lab value)", "${"%.1f".format(Locale.US, it.value)}% on ${HealthLabels.day(it.measuredAtMillis, nowMillis, zone)}") }

        // Compare like with like: fasting with fasting when there is any, otherwise every reading.
        val useFasting = fasting.isNotEmpty()
        val curAvg = (if (useFasting) fasting else cur).takeIf { it.isNotEmpty() }?.map { it.value }?.average()
        val prevAvg = (if (useFasting) prev.filter { it.kind == GlucoseKind.FASTING } else prev).takeIf { it.isNotEmpty() }?.map { it.value }?.average()
        val headline = when {
            curAvg == null -> NOT_LOGGED
            useFasting -> "Fasting average ${fmt0(curAvg)} mg/dL"
            else -> "Average ${fmt0(curAvg)} mg/dL"
        }
        val change = change(curAvg, prevAvg, prevName, tolerance = 3.0, noun = "", unit = "mg/dL", decimals = 0) { toward(70.0, 130.0, it) }
        return SummaryMetric(TrendMetric.GLUCOSE, "Blood sugar", headline, lines, change, spark, cur.isNotEmpty() || state.latestHbA1c != null)
    }

    // ---- blood pressure -------------------------------------------------------------------------------------------

    private fun bloodPressure(cur: List<BpEntry>, prev: List<BpEntry>, prevName: String, spark: List<Double>, zone: java.time.ZoneId): SummaryMetric {
        if (cur.isEmpty()) return empty(TrendMetric.BP, "Blood pressure")
        val avgS = cur.map { it.systolic }.average(); val avgD = cur.map { it.diastolic }.average()
        val latest = cur.maxBy { it.measuredAtMillis }
        val high = cur.count { it.systolic >= 140 || it.diastolic >= 90 }
        val lines = buildList {
            add(SummaryLine("Readings", cur.size.toString()))
            add(SummaryLine("Latest", "${latest.systolic}/${latest.diastolic} mmHg"))
            if (high > 0) add(SummaryLine("On the higher side (140/90 or above)", count(high, "reading")))
        }
        val prevAvgS = prev.takeIf { it.isNotEmpty() }?.map { it.systolic }?.average()
        val change = change(avgS, prevAvgS, prevName, tolerance = 3.0, noun = "Upper number ", unit = "mmHg", decimals = 0) { toward(90.0, 130.0, it) }
        return SummaryMetric(TrendMetric.BP, "Blood pressure", "Average ${fmt0(avgS)}/${fmt0(avgD)} mmHg", lines, change, spark, true)
    }

    // ---- weight ---------------------------------------------------------------------------------------------------

    private fun weightMetric(cur: List<WeightEntry>, prevLatest: WeightEntry?, prevName: String, spark: List<Double>): SummaryMetric {
        if (cur.isEmpty()) return empty(TrendMetric.WEIGHT, "Weight")
        val latest = cur.last()
        val lines = buildList {
            add(SummaryLine("Entries", cur.size.toString()))
            if (cur.size >= 2) {
                val delta = latest.valueKg - cur.first().valueKg
                add(SummaryLine("Within this period", "${fmt1(cur.first().valueKg)} kg to ${fmt1(latest.valueKg)} kg (${signed1(delta)} kg)"))
            }
        }
        val change = change(latest.valueKg, prevLatest?.valueKg, prevName, tolerance = 0.5, noun = "", unit = "kg", decimals = 1, since = true) { _ -> ChangeTone.NEUTRAL }
        return SummaryMetric(TrendMetric.WEIGHT, "Weight", "Latest ${fmt1(latest.valueKg)} kg", lines, change, spark, true)
    }

    // ---- sleep ----------------------------------------------------------------------------------------------------

    private fun sleepMetric(cur: List<SleepEntry>, prev: List<SleepEntry>, prevName: String, spark: List<Double>, zone: java.time.ZoneId): SummaryMetric {
        if (cur.isEmpty()) return empty(TrendMetric.SLEEP, "Sleep")
        fun nightlyAverage(list: List<SleepEntry>): Double? = list.groupBy { HealthStateBuilder.dateOf(it.measuredAtMillis, zone) }.values
            .map { d -> d.sumOf { it.durationMinutes }.toDouble() }.takeIf { it.isNotEmpty() }?.average()
        val avg = nightlyAverage(cur)!!
        val nights = cur.groupBy { HealthStateBuilder.dateOf(it.measuredAtMillis, zone) }
        val short = nights.values.count { d -> d.sumOf { it.durationMinutes } < 6 * 60 }
        val lines = buildList {
            add(SummaryLine("Nights logged", nights.size.toString()))
            if (short > 0) add(SummaryLine("Nights under 6 hours", short.toString()))
        }
        val change = changeMinutes(avg, nightlyAverage(prev), prevName) { toward(7 * 60.0, 9 * 60.0, it) }
        return SummaryMetric(TrendMetric.SLEEP, "Sleep", "Average ${ClockText.duration(avg.roundToInt())} a night", lines, change, spark, true)
    }

    // ---- walking and activity -------------------------------------------------------------------------------------

    private fun stepsPerDay(list: List<ActivityEntry>, zone: java.time.ZoneId): Double? = list.filter { (it.deviceSteps ?: 0L) > 0 }
        .groupBy { HealthStateBuilder.dateOf(it.measuredAtMillis, zone) }.values
        .map { d -> d.sumOf { it.deviceSteps ?: 0L }.toDouble() }.takeIf { it.isNotEmpty() }?.average()

    private fun minutesPerActiveDay(list: List<ActivityEntry>, zone: java.time.ZoneId): Double? = list.filter { (it.minutes ?: 0) > 0 }
        .groupBy { HealthStateBuilder.dateOf(it.measuredAtMillis, zone) }.values
        .map { d -> d.sumOf { it.minutes ?: 0 }.toDouble() }.takeIf { it.isNotEmpty() }?.average()

    private fun walking(cur: List<ActivityEntry>, prev: List<ActivityEntry>, prevName: String, spark: List<Double>, zone: java.time.ZoneId): SummaryMetric {
        val steps = stepsPerDay(cur, zone)
        val minutes = minutesPerActiveDay(cur, zone)
        if (steps == null && minutes == null) return empty(TrendMetric.WALKING, "Walking and activity")
        val lines = ArrayList<SummaryLine>()
        steps?.let { s ->
            val days = cur.filter { (it.deviceSteps ?: 0L) > 0 }.map { HealthStateBuilder.dateOf(it.measuredAtMillis, zone) }.distinct().size
            lines += SummaryLine("Steps", "${thousands(s)} a day on average · $days ${if (days == 1) "day" else "days"} counted")
        }
        minutes?.let {
            val total = cur.sumOf { e -> e.minutes ?: 0 }
            val days = cur.filter { (it.minutes ?: 0) > 0 }.map { HealthStateBuilder.dateOf(it.measuredAtMillis, zone) }.distinct().size
            lines += SummaryLine("Walks and workouts", "$total min over $days ${if (days == 1) "day" else "days"}")
        }
        val headline = steps?.let { "Average ${thousands(it)} steps a day" } ?: "About ${fmt0(minutes!!)} min on active days"
        val change = if (steps != null) change(steps, stepsPerDay(prev, zone), prevName, tolerance = 500.0, noun = "", unit = "steps a day", decimals = 0) { up(it) }
        else change(minutes, minutesPerActiveDay(prev, zone), prevName, tolerance = 5.0, noun = "", unit = "min", decimals = 0) { up(it) }
        return SummaryMetric(TrendMetric.WALKING, "Walking and activity", headline, lines, change, spark, true)
    }

    // ---- shared helpers -------------------------------------------------------------------------------------------

    private const val NOT_LOGGED = "Not logged in this period"
    private fun empty(metric: TrendMetric, title: String) = SummaryMetric(metric, title, NOT_LOGGED, emptyList(), PeriodChange.NONE, emptyList(), false)

    /** BETTER when the average moves closer to the healthy band [lo, hi], WORSE when it moves further away. */
    private fun toward(lo: Double, hi: Double, move: Pair<Double, Double>): ChangeTone {
        fun dist(x: Double) = maxOf(lo - x, 0.0, x - hi)
        val (before, after) = move
        return when {
            dist(after) < dist(before) - 1e-9 -> ChangeTone.BETTER
            dist(after) > dist(before) + 1e-9 -> ChangeTone.WORSE
            else -> ChangeTone.NEUTRAL
        }
    }

    private fun up(move: Pair<Double, Double>): ChangeTone = if (move.second > move.first) ChangeTone.BETTER else ChangeTone.WORSE

    private fun change(
        cur: Double?, prev: Double?, prevName: String, tolerance: Double, noun: String, unit: String, decimals: Int,
        since: Boolean = false, judge: (Pair<Double, Double>) -> ChangeTone,
    ): PeriodChange {
        if (cur == null) return PeriodChange.NONE
        if (prev == null) return PeriodChange("Nothing logged in $prevName to compare with yet.", ChangeTone.NONE)
        val delta = cur - prev
        if (abs(delta) < tolerance) return PeriodChange("About the same as $prevName.", ChangeTone.NEUTRAL)
        val amount = if (decimals == 0) fmt0(abs(delta)) else fmt1(abs(delta))
        val direction = if (delta > 0) "Up" else "Down"
        val text = if (noun.isBlank()) "$direction $amount $unit ${if (since) "since" else "from"} $prevName." else "$noun${direction.lowercase()} $amount $unit from $prevName."
        return PeriodChange(text, judge(prev to cur), if (delta > 0) 1 else -1)
    }

    private fun changeMinutes(cur: Double, prev: Double?, prevName: String, judge: (Pair<Double, Double>) -> ChangeTone): PeriodChange {
        if (prev == null) return PeriodChange("Nothing logged in $prevName to compare with yet.", ChangeTone.NONE)
        val delta = cur - prev
        if (abs(delta) < 15) return PeriodChange("About the same as $prevName.", ChangeTone.NEUTRAL)
        return PeriodChange("${if (delta > 0) "Up" else "Down"} ${ClockText.duration(abs(delta).roundToInt())} a night from $prevName.", judge(prev to cur), if (delta > 0) 1 else -1)
    }

    private fun fmt0(v: Double) = "%.0f".format(Locale.US, v)
    private fun fmt1(v: Double) = "%.1f".format(Locale.US, v)
    private fun signed1(v: Double) = (if (v > 0) "+" else "") + fmt1(v)
    private fun thousands(v: Double) = "%,d".format(Locale.US, v.roundToInt())
    private fun count(n: Int, word: String) = "$n ${if (n == 1) word else "${word}s"}"
}
