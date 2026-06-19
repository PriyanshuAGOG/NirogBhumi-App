package in.nirogbhumi.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.google.firebase.firestore.FieldValue
import in.nirogbhumi.app.data.HealthRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

data class HealthSyncSummary(val steps: Int, val sleep: Int, val glucose: Int, val bloodPressure: Int, val weight: Int)

class HealthConnectManager(private val context: Context, private val repository: HealthRepository) {
    companion object {
        val permissions = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(BloodGlucoseRecord::class),
            HealthPermission.getReadPermission(BloodPressureRecord::class)
        )
    }

    val isAvailable: Boolean get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    private val client: HealthConnectClient get() = HealthConnectClient.getOrCreate(context)

    suspend fun hasPermissions(): Boolean = isAvailable && client.permissionController.getGrantedPermissions().isNotEmpty()

    suspend fun syncLastThirtyDays(): HealthSyncSummary {
        check(isAvailable) { "Health Connect is unavailable or needs an update" }
        val granted = client.permissionController.getGrantedPermissions()
        check(granted.isNotEmpty()) { "Choose at least one Health Connect category first" }
        val end = Instant.now(); val filter = TimeRangeFilter.between(end.minus(30, ChronoUnit.DAYS), end)
        val steps = if (HealthPermission.getReadPermission(StepsRecord::class) in granted) client.readRecords(ReadRecordsRequest<StepsRecord>(filter)).records else emptyList()
        val sleep = if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) client.readRecords(ReadRecordsRequest<SleepSessionRecord>(filter)).records else emptyList()
        val glucose = if (HealthPermission.getReadPermission(BloodGlucoseRecord::class) in granted) client.readRecords(ReadRecordsRequest<BloodGlucoseRecord>(filter)).records else emptyList()
        val bp = if (HealthPermission.getReadPermission(BloodPressureRecord::class) in granted) client.readRecords(ReadRecordsRequest<BloodPressureRecord>(filter)).records else emptyList()
        val weight = if (HealthPermission.getReadPermission(WeightRecord::class) in granted) client.readRecords(ReadRecordsRequest<WeightRecord>(filter)).records else emptyList()

        steps.forEach { record -> repository.upsertUserRecord("walkLogs", id("steps", record.metadata.id, record.startTime), mapOf("steps" to record.count, "startTime" to Date.from(record.startTime), "endTime" to Date.from(record.endTime), "source" to "health_connect", "providerRecordId" to record.metadata.id)) }
        sleep.forEach { record -> repository.upsertUserRecord("sleepLogs", id("sleep", record.metadata.id, record.startTime), mapOf("sleepTime" to Date.from(record.startTime), "wakeTime" to Date.from(record.endTime), "duration" to (record.endTime.epochSecond - record.startTime.epochSecond) / 3600.0, "source" to "health_connect", "providerRecordId" to record.metadata.id)) }
        glucose.forEach { record -> repository.upsertUserRecord("glucoseReadings", id("glucose", record.metadata.id, record.time), mapOf("value" to record.level.inMilligramsPerDeciliter, "unit" to "mg/dL", "readingType" to "device", "measuredAt" to Date.from(record.time), "source" to "health_connect", "providerRecordId" to record.metadata.id)) }
        bp.forEach { record -> repository.upsertUserRecord("bpReadings", id("bp", record.metadata.id, record.time), mapOf("systolic" to record.systolic.inMillimetersOfMercury, "diastolic" to record.diastolic.inMillimetersOfMercury, "measuredAt" to Date.from(record.time), "source" to "health_connect", "providerRecordId" to record.metadata.id)) }
        weight.forEach { record -> repository.upsertUserRecord("weightLogs", id("weight", record.metadata.id, record.time), mapOf("weightKg" to record.weight.inKilograms, "measuredAt" to Date.from(record.time), "source" to "health_connect", "providerRecordId" to record.metadata.id)) }

        repository.upsertUserRecord("deviceConnections", "health_connect", mapOf("provider" to "health_connect", "status" to "connected", "permissions" to permissions.associateWith { it in granted }, "lastSyncedAt" to FieldValue.serverTimestamp()))
        return HealthSyncSummary(steps.size, sleep.size, glucose.size, bp.size, weight.size)
    }

    private fun id(prefix: String, providerId: String, time: Instant): String = "${prefix}_${providerId.ifBlank { time.toEpochMilli().toString() }}"
}
