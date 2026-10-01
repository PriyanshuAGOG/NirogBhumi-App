package com.nirogbhumi.app.health.domain

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The words screens use for health records, so a reading reads the same on Today, Track, its detail screen and the Health File. */
object HealthLabels {
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    /** "Today", "Yesterday", "29 Sep", or "29 Sep 2025" for another year. */
    fun day(millis: Long, nowMillis: Long, zone: ZoneId): String {
        val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return when {
            date == today -> "Today"
            date == today.minusDays(1) -> "Yesterday"
            date.year == today.year -> date.format(dayMonth)
            else -> date.format(dayMonthYear)
        }
    }

    /** "Today, 8:10 AM". */
    fun dayAndTime(millis: Long, nowMillis: Long, zone: ZoneId): String = "${day(millis, nowMillis, zone)}, ${ClockText.format12(millis, zone)}"

    fun glucoseKind(kind: GlucoseKind): String = when (kind) {
        GlucoseKind.FASTING -> "Fasting"
        GlucoseKind.POST_MEAL -> "After a meal"
        GlucoseKind.RANDOM -> "Any time"
        GlucoseKind.HBA1C -> "HbA1c"
        GlucoseKind.DEVICE -> "From your device"
    }

    fun glucoseStatus(status: GlucoseStatus): String = when (status) {
        GlucoseStatus.HIGH -> "High"
        GlucoseStatus.LOW -> "Low"
        GlucoseStatus.NORMAL -> "Normal"
    }

    fun glucoseValue(entry: GlucoseEntry): String =
        if (entry.isHbA1c) "${"%.1f".format(Locale.US, entry.value)}%" else "${entry.value.toInt()} mg/dL"

    fun bp(entry: BpEntry): String = "${entry.systolic}/${entry.diastolic}"

    fun weight(kg: Double): String = "${"%.1f".format(Locale.US, kg)} kg"

    /** "10:30 PM – 6:30 AM", or just the length when the start and end are unknown. */
    fun sleepRange(entry: SleepEntry, zone: ZoneId): String? {
        val start = entry.startAtMillis ?: return null
        val end = entry.endAtMillis ?: return null
        return "${ClockText.format12(start, zone)} – ${ClockText.format12(end, zone)}"
    }

    fun activity(entry: ActivityEntry): String = when {
        (entry.deviceSteps ?: 0L) > 0 -> "${"%,d".format(Locale.US, entry.deviceSteps)} steps"
        entry.minutes != null -> "${entry.minutes} min${entry.activityType?.let { " · ${it.replaceFirstChar { c -> c.uppercase() }}" } ?: ""}"
        else -> "Activity"
    }

    fun source(source: HealthSource): String? = when (source) {
        HealthSource.HEALTH_CONNECT -> "From Health Connect"
        HealthSource.DEVICE -> "From your device"
        HealthSource.ADMIN_CORRECTION -> "Corrected by your care team"
        else -> null
    }

    /** ZonedDateTime for callers that need date parts (month headers, pickers). */
    fun zoned(millis: Long, zone: ZoneId): ZonedDateTime = Instant.ofEpochMilli(millis).atZone(zone)
}
