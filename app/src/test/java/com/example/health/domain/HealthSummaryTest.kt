package com.nirogbhumi.app.health.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthSummaryTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    private fun ago(days: Long, hour: Int = 8) = ZonedDateTime.of(2026, 7, 15, hour, 0, 0, 0, zone).minusDays(days).toInstant().toEpochMilli()

    private fun glucose(id: String, v: Double, kind: GlucoseKind, at: Long) = GlucoseEntry(id, v, kind, at, at, HealthSource.MANUAL)
    private fun bp(id: String, s: Int, d: Int, at: Long) = BpEntry(id, s, d, null, null, at, at, HealthSource.MANUAL)
    private fun weight(id: String, kg: Double, at: Long) = WeightEntry(id, kg, at, at, HealthSource.MANUAL)
    private fun sleep(id: String, minutes: Int, at: Long) = SleepEntry(id, at - minutes * 60_000L, at, minutes, false, at, at, HealthSource.MANUAL)
    private fun steps(id: String, n: Long, at: Long) = ActivityEntry(id, null, n, null, at, at, HealthSource.HEALTH_CONNECT, isDailyTotal = true)
    private fun walk(id: String, min: Int, at: Long) = ActivityEntry(id, min, null, "Walk", at, at, HealthSource.MANUAL)

    private fun state(
        g: List<GlucoseEntry> = emptyList(), b: List<BpEntry> = emptyList(), w: List<WeightEntry> = emptyList(),
        s: List<SleepEntry> = emptyList(), a: List<ActivityEntry> = emptyList(),
    ) = HealthStateBuilder.build(HealthInputs(glucose = g, bp = b, weight = w, sleep = s, activity = a), now, zone)

    private fun metric(summary: HealthSummary, m: TrendMetric) = summary.metrics.first { it.metric == m }

    @Test fun `nothing logged says so for every metric and invents nothing`() {
        val r = HealthSummaryBuilder.build(state(), 7, now)
        assertFalse(r.hasData); assertEquals(0, r.loggedDays); assertEquals(5, r.metrics.size)
        r.metrics.forEach { assertEquals("Not logged in this period", it.headline); assertTrue(it.lines.isEmpty()); assertEquals(ChangeTone.NONE, it.change.tone) }
    }

    @Test fun `blood sugar is broken out by type, with fasting named as the headline`() {
        val r = HealthSummaryBuilder.build(state(g = listOf(
            glucose("a", 100.0, GlucoseKind.FASTING, ago(1)), glucose("b", 120.0, GlucoseKind.FASTING, ago(2)),
            glucose("c", 160.0, GlucoseKind.POST_MEAL, ago(1)), glucose("d", 6.5, GlucoseKind.HBA1C, ago(3)),
        )), 7, now)
        val m = metric(r, TrendMetric.GLUCOSE)
        assertEquals("Fasting average 110 mg/dL", m.headline)
        assertTrue(m.lines.any { it.label == "Fasting (before eating)" && it.value == "110 mg/dL · 2 readings" })
        assertTrue(m.lines.any { it.label == "After a meal" && it.value == "160 mg/dL · 1 reading" })
        assertTrue(m.lines.any { it.label.startsWith("Latest HbA1c") && it.value.startsWith("6.5%") })
        // HbA1c is a different scale and must never leak into the averages or the in-range share.
        assertTrue(m.lines.any { it.label == "In the usual range" && it.value == "100% of 3 readings" })
    }

    @Test fun `with no fasting readings the headline is a plain average, never called fasting`() {
        val r = HealthSummaryBuilder.build(state(g = listOf(glucose("a", 140.0, GlucoseKind.RANDOM, ago(1)))), 7, now)
        assertEquals("Average 140 mg/dL", metric(r, TrendMetric.GLUCOSE).headline)
    }

    @Test fun `a fasting average moving toward the usual range is better and away from it is worse`() {
        val improving = HealthSummaryBuilder.build(state(g = listOf(
            glucose("p", 160.0, GlucoseKind.FASTING, ago(10)), glucose("c", 125.0, GlucoseKind.FASTING, ago(1)))), 7, now)
        assertEquals(ChangeTone.BETTER, metric(improving, TrendMetric.GLUCOSE).change.tone)
        assertEquals("Down 35 mg/dL from the previous 7 days.", metric(improving, TrendMetric.GLUCOSE).change.text)

        val worsening = HealthSummaryBuilder.build(state(g = listOf(
            glucose("p", 110.0, GlucoseKind.FASTING, ago(10)), glucose("c", 150.0, GlucoseKind.FASTING, ago(1)))), 7, now)
        assertEquals(ChangeTone.WORSE, metric(worsening, TrendMetric.GLUCOSE).change.tone)
    }

    @Test fun `a small change is reported as about the same`() {
        val r = HealthSummaryBuilder.build(state(g = listOf(
            glucose("p", 110.0, GlucoseKind.FASTING, ago(10)), glucose("c", 111.0, GlucoseKind.FASTING, ago(1)))), 7, now)
        assertEquals(ChangeTone.NEUTRAL, metric(r, TrendMetric.GLUCOSE).change.tone)
        assertEquals("About the same as the previous 7 days.", metric(r, TrendMetric.GLUCOSE).change.text)
    }

    @Test fun `without an earlier period the change says there is nothing to compare`() {
        val r = HealthSummaryBuilder.build(state(g = listOf(glucose("c", 111.0, GlucoseKind.FASTING, ago(1)))), 7, now)
        assertEquals(ChangeTone.NONE, metric(r, TrendMetric.GLUCOSE).change.tone)
        assertTrue(metric(r, TrendMetric.GLUCOSE).change.text.contains("to compare with yet"))
    }

    @Test fun `the weekly and monthly windows use calendar days`() {
        val g = listOf(glucose("in", 110.0, GlucoseKind.FASTING, ago(6, 0)), glucose("out", 300.0, GlucoseKind.FASTING, ago(7, 23)))
        val week = metric(HealthSummaryBuilder.build(state(g = g), 7, now), TrendMetric.GLUCOSE)
        assertEquals("Fasting average 110 mg/dL", week.headline)
        val month = metric(HealthSummaryBuilder.build(state(g = g), 30, now), TrendMetric.GLUCOSE)
        assertEquals("Fasting average 205 mg/dL", month.headline)
    }

    @Test fun `blood pressure lines and higher-side count`() {
        val m = metric(HealthSummaryBuilder.build(state(b = listOf(bp("a", 120, 80, ago(2)), bp("b", 150, 95, ago(1)))), 7, now), TrendMetric.BP)
        assertEquals("Average 135/88 mmHg", m.headline)
        assertTrue(m.lines.any { it.label.startsWith("On the higher side") && it.value == "1 reading" })
        assertTrue(m.lines.any { it.label == "Latest" && it.value == "150/95 mmHg" })
    }

    @Test fun `blood pressure going down from a high level is better`() {
        val r = HealthSummaryBuilder.build(state(b = listOf(bp("p", 150, 95, ago(10)), bp("c", 130, 85, ago(1)))), 7, now)
        assertEquals(ChangeTone.BETTER, metric(r, TrendMetric.BP).change.tone)
        assertEquals("Upper number down 20 mmHg from the previous 7 days.", metric(r, TrendMetric.BP).change.text)
    }

    @Test fun `weight change is shown without judging it`() {
        val r = HealthSummaryBuilder.build(state(w = listOf(weight("p", 72.0, ago(9)), weight("a", 71.0, ago(5)), weight("b", 70.5, ago(1)))), 7, now)
        val m = metric(r, TrendMetric.WEIGHT)
        assertEquals("Latest 70.5 kg", m.headline)
        assertTrue(m.lines.any { it.value == "71.0 kg to 70.5 kg (-0.5 kg)" })
        assertEquals(ChangeTone.NEUTRAL, m.change.tone)
        assertEquals("Down 1.5 kg since the previous 7 days.", m.change.text)
    }

    @Test fun `sleep average and short nights`() {
        val m = metric(HealthSummaryBuilder.build(state(s = listOf(sleep("a", 300, ago(1)), sleep("b", 480, ago(2)))), 7, now), TrendMetric.SLEEP)
        assertEquals("Average 6h 30m a night", m.headline)
        assertTrue(m.lines.any { it.label == "Nights under 6 hours" && it.value == "1" })
    }

    @Test fun `steps from a device are averaged per day and more is better`() {
        val r = HealthSummaryBuilder.build(state(a = listOf(steps("p", 4000, ago(10)), steps("a", 6000, ago(2)), steps("b", 8000, ago(1)))), 7, now)
        val m = metric(r, TrendMetric.WALKING)
        assertEquals("Average 7,000 steps a day", m.headline)
        assertEquals(ChangeTone.BETTER, m.change.tone)
    }

    @Test fun `a walk logged by minutes is a walking summary, not no data`() {
        val m = metric(HealthSummaryBuilder.build(state(a = listOf(walk("a", 30, ago(1)))), 7, now), TrendMetric.WALKING)
        assertTrue(m.hasData); assertEquals("About 30 min on active days", m.headline)
        assertTrue(m.lines.any { it.value == "30 min over 1 day" })
    }

    @Test fun `logged days count distinct days and ignore imported steps`() {
        val r = HealthSummaryBuilder.build(state(
            g = listOf(glucose("a", 100.0, GlucoseKind.FASTING, ago(1))), b = listOf(bp("b", 120, 80, ago(1, 9))),
            w = listOf(weight("w", 70.0, ago(2))), a = listOf(steps("s", 5000, ago(3)), walk("k", 20, ago(4))),
        ), 7, now)
        assertEquals(3, r.loggedDays)   // day-1 (sugar+bp), day-2 (weight), day-4 (a typed walk); imported steps are not a log
    }
}
