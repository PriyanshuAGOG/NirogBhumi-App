package com.nirogbhumi.app.health.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Placeholder the repository swaps for Firestore's server timestamp. Keeps this file free of
 * Firebase while still letting payloads say "the server's clock, not the phone's".
 */
data object ServerTime

/**
 * The only place that decides what a health record looks like when it is written. Screens and the
 * Health Connect importer call these instead of hand-building maps, so the stored shape cannot drift
 * between "typed in" and "imported" again (the cause of the weightKg/valueKg and sleep-format mismatches).
 *
 * `userId`, `profileId` and `createdAt` are added by the repository for manual entries.
 */
object HealthPayloads {
    private const val MANUAL = "manual"
    private const val HEALTH_CONNECT = "health_connect"

    fun glucose(mgDl: Int, kind: GlucoseKind): Map<String, Any?> {
        require(kind != GlucoseKind.HBA1C && kind != GlucoseKind.DEVICE) { "use hba1c() / an importer" }
        return mapOf("value" to mgDl, "unit" to "mg/dL", "readingType" to wire(kind), "measuredAt" to ServerTime, "source" to MANUAL)
    }

    fun hba1c(percent: Double): Map<String, Any?> =
        mapOf("value" to percent, "unit" to "%", "readingType" to "hba1c", "measuredAt" to ServerTime, "source" to MANUAL)

    fun bp(systolic: Int, diastolic: Int, pulse: Int? = null, context: String? = null): Map<String, Any?> = buildMap {
        put("systolic", systolic); put("diastolic", diastolic)
        if (pulse != null) put("pulse", pulse)
        if (!context.isNullOrBlank()) put("context", context.trim().take(40))
        put("measuredAt", ServerTime); put("source", MANUAL)
    }

    fun weight(kg: Double): Map<String, Any?> = mapOf("valueKg" to kg, "measuredAt" to ServerTime, "source" to MANUAL)

    /** Sleep is always stored as real instants plus the minutes between them; `measuredAt` is the wake-up time. */
    fun sleep(startAt: Instant, endAt: Instant, minutes: Int): Map<String, Any?> = mapOf(
        "sleepStartAt" to Date.from(startAt), "sleepEndAt" to Date.from(endAt), "durationMinutes" to minutes,
        "measuredAt" to Date.from(endAt), "source" to MANUAL,
    )

    fun walk(minutes: Int, activityType: String, seconds: Int? = null, estimatedSteps: Int? = null, mealRelation: String? = null): Map<String, Any?> = buildMap {
        put("minutes", minutes); put("activityType", activityType.lowercase())
        if (seconds != null) put("seconds", seconds)
        if (estimatedSteps != null && estimatedSteps > 0) put("estimatedSteps", estimatedSteps)
        if (mealRelation != null) put("mealRelation", mealRelation)
        put("measuredAt", ServerTime); put("source", MANUAL)
    }

    fun medication(taken: Boolean, name: String?): Map<String, Any?> =
        mapOf("taken" to taken, "name" to name?.trim()?.take(80)?.ifBlank { null }, "measuredAt" to ServerTime, "source" to MANUAL)

    private fun wire(kind: GlucoseKind) = when (kind) {
        GlucoseKind.FASTING -> "fasting"
        GlucoseKind.POST_MEAL -> "post_meal"
        GlucoseKind.RANDOM -> "random"
        GlucoseKind.HBA1C -> "hba1c"
        GlucoseKind.DEVICE -> "device"
    }

    // ---- Health Connect imports ----
    // `createdAt` is the provider's own time so it stays identical on every re-sync (the importer
    // used to re-stamp it each run, which pushed old imports to the front of every bounded query).
    // `importedAt` is the server time of the latest sync. Imported docs have deterministic ids.

    private fun imported(providerRecordId: String, at: Instant, extra: Map<String, Any?>): Map<String, Any?> =
        extra + mapOf("source" to HEALTH_CONNECT, "providerRecordId" to providerRecordId, "createdAt" to Date.from(at), "importedAt" to ServerTime)

