package com.nirogbhumi.app.health.domain

import java.time.LocalDate
import java.util.Locale

/**
 * The short, printable summary behind the Health File and its PDF. Built from the same
 * [HealthUiState] as every other screen, so the numbers in a doctor's hands match the ones in the app.
 * Returns `loggedDays == 0` and null summaries rather than writing a "quiet period" story.
 */
data class DoctorReportSummary(
    val periodDays: Int,
    val loggedDays: Int,
    val sugarSummary: String?,
    val bpSummary: String?,
    val weightSummary: String?,
    val sleepSummary: String?,
)

object DoctorReport {
    fun build(state: HealthUiState, periodDays: Int): DoctorReportSummary {
        val zone = state.zone
        val today = state.today.date
        val first = today.minusDays(periodDays - 1L)
        fun day(millis: Long): LocalDate = HealthStateBuilder.dateOf(millis, zone)
        fun inPeriod(millis: Long) = day(millis).let { !it.isBefore(first) && !it.isAfter(today) }

        val sugar = state.sugarReadings.filter { inPeriod(it.measuredAtMillis) }
        val bp = state.bp.filter { inPeriod(it.measuredAtMillis) }
        val weight = state.weight.filter { inPeriod(it.measuredAtMillis) }.sortedBy { it.measuredAtMillis }
        val sleep = state.sleep.filter { !it.isSuspect && inPeriod(it.measuredAtMillis) }

        val loggedDays = buildSet {
            sugar.forEach { add(day(it.measuredAtMillis)) }
            bp.forEach { add(day(it.measuredAtMillis)) }
            weight.forEach { add(day(it.measuredAtMillis)) }
            sleep.forEach { add(day(it.measuredAtMillis)) }
        }.size

        val sugarSummary = sugar.takeIf { it.isNotEmpty() }?.let { list ->
            val inRange = list.count { it.status() == GlucoseStatus.NORMAL } * 100 / list.size
            "average ${fmt0(list.map { it.value }.average())} mg/dL across ${list.size} ${plural(list.size, "reading")}, $inRange% in the usual range"
        }
        val bpSummary = bp.takeIf { it.isNotEmpty() }?.let { list ->
            val high = list.count { it.systolic >= 140 || it.diastolic >= 90 }
            "${list.size} ${plural(list.size, "reading")}, latest ${list.first().systolic}/${list.first().diastolic}" +
                if (high > 0) ", $high on the higher side" else ""
        }
        val weightSummary = weight.takeIf { it.size >= 2 }?.let { list ->
            val a = list.first().valueKg; val b = list.last().valueKg; val delta = b - a
            "${fmt1(a)} kg to ${fmt1(b)} kg (${if (delta > 0) "+" else ""}${fmt1(delta)} kg)"
        }
        val sleepSummary = sleep.takeIf { it.isNotEmpty() }?.let { list ->
            val nights = list.groupBy { day(it.measuredAtMillis) }
            val avg = nights.values.map { d -> d.sumOf { it.durationMinutes } }.average().toInt()
            "average ${avg / 60}h ${avg % 60}m across ${nights.size} ${plural(nights.size, "night")}"
        }
        return DoctorReportSummary(periodDays, loggedDays, sugarSummary, bpSummary, weightSummary, sleepSummary)
    }

    private fun fmt0(v: Double) = "%.0f".format(Locale.US, v)
    private fun fmt1(v: Double) = "%.1f".format(Locale.US, v)
    private fun plural(n: Int, word: String) = if (n == 1) word else "${word}s"
}
