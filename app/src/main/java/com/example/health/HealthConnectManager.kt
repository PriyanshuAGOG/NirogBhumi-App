package com.nirogbhumi.app.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.google.firebase.firestore.FieldValue
import com.nirogbhumi.app.data.HealthRepository
import com.nirogbhumi.app.health.domain.DailySteps
import com.nirogbhumi.app.health.domain.DailyStepTotal
import com.nirogbhumi.app.health.domain.HealthPayloads
import com.nirogbhumi.app.health.domain.HealthSources
import com.nirogbhumi.app.health.domain.ImportSanity
import java.time.Instant
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class HealthSyncSummary(val steps: Int, val sleep: Int, val glucose: Int, val bloodPressure: Int, val weight: Int)

/** Today-only pre-fill summary shown in the Daily Check-in wizard. Read-only: showing it never saves anything. */
data class TodaySyncSummary(val totalSteps: Int, val sleepHours: Int, val sleepMinutes: Int)

/**
 * Everything a sync WOULD add, worked out without saving anything, so the member can review it first.
 * Steps are Health Connect's own daily totals (it removes overlap between apps); the other records are
 * every page of what is stored, with overlapping sleep from two apps counted once and unbelievable values set aside.
 */
class SyncPreview(
    val stepDays: List<DailySteps>,
    val skippedStepDays: Int,
    val sleep: List<SleepSessionRecord>,
    val glucose: List<BloodGlucoseRecord>,
    val bloodPressure: List<BloodPressureRecord>,
    val weight: List<WeightRecord>,
    val skippedOther: Int,
    val sources: List<String>,
    internal val granted: Set<String>,
) {
    val totalSteps: Long get() = stepDays.sumOf { it.steps }
    val skippedTotal: Int get() = skippedStepDays + skippedOther
    val isEmpty: Boolean get() = stepDays.isEmpty() && sleep.isEmpty() && glucose.isEmpty() && bloodPressure.isEmpty() && weight.isEmpty()
}

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
private const val SYNC_DAYS = 30L
private const val MAX_RECORDS_PER_TYPE = 20_000

enum class HealthConnectStatus { AVAILABLE, NEEDS_INSTALL_OR_UPDATE, UNSUPPORTED }

class HealthConnectManager(private val context: Context, private val repository: HealthRepository) {
    companion object {
        val permissions = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(BloodGlucoseRecord::class),
            HealthPermission.getReadPermission(BloodPressureRecord::class)
        )

