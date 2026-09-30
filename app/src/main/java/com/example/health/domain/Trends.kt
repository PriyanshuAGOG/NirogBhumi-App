package com.nirogbhumi.app.health.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

enum class TrendMetric(val title: String) {
    GLUCOSE("Blood sugar"), BP("Blood pressure"), SLEEP("Sleep"), WALKING("Walking"), WEIGHT("Weight")
}

enum class TrendRange(val days: Int, val label: String) {
    WEEK(7, "7 days"), MONTH(30, "30 days"), QUARTER(90, "90 days")
}

/** One measured day. Days with no data simply have no point: nothing is invented. */
data class TrendPoint(val timestampMillis: Long, val date: LocalDate, val value: Double, val readings: Int)

data class TrendSeries(val key: String, val label: String, val points: List<TrendPoint>)

data class AxisSpec(val min: Double, val max: Double, val ticks: List<Double>) {
    val span: Double get() = max - min
}

data class AxisLabel(val date: LocalDate, val text: String)

/** Where the chart may draw a line between two points. */
enum class GapPolicy { CONNECT_SHORT_GAPS }

data class TrendData(
    val metric: TrendMetric,
    val range: TrendRange,
    /** "mg/dL", "mmHg", "h", "steps", "min", "kg". */
    val unit: String,
    val series: List<TrendSeries>,
    val firstDate: LocalDate,
    val lastDate: LocalDate,
    val axis: AxisSpec?,
    val hasEnoughData: Boolean,
    val message: String?,
) {
    val primary: TrendSeries? get() = series.firstOrNull()
}

data class AxisPolicy(
    val minSpan: Double,
    val floorAtZero: Boolean,
    val stepCandidates: List<Double>,
    val padFraction: Double = 0.1,
    val maxTicks: Int = 5,
)

object TrendBuilder {
    const val INSUFFICIENT_MESSAGE = "Log at least two readings to see a trend."
    // Plain English month/day names ("Sep", not en-IN's "Sept") so labels match the rest of the app's copy.
    val chartLocale: Locale = Locale.ENGLISH

    fun build(state: HealthUiState, metric: TrendMetric, range: TrendRange, nowMillis: Long): TrendData {
        val zone = state.zone
        val today = HealthStateBuilder.dateOf(nowMillis, zone)
        val first = today.minusDays(range.days - 1L)
        fun inWindow(millis: Long) = HealthStateBuilder.dateOf(millis, zone).let { !it.isBefore(first) && !it.isAfter(today) }

        fun <T> daily(items: List<T>, at: (T) -> Long, value: (List<T>) -> Double): List<TrendPoint> = items
            .filter { inWindow(at(it)) }
            .groupBy { HealthStateBuilder.dateOf(at(it), zone) }
            .toSortedMap()
            .map { (date, list) ->
                TrendPoint(date.atStartOfDay(zone).toInstant().toEpochMilli(), date, value(list), list.size)
            }

        val unit: String
        val series: List<TrendSeries>
        when (metric) {
            TrendMetric.GLUCOSE -> {
                unit = "mg/dL"
                series = listOf(TrendSeries("glucose", "Blood sugar", daily(state.sugarReadings, { it.measuredAtMillis }) { l -> l.map { it.value }.average() }))
            }
            TrendMetric.BP -> {
                unit = "mmHg"
                series = listOf(
                    TrendSeries("systolic", "Upper BP", daily(state.bp, { it.measuredAtMillis }) { l -> l.map { it.systolic }.average() }),
                    TrendSeries("diastolic", "Lower BP", daily(state.bp, { it.measuredAtMillis }) { l -> l.map { it.diastolic }.average() }),
                )
            }
            TrendMetric.SLEEP -> {
                unit = "h"
                series = listOf(TrendSeries("sleep", "Hours slept", daily(state.sleep.filter { !it.isSuspect }, { it.measuredAtMillis }) { l -> l.sumOf { it.durationMinutes } / 60.0 }))
            }
            TrendMetric.WALKING -> {
                val steps = daily(state.activity.filter { (it.deviceSteps ?: 0L) > 0 }, { it.measuredAtMillis }) { l -> l.sumOf { it.deviceSteps ?: 0L }.toDouble() }
                if (steps.size >= 2) {
                    unit = "steps"; series = listOf(TrendSeries("steps", "Steps", steps))
                } else {
                    unit = "min"
                    series = listOf(TrendSeries("minutes", "Active minutes", daily(state.activity.filter { (it.minutes ?: 0) > 0 }, { it.measuredAtMillis }) { l -> l.sumOf { it.minutes ?: 0 }.toDouble() }))
                }
            }
            TrendMetric.WEIGHT -> {
                unit = "kg"
                series = listOf(TrendSeries("weight", "Weight", daily(state.weight, { it.measuredAtMillis }) { l -> l.maxBy { it.measuredAtMillis }.valueKg }))
            }
        }
        val enough = (series.firstOrNull()?.points?.size ?: 0) >= 2
        val allValues = series.flatMap { s -> s.points.map { it.value } }
        return TrendData(
            metric = metric, range = range, unit = unit, series = series, firstDate = first, lastDate = today,
            axis = if (allValues.isEmpty()) null else axis(allValues, policyFor(metric, unit, allValues)),
            hasEnoughData = enough,
            message = if (enough) null else INSUFFICIENT_MESSAGE,
        )
    }

