package com.nirogbhumi.app.health.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoctorReportTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    private fun ago(days: Long, hour: Int = 8) = ZonedDateTime.of(2026, 7, 15, hour, 0, 0, 0, zone).minusDays(days).toInstant().toEpochMilli()

    private fun glucose(id: String, v: Double, kind: GlucoseKind, at: Long) = GlucoseEntry(id, v, kind, at, at, HealthSource.MANUAL)
    private fun bp(id: String, s: Int, d: Int, at: Long) = BpEntry(id, s, d, null, null, at, at, HealthSource.MANUAL)
    private fun weight(id: String, kg: Double, at: Long) = WeightEntry(id, kg, at, at, HealthSource.MANUAL)

    private fun state(g: List<GlucoseEntry> = emptyList(), b: List<BpEntry> = emptyList(), w: List<WeightEntry> = emptyList()) =
        HealthStateBuilder.build(HealthInputs(glucose = g, bp = b, weight = w), now, zone)

    @Test fun nothingLoggedSaysNothing() {
        val r = DoctorReport.build(state(), 7)
        assertEquals(0, r.loggedDays)
        assertNull(r.sugarSummary); assertNull(r.bpSummary); assertNull(r.weightSummary); assertNull(r.sleepSummary)
    }

    @Test fun hba1cIsNeverAveragedWithSugar() {
        val s = state(g = listOf(glucose("a", 100.0, GlucoseKind.FASTING, ago(1)), glucose("b", 6.5, GlucoseKind.HBA1C, ago(1))))
        val r = DoctorReport.build(s, 7)
        assertTrue(r.sugarSummary, r.sugarSummary!!.contains("average 100 mg/dL across 1 reading"))
    }

    @Test fun periodWindowUsesCalendarDays() {
        val s = state(g = listOf(glucose("in", 110.0, GlucoseKind.FASTING, ago(6, 0)), glucose("out", 300.0, GlucoseKind.FASTING, ago(7, 23))))
        val r = DoctorReport.build(s, 7)
        assertTrue(r.sugarSummary, r.sugarSummary!!.contains("across 1 reading"))
        assertEquals(1, r.loggedDays)
    }

    @Test fun weightChangeNeedsTwoReadings() {
        assertNull(DoctorReport.build(state(w = listOf(weight("w1", 70.0, ago(1)))), 30).weightSummary)
        val two = DoctorReport.build(state(w = listOf(weight("w1", 72.0, ago(20)), weight("w2", 70.5, ago(1)))), 30)
        assertEquals("72.0 kg to 70.5 kg (-1.5 kg)", two.weightSummary)
    }

    @Test fun bpFlagsHigherReadings() {
        val r = DoctorReport.build(state(b = listOf(bp("a", 120, 80, ago(2)), bp("b", 150, 95, ago(1)))), 7)
        assertTrue(r.bpSummary, r.bpSummary!!.contains("2 readings"))
        assertTrue(r.bpSummary, r.bpSummary!!.contains("1 on the higher side"))
    }

    @Test fun loggedDaysCountsDistinctDaysAcrossMetrics() {
        val r = DoctorReport.build(state(g = listOf(glucose("a", 100.0, GlucoseKind.FASTING, ago(1))), b = listOf(bp("b", 120, 80, ago(1, 9))), w = listOf(weight("w", 70.0, ago(2)))), 7)
        assertEquals(2, r.loggedDays)
    }

    @Test fun checkInDatesFollowTheOneDefinition() {
        val manual = HealthSource.MANUAL
        val imported = HealthSource.HEALTH_CONNECT
        val s = HealthStateBuilder.build(HealthInputs(
            glucose = listOf(
                GlucoseEntry("g1", 100.0, GlucoseKind.FASTING, ago(1), ago(1), manual),
                GlucoseEntry("g2", 6.5, GlucoseKind.HBA1C, ago(2), ago(2), manual),      // lab value: not a check-in
                GlucoseEntry("g3", 110.0, GlucoseKind.DEVICE, ago(3), ago(3), imported), // passive import: not a check-in
            ),
            weight = listOf(WeightEntry("w1", 70.0, ago(4), ago(4), manual)),
        ), now, zone)
        val days = HealthStateBuilder.checkInDates(s)
        assertEquals(setOf(java.time.LocalDate.of(2026, 7, 14), java.time.LocalDate.of(2026, 7, 11)), days)
    }
}