        /** Opens the Play Store listing so the user can install/update the Health Connect app. */
        fun openHealthConnectInstall(context: Context) {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE"))
                .setPackage("com.android.vending")
            runCatching { context.startActivity(marketIntent) }
                .onFailure {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE")))
                }
        }
    }

    val status: HealthConnectStatus
        get() = when (HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PACKAGE)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectStatus.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectStatus.NEEDS_INSTALL_OR_UPDATE
            else -> HealthConnectStatus.UNSUPPORTED
        }

    val isAvailable: Boolean get() = status == HealthConnectStatus.AVAILABLE
    private val client: HealthConnectClient get() = HealthConnectClient.getOrCreate(context)

    suspend fun hasPermissions(): Boolean = isAvailable && client.permissionController.getGrantedPermissions().isNotEmpty()

    /** Reads every page of one record type. (A single read returns at most one page, which used to cut a month of data short.) */
    private suspend inline fun <reified T : Record> readAll(filter: TimeRangeFilter): List<T> {
        val all = ArrayList<T>()
        var token: String? = null
        do {
            val page = client.readRecords(ReadRecordsRequest<T>(timeRangeFilter = filter, pageToken = token))
            all += page.records
            token = page.pageToken
        } while (!token.isNullOrEmpty() && all.size < MAX_RECORDS_PER_TYPE)
        return all
    }

    /** Works out what a 30-day sync would add. Saves nothing. */
    suspend fun preview(): SyncPreview {
        check(isAvailable) { "Health Connect is unavailable or needs an update" }
        val granted = client.permissionController.getGrantedPermissions()
        check(granted.isNotEmpty()) { "Choose at least one Health Connect category first" }
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val sources = LinkedHashSet<String>()
        var skippedOther = 0

        // Steps: Health Connect's own per-day totals, which already remove overlap between apps (a watch and a phone app
        // counting the same walk). Whole local days: today plus the 29 before it.
        var stepDays = emptyList<DailySteps>()
        var skippedStepDays = 0
        if (HealthPermission.getReadPermission(StepsRecord::class) in granted) {
            val firstDay = now.atZone(zone).toLocalDate().minusDays(SYNC_DAYS - 1)
            val groups = client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(firstDay.atStartOfDay(), LocalDateTime.now(zone)),
                    timeRangeSlicer = Period.ofDays(1),
                ),
            )
            val totals = groups.map { g ->
                g.result.dataOrigins.forEach { sources += it.packageName }
                DailyStepTotal(
                    date = g.startTime.toLocalDate(),
                    steps = g.result[StepsRecord.COUNT_TOTAL] ?: 0L,
                    dayStart = g.startTime.atZone(zone).toInstant(),
                    periodEnd = minOf(g.endTime.atZone(zone).toInstant(), now),
                )
            }
            val days = ImportSanity.stepDays(totals, zone)
            stepDays = days.accepted
            skippedStepDays = days.rejectedDates.size
        }

        val filter = TimeRangeFilter.between(now.minus(SYNC_DAYS, ChronoUnit.DAYS), now)

        var sleep = emptyList<SleepSessionRecord>()
        if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) {
            val all = readAll<SleepSessionRecord>(filter)
            all.forEach { sources += it.metadata.dataOrigin.packageName }
            val sane = all.filter { ImportSanity.sleepOk(it.startTime, it.endTime, now) }
            skippedOther += all.size - sane.size
            sleep = ImportSanity.withoutOverlappingSleep(sane, { it.startTime }, { it.endTime })
        }

        var glucose = emptyList<BloodGlucoseRecord>()
        if (HealthPermission.getReadPermission(BloodGlucoseRecord::class) in granted) {
            val all = readAll<BloodGlucoseRecord>(filter)
            all.forEach { sources += it.metadata.dataOrigin.packageName }
            val sane = all.filter { ImportSanity.glucoseOk(it.level.inMilligramsPerDeciliter) && ImportSanity.timeOk(it.time, now) }
            skippedOther += all.size - sane.size
            glucose = sane.distinctBy { it.time.truncatedTo(ChronoUnit.MINUTES) to Math.round(it.level.inMilligramsPerDeciliter) }
        }

        var bp = emptyList<BloodPressureRecord>()
        if (HealthPermission.getReadPermission(BloodPressureRecord::class) in granted) {
            val all = readAll<BloodPressureRecord>(filter)
            all.forEach { sources += it.metadata.dataOrigin.packageName }
            val sane = all.filter { ImportSanity.bpOk(it.systolic.inMillimetersOfMercury, it.diastolic.inMillimetersOfMercury) && ImportSanity.timeOk(it.time, now) }
            skippedOther += all.size - sane.size
            bp = sane.distinctBy { Triple(it.time.truncatedTo(ChronoUnit.MINUTES), Math.round(it.systolic.inMillimetersOfMercury), Math.round(it.diastolic.inMillimetersOfMercury)) }
        }

        var weight = emptyList<WeightRecord>()
        if (HealthPermission.getReadPermission(WeightRecord::class) in granted) {
            val all = readAll<WeightRecord>(filter)
            all.forEach { sources += it.metadata.dataOrigin.packageName }
            val sane = all.filter { ImportSanity.weightOk(it.weight.inKilograms) && ImportSanity.timeOk(it.time, now) }
            skippedOther += all.size - sane.size
            weight = sane.distinctBy { it.time.truncatedTo(ChronoUnit.MINUTES) to Math.round(it.weight.inKilograms * 10) }
        }

        return SyncPreview(stepDays, skippedStepDays, sleep, glucose, bp, weight, skippedOther, HealthSources.describe(sources), granted.toSet())
    }

    /** Saves exactly what was previewed (nothing else). Re-running is safe: ids are stable, so a record is updated, never duplicated. */
    fun commit(preview: SyncPreview): HealthSyncSummary {
        preview.stepDays.forEach { day -> repository.upsertUserRecord("walkLogs", HealthPayloads.importedStepsDocId(day), HealthPayloads.importedStepsDay(day)) }
        preview.sleep.forEach { saveSleep(it) }
        preview.glucose.forEach { r -> repository.upsertUserRecord("glucoseReadings", id("glucose", r.metadata.id, r.time), HealthPayloads.importedGlucose(r.metadata.id, r.level.inMilligramsPerDeciliter, r.time)) }
        preview.bloodPressure.forEach { r -> repository.upsertUserRecord("bpReadings", id("bp", r.metadata.id, r.time), HealthPayloads.importedBp(r.metadata.id, r.systolic.inMillimetersOfMercury, r.diastolic.inMillimetersOfMercury, r.time)) }
        preview.weight.forEach { r -> repository.upsertUserRecord("weightLogs", id("weight", r.metadata.id, r.time), HealthPayloads.importedWeight(r.metadata.id, r.weight.inKilograms, r.time)) }
        repository.upsertUserRecord("deviceConnections", "health_connect", mapOf("provider" to "health_connect", "status" to "connected", "permissions" to permissions.associateWith { it in preview.granted }, "lastSyncedAt" to FieldValue.serverTimestamp()))
        return HealthSyncSummary(preview.stepDays.size, preview.sleep.size, preview.glucose.size, preview.bloodPressure.size, preview.weight.size)
    }

    /**
     * Today's steps and last night's sleep for the Daily Check-in pre-fill. Read-only: opening the check-in must never add data.
     * Steps are Health Connect's de-duplicated total; an impossible total is ignored.
     */
    suspend fun syncToday(): TodaySyncSummary? {
        if (!isAvailable) return null
        val granted = client.permissionController.getGrantedPermissions()
        if (granted.isEmpty()) return null
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val startOfDay = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

        var totalSteps = 0L
        if (HealthPermission.getReadPermission(StepsRecord::class) in granted) {
            val aggregate = client.aggregate(AggregateRequest(metrics = setOf(StepsRecord.COUNT_TOTAL), timeRangeFilter = TimeRangeFilter.between(startOfDay, now)))
            totalSteps = (aggregate[StepsRecord.COUNT_TOTAL] ?: 0L).takeIf { ImportSanity.stepsOk(it) } ?: 0L
        }

        var sleepMinutesTotal = 0L
        if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) {
            // Sleep sessions typically start the night before, so look back further than midnight.
            val sessions = readAll<SleepSessionRecord>(TimeRangeFilter.between(now.minus(18, ChronoUnit.HOURS), now))
                .filter { ImportSanity.sleepOk(it.startTime, it.endTime, now) }
            sleepMinutesTotal = ImportSanity.withoutOverlappingSleep(sessions, { it.startTime }, { it.endTime })
                .sumOf { (it.endTime.epochSecond - it.startTime.epochSecond) / 60 }
        }

        if (totalSteps == 0L && sleepMinutesTotal == 0L) return null
        return TodaySyncSummary(totalSteps.toInt(), (sleepMinutesTotal / 60).toInt(), (sleepMinutesTotal % 60).toInt())
    }

    private fun saveSleep(record: SleepSessionRecord) {
        repository.upsertUserRecord("sleepLogs", id("sleep", record.metadata.id, record.startTime), HealthPayloads.importedSleep(record.metadata.id, record.startTime, record.endTime))
    }

    private fun id(prefix: String, providerId: String, time: Instant): String = "${prefix}_${providerId.ifBlank { time.toEpochMilli().toString() }}"
}