    fun policyFor(metric: TrendMetric, unit: String, values: List<Double>): AxisPolicy = when (metric) {
        TrendMetric.GLUCOSE -> AxisPolicy(minSpan = 60.0, floorAtZero = false, stepCandidates = listOf(10.0, 20.0, 25.0, 50.0))
        TrendMetric.BP -> AxisPolicy(minSpan = 50.0, floorAtZero = false, stepCandidates = listOf(10.0, 20.0, 25.0, 50.0))
        TrendMetric.SLEEP -> AxisPolicy(minSpan = 4.0, floorAtZero = false, stepCandidates = listOf(1.0, 2.0, 4.0))
        TrendMetric.WALKING -> if (unit == "steps") {
            AxisPolicy(minSpan = 2000.0, floorAtZero = true, stepCandidates = listOf(500.0, 1000.0, 2000.0, 2500.0, 5000.0, 10000.0))
        } else {
            AxisPolicy(minSpan = 30.0, floorAtZero = true, stepCandidates = listOf(10.0, 15.0, 30.0, 60.0))
        }
        // A 0.2 kg change must not look dramatic: always show at least 3 kg (or 4% of body weight) of range.
        TrendMetric.WEIGHT -> AxisPolicy(minSpan = max(3.0, values.average() * 0.04), floorAtZero = false, stepCandidates = listOf(0.5, 1.0, 2.0, 5.0))
    }

    /** Round-number axis around the data, never narrower than the policy's minimum span. */
    fun axis(values: List<Double>, policy: AxisPolicy): AxisSpec {
        var lo = values.min()
        var hi = values.max()
        val pad = (hi - lo) * policy.padFraction
        lo -= pad; hi += pad
        val span = max(hi - lo, policy.minSpan)
        val mid = (lo + hi) / 2
        lo = mid - span / 2; hi = mid + span / 2
        if (policy.floorAtZero) { hi += max(0.0, -lo); lo = 0.0 }
        lo = max(lo, 0.0)
        var step = policy.stepCandidates.last()
        var scale = 1.0
        search@ while (true) {
            for (candidate in policy.stepCandidates) {
                val s = candidate * scale
                val count = (ceil(hi / s) - floor(lo / s)).toInt() + 1
                if (count <= policy.maxTicks) { step = s; break@search }
            }
            scale *= 10.0
            if (scale > 1e6) break
        }
        val axisMin = max(0.0, floor(lo / step) * step)
        val axisMax = ceil(hi / step) * step
        val ticks = generateSequence(axisMin) { it + step }.takeWhile { it <= axisMax + step * 1e-9 }.map { roundTo(it, 6) }.toList()
        return AxisSpec(axisMin, axisMax, ticks)
    }

    private fun roundTo(v: Double, digits: Int): Double { val m = Math.pow(10.0, digits.toDouble()); return Math.round(v * m) / m }

    /** X-axis labels: weekday names for a week, day + month for longer ranges, at most about seven. */
    fun xLabels(range: TrendRange, first: LocalDate, last: LocalDate): List<AxisLabel> {
        val weekday = DateTimeFormatter.ofPattern("EEE", chartLocale)
        val dayMonth = DateTimeFormatter.ofPattern("d MMM", chartLocale)
        if (range == TrendRange.WEEK) {
            return (0..ChronoUnit.DAYS.between(first, last)).map { first.plusDays(it) }.map { AxisLabel(it, it.format(weekday)) }
        }
        val step = if (range == TrendRange.MONTH) 5L else 15L
        val dates = generateSequence(first) { it.plusDays(step) }.takeWhile { !it.isAfter(last) }.toMutableList()
        if (ChronoUnit.DAYS.between(dates.last(), last) >= step / 2 + 1) dates += last
        return dates.map { AxisLabel(it, it.format(dayMonth)) }
    }

    /**
     * Splits a series where the member stopped logging for a while, so the chart does not draw a
     * line that implies continuous measurement across a long silence.
     */
    fun segments(points: List<TrendPoint>, range: TrendRange): List<List<TrendPoint>> {
        val maxGap = if (range == TrendRange.QUARTER) 7L else 3L
        val out = mutableListOf<MutableList<TrendPoint>>()
        for (p in points) {
            val current = out.lastOrNull()
            if (current != null && ChronoUnit.DAYS.between(current.last().date, p.date) <= maxGap) current += p
            else out += mutableListOf(p)
        }
        return out
    }
}

