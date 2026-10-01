package com.nirogbhumi.app.health.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthPayloadsTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private fun at(d: Int, h: Int, m: Int = 0): Instant = ZonedDateTime.of(2026, 9, d, h, m, 0, 0, ist).toInstant()

    // ---- manual payloads parse back to exactly what was entered ----
    private fun withTimes(map: Map<String, Any?>): Map<String, Any?> = map.mapValues { (_, v) -> if (v == ServerTime) Date(at(29, 8).toEpochMilli()) else v } + ("createdAt" to Date(at(29, 8).toEpochMilli()))

    @Test fun `weight is written as valueKg only`() {
        val p = HealthPayloads.weight(70.4)
        assertEquals(70.4, p["valueKg"]); assertFalse(p.containsKey("weightKg")); assertEquals("manual", p["source"])
        assertEquals(70.4, HealthParsers.weight("w", withTimes(p))!!.valueKg, 0.0)
    }

    @Test fun `manual sleep is timestamps and minutes, never clock text`() {
        val p = HealthPayloads.sleep(at(28, 22, 30), at(29, 6, 30), 480)
        assertTrue(p["sleepStartAt"] is Date); assertTrue(p["sleepEndAt"] is Date)
        assertEquals(480, p["durationMinutes"]); assertFalse(p.containsKey("sleepTime")); assertFalse(p.containsKey("wakeTime"))
        assertEquals(at(29, 6, 30).toEpochMilli(), (p["measuredAt"] as Date).time) // measuredAt is wake time
        val e = HealthParsers.sleep("s", p + ("createdAt" to Date(at(29, 7).toEpochMilli())), ist)!!
        assertEquals(480, e.durationMinutes); assertEquals(at(29, 6, 30).toEpochMilli(), e.measuredAtMillis)
    }

    @Test fun `glucose, bp, walk and medication parse back`() {
        assertEquals(GlucoseKind.FASTING, HealthParsers.glucose("g", withTimes(HealthPayloads.glucose(98, GlucoseKind.FASTING)))!!.kind)
        assertTrue(HealthParsers.glucose("g", withTimes(HealthPayloads.hba1c(6.4)))!!.isHbA1c)
        val bp = HealthParsers.bp("b", withTimes(HealthPayloads.bp(122, 80, 70, "after walk")))!!
        assertEquals(122, bp.systolic); assertEquals(80, bp.diastolic); assertEquals(70, bp.pulse); assertEquals("after walk", bp.context)
        val walk = HealthParsers.activity("a", withTimes(HealthPayloads.walk(25, "Walk", estimatedSteps = 2500)))!!
        assertEquals(25, walk.minutes); assertNull(walk.deviceSteps); assertEquals("walk", walk.activityType)
        assertEquals(true, HealthParsers.medication("m", withTimes(HealthPayloads.medication(true, "  Metformin  ")))!!.taken)
    }

    @Test fun `imported and device readings cannot be written as manual kinds`() {
        try { HealthPayloads.glucose(100, GlucoseKind.HBA1C); error("should reject") } catch (_: IllegalArgumentException) {}
        try { HealthPayloads.glucose(100, GlucoseKind.DEVICE); error("should reject") } catch (_: IllegalArgumentException) {}
    }

    // ---- imports ----
    @Test fun `imported weight uses the canonical valueKg and a stable createdAt`() {
        val p = HealthPayloads.importedWeight("rec-1", 69.8, at(28, 7))
        assertEquals(69.8, p["valueKg"]); assertFalse(p.containsKey("weightKg")); assertEquals("health_connect", p["source"])
        assertEquals(p["createdAt"], HealthPayloads.importedWeight("rec-1", 69.8, at(28, 7))["createdAt"]) // identical on re-sync
        assertEquals(at(28, 7).toEpochMilli(), (p["measuredAt"] as Date).time)
        assertEquals(ServerTime, p["importedAt"])
    }

    @Test fun `imported sleep is canonical and ordered by wake time`() {
        val p = HealthPayloads.importedSleep("s1", at(28, 23), at(29, 6, 45))
        assertEquals(465, p["durationMinutes"])
        val e = HealthParsers.sleep("s", p.mapValues { (_, v) -> if (v == ServerTime) Date(0) else v }, ist)!!
        assertEquals(at(29, 6, 45).toEpochMilli(), e.measuredAtMillis); assertTrue(e.source.isImported)
    }

    @Test fun `steps are totalled per local day with one stable document per day`() {
        val days = StepAggregation.perDay(listOf(
            StepInterval(at(28, 9), at(28, 10), 1200), StepInterval(at(28, 18), at(28, 19), 2300),
            StepInterval(at(29, 0, 5), at(29, 0, 40), 150), StepInterval(at(29, 8), at(29, 9), 4000), StepInterval(at(29, 12), at(29, 12), 0),
        ), ist)
        assertEquals(listOf(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 29)), days.map { it.date })
        assertEquals(listOf(3500L, 4150L), days.map { it.steps })
        val p = HealthPayloads.importedStepsDay(days[1])
        assertEquals("day", p["granularity"]); assertEquals("2026-09-29", p["dayKey"]); assertEquals("daily:2026-09-29", p["providerRecordId"])
        assertEquals(HealthPayloads.importedStepsDay(days[1])["createdAt"], p["createdAt"])
        assertEquals("steps_day_2026-09-29", HealthPayloads.importedStepsDocId(days[1]))
        val parsed = HealthParsers.activity("a", p.mapValues { (_, v) -> if (v == ServerTime) Date(0) else v })!!
        assertTrue(parsed.isDailyTotal); assertEquals(4150L, parsed.deviceSteps)
    }

    @Test fun `a daily total replaces legacy per-interval step documents for that day only`() {
        val zone = ist
        fun rec(id: String, d: Int, n: Long) = ActivityEntry(id, null, n, null, at(d, 10).toEpochMilli(), at(d, 10).toEpochMilli(), HealthSource.HEALTH_CONNECT)
        val total = ActivityEntry("day", null, 5000, null, at(29, 20).toEpochMilli(), at(29, 0).toEpochMilli(), HealthSource.HEALTH_CONNECT, isDailyTotal = true)
        val state = HealthStateBuilder.build(HealthInputs(activity = listOf(rec("old1", 29, 2000), rec("old2", 29, 2500), total, rec("yesterday", 28, 7000))), at(29, 21).toEpochMilli(), zone)
        assertEquals(5000L, state.today.stepsToday)              // not 9,500
        assertEquals(12000L, state.week.steps)                   // 5,000 + yesterday's 7,000
    }

    // ---- corrections ----
    @Test fun `a correction patch keeps only editable fields`() {
        val patch = Corrections.patch("weightLogs", mapOf("valueKg" to 70.0, "userId" to "attacker", "profileId" to "x", "source" to "health_connect", "createdAt" to Date(0), "providerRecordId" to "r", "measuredAt" to ServerTime))
        assertEquals(mapOf<String, Any?>("valueKg" to 70.0), patch)
    }

    @Test fun `correcting a value does not reset when it was measured`() {
        val patch = Corrections.patch("glucoseReadings", HealthPayloads.glucose(105, GlucoseKind.FASTING))
        assertEquals(setOf("value", "unit", "readingType"), patch.keys)
        assertEquals(setOf("value", "unit", "readingType", "measuredAt"), Corrections.editableKeys["glucoseReadings"])
    }

    @Test fun `changing the time of a reading is allowed when the member chose one`() {
        val patch = Corrections.patch("sleepLogs", HealthPayloads.sleep(at(28, 23), at(29, 6), 420))
        assertEquals(setOf("sleepStartAt", "sleepEndAt", "durationMinutes", "measuredAt"), patch.keys)
    }

    @Test fun `only the logs a member can type in are correctable`() {
        assertTrue(Corrections.isCorrectable("bpReadings")); assertFalse(Corrections.isCorrectable("labReports"))
        assertEquals(emptyMap<String, Any?>(), Corrections.patch("labReports", mapOf("notes" to "x")))
        listOf("userId", "profileId", "source", "createdAt", "providerRecordId").forEach { protected ->
            Corrections.editableKeys.values.forEach { keys -> assertFalse("$protected must never be correctable", protected in keys) }
        }
    }
}
