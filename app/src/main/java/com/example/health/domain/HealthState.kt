package com.nirogbhumi.app.health.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Existing app thresholds, kept in one place so every screen labels a reading the same way (clinician sign-off is tracked in the launch checklist). */
object GlucoseRanges {
    const val LOW_BELOW = 80.0
    const val HIGH_ABOVE = 130.0
}

enum class GlucoseStatus { LOW, NORMAL, HIGH }

fun GlucoseEntry.status(): GlucoseStatus = when {
    value > GlucoseRanges.HIGH_ABOVE -> GlucoseStatus.HIGH
    value < GlucoseRanges.LOW_BELOW -> GlucoseStatus.LOW
    else -> GlucoseStatus.NORMAL
}

/**
 * What "checked in today" means, everywhere (Today card, prompts, streak copy, reports):
 *
 *   The member saved at least one of blood sugar, blood pressure, weight or medication TODAY
 *   (their phone's calendar day) by entering it themselves.
 *
 * Not counted: HbA1c (a lab value), anything imported from Health Connect or a device (passive,
 * not a deliberate check-in), sleep and walking (logged on their own screens and shown separately
 * as "also logged today"). The Daily Check-in wizard asks for exactly these four things, each optional.
 */
data class TodayStatus(
    val date: LocalDate,
    val hasSugar: Boolean,
    val hasBp: Boolean,
    val hasWeight: Boolean,
    val hasMedication: Boolean,
    val hasSleep: Boolean,
    val hasActivity: Boolean,
    val checkedIn: Boolean,
    val stepsToday: Long,
    val activityMinutesToday: Int,
    val sleepMinutesToday: Int?,
)

data class PeriodSummary(
    val days: Int,
    val glucoseCount: Int,
    val glucoseAverage: Double?,
    val glucoseInRangePercent: Int?,
    val bpCount: Int,
    val bpAverageSystolic: Double?,
    val bpAverageDiastolic: Double?,
    val weightCount: Int,
    val weightChangeKg: Double?,
    val sleepNights: Int,
    val sleepAverageMinutes: Int?,
    val activeDays: Int,
    val activityMinutes: Int,
    val steps: Long,
    val checkInDays: Int,
)

/** Everything a screen needs about the signed-in profile's health, read from one place. */
data class HealthUiState(
    /** True until the first snapshot of every collection has arrived. */
    val isLoading: Boolean = true,
    /** The data shown came from the local cache / a listener error: may be behind the server. */
    val isStale: Boolean = false,
    /** Per-metric problems to show instead of silently empty data. */
    val errors: Map<HealthMetric, String> = emptyMap(),
    val zone: ZoneId = ZoneId.systemDefault(),
    val generatedAtMillis: Long = 0L,

    // Newest first by measuredAt (then createdAt). Bounded windows, see HealthDataStore.
    val glucose: List<GlucoseEntry> = emptyList(),
    val bp: List<BpEntry> = emptyList(),
    val weight: List<WeightEntry> = emptyList(),
    val sleep: List<SleepEntry> = emptyList(),
    val activity: List<ActivityEntry> = emptyList(),
    val medication: List<MedicationEntry> = emptyList(),
    val labReports: List<LabReportEntry> = emptyList(),

    val latestGlucose: GlucoseEntry? = null,
    val latestHbA1c: GlucoseEntry? = null,
    val latestBp: BpEntry? = null,
    val latestWeight: WeightEntry? = null,
    /** Most recent sleep that counts (suspect entries excluded). */
    val lastSleep: SleepEntry? = null,

    val today: TodayStatus = TodayStatus(LocalDate.MIN, false, false, false, false, false, false, false, 0L, 0, null),
    val week: PeriodSummary = PeriodSummary(7, 0, null, null, 0, null, null, 0, null, 0, null, 0, 0, 0L, 0),
    val month: PeriodSummary = PeriodSummary(30, 0, null, null, 0, null, null, 0, null, 0, null, 0, 0, 0L, 0),
) {
    val hasAnyReading: Boolean get() = glucose.isNotEmpty() || bp.isNotEmpty() || weight.isNotEmpty() || sleep.isNotEmpty() || activity.isNotEmpty()
    /** mg/dL readings only: HbA1c is a percentage on another scale and must never be averaged with them. */
    val sugarReadings: List<GlucoseEntry> get() = glucose.filter { !it.isHbA1c }
}

/** Everything the store has parsed so far. Plain lists so the builder stays a pure function. */
data class HealthInputs(
    val glucose: List<GlucoseEntry> = emptyList(),
    val bp: List<BpEntry> = emptyList(),
    val weight: List<WeightEntry> = emptyList(),
    val sleep: List<SleepEntry> = emptyList(),
    val activity: List<ActivityEntry> = emptyList(),
    val medication: List<MedicationEntry> = emptyList(),
    val labReports: List<LabReportEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isStale: Boolean = false,
    val errors: Map<HealthMetric, String> = emptyMap(),
)

object HealthStateBuilder {
    /** A write the server has not stamped yet is the newest thing there is. */
    private fun <T : HealthEntry> newestFirst(list: List<T>): List<T> = list
        .distinctBy { it.id }
        .sortedWith(
            compareByDescending<T> { it.measuredAtMillis }
                .thenByDescending { it.createdAtMillis ?: Long.MAX_VALUE }
                .thenByDescending { it.id },
        )

