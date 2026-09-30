package com.nirogbhumi.app.health.domain

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import kotlin.math.roundToInt

/*
 * Canonical, framework-free model of the member's health records.
 *
 * Firestore stays the source of truth; these types are what every screen reads
 * (through HealthDataStore) so a reading is interpreted exactly one way
 * everywhere. Nothing here imports Android or Firebase, so it runs as plain
 * JVM unit tests. The Android layer converts Firestore Timestamps to
 * java.util.Date before calling in.
 *
 * Schemas (see docs/HEALTH_DATA_SCHEMA.md):
 *   every log: userId, profileId, source, measuredAt, createdAt, updatedAt
 *   weightLogs:     valueKg            (legacy: weightKg)
 *   sleepLogs:      sleepStartAt, sleepEndAt, durationMinutes, measuredAt = wake time
 *                   (legacy: sleepTime/wakeTime as "HH:mm" text or Dates, duration/durationHours in hours)
 *   bpReadings:     systolic, diastolic, pulse?, context?
 *   glucoseReadings value, unit, readingType (fasting|post_meal|random|hba1c|device)
 *   walkLogs:       minutes | steps, activityType?, startTime/endTime for imports
 */

/** Where a record came from. Persisted as [wire], never as display text. */
enum class HealthSource(val wire: String) {
    MANUAL("manual"),
    HEALTH_CONNECT("health_connect"),
    DEVICE("device"),
    ADMIN_CORRECTION("admin_correction"),
    MIGRATION("migration");

    /** Imported from another app/device: read-only here, corrected in the source app. */
    val isImported: Boolean get() = this == HEALTH_CONNECT || this == DEVICE

    companion object {
        /** Records written before `source` existed were all typed in by the member. */
        fun fromWire(raw: Any?): HealthSource = entries.firstOrNull { it.wire == raw } ?: MANUAL
    }
}

enum class HealthMetric { GLUCOSE, BP, WEIGHT, SLEEP, ACTIVITY, MEDICATION, LAB_REPORTS }

sealed interface HealthEntry {
    val id: String
    /** When the thing was measured/happened (sleep: when they woke up). Used for ordering and day bucketing. */
    val measuredAtMillis: Long
    /** Server-stamped creation time; null while a local write is still waiting for the server. Drives the edit window. */
    val createdAtMillis: Long?
    val source: HealthSource
}

enum class GlucoseKind { FASTING, POST_MEAL, RANDOM, HBA1C, DEVICE }

data class GlucoseEntry(
    override val id: String,
    /** mg/dL for every kind except [GlucoseKind.HBA1C], where it is a percentage. */
    val value: Double,
    val kind: GlucoseKind,
    override val measuredAtMillis: Long,
    override val createdAtMillis: Long?,
    override val source: HealthSource,
) : HealthEntry {
    val isHbA1c: Boolean get() = kind == GlucoseKind.HBA1C
}

data class BpEntry(
    override val id: String,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    val context: String?,
    override val measuredAtMillis: Long,
    override val createdAtMillis: Long?,
    override val source: HealthSource,
) : HealthEntry

data class WeightEntry(
    override val id: String,
    val valueKg: Double,
    override val measuredAtMillis: Long,
    override val createdAtMillis: Long?,
    override val source: HealthSource,
) : HealthEntry

data class SleepEntry(
    override val id: String,
    val startAtMillis: Long?,
    val endAtMillis: Long?,
    val durationMinutes: Int,
    /** True when the stored duration is impossible (e.g. the old identical-times = 24 hours bug): shown, never counted. */
    val isSuspect: Boolean,
    override val measuredAtMillis: Long,
    override val createdAtMillis: Long?,
    override val source: HealthSource,
) : HealthEntry

data class ActivityEntry(
    override val id: String,
    val minutes: Int?,
    /** Steps counted by a device (Health Connect). Manual "estimated" steps are deliberately not included. */
    val deviceSteps: Long?,
    val activityType: String?,
    override val measuredAtMillis: Long,
    override val createdAtMillis: Long?,
    override val source: HealthSource,
) : HealthEntry

data class MedicationEntry(
    override val id: String,
    val taken: Boolean,
    val name: String?,
    override val measuredAtMillis: Long,
    override val createdAtMillis: Long?,
    override val source: HealthSource,
) : HealthEntry

data class LabReportEntry(
    override val id: String,
    val title: String,
    val notes: String?,
    val fileUrl: String?,
    override val measuredAtMillis: Long,
    override val createdAtMillis: Long?,
    override val source: HealthSource,
) : HealthEntry

/** Sleep sanity bounds shared by parsing, the editor and the server-side backfill. */
object SleepLimits {
    const val MIN_MINUTES = 10
    const val MAX_MINUTES = 18 * 60
}

/** Tolerant readers for loosely-typed Firestore maps. */
object RecordValues {
    fun millis(v: Any?): Long? = when (v) {
        is Date -> v.time
        is Instant -> v.toEpochMilli()
        is Long -> v
        else -> null
    }
    fun double(v: Any?): Double? = (v as? Number)?.toDouble()?.takeIf { it.isFinite() }
    fun int(v: Any?): Int? = double(v)?.roundToInt()
    fun string(v: Any?): String? = (v as? String)?.trim()?.takeIf { it.isNotEmpty() }
}

object HealthParsers {
    private fun times(v: Map<String, Any?>): Pair<Long?, Long?> =
        RecordValues.millis(v["measuredAt"]) to RecordValues.millis(v["createdAt"])

