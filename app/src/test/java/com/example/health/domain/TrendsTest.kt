package com.nirogbhumi.app.health.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun t(d: Int, h: Int = 8, mo: Int = 9) = ZonedDateTime.of(2026, mo, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val now = t(29, 18)
    private fun date(d: Int, mo: Int = 9) = LocalDate.of(2026, mo, d)

    private fun weight(id: String, kg: Double, at: Long) = WeightEntry(id, kg, at, at, HealthSource.MANUAL)
    private fun sleep(id: String, end: Long, minutes: Int) = SleepEntry(id, end - minutes * 60_000L, end, minutes, false, end, end, HealthSource.MANUAL)
    private fun glucose(id: String, v: Double, at: Long) = GlucoseEntry(id, v, GlucoseKind.FASTING, at, at, HealthSource.MANUAL)
    private fun bp(id: String, s: Int, d: Int, at: Long) = BpEntry(id, s, d, null, null, at, at, HealthSource.MANUAL)
    private fun steps(id: String, at: Long, n: Long) = ActivityEntry(id, null, n, null, at, at, HealthSource.HEALTH_CONNECT)
    private fun state(input: HealthInputs) = HealthStateBuilder.build(input, now, zone)
    private fun trend(input: HealthInputs, m: TrendMetric, r: TrendRange = TrendRange.WEEK) = TrendBuilder.build(state(input), m, r, now)

    // ---- data ----
    @Test fun `sleep trend has one point per measured night and invents nothing for the gap`() {
        val d = trend(HealthInputs(sleep = listOf(sleep("a", t(26, 6), 420), sleep("b", t(27, 6), 450), sleep("c", t(29, 6), 480))), TrendMetric.SLEEP)
        assertEquals(listOf(date(26), date(27), date(29)), d.primary!!.points.map { it.date })
        assertEquals(listOf(7.0, 7.5, 8.0), d.primary!!.points.map { it.value })
        assertEquals("h", d.unit); assertTrue(d.hasEnoughData); assertNull(d.message)
    }

    @Test fun `two sleeps ending the same day are added together`() {
        val d = trend(HealthInputs(sleep = listOf(sleep("n", t(29, 6), 420), sleep("nap", t(29, 14), 60), sleep("p", t(28, 6), 400))), TrendMetric.SLEEP)
        assertEquals(8.0, d.primary!!.points.last().value, 0.0001)
        assertEquals(2, d.primary!!.points.last().readings)
    }

    @Test fun `weight trend reads kilograms from manual and Health Connect alike`() {
        val hc = WeightEntry("h", 69.8, t(28), t(28), HealthSource.HEALTH_CONNECT)
        val d = trend(HealthInputs(weight = listOf(weight("m", 70.2, t(27)), hc)), TrendMetric.WEIGHT)
        assertEquals(listOf(70.2, 69.8), d.primary!!.points.map { it.value })
        assertEquals("kg", d.unit)
    }

    @Test fun `glucose trend averages the day and excludes HbA1c`() {
        val a1c = GlucoseEntry("a", 6.4, GlucoseKind.HBA1C, t(28), t(28), HealthSource.MANUAL)
        val d = trend(HealthInputs(glucose = listOf(glucose("a", 100.0, t(28, 7)), glucose("b", 120.0, t(28, 20)), glucose("c", 90.0, t(29)), a1c)), TrendMetric.GLUCOSE)
        assertEquals(listOf(110.0, 90.0), d.primary!!.points.map { it.value })
    }

    @Test fun `blood pressure has an upper and a lower series`() {
        val d = trend(HealthInputs(bp = listOf(bp("a", 130, 85, t(28)), bp("b", 120, 80, t(29)))), TrendMetric.BP)
        assertEquals(listOf("Upper BP", "Lower BP"), d.series.map { it.label })
        assertEquals(listOf(130.0, 120.0), d.series[0].points.map { it.value })
        assertEquals(listOf(85.0, 80.0), d.series[1].points.map { it.value })
    }

    @Test fun `walking prefers device steps and falls back to active minutes`() {
        val withSteps = trend(HealthInputs(activity = listOf(steps("a", t(28), 4000), steps("b", t(29), 6000))), TrendMetric.WALKING)
        assertEquals("steps", withSteps.unit)
        val onlyMinutes = trend(HealthInputs(activity = listOf(ActivityEntry("m1", 20, null, "walk", t(28), t(28), HealthSource.MANUAL), ActivityEntry("m2", 30, null, "walk", t(29), t(29), HealthSource.MANUAL))), TrendMetric.WALKING)
        assertEquals("min", onlyMinutes.unit)
        assertEquals(listOf(20.0, 30.0), onlyMinutes.primary!!.points.map { it.value })
    }

    @Test fun `each metric uses its own data - weight never comes from glucose`() {
        val input = HealthInputs(glucose = listOf(glucose("a", 100.0, t(28)), glucose("b", 110.0, t(29))))
        val d = trend(input, TrendMetric.WEIGHT)
        assertTrue(d.primary!!.points.isEmpty()); assertFalse(d.hasEnoughData)
        assertEquals("Log at least two readings to see a trend.", d.message)
        assertNull(d.axis)
    }

    @Test fun `one reading is not a trend`() {
        val d = trend(HealthInputs(weight = listOf(weight("a", 70.0, t(29)))), TrendMetric.WEIGHT)
        assertFalse(d.hasEnoughData); assertEquals(TrendBuilder.INSUFFICIENT_MESSAGE, d.message)
        assertNotNull(d.axis) // the single point can still be shown
    }

    @Test fun `ranges include today and drop older days`() {
        val input = HealthInputs(weight = listOf(weight("a", 70.0, t(29)), weight("b", 70.1, t(22)), weight("c", 70.3, t(1)), weight("d", 71.0, t(1, mo = 7))))
        assertEquals(1, trend(input, TrendMetric.WEIGHT, TrendRange.WEEK).primary!!.points.size)
        assertEquals(3, trend(input, TrendMetric.WEIGHT, TrendRange.MONTH).primary!!.points.size)
        assertEquals(3, trend(input, TrendMetric.WEIGHT, TrendRange.QUARTER).primary!!.points.size)
    }

    // ---- axes ----
    @Test fun `sleep axis uses round hour ticks that follow the data`() {
        val axis = TrendBuilder.axis(listOf(6.0, 7.5, 8.5), TrendBuilder.policyFor(TrendMetric.SLEEP, "h", listOf(6.0, 8.5)))
        assertEquals(listOf(4.0, 6.0, 8.0, 10.0), axis.ticks)
    }

    @Test fun `weight axis never zooms into a tiny range`() {
        val values = listOf(70.0, 70.2)
        val axis = TrendBuilder.axis(values, TrendBuilder.policyFor(TrendMetric.WEIGHT, "kg", values))
        assertTrue("a 0.2 kg change must sit inside at least 3 kg of axis, was ${axis.span}", axis.span >= 3.0)
        assertTrue(axis.min <= 70.0 && axis.max >= 70.2)
    }

    @Test fun `weight axis range grows with body weight`() {
        val values = listOf(100.0, 100.1)
        assertTrue(TrendBuilder.axis(values, TrendBuilder.policyFor(TrendMetric.WEIGHT, "kg", values)).span >= 4.0)
    }

    @Test fun `steps axis starts at zero`() {
        val values = listOf(3000.0, 8000.0)
        val axis = TrendBuilder.axis(values, TrendBuilder.policyFor(TrendMetric.WALKING, "steps", values))
        assertEquals(0.0, axis.min, 0.0); assertTrue(axis.max >= 8000.0)
    }

    @Test fun `axis always contains every value and has a sensible tick count`() {
        listOf(listOf(85.0, 140.0), listOf(120.0, 121.0), listOf(250.0), listOf(60.0, 61.0, 300.0)).forEach { values ->
            val axis = TrendBuilder.axis(values, TrendBuilder.policyFor(TrendMetric.GLUCOSE, "mg/dL", values))
            assertTrue(axis.min <= values.min() && axis.max >= values.max())
            assertTrue("ticks=${axis.ticks}", axis.ticks.size in 2..6)
            assertEquals(axis.min, axis.ticks.first(), 1e-9); assertEquals(axis.max, axis.ticks.last(), 1e-9)
        }
    }

    // ---- labels and gaps ----
    @Test fun `a week is labelled with weekday names`() {
        val labels = TrendBuilder.xLabels(TrendRange.WEEK, date(23), date(29))
        assertEquals(listOf("Wed", "Thu", "Fri", "Sat", "Sun", "Mon", "Tue"), labels.map { it.text })
    }

    @Test fun `longer ranges are labelled with day and month, few enough to read`() {
        val month = TrendBuilder.xLabels(TrendRange.MONTH, date(31, 8), date(29))
        assertEquals("31 Aug", month.first().text); assertTrue(month.size <= 8)
        val quarter = TrendBuilder.xLabels(TrendRange.QUARTER, date(1, 7), date(29))
        assertTrue(quarter.size <= 8); assertEquals("1 Jul", quarter.first().text)
    }

    @Test fun `a long silence breaks the line instead of implying continuous data`() {
        fun p(d: Int) = TrendPoint(0, date(d), 1.0, 1)
        val segs = TrendBuilder.segments(listOf(p(1), p(2), p(4), p(20), p(21)), TrendRange.MONTH)
        assertEquals(listOf(listOf(1, 2, 4), listOf(20, 21)), segs.map { s -> s.map { it.date.dayOfMonth } })
        assertEquals(1, TrendBuilder.segments(listOf(p(1), p(8)), TrendRange.QUARTER).size) // a week is fine for the 90-day view
        assertEquals(2, TrendBuilder.segments(listOf(p(1), p(8)), TrendRange.WEEK).size)
    }

    // ---- text ----
    @Test fun `tapping a sleep point reads the day and the duration`() {
        val d = trend(HealthInputs(sleep = listOf(sleep("a", t(28, 6), 400), sleep("b", t(29, 6), 455))), TrendMetric.SLEEP)
        assertEquals("Tuesday, 29 Sep\n7h 35m", TrendText.describeDay(d, date(29)))
        assertNull(TrendText.describeDay(d, date(27)))
    }

    @Test fun `screen readers get a full sentence per point`() {
        val d = trend(HealthInputs(sleep = listOf(sleep("a", t(28, 6), 400), sleep("b", t(29, 6), 455))), TrendMetric.SLEEP)
        assertEquals("Tuesday 29 September, 7 hours 35 minutes of sleep.", TrendText.spokenPoint(d, date(29)))
    }

    @Test fun `screen readers get a summary of the whole chart`() {
        val d = trend(HealthInputs(sleep = listOf(sleep("a", t(28, 6), 420), sleep("b", t(29, 6), 480))), TrendMetric.SLEEP)
        val summary = TrendText.spokenSummary(d)
        assertTrue(summary, summary.startsWith("Sleep, last 7 days."))
        assertTrue(summary, summary.contains("2 of 7 days have readings"))
        assertTrue(summary, summary.contains("7 hours 30 minutes"))
        assertEquals("Weight, last 7 days. No readings yet.", TrendText.spokenSummary(trend(HealthInputs(), TrendMetric.WEIGHT)))
    }

    @Test fun `blood pressure reads upper over lower`() {
        val d = trend(HealthInputs(bp = listOf(bp("a", 130, 85, t(28)), bp("b", 122, 80, t(29)))), TrendMetric.BP)
        assertEquals("Tuesday, 29 Sep\n122 / 80 mmHg", TrendText.describeDay(d, date(29)))
        assertTrue(TrendText.spokenPoint(d, date(29))!!.contains("upper blood pressure 122, lower 80"))
    }

    @Test fun `axis labels are short and unit-aware`() {
        assertEquals("8h", TrendText.axisLabel(TrendMetric.SLEEP, "h", 8.0))
        assertEquals("70.0", TrendText.axisLabel(TrendMetric.WEIGHT, "kg", 70.0))
        assertEquals("5k", TrendText.axisLabel(TrendMetric.WALKING, "steps", 5000.0))
        assertEquals("2.5k", TrendText.axisLabel(TrendMetric.WALKING, "steps", 2500.0))
        assertEquals("120", TrendText.axisLabel(TrendMetric.GLUCOSE, "mg/dL", 120.0))
    }

    @Test fun `tiny weight differences are not called a change`() {
        assertFalse(TrendText.isMeaningfulWeightChange(0.2)); assertTrue(TrendText.isMeaningfulWeightChange(-0.6))
    }
}