    fun dateOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun build(input: HealthInputs, nowMillis: Long, zone: ZoneId): HealthUiState {
        val glucose = newestFirst(input.glucose)
        val bp = newestFirst(input.bp)
        val weight = newestFirst(input.weight)
        val sleep = newestFirst(input.sleep)
        val activity = newestFirst(input.activity)
        val medication = newestFirst(input.medication)
        val labs = newestFirst(input.labReports)
        val today = dateOf(nowMillis, zone)
        return HealthUiState(
            isLoading = input.isLoading,
            isStale = input.isStale,
            errors = input.errors,
            zone = zone,
            generatedAtMillis = nowMillis,
            glucose = glucose,
            bp = bp,
            weight = weight,
            sleep = sleep,
            activity = activity,
            medication = medication,
            labReports = labs,
            latestGlucose = glucose.firstOrNull { !it.isHbA1c },
            latestHbA1c = glucose.firstOrNull { it.isHbA1c },
            latestBp = bp.firstOrNull(),
            latestWeight = weight.firstOrNull(),
            lastSleep = sleep.firstOrNull { !it.isSuspect },
            today = todayStatus(glucose, bp, weight, sleep, activity, medication, today, zone),
            week = summarise(7, glucose, bp, weight, sleep, activity, medication, today, zone),
            month = summarise(30, glucose, bp, weight, sleep, activity, medication, today, zone),
        )
    }

    private fun isCheckIn(source: HealthSource) = !source.isImported

    private fun todayStatus(
        glucose: List<GlucoseEntry>, bp: List<BpEntry>, weight: List<WeightEntry>, sleep: List<SleepEntry>,
        activity: List<ActivityEntry>, medication: List<MedicationEntry>, today: LocalDate, zone: ZoneId,
    ): TodayStatus {
        fun isToday(millis: Long) = dateOf(millis, zone) == today
        val sugar = glucose.any { !it.isHbA1c && isCheckIn(it.source) && isToday(it.measuredAtMillis) }
        val hasBp = bp.any { isCheckIn(it.source) && isToday(it.measuredAtMillis) }
        val hasWeight = weight.any { isCheckIn(it.source) && isToday(it.measuredAtMillis) }
        val hasMed = medication.any { isCheckIn(it.source) && isToday(it.measuredAtMillis) }
        val sleepToday = sleep.filter { !it.isSuspect && isToday(it.measuredAtMillis) }
        val activityToday = activity.filter { isToday(it.measuredAtMillis) }
        return TodayStatus(
            date = today,
            hasSugar = sugar, hasBp = hasBp, hasWeight = hasWeight, hasMedication = hasMed,
            hasSleep = sleepToday.isNotEmpty(),
            hasActivity = activityToday.isNotEmpty(),
            checkedIn = sugar || hasBp || hasWeight || hasMed,
            stepsToday = activityToday.sumOf { it.deviceSteps ?: 0L },
            activityMinutesToday = activityToday.sumOf { it.minutes ?: 0 },
            sleepMinutesToday = sleepToday.takeIf { it.isNotEmpty() }?.sumOf { it.durationMinutes },
        )
    }

    private fun summarise(
        days: Int, glucose: List<GlucoseEntry>, bp: List<BpEntry>, weight: List<WeightEntry>, sleep: List<SleepEntry>,
        activity: List<ActivityEntry>, medication: List<MedicationEntry>, today: LocalDate, zone: ZoneId,
    ): PeriodSummary {
        val first = today.minusDays(days - 1L)
        fun inWindow(millis: Long) = dateOf(millis, zone).let { !it.isBefore(first) && !it.isAfter(today) }
        val g = glucose.filter { !it.isHbA1c && inWindow(it.measuredAtMillis) }
        val b = bp.filter { inWindow(it.measuredAtMillis) }
        val w = weight.filter { inWindow(it.measuredAtMillis) }.sortedBy { it.measuredAtMillis }
        val s = sleep.filter { !it.isSuspect && inWindow(it.measuredAtMillis) }
        val a = activity.filter { inWindow(it.measuredAtMillis) }
        val nights = s.groupBy { dateOf(it.measuredAtMillis, zone) }
        val checkInDates = buildSet {
            glucose.filter { !it.isHbA1c && isCheckIn(it.source) && inWindow(it.measuredAtMillis) }.forEach { add(dateOf(it.measuredAtMillis, zone)) }
            bp.filter { isCheckIn(it.source) && inWindow(it.measuredAtMillis) }.forEach { add(dateOf(it.measuredAtMillis, zone)) }
            weight.filter { isCheckIn(it.source) && inWindow(it.measuredAtMillis) }.forEach { add(dateOf(it.measuredAtMillis, zone)) }
            medication.filter { isCheckIn(it.source) && inWindow(it.measuredAtMillis) }.forEach { add(dateOf(it.measuredAtMillis, zone)) }
        }
        return PeriodSummary(
            days = days,
            glucoseCount = g.size,
            glucoseAverage = g.takeIf { it.isNotEmpty() }?.map { it.value }?.average(),
            glucoseInRangePercent = g.takeIf { it.isNotEmpty() }?.let { list ->
                (list.count { it.status() == GlucoseStatus.NORMAL } * 100.0 / list.size).toInt()
            },
            bpCount = b.size,
            bpAverageSystolic = b.takeIf { it.isNotEmpty() }?.map { it.systolic }?.average(),
            bpAverageDiastolic = b.takeIf { it.isNotEmpty() }?.map { it.diastolic }?.average(),
            weightCount = w.size,
            weightChangeKg = if (w.size >= 2) w.last().valueKg - w.first().valueKg else null,
            sleepNights = nights.size,
            sleepAverageMinutes = nights.takeIf { it.isNotEmpty() }?.values?.map { day -> day.sumOf { it.durationMinutes } }?.average()?.toInt(),
            activeDays = a.map { dateOf(it.measuredAtMillis, zone) }.toSet().size,
            activityMinutes = a.sumOf { it.minutes ?: 0 },
            steps = a.sumOf { it.deviceSteps ?: 0L },
            checkInDays = checkInDates.size,
        )
    }
}
