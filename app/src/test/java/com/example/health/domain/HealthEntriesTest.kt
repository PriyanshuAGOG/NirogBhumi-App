package com.nirogbhumi.app.health.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val IST: ZoneId = ZoneId.of("Asia/Kolkata")
private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, zone: ZoneId = IST): Long = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

class HealthEntriesTest {
    private val measured = at(2026, 9, 29, 8, 0)
    private val created = at(2026, 9, 29, 8, 1)
    private fun base(vararg extra: Pair<String, Any?>) = mapOf<String, Any?>("measuredAt" to Date(measured), "createdAt" to Date(created)) + mapOf(*extra)

    // ---- weight ----
    @Test fun `weight reads the canonical valueKg`() {
        val e = HealthParsers.weight("w", base("valueKg" to 70.2, "source" to "manual"))!!
        assertEquals(70.2, e.valueKg, 0.0001)
        assertEquals(HealthSource.MANUAL, e.source)
    }

    @Test fun `weight falls back to the legacy Health Connect weightKg`() {
        val e = HealthParsers.weight("w", base("weightKg" to 68.5, "source" to "health_connect"))!!
        assertEquals(68.5, e.valueKg, 0.0001)
        assertEquals(HealthSource.HEALTH_CONNECT, e.source)
    }

    @Test fun `valueKg wins when both fields exist`() {
        assertEquals(71.0, HealthParsers.weight("w", base("valueKg" to 71.0, "weightKg" to 60.0))!!.valueKg, 0.0001)
    }

    @Test fun `weight without a number or a time is dropped`() {
        assertNull(HealthParsers.weight("w", base()))
        assertNull(HealthParsers.weight("w", mapOf("valueKg" to 70.0)))
        assertNull(HealthParsers.weight("w", base("valueKg" to 0.0)))
    }

    // ---- source ----
    @Test fun `records without a source were typed in by the member`() {
        assertEquals(HealthSource.MANUAL, HealthSource.fromWire(null))
        assertEquals(HealthSource.MANUAL, HealthSource.fromWire("something new"))
        assertTrue(HealthSource.fromWire("health_connect").isImported)
        assertFalse(HealthSource.fromWire("admin_correction").isImported)
    }

    // ---- sleep ----
    @Test fun `canonical sleep uses start, end and durationMinutes`() {
        val start = at(2026, 9, 28, 22, 30); val end = at(2026, 9, 29, 6, 30)
        val e = HealthParsers.sleep("s", mapOf("sleepStartAt" to Date(start), "sleepEndAt" to Date(end), "durationMinutes" to 480, "measuredAt" to Date(end), "createdAt" to Date(created), "source" to "manual"), IST)!!
        assertEquals(480, e.durationMinutes)
        assertEquals(end, e.measuredAtMillis)
        assertEquals(start, e.startAtMillis)
        assertFalse(e.isSuspect)
    }

    @Test fun `legacy Health Connect sleep stored real Dates under sleepTime and wakeTime`() {
        val start = at(2026, 9, 28, 23, 0); val end = at(2026, 9, 29, 6, 45)
        val e = HealthParsers.sleep("s", mapOf("sleepTime" to Date(start), "wakeTime" to Date(end), "duration" to 7.75, "createdAt" to Date(created), "source" to "health_connect"), IST)!!
        assertEquals(465, e.durationMinutes)
        assertEquals(end, e.measuredAtMillis) // wake time, not the moment of the sync
        assertEquals(start, e.startAtMillis)
        assertTrue(e.source.isImported)
    }

    @Test fun `legacy manual sleep stored clock text and is resolved against the log time`() {
        val logged = at(2026, 9, 29, 7, 10)
        val e = HealthParsers.sleep("s", mapOf("sleepTime" to "22:30", "wakeTime" to "06:30", "duration" to 8.0, "measuredAt" to Date(logged), "createdAt" to Date(logged)), IST)!!
        assertEquals(at(2026, 9, 29, 6, 30), e.endAtMillis)
        assertEquals(at(2026, 9, 28, 22, 30), e.startAtMillis)
        assertEquals(480, e.durationMinutes)
        assertEquals(at(2026, 9, 29, 6, 30), e.measuredAtMillis)
    }

    @Test fun `legacy clock text later than the log time means the previous day`() {
        val logged = at(2026, 9, 29, 5, 0) // logged before the stated wake time
        val e = HealthParsers.sleep("s", mapOf("sleepTime" to "21:00", "wakeTime" to "06:30", "duration" to 9.5, "measuredAt" to Date(logged), "createdAt" to Date(logged)), IST)!!
        assertEquals(at(2026, 9, 28, 6, 30), e.endAtMillis)
    }

