package com.nirogbhumi.app.health.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthStateTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun t(d: Int, h: Int, m: Int = 0, mo: Int = 9) = ZonedDateTime.of(2026, mo, d, h, m, 0, 0, zone).toInstant().toEpochMilli()
    private val now = t(29, 18)

    private fun glucose(id: String, v: Double, at: Long, kind: GlucoseKind = GlucoseKind.FASTING, source: HealthSource = HealthSource.MANUAL, created: Long? = at) =
        GlucoseEntry(id, v, kind, at, created, source)
    private fun bp(id: String, s: Int, d: Int, at: Long, source: HealthSource = HealthSource.MANUAL) = BpEntry(id, s, d, null, null, at, at, source)
    private fun weight(id: String, kg: Double, at: Long, source: HealthSource = HealthSource.MANUAL) = WeightEntry(id, kg, at, at, source)
    private fun sleep(id: String, end: Long, minutes: Int, suspect: Boolean = false) = SleepEntry(id, end - minutes * 60_000L, end, minutes, suspect, end, end, HealthSource.MANUAL)
    private fun steps(id: String, end: Long, n: Long) = ActivityEntry(id, null, n, null, end, end, HealthSource.HEALTH_CONNECT)
    private fun walk(id: String, at: Long, min: Int) = ActivityEntry(id, min, null, "walk", at, at, HealthSource.MANUAL)
    private fun build(input: HealthInputs) = HealthStateBuilder.build(input, now, zone)

    @Test fun `latest values are chosen by measuredAt, not by list order or createdAt`() {
        val older = glucose("old", 150.0, t(28, 8), created = t(29, 9)) // logged later but measured earlier
        val newer = glucose("new", 95.0, t(29, 7), created = t(29, 7))
        val s = build(HealthInputs(glucose = listOf(older, newer)))
        assertEquals("new", s.latestGlucose!!.id)
        assertEquals(listOf("new", "old"), s.glucose.map { it.id })
    }

    @Test fun `mixed manual and Health Connect weights are ordered together by measuredAt`() {
        val s = build(HealthInputs(weight = listOf(weight("a", 70.0, t(27, 7)), weight("b", 69.6, t(29, 6), HealthSource.HEALTH_CONNECT), weight("c", 70.2, t(28, 7)))))
        assertEquals("b", s.latestWeight!!.id)
        assertEquals(69.6, s.latestWeight!!.valueKg, 0.0001)
    }

    @Test fun `a write the server has not stamped yet is treated as the newest of its moment`() {
        val pending = glucose("pending", 101.0, t(29, 7), created = null)
        val stamped = glucose("stamped", 99.0, t(29, 7), created = t(29, 7))
        assertEquals("pending", build(HealthInputs(glucose = listOf(stamped, pending))).latestGlucose!!.id)
    }

    @Test fun `duplicate documents are shown once`() {
        val g = glucose("same", 100.0, t(29, 7))
        assertEquals(1, build(HealthInputs(glucose = listOf(g, g))).glucose.size)
    }

    @Test fun `HbA1c is never the blood sugar reading and never averaged with mg per dL`() {
        val a1c = glucose("a1c", 6.4, t(29, 9), GlucoseKind.HBA1C)
        val mg = glucose("mg", 100.0, t(29, 7))
        val s = build(HealthInputs(glucose = listOf(a1c, mg)))
        assertEquals("mg", s.latestGlucose!!.id)
        assertEquals("a1c", s.latestHbA1c!!.id)
        assertEquals(100.0, s.week.glucoseAverage!!, 0.0001)
        assertEquals(1, s.sugarReadings.size)
    }

    // ---- today / "checked in" ----
    @Test fun `one manual vital today is a check-in`() {
        assertTrue(build(HealthInputs(bp = listOf(bp("b", 120, 80, t(29, 8))))).today.checkedIn)
        assertTrue(build(HealthInputs(weight = listOf(weight("w", 70.0, t(29, 8))))).today.checkedIn)
        assertTrue(build(HealthInputs(glucose = listOf(glucose("g", 100.0, t(29, 8))))).today.checkedIn)
        assertTrue(build(HealthInputs(medication = listOf(MedicationEntry("m", true, null, t(29, 8), t(29, 8), HealthSource.MANUAL)))).today.checkedIn)
    }

    @Test fun `sleep, walking, HbA1c and imports alone are not a check-in`() {
        val s = build(HealthInputs(
            sleep = listOf(sleep("s", t(29, 6), 450)),
            activity = listOf(walk("a", t(29, 7), 20), steps("st", t(29, 9), 3000)),
            glucose = listOf(glucose("a1c", 6.4, t(29, 8), GlucoseKind.HBA1C), glucose("dev", 110.0, t(29, 8), GlucoseKind.DEVICE, HealthSource.HEALTH_CONNECT)),
            weight = listOf(weight("hcw", 70.0, t(29, 8), HealthSource.HEALTH_CONNECT)),
        ))
        assertFalse(s.today.checkedIn)
        assertTrue(s.today.hasSleep); assertTrue(s.today.hasActivity)
        assertFalse(s.today.hasSugar); assertFalse(s.today.hasWeight)
    }

    @Test fun `yesterday does not count, and the day follows the phone's calendar`() {
        assertFalse(build(HealthInputs(bp = listOf(bp("b", 120, 80, t(28, 23, 59))))).today.checkedIn)
        // 00:30 local on the 29th is still "today" although it is the 28th in UTC
        val early = HealthStateBuilder.build(HealthInputs(bp = listOf(bp("b", 120, 80, t(29, 0, 30)))), t(29, 10), zone)
        assertTrue(early.today.checkedIn)
    }

    @Test fun `today's steps are summed from device records by when they ended, not when they were synced`() {
        val s = build(HealthInputs(activity = listOf(steps("a", t(29, 9), 2000), steps("b", t(29, 15), 3500), steps("y", t(28, 20), 9000))))
        assertEquals(5500L, s.today.stepsToday)
    }

    @Test fun `manual minutes are not device steps`() {
        val s = build(HealthInputs(activity = listOf(walk("a", t(29, 7), 30))))
        assertEquals(0L, s.today.stepsToday); assertEquals(30, s.today.activityMinutesToday)
    }

    @Test fun `sleep today counts sessions that ended today and ignores suspect entries`() {
        val s = build(HealthInputs(sleep = listOf(sleep("n", t(29, 6), 420), sleep("nap", t(29, 14), 30), sleep("bad", t(29, 7), 1440, suspect = true))))
        assertEquals(450, s.today.sleepMinutesToday)
        assertEquals("nap", s.lastSleep!!.id)
    }

    // ---- aggregates ----
    @Test fun `weekly and monthly windows are inclusive of today and exclude older days`() {
        val s = build(HealthInputs(glucose = listOf(
            glucose("d0", 100.0, t(29, 7)), glucose("d6", 120.0, t(23, 7)), glucose("d7", 200.0, t(22, 7)), glucose("d29", 140.0, t(31, 7, mo = 8)), glucose("d30", 300.0, t(30, 7, mo = 8)),
        )))
        assertEquals(2, s.week.glucoseCount); assertEquals(110.0, s.week.glucoseAverage!!, 0.0001)
        assertEquals(4, s.month.glucoseCount)
        assertEquals(50, s.month.glucoseInRangePercent) // 100 and 120 are in range; 200 and 140 are high
    }

    @Test fun `in-range percentage uses the shared thresholds`() {
        val s = build(HealthInputs(glucose = listOf(glucose("a", 100.0, t(29, 7)), glucose("b", 131.0, t(28, 7)), glucose("c", 69.0, t(27, 7)), glucose("d", 130.0, t(26, 7)))))
        assertEquals(50, s.week.glucoseInRangePercent)
    }

    @Test fun `weight change is last minus first in the window and needs two readings`() {
        assertNull(build(HealthInputs(weight = listOf(weight("a", 70.0, t(29, 7))))).week.weightChangeKg)
        val s = build(HealthInputs(weight = listOf(weight("a", 71.0, t(25, 7)), weight("b", 70.4, t(29, 7)))))
        assertEquals(-0.6, s.week.weightChangeKg!!, 0.0001)
    }

    @Test fun `sleep average is per night and suspect entries are excluded`() {
        val s = build(HealthInputs(sleep = listOf(sleep("a", t(29, 6), 420), sleep("b", t(28, 6), 480), sleep("x", t(27, 6), 1440, suspect = true))))
        assertEquals(2, s.week.sleepNights); assertEquals(450, s.week.sleepAverageMinutes)
    }

    @Test fun `check-in days are distinct days with a manual vital`() {
        val s = build(HealthInputs(
            glucose = listOf(glucose("a", 100.0, t(29, 7)), glucose("b", 110.0, t(29, 20)), glucose("c", 105.0, t(28, 7))),
            bp = listOf(bp("d", 120, 80, t(27, 7))),
            weight = listOf(weight("hc", 70.0, t(26, 7), HealthSource.HEALTH_CONNECT)),
        ))
        assertEquals(3, s.week.checkInDays)
    }

    @Test fun `loading, stale and error flags are carried through`() {
        val s = build(HealthInputs(isLoading = true, isStale = true, errors = mapOf(HealthMetric.SLEEP to "Couldn't load sleep")))
        assertTrue(s.isLoading); assertTrue(s.isStale); assertEquals("Couldn't load sleep", s.errors[HealthMetric.SLEEP])
        assertFalse(s.hasAnyReading)
    }

    @Test fun `the empty state before any data has a usable today`() {
        val s = HealthUiState()
        // date maths on it must not overflow (it used to be LocalDate.MIN and Rhythm crashed on a cold start)
        assertEquals(s.today.date.minusDays(29), s.today.date.minusDays(29))
        assertTrue(s.today.date.isAfter(java.time.LocalDate.of(2020, 1, 1)))
        assertTrue(HealthStateBuilder.checkInDates(s).isEmpty())
        assertNull(Insights.sugarDirection(s))
        assertNull(Insights.weeklySummary(s))
    }
}
