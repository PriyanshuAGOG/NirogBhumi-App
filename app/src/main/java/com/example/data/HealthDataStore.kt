package com.nirogbhumi.app.data

import com.nirogbhumi.app.health.domain.GlucoseKind
import com.nirogbhumi.app.health.domain.HealthInputs
import com.nirogbhumi.app.health.domain.HealthMetric
import com.nirogbhumi.app.health.domain.HealthParsers
import com.nirogbhumi.app.health.domain.HealthPayloads
import com.nirogbhumi.app.health.domain.HealthStateBuilder
import com.nirogbhumi.app.health.domain.HealthUiState
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one place the app reads and writes the signed-in member's health records.
 *
 * It keeps a single, bounded Firestore listener per collection and publishes one immutable
 * [HealthUiState]; Today, Track, the metric screens, Insights, Trends, reports and the Health File
 * all read [ui]. Firestore stays the source of truth: after a save (or correction) the listener
 * delivers the new document and every screen changes together, with no per-screen variables to
 * forget to update. A write that has not reached the server yet still appears at once (Firestore
 * returns it from the local cache), and is replaced by the server's version when it lands.
 *
 * Plain class (no Android types) so tests can drive it with a small fake [HealthLogBackend].
 */
class HealthDataStore(
    private val repository: HealthLogBackend,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private data class Source(val metric: HealthMetric, val collection: String, val limit: Long)

    // Bounded windows: enough for the 90-day charts without downloading a member's whole history.
    private val sources = listOf(
        Source(HealthMetric.GLUCOSE, "glucoseReadings", 300),
        Source(HealthMetric.BP, "bpReadings", 150),
        Source(HealthMetric.WEIGHT, "weightLogs", 150),
        Source(HealthMetric.SLEEP, "sleepLogs", 150),
        Source(HealthMetric.ACTIVITY, "walkLogs", 250),
        Source(HealthMetric.MEDICATION, "medicationLogs", 120),
        Source(HealthMetric.LAB_REPORTS, "labReports", 50),
    )

    private val lock = Any()
    private var subscriptions: List<CloudSubscription> = emptyList()
    private var boundUid: String? = null
    private var inputs = HealthInputs(isLoading = true)
    private val loaded = mutableSetOf<HealthMetric>()
    private val fromCache = mutableSetOf<HealthMetric>()
    private val errors = mutableMapOf<HealthMetric, String>()

    private val _ui = MutableStateFlow(HealthUiState(isLoading = true, zone = zone()))
    val ui: StateFlow<HealthUiState> = _ui.asStateFlow()

    /** Starts listening for the signed-in member. Safe to call repeatedly; re-binds when the account changes. */
    fun start() {
        val uid = repository.userId
        synchronized(lock) {
            if (uid == null) { stopLocked(); _ui.value = HealthUiState(isLoading = false, zone = zone(), generatedAtMillis = clock()); return }
            if (uid == boundUid && subscriptions.isNotEmpty()) return
            stopLocked()
            boundUid = uid
            sources.forEach { listen(it, uid) }
        }
    }

    fun stop() = synchronized(lock) { stopLocked(); _ui.value = HealthUiState(isLoading = true, zone = zone()) }

    /** Re-derives "today" and the windows from the clock without new data (midnight rollover, app resumed). */
    fun recompute() = synchronized(lock) { if (boundUid != null) publishLocked() }

    private fun stopLocked() {
        subscriptions.forEach { runCatching { it.cancel() } }
        subscriptions = emptyList(); boundUid = null
        inputs = HealthInputs(isLoading = true); loaded.clear(); fromCache.clear(); errors.clear()
    }

    private fun listen(source: Source, uid: String) {
        val sub = repository.listenHealthSnapshot(source.collection, source.limit) { result ->
            synchronized(lock) {
                if (boundUid != uid) return@synchronized // a late callback from a previous account
                when (result) {
                    is CloudResult.Success -> {
                        val own = result.value.documents.filter { belongsToSignedInProfile(it.values, uid) }
                        inputs = assign(inputs, source.metric, own)
                        errors.remove(source.metric)
                        if (result.value.fromCache) fromCache += source.metric else fromCache -= source.metric
                    }
                    is CloudResult.Failure -> errors[source.metric] = "We couldn't load your ${label(source.metric)} just now."
                }
                loaded += source.metric
                publishLocked()
            }
        }
        subscriptions = subscriptions + sub
    }

    private fun publishLocked() {
        _ui.value = HealthStateBuilder.build(
            inputs.copy(isLoading = loaded.size < sources.size, isStale = fromCache.isNotEmpty() || errors.isNotEmpty(), errors = errors.toMap()),
            clock(), zone(),
        )
    }

    private fun assign(current: HealthInputs, metric: HealthMetric, docs: List<CloudDocument>): HealthInputs {
        val z = zone()
        return when (metric) {
            HealthMetric.GLUCOSE -> current.copy(glucose = docs.mapNotNull { HealthParsers.glucose(it.id, it.values) })
            HealthMetric.BP -> current.copy(bp = docs.mapNotNull { HealthParsers.bp(it.id, it.values) })
            HealthMetric.WEIGHT -> current.copy(weight = docs.mapNotNull { HealthParsers.weight(it.id, it.values) })
            HealthMetric.SLEEP -> current.copy(sleep = docs.mapNotNull { HealthParsers.sleep(it.id, it.values, z) })
            HealthMetric.ACTIVITY -> current.copy(activity = docs.mapNotNull { HealthParsers.activity(it.id, it.values) })
            HealthMetric.MEDICATION -> current.copy(medication = docs.mapNotNull { HealthParsers.medication(it.id, it.values) })
            HealthMetric.LAB_REPORTS -> current.copy(labReports = docs.mapNotNull { HealthParsers.labReport(it.id, it.values) })
        }
    }

    // ---- writes: one place, one payload shape per log type ----

    fun logGlucose(mgDl: Int, kind: GlucoseKind, done: (CloudResult<String>) -> Unit) = repository.addHealthLog("glucoseReadings", HealthPayloads.glucose(mgDl, kind), done)
    fun logHba1c(percent: Double, done: (CloudResult<String>) -> Unit) = repository.addHealthLog("glucoseReadings", HealthPayloads.hba1c(percent), done)
    fun logBp(systolic: Int, diastolic: Int, pulse: Int? = null, context: String? = null, done: (CloudResult<String>) -> Unit) =
        repository.addHealthLog("bpReadings", HealthPayloads.bp(systolic, diastolic, pulse, context), done)
    fun logWeight(kg: Double, done: (CloudResult<String>) -> Unit) = repository.addHealthLog("weightLogs", HealthPayloads.weight(kg), done)
    fun logSleep(start: Instant, end: Instant, minutes: Int, done: (CloudResult<String>) -> Unit) = repository.addHealthLog("sleepLogs", HealthPayloads.sleep(start, end, minutes), done)
    fun logWalk(minutes: Int, activityType: String, seconds: Int? = null, estimatedSteps: Int? = null, mealRelation: String? = null, done: (CloudResult<String>) -> Unit) =
        repository.addHealthLog("walkLogs", HealthPayloads.walk(minutes, activityType, seconds, estimatedSteps, mealRelation), done)
    fun logMedication(taken: Boolean, name: String?, done: (CloudResult<String>) -> Unit) = repository.addHealthLog("medicationLogs", HealthPayloads.medication(taken, name), done)

    /** Corrects a reading the member entered. The server, not this phone, decides whether the 60 minutes are still open. */
    fun correct(collection: String, documentId: String, edits: Map<String, Any?>, done: (CloudResult<Unit>) -> Unit) =
        repository.updateHealthLog(collection, documentId, edits, done)

    companion object {
        /** Only the signed-in person's own records: family-profile entries carry another profileId. */
        fun belongsToSignedInProfile(values: Map<String, Any?>, uid: String): Boolean {
            val profile = (values["profileId"] as? String)?.takeIf { it.isNotBlank() } ?: return true
            return profile == uid
        }

        fun label(metric: HealthMetric): String = when (metric) {
            HealthMetric.GLUCOSE -> "blood sugar"
            HealthMetric.BP -> "blood pressure"
            HealthMetric.WEIGHT -> "weight"
            HealthMetric.SLEEP -> "sleep"
            HealthMetric.ACTIVITY -> "walking and activity"
            HealthMetric.MEDICATION -> "medicine log"
            HealthMetric.LAB_REPORTS -> "lab reports"
        }
    }
}