    @Test fun `the old identical-times bug leaves a 24 hour entry that is flagged, not counted`() {
        val logged = at(2026, 9, 29, 7, 0)
        val e = HealthParsers.sleep("s", mapOf("sleepTime" to "22:30", "wakeTime" to "22:30", "duration" to 24.0, "measuredAt" to Date(logged), "createdAt" to Date(logged)), IST)!!
        assertTrue(e.isSuspect)
    }

    @Test fun `durationHours is also understood`() {
        val e = HealthParsers.sleep("s", base("durationHours" to 6.5), IST)!!
        assertEquals(390, e.durationMinutes)
    }

    @Test fun `sleep with no duration at all is dropped`() {
        assertNull(HealthParsers.sleep("s", base(), IST))
    }

    // ---- glucose ----
    @Test fun `glucose kinds and HbA1c are told apart`() {
        assertEquals(GlucoseKind.FASTING, HealthParsers.glucose("g", base("value" to 98, "readingType" to "fasting"))!!.kind)
        assertEquals(GlucoseKind.POST_MEAL, HealthParsers.glucose("g", base("value" to 150, "readingType" to "post_meal"))!!.kind)
        assertEquals(GlucoseKind.DEVICE, HealthParsers.glucose("g", base("value" to 120, "readingType" to "device", "source" to "health_connect"))!!.kind)
        val a1c = HealthParsers.glucose("g", base("value" to 6.4, "unit" to "%", "readingType" to "hba1c"))!!
        assertTrue(a1c.isHbA1c)
        assertEquals(GlucoseKind.HBA1C, HealthParsers.glucose("g", base("value" to 6.4, "unit" to "%"))!!.kind)
    }

    @Test fun `glucose thresholds are shared by every screen`() {
        fun s(v: Double, kind: GlucoseKind = GlucoseKind.FASTING) = GlucoseEntry("g", v, kind, measured, created, HealthSource.MANUAL).status()
        assertEquals(GlucoseStatus.LOW, s(69.0)); assertEquals(GlucoseStatus.NORMAL, s(70.0))
        assertEquals(GlucoseStatus.NORMAL, s(130.0)); assertEquals(GlucoseStatus.HIGH, s(131.0))
        // after a meal the high line is 180, not 130
        assertEquals(GlucoseStatus.NORMAL, s(150.0, GlucoseKind.POST_MEAL)); assertEquals(GlucoseStatus.NORMAL, s(180.0, GlucoseKind.POST_MEAL)); assertEquals(GlucoseStatus.HIGH, s(181.0, GlucoseKind.POST_MEAL))
        // HbA1c is a percentage on another scale and is never classified as mg/dL
        assertEquals(GlucoseStatus.NORMAL, s(6.5, GlucoseKind.HBA1C))
        assertEquals(true, GlucoseRanges.isCritical(53.0)); assertEquals(false, GlucoseRanges.isCritical(54.0)); assertEquals(true, GlucoseRanges.isCritical(300.0)); assertEquals(false, GlucoseRanges.isCritical(299.0))
    }

    // ---- bp / activity / medication ----
    @Test fun `blood pressure keeps clinical field names`() {
        val e = HealthParsers.bp("b", base("systolic" to 122, "diastolic" to 80, "pulse" to 72))!!
        assertEquals(122, e.systolic); assertEquals(80, e.diastolic); assertEquals(72, e.pulse)
        assertNull(HealthParsers.bp("b", base("systolic" to 122)))
    }

    @Test fun `legacy step records use their end time, and manual minutes never become device steps`() {
        val end = at(2026, 9, 28, 19, 0)
        val hc = HealthParsers.activity("a", mapOf("steps" to 4200L, "startTime" to Date(end - 3_600_000), "endTime" to Date(end), "source" to "health_connect", "createdAt" to Date(created)))!!
        assertEquals(end, hc.measuredAtMillis) // not the sync time
        assertEquals(4200L, hc.deviceSteps)
        val manual = HealthParsers.activity("a", base("minutes" to 25, "activityType" to "walk", "estimatedSteps" to 2500))!!
        assertNull(manual.deviceSteps)
        assertEquals(25, manual.minutes)
        assertNull(HealthParsers.activity("a", base()))
    }

    @Test fun `medication needs a taken flag`() {
        assertNotNull(HealthParsers.medication("m", base("taken" to true, "name" to "Metformin")))
        assertNull(HealthParsers.medication("m", base("name" to "x")))
    }

    @Test fun `a record with no usable time is dropped rather than shown with an invented one`() {
        assertNull(HealthParsers.glucose("g", mapOf("value" to 100, "readingType" to "fasting")))
    }

    @Test fun `legacy wake text resolver rejects nonsense`() {
        assertNull(LegacySleep.resolveWake("25:00", measured, IST))
        assertNull(LegacySleep.resolveWake("6.30", measured, IST))
        assertNull(LegacySleep.resolveWake("ab:cd", measured, IST))
    }
}
