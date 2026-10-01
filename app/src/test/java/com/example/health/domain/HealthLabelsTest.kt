package com.nirogbhumi.app.health.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthLabelsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun t(y: Int, mo: Int, d: Int, h: Int, m: Int) = ZonedDateTime.of(y, mo, d, h, m, 0, 0, zone).toInstant().toEpochMilli()
    private val now = t(2026, 9, 29, 18, 0)

    @Test fun `days are named the way people say them`() {
        assertEquals("Today", HealthLabels.day(t(2026, 9, 29, 0, 5), now, zone))
        assertEquals("Yesterday", HealthLabels.day(t(2026, 9, 28, 23, 59), now, zone))
        assertEquals("27 Sep", HealthLabels.day(t(2026, 9, 27, 10, 0), now, zone))
        assertEquals("3 Jan 2026".replace("2026", "2025"), HealthLabels.day(t(2025, 1, 3, 10, 0), now, zone))
    }

    @Test fun `date and time always carry AM or PM`() {
        assertEquals("Today, 8:10 AM", HealthLabels.dayAndTime(t(2026, 9, 29, 8, 10), now, zone))
        assertEquals("Yesterday, 10:30 PM", HealthLabels.dayAndTime(t(2026, 9, 28, 22, 30), now, zone))
    }

    @Test fun `readings read the same everywhere`() {
        assertEquals("Fasting", HealthLabels.glucoseKind(GlucoseKind.FASTING))
        assertEquals("After a meal", HealthLabels.glucoseKind(GlucoseKind.POST_MEAL))
        assertEquals("High", HealthLabels.glucoseStatus(GlucoseStatus.HIGH))
        val g = GlucoseEntry("g", 98.0, GlucoseKind.FASTING, 0, 0, HealthSource.MANUAL)
        assertEquals("98 mg/dL", HealthLabels.glucoseValue(g))
        assertEquals("6.4%", HealthLabels.glucoseValue(GlucoseEntry("a", 6.4, GlucoseKind.HBA1C, 0, 0, HealthSource.MANUAL)))
        assertEquals("122/80", HealthLabels.bp(BpEntry("b", 122, 80, null, null, 0, 0, HealthSource.MANUAL)))
        assertEquals("70.2 kg", HealthLabels.weight(70.2))
    }

    @Test fun `sleep shows its hours with AM or PM`() {
        val e = SleepEntry("s", t(2026, 9, 28, 22, 30), t(2026, 9, 29, 6, 30), 480, false, t(2026, 9, 29, 6, 30), 0, HealthSource.MANUAL)
        assertEquals("10:30 PM – 6:30 AM", HealthLabels.sleepRange(e, zone))
        assertNull(HealthLabels.sleepRange(e.copy(startAtMillis = null), zone))
    }

    @Test fun `activity prefers device steps and names manual activities`() {
        assertEquals("4,200 steps", HealthLabels.activity(ActivityEntry("a", null, 4200, null, 0, 0, HealthSource.HEALTH_CONNECT)))
        assertEquals("25 min · Walk", HealthLabels.activity(ActivityEntry("a", 25, null, "walk", 0, 0, HealthSource.MANUAL)))
    }

    @Test fun `only imported or corrected entries mention where they came from`() {
        assertNull(HealthLabels.source(HealthSource.MANUAL))
        assertEquals("From Health Connect", HealthLabels.source(HealthSource.HEALTH_CONNECT))
    }
}
