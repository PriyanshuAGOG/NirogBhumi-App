package com.nirogbhumi.app.data

import com.nirogbhumi.app.health.domain.GlucoseKind
import com.nirogbhumi.app.health.domain.HealthMetric
import com.nirogbhumi.app.health.domain.HealthSource
import com.nirogbhumi.app.health.domain.ServerTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeBackend(override var userId: String? = "u1") : HealthLogBackend {
    data class Listener(val collection: String, val limit: Long, val update: (CloudResult<HealthSnapshot>) -> Unit, var cancelled: Boolean = false)
    val listeners = mutableListOf<Listener>()
    val added = mutableListOf<Pair<String, Map<String, Any?>>>()
    val corrections = mutableListOf<Triple<String, String, Map<String, Any?>>>()

    override fun listenHealthSnapshot(collection: String, limit: Long, update: (CloudResult<HealthSnapshot>) -> Unit): CloudSubscription {
        val l = Listener(collection, limit, update); listeners += l
        return CloudSubscription { l.cancelled = true }
    }
    override fun addHealthLog(collection: String, values: Map<String, Any?>, done: (CloudResult<String>) -> Unit) { added += collection to values; done(CloudResult.Success("new-id")) }
    override fun updateHealthLog(collection: String, documentId: String, values: Map<String, Any?>, done: (CloudResult<Unit>) -> Unit) { corrections += Triple(collection, documentId, values); done(CloudResult.Success(Unit)) }

    fun live(collection: String) = listeners.last { it.collection == collection && !it.cancelled }
    fun emit(collection: String, docs: List<CloudDocument>, fromCache: Boolean = false) = live(collection).update(CloudResult.Success(HealthSnapshot(docs, fromCache, false)))
    fun emitAllEmpty() = listeners.filter { !it.cancelled }.forEach { it.update(CloudResult.Success(HealthSnapshot(emptyList(), false, false))) }
}

class HealthDataStoreTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private fun t(d: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 9, d, h, m, 0, 0, ist).toInstant().toEpochMilli()
    private var now = t(29, 18)
    private val backend = FakeBackend()
    private val store = HealthDataStore(backend, clock = { now }, zone = { ist })

    private fun weightDoc(id: String, kgField: String, kg: Double, at: Long, source: String = "manual", extra: Map<String, Any?> = emptyMap()) =
        CloudDocument(id, mapOf(kgField to kg, "measuredAt" to Date(at), "createdAt" to Date(at), "source" to source, "userId" to "u1") + extra)

    @Test fun `starting listens once per health collection with a bounded window`() {
        store.start()
        assertEquals(setOf("glucoseReadings", "bpReadings", "weightLogs", "sleepLogs", "walkLogs", "medicationLogs", "labReports"), backend.listeners.map { it.collection }.toSet())
        assertEquals(7, backend.listeners.size)
        assertTrue(backend.listeners.all { it.limit in 1..300 })
        store.start() // idempotent
        assertEquals(7, backend.listeners.size)
    }

    @Test fun `it is loading until every collection has reported once`() {
        store.start()
        assertTrue(store.ui.value.isLoading)
        backend.listeners.dropLast(1).forEach { it.update(CloudResult.Success(HealthSnapshot(emptyList(), false, false))) }
        assertTrue(store.ui.value.isLoading)
        backend.listeners.last().update(CloudResult.Success(HealthSnapshot(emptyList(), false, false)))
        assertFalse(store.ui.value.isLoading)
    }

    @Test fun `a saved reading reaches every consumer through the shared state, with no manual assignment`() {
        store.start(); backend.emitAllEmpty()
        assertNull(store.ui.value.latestWeight); assertFalse(store.ui.value.today.checkedIn)

        store.logWeight(70.4) { }
        assertEquals("weightLogs", backend.added.single().first)
        assertEquals(70.4, backend.added.single().second["valueKg"]); assertEquals(ServerTime, backend.added.single().second["measuredAt"])

        // Firestore's listener now delivers the new document (from the local cache first, then the server).
        backend.emit("weightLogs", listOf(weightDoc("new-id", "valueKg", 70.4, t(29, 17, 59))), fromCache = true)
        assertEquals(70.4, store.ui.value.latestWeight!!.valueKg, 0.0)
        assertTrue(store.ui.value.today.hasWeight); assertTrue(store.ui.value.today.checkedIn)
        assertTrue(store.ui.value.isStale) // not yet confirmed by the server
        backend.emit("weightLogs", listOf(weightDoc("new-id", "valueKg", 70.4, t(29, 17, 59))))
        assertFalse(store.ui.value.isStale)
    }

    @Test fun `a correction changes the value everywhere when the listener delivers it`() {
        store.start(); backend.emitAllEmpty()
        backend.emit("weightLogs", listOf(weightDoc("w1", "valueKg", 72.0, t(29, 17))))
        store.correct("weightLogs", "w1", mapOf("valueKg" to 70.2)) { }
        assertEquals(Triple("weightLogs", "w1", mapOf<String, Any?>("valueKg" to 70.2)), backend.corrections.single())
        backend.emit("weightLogs", listOf(weightDoc("w1", "valueKg", 70.2, t(29, 17))))
        assertEquals(70.2, store.ui.value.latestWeight!!.valueKg, 0.0)
        assertEquals(1, store.ui.value.weight.size)
    }

    @Test fun `manual valueKg, legacy weightKg and Health Connect weights live in one ordered history`() {
        store.start(); backend.emitAllEmpty()
        backend.emit("weightLogs", listOf(
            weightDoc("m", "valueKg", 70.0, t(27, 7)),
            weightDoc("legacy", "weightKg", 69.5, t(28, 7), source = "health_connect"),
            weightDoc("hc", "valueKg", 69.3, t(29, 6), source = "health_connect"),
        ))
        assertEquals(listOf("hc", "legacy", "m"), store.ui.value.weight.map { it.id })
        assertEquals(69.3, store.ui.value.latestWeight!!.valueKg, 0.0)
        assertEquals(HealthSource.HEALTH_CONNECT, store.ui.value.latestWeight!!.source)
    }

    @Test fun `another family profile's records are not mixed into the signed-in person's state`() {
        store.start(); backend.emitAllEmpty()
        backend.emit("weightLogs", listOf(weightDoc("me", "valueKg", 70.0, t(29, 7)), weightDoc("kid", "valueKg", 25.0, t(29, 8), extra = mapOf("profileId" to "kid-profile")), weightDoc("me2", "valueKg", 70.1, t(28, 8), extra = mapOf("profileId" to "u1"))))
        assertEquals(listOf("me", "me2"), store.ui.value.weight.map { it.id })
        assertTrue(HealthDataStore.belongsToSignedInProfile(mapOf("profileId" to ""), "u1"))
        assertFalse(HealthDataStore.belongsToSignedInProfile(mapOf("profileId" to "other"), "u1"))
    }

    @Test fun `one collection failing does not blank the others, and says so in plain words`() {
        store.start(); backend.emitAllEmpty()
        backend.emit("weightLogs", listOf(weightDoc("w", "valueKg", 70.0, t(29, 7))))
        backend.live("sleepLogs").update(CloudResult.Failure("permission-denied"))
        val s = store.ui.value
        assertEquals("We couldn't load your sleep just now.", s.errors[HealthMetric.SLEEP])
        assertEquals(70.0, s.latestWeight!!.valueKg, 0.0)
        assertTrue(s.isStale)
        backend.emit("sleepLogs", emptyList())
        assertTrue(store.ui.value.errors.isEmpty())
    }

    @Test fun `switching accounts cancels the old listeners and ignores their late callbacks`() {
        store.start(); backend.emitAllEmpty()
        val old = backend.live("weightLogs")
        backend.userId = "u2"
        store.start()
        assertTrue(old.cancelled)
        assertEquals(14, backend.listeners.size)
        old.update(CloudResult.Success(HealthSnapshot(listOf(weightDoc("ghost", "valueKg", 99.0, t(29, 7))), false, false)))
        backend.emitAllEmpty()
        assertNull(store.ui.value.latestWeight)
    }

    @Test fun `signed out means nothing is loading and nothing is shown`() {
        store.start(); backend.emitAllEmpty()
        backend.emit("weightLogs", listOf(weightDoc("w", "valueKg", 70.0, t(29, 7))))
        backend.userId = null
        store.start()
        assertFalse(store.ui.value.isLoading); assertFalse(store.ui.value.hasAnyReading)
        assertTrue(backend.listeners.all { it.cancelled })
    }

    @Test fun `stop cancels everything`() {
        store.start(); store.stop()
        assertTrue(backend.listeners.all { it.cancelled })
        assertTrue(store.ui.value.isLoading)
    }

    @Test fun `today rolls over at midnight without any new data`() {
        store.start(); backend.emitAllEmpty()
        backend.emit("weightLogs", listOf(weightDoc("w", "valueKg", 70.0, t(29, 20))))
        assertTrue(store.ui.value.today.checkedIn)
        now = t(30, 0, 5)
        store.recompute()
        assertFalse(store.ui.value.today.checkedIn)
        assertEquals(1, store.ui.value.week.weightCount)
    }

    @Test fun `every log type is written in its canonical shape`() {
        store.start()
        store.logGlucose(98, GlucoseKind.FASTING) { }
        store.logBp(122, 80, done = { })
        store.logMedication(true, "Metformin") { }
        store.logWalk(25, "Walk") { }
        store.logSleep(ZonedDateTime.of(2026, 9, 28, 22, 30, 0, 0, ist).toInstant(), ZonedDateTime.of(2026, 9, 29, 6, 30, 0, 0, ist).toInstant(), 480) { }
        assertEquals(listOf("glucoseReadings", "bpReadings", "medicationLogs", "walkLogs", "sleepLogs"), backend.added.map { it.first })
        val sleep = backend.added.last().second
        assertEquals(480, sleep["durationMinutes"]); assertFalse(sleep.containsKey("sleepTime"))
    }
}