    fun importedWeight(providerId: String, kg: Double, at: Instant) =
        imported(providerId, at, mapOf("valueKg" to kg, "measuredAt" to Date.from(at)))

    fun importedGlucose(providerId: String, mgDl: Double, at: Instant) =
        imported(providerId, at, mapOf("value" to mgDl, "unit" to "mg/dL", "readingType" to "device", "measuredAt" to Date.from(at)))

    fun importedBp(providerId: String, systolic: Double, diastolic: Double, at: Instant) =
        imported(providerId, at, mapOf("systolic" to systolic.toInt(), "diastolic" to diastolic.toInt(), "measuredAt" to Date.from(at)))

    fun importedSleep(providerId: String, start: Instant, end: Instant): Map<String, Any?> {
        val minutes = ((end.epochSecond - start.epochSecond) / 60).toInt()
        return imported(providerId, end, mapOf(
            "sleepStartAt" to Date.from(start), "sleepEndAt" to Date.from(end), "durationMinutes" to minutes, "measuredAt" to Date.from(end),
        ))
    }

    /** One document per local day instead of one per step interval: a month used to be thousands of writes. */
    fun importedStepsDay(day: DailySteps): Map<String, Any?> = mapOf(
        "steps" to day.steps, "granularity" to "day", "dayKey" to day.date.toString(),
        "startTime" to Date.from(day.firstStart), "endTime" to Date.from(day.lastEnd), "measuredAt" to Date.from(day.lastEnd),
        // stable for the whole day, so later syncs update the same document without touching createdAt
        "createdAt" to Date.from(day.dayStart), "source" to HEALTH_CONNECT, "providerRecordId" to "daily:${day.date}", "importedAt" to ServerTime,
    )

    fun importedStepsDocId(day: DailySteps) = "steps_day_${day.date}"
}

data class StepInterval(val start: Instant, val end: Instant, val count: Long)

data class DailySteps(val date: LocalDate, val steps: Long, val firstStart: Instant, val lastEnd: Instant, val dayStart: Instant)

object StepAggregation {
    /** Groups step intervals by the local day they started on and totals them. */
    fun perDay(intervals: List<StepInterval>, zone: ZoneId): List<DailySteps> = intervals
        .filter { it.count > 0 }
        .groupBy { it.start.atZone(zone).toLocalDate() }
        .toSortedMap()
        .map { (date, list) ->
            DailySteps(date, list.sumOf { it.count }, list.minOf { it.start }, list.maxOf { it.end }, date.atStartOfDay(zone).toInstant())
        }
}

/**
 * What a member may change when correcting a reading. Everything else (userId, profileId, source,
 * createdAt, providerRecordId) is protected: dropped here, and rejected by Firestore rules if a
 * modified client sends it anyway. Keep these lists identical to the rules' `correctableKeys()`.
 */
object Corrections {
    val editableKeys: Map<String, Set<String>> = mapOf(
        "glucoseReadings" to setOf("value", "unit", "readingType", "measuredAt"),
        "bpReadings" to setOf("systolic", "diastolic", "pulse", "context", "measuredAt"),
        "weightLogs" to setOf("valueKg", "measuredAt"),
        "sleepLogs" to setOf("sleepStartAt", "sleepEndAt", "durationMinutes", "measuredAt"),
        "walkLogs" to setOf("minutes", "seconds", "activityType", "estimatedSteps", "mealRelation", "measuredAt"),
        "medicationLogs" to setOf("taken", "name", "measuredAt"),
    )

    /** Bookkeeping the repository adds itself on every correction. */
    val systemKeys: Set<String> = setOf("updatedAt", "lastCorrectedAt", "correctionCount")

    fun isCorrectable(collection: String) = collection in editableKeys

    /** Only fields the member may change. `measuredAt` stays untouched unless the member changed the time. */
    fun patch(collection: String, edits: Map<String, Any?>): Map<String, Any?> {
        val allowed = editableKeys[collection] ?: return emptyMap()
        return edits.filterKeys { it in allowed }.filterValues { it != ServerTime }
    }
}
