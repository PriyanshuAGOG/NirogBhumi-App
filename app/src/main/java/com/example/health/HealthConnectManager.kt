package com.nirogbhumi.app.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.google.firebase.firestore.FieldValue
import com.nirogbhumi.app.data.HealthRepository
import com.nirogbhumi.app.health.domain.HealthPayloads
import com.nirogbhumi.app.health.domain.StepAggregation
import com.nirogbhumi.app.health.domain.StepInterval
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class HealthSyncSummary(val steps: Int, val sleep: Int, val glucose: Int, val bloodPressure: Int, val weight: Int)

/** Today-only pre-fill summary shown in the Daily Check-in wizard (PRD v2's "2-minute" USP). */
data class TodaySyncSummary(val totalSteps: Int, val sleepHours: Int, val sleepMinutes: Int)

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

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

    suspend fun syncLastThirtyDays(): HealthSyncSummary {
        check(isAvailable) { "Health Connect is unavailable or needs an update" }
        val granted = client.permissionController.getGrantedPermissions()
        check(granted.isNotEmpty()) { "Choose at least one Health Connect category first" }
        val zone = ZoneId.systemDefault()
        val end = Instant.now()
        // Whole local days (today plus the 29 before it) so every day's step total is complete.
        val start = end.atZone(zone).toLocalDate().minusDays(29).atStartOfDay(zone).toInstant()
        val filter = TimeRangeFilter.between(start, end)
        val steps = if (HealthPermission.getReadPermission(StepsRecord::class) in granted) client.readRecords(ReadRecordsRequest<StepsRecord>(filter)).records else emptyList()
        val sleep = if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) client.readRecords(ReadRecordsRequest<SleepSessionRecord>(filter)).records else emptyList()
        val glucose = if (HealthPermission.getReadPermission(BloodGlucoseRecord::class) in granted) client.readRecords(ReadRecordsRequest<BloodGlucoseRecord>(filter)).records else emptyList()
        val bp = if (HealthPermission.getReadPermission(BloodPressureRecord::class) in granted) client.readRecords(ReadRecordsRequest<BloodPressureRecord>(filter)).records else emptyList()
        val weight = if (HealthPermission.getReadPermission(WeightRecord::class) in granted) client.readRecords(ReadRecordsRequest<WeightRecord>(filter)).records else emptyList()

        saveStepDays(steps, zone)
        sleep.forEach { saveSleep(it) }
        glucose.forEach { record -> repository.upsertUserRecord("glucoseReadings", id("glucose", record.metadata.id, record.time), HealthPayloads.importedGlucose(record.metadata.id, record.level.inMilligramsPerDeciliter, record.time)) }
        bp.forEach { record -> repository.upsertUserRecord("bpReadings", id("bp", record.metadata.id, record.time), HealthPayloads.importedBp(record.metadata.id, record.systolic.inMillimetersOfMercury, record.diastolic.inMillimetersOfMercury, record.time)) }
        weight.forEach { record -> repository.upsertUserRecord("weightLogs", id("weight", record.metadata.id, record.time), HealthPayloads.importedWeight(record.metadata.id, record.weight.inKilograms, record.time)) }

        repository.upsertUserRecord("deviceConnections", "health_connect", mapOf("provider" to "health_connect", "status" to "connected", "permissions" to permissions.associateWith { it in granted }, "lastSyncedAt" to FieldValue.serverTimestamp()))
        return HealthSyncSummary(steps.size, sleep.size, glucose.size, bp.size, weight.size)
    }

    /** Reads + saves TODAY's steps and sleep only - the fast pre-fill check the Daily Check-in wizard opens with. */
    suspend fun syncToday(): TodaySyncSummary? {
        if (!isAvailable) return null
        val granted = client.permissionController.getGrantedPermissions()
        if (granted.isEmpty()) return null
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        // The member's own midnight. (This used to be UTC midnight, which is 5:30 AM in India, so the
        // morning's steps were missing from "today".)
        val startOfDay = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val filter = TimeRangeFilter.between(startOfDay, now)

        var totalSteps = 0
        if (HealthPermission.getReadPermission(StepsRecord::class) in granted) {
            val steps = client.readRecords(ReadRecordsRequest<StepsRecord>(filter)).records
            totalSteps = steps.sumOf { it.count }.toInt()
            saveStepDays(steps, zone)
        }

        var sleepMinutesTotal = 0
        if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) {
            // Sleep sessions typically start the night before, so look back further
            // than midnight but only count minutes that fall within the last 18 hours.
            val sleepFilter = TimeRangeFilter.between(now.minus(18, ChronoUnit.HOURS), now)
            val sleep = client.readRecords(ReadRecordsRequest<SleepSessionRecord>(sleepFilter)).records
            sleepMinutesTotal = sleep.sumOf { (it.endTime.epochSecond - it.startTime.epochSecond) / 60 }.toInt()
            sleep.forEach { saveSleep(it) }
        }

        if (totalSteps == 0 && sleepMinutesTotal == 0) return null
        return TodaySyncSummary(totalSteps, sleepMinutesTotal / 60, sleepMinutesTotal % 60)
    }

    /** One document per local day: a month of step intervals used to be thousands of writes per sync. */
    private fun saveStepDays(records: List<StepsRecord>, zone: ZoneId) {
        StepAggregation.perDay(records.map { StepInterval(it.startTime, it.endTime, it.count) }, zone).forEach { day ->
            repository.upsertUserRecord("walkLogs", HealthPayloads.importedStepsDocId(day), HealthPayloads.importedStepsDay(day))
        }
    }

    private fun saveSleep(record: SleepSessionRecord) {
        repository.upsertUserRecord("sleepLogs", id("sleep", record.metadata.id, record.startTime), HealthPayloads.importedSleep(record.metadata.id, record.startTime, record.endTime))
    }

    private fun id(prefix: String, providerId: String, time: Instant): String = "${prefix}_${providerId.ifBlank { time.toEpochMilli().toString() }}"
}