    fun glucose(id: String, v: Map<String, Any?>): GlucoseEntry? {
        val (measured, created) = times(v)
        val at = measured ?: created ?: return null
        val value = RecordValues.double(v["value"])?.takeIf { it > 0 } ?: return null
        val unit = RecordValues.string(v["unit"])
        val kind = when (RecordValues.string(v["readingType"])) {
            "fasting" -> GlucoseKind.FASTING
            "post_meal" -> GlucoseKind.POST_MEAL
            "hba1c" -> GlucoseKind.HBA1C
            "device" -> GlucoseKind.DEVICE
            "random" -> GlucoseKind.RANDOM
            else -> if (unit == "%") GlucoseKind.HBA1C else GlucoseKind.RANDOM
        }
        return GlucoseEntry(id, value, kind, at, created, HealthSource.fromWire(v["source"]))
    }

    fun bp(id: String, v: Map<String, Any?>): BpEntry? {
        val (measured, created) = times(v)
        val at = measured ?: created ?: return null
        val sys = RecordValues.int(v["systolic"])?.takeIf { it > 0 } ?: return null
        val dia = RecordValues.int(v["diastolic"])?.takeIf { it > 0 } ?: return null
        return BpEntry(id, sys, dia, RecordValues.int(v["pulse"]), RecordValues.string(v["context"]), at, created, HealthSource.fromWire(v["source"]))
    }

    /** Canonical `valueKg`, falling back to the legacy Health Connect `weightKg`. */
    fun weight(id: String, v: Map<String, Any?>): WeightEntry? {
        val (measured, created) = times(v)
        val at = measured ?: created ?: return null
        val kg = (RecordValues.double(v["valueKg"]) ?: RecordValues.double(v["weightKg"]))?.takeIf { it > 0 } ?: return null
        return WeightEntry(id, kg, at, created, HealthSource.fromWire(v["source"]))
    }

    fun sleep(id: String, v: Map<String, Any?>, zone: ZoneId): SleepEntry? {
        val measuredRaw = RecordValues.millis(v["measuredAt"])
        val created = RecordValues.millis(v["createdAt"])
        var start = RecordValues.millis(v["sleepStartAt"])
        var end = RecordValues.millis(v["sleepEndAt"])
        // Legacy Health Connect: real Dates under the old names.
        if (start == null) start = RecordValues.millis(v["sleepTime"])
        if (end == null) end = RecordValues.millis(v["wakeTime"])
        val legacyMinutes = (RecordValues.double(v["duration"]) ?: RecordValues.double(v["durationHours"]))?.let { (it * 60).roundToInt() }
        var duration = RecordValues.int(v["durationMinutes"])
        if (duration == null) {
            duration = if (start != null && end != null) ((end - start) / 60_000L).toInt() else legacyMinutes
        }
        // Legacy manual entries: "HH:mm" text, where the log time is roughly when they woke.
        if (end == null) {
            val wakeText = v["wakeTime"] as? String
            val anchor = measuredRaw ?: created
            if (wakeText != null && anchor != null) end = LegacySleep.resolveWake(wakeText, anchor, zone)
        }
        if (start == null && end != null && duration != null) start = end - duration * 60_000L
        val at = end ?: measuredRaw ?: created ?: return null
        val minutes = duration ?: return null
        val suspect = minutes < SleepLimits.MIN_MINUTES || minutes > SleepLimits.MAX_MINUTES
        return SleepEntry(id, start, end, minutes, suspect, at, created, HealthSource.fromWire(v["source"]))
    }

    fun activity(id: String, v: Map<String, Any?>): ActivityEntry? {
        val measured = RecordValues.millis(v["measuredAt"])
        val created = RecordValues.millis(v["createdAt"])
        // Legacy Health Connect step records carried no measuredAt, only the interval they covered.
        val at = measured ?: RecordValues.millis(v["endTime"]) ?: created ?: return null
        val source = HealthSource.fromWire(v["source"])
        val steps = RecordValues.double(v["steps"])?.toLong()?.takeIf { it > 0 }
        val minutes = RecordValues.int(v["minutes"])?.takeIf { it > 0 }
        if (steps == null && minutes == null) return null
        return ActivityEntry(id, minutes, if (source.isImported || steps != null) steps else null, RecordValues.string(v["activityType"]), at, created, source)
    }

    fun medication(id: String, v: Map<String, Any?>): MedicationEntry? {
        val (measured, created) = times(v)
        val at = measured ?: created ?: return null
        val taken = v["taken"] as? Boolean ?: return null
        return MedicationEntry(id, taken, RecordValues.string(v["name"]), at, created, HealthSource.fromWire(v["source"]))
    }

    fun labReport(id: String, v: Map<String, Any?>): LabReportEntry? {
        val (measured, created) = times(v)
        val at = measured ?: created ?: return null
        val title = RecordValues.string(v["reportType"]) ?: RecordValues.string(v["labName"]) ?: "Lab report"
        return LabReportEntry(id, title, RecordValues.string(v["notes"]), RecordValues.string(v["fileUrl"]), at, created, HealthSource.fromWire(v["source"]))
    }
}

/** The one place that knows how old manual sleep entries (clock text, no dates) are interpreted. */
object LegacySleep {
    /** The most recent occurrence of `HH:mm` at or before [anchorMillis], in [zone]. */
    fun resolveWake(clockText: String, anchorMillis: Long, zone: ZoneId): Long? {
        val parts = clockText.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val minute = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        val anchor = ZonedDateTime.ofInstant(Instant.ofEpochMilli(anchorMillis), zone)
        var candidate = anchor.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (candidate.isAfter(anchor)) candidate = candidate.minusDays(1)
        return candidate.toInstant().toEpochMilli()
    }
}