/** Numbers and sentences for tooltips and screen readers. */
object TrendText {
    private val dayFmt = DateTimeFormatter.ofPattern("EEEE, d MMM", TrendBuilder.chartLocale)
    private val spokenDayFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", TrendBuilder.chartLocale)

    fun dayLabel(date: LocalDate): String = date.format(dayFmt)

    fun formatValue(metric: TrendMetric, unit: String, value: Double): String = when (metric) {
        TrendMetric.SLEEP -> ClockText.duration((value * 60).roundToInt())
        TrendMetric.WEIGHT -> "${oneDecimal(value)} kg"
        TrendMetric.GLUCOSE -> "${value.roundToInt()} mg/dL"
        TrendMetric.BP -> "${value.roundToInt()} mmHg"
        TrendMetric.WALKING -> if (unit == "steps") "${"%,d".format(Locale.US, value.roundToInt())} steps" else "${value.roundToInt()} min"
    }

    fun axisLabel(metric: TrendMetric, unit: String, value: Double): String = when (metric) {
        TrendMetric.SLEEP -> "${value.roundToInt()}h"
        TrendMetric.WEIGHT -> oneDecimal(value)
        TrendMetric.WALKING -> if (unit == "steps" && value >= 1000) "${oneDecimal(value / 1000.0).removeSuffix(".0")}k" else value.roundToInt().toString()
        else -> value.roundToInt().toString()
    }

    /** Text shown when a point is tapped: "Tuesday, 29 Sep" / "7h 35m" (BP shows "122 / 80 mmHg"). */
    fun describeDay(data: TrendData, date: LocalDate): String? {
        val values = data.series.mapNotNull { s -> s.points.firstOrNull { it.date == date }?.let { s to it } }
        if (values.isEmpty()) return null
        val body = if (data.metric == TrendMetric.BP && values.size == 2) {
            "${values[0].second.value.roundToInt()} / ${values[1].second.value.roundToInt()} mmHg"
        } else {
            formatValue(data.metric, data.unit, values[0].second.value)
        }
        return "${dayLabel(date)}\n$body"
    }

    /** One sentence per point for TalkBack: "Tuesday 29 September, 7 hours 35 minutes of sleep." */
    fun spokenPoint(data: TrendData, date: LocalDate): String? {
        val values = data.series.mapNotNull { s -> s.points.firstOrNull { it.date == date }?.let { s to it } }
        if (values.isEmpty()) return null
        val day = date.format(spokenDayFmt)
        val what = when (data.metric) {
            TrendMetric.SLEEP -> "${ClockText.durationSpoken((values[0].second.value * 60).roundToInt())} of sleep"
            TrendMetric.WEIGHT -> "${oneDecimal(values[0].second.value)} kilograms"
            TrendMetric.GLUCOSE -> "blood sugar ${values[0].second.value.roundToInt()} milligrams per decilitre"
            TrendMetric.BP -> if (values.size == 2) "upper blood pressure ${values[0].second.value.roundToInt()}, lower ${values[1].second.value.roundToInt()}" else "blood pressure ${values[0].second.value.roundToInt()}"
            TrendMetric.WALKING -> if (data.unit == "steps") "${values[0].second.value.roundToInt()} steps" else "${values[0].second.value.roundToInt()} active minutes"
        }
        return "$day, $what."
    }

    /** Summary read once when a chart gets focus, so the information is not only pixels on a Canvas. */
    fun spokenSummary(data: TrendData): String {
        val primary = data.primary
        val title = "${data.metric.title}, last ${data.range.days} days."
        if (primary == null || primary.points.isEmpty()) return "$title No readings yet."
        if (!data.hasEnoughData) return "$title ${primary.points.size} reading. ${TrendBuilder.INSUFFICIENT_MESSAGE}"
        val values = primary.points.map { it.value }
        val days = "${primary.points.size} of ${data.range.days} days have readings."
        val avg = "Average ${spokenValue(data, values.average())}. Lowest ${spokenValue(data, values.min())}, highest ${spokenValue(data, values.max())}."
        return "$title $days $avg"
    }

    private fun spokenValue(data: TrendData, v: Double): String = when (data.metric) {
        TrendMetric.SLEEP -> ClockText.durationSpoken((v * 60).roundToInt())
        TrendMetric.WEIGHT -> "${oneDecimal(v)} kilograms"
        TrendMetric.GLUCOSE -> "${v.roundToInt()} milligrams per decilitre"
        TrendMetric.BP -> "${v.roundToInt()}"
        TrendMetric.WALKING -> if (data.unit == "steps") "${v.roundToInt()} steps" else "${v.roundToInt()} minutes"
    }

    private fun oneDecimal(v: Double): String = "%.1f".format(Locale.US, v)

    /** Whether two weights differ enough to call it a change, rather than scale noise. */
    fun isMeaningfulWeightChange(deltaKg: Double): Boolean = abs(deltaKg) >= 0.5
}
