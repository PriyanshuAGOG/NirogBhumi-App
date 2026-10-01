package com.nirogbhumi.app.health.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InsightsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun t(mo: Int, d: Int, h: Int = 8) = ZonedDateTime.of(2026, mo, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()
    private fun sleepEndingOn(mo: Int, d: Int, hours: Double) = t(mo, d, 6).let { end -> SleepEntry("s$mo-$d", end - (hours * 3_600_000).toLong(), end, (hours * 60).toInt(), false, end, end, HealthSource.MANUAL) }
    private fun fasting(mo: Int, d: Int, v: Double, h: Int = 7) = GlucoseEntry("g$mo-$d", v, GlucoseKind.FASTING, t(mo, d, h), t(mo, d, h), HealthSource.MANUAL)

    // ---- sleep x glucose ----
    @Test fun `needs at least three nights in each bucket`() {
        assertNull(Insights.sleepGlucose(listOf(sleepEndingOn(1, 2, 5.0)), listOf(fasting(1, 2, 140.0)), zone))
    }

    @Test fun `similar buckets say nothing`() {
        val sleep = (0 until 3).flatMap { listOf(sleepEndingOn(1, 2 + it * 2, 5.0), sleepEndingOn(1, 16 + it * 2, 8.0)) }
        val glucose = (0 until 3).flatMap { listOf(fasting(1, 2 + it * 2, 100.0), fasting(1, 16 + it * 2, 102.0)) }
        assertNull(Insights.sleepGlucose(sleep, glucose, zone))
    }

    @Test fun `a meaningful difference surfaces once enough nights are logged`() {
        val sleep = (0 until 3).flatMap { listOf(sleepEndingOn(1, 2 + it * 2, 5.0), sleepEndingOn(1, 16 + it * 2, 8.0)) }
        val glucose = (0 until 3).flatMap { listOf(fasting(1, 2 + it * 2, 140.0), fasting(1, 16 + it * 2, 100.0)) }
        val insight = Insights.sleepGlucose(sleep, glucose, zone)!!
        assertEquals(140.0, insight.shortSleepAvg, 0.01); assertEquals(100.0, insight.longSleepAvg, 0.01)
        assertEquals(3, insight.shortNights); assertEquals(3, insight.longNights)
    }

    @Test fun `it works for imported sleep that only has timestamps`() {
        fun imported(d: Int, hours: Double) = t(1, d, 6).let { end -> SleepEntry("hc$d", end - (hours * 3_600_000).toLong(), end, (hours * 60).toInt(), false, end, end, HealthSource.HEALTH_CONNECT) }
        val sleep = (0 until 3).flatMap { listOf(imported(2 + it * 2, 5.0), imported(16 + it * 2, 8.0)) }
        val glucose = (0 until 3).flatMap { listOf(fasting(1, 2 + it * 2, 140.0), fasting(1, 16 + it * 2, 100.0)) }
        assertNotNull(Insights.sleepGlucose(sleep, glucose, zone))
    }

    @Test fun `only fasting readings count, suspect sleep is ignored`() {
        val sleep = listOf(sleepEndingOn(1, 2, 5.0))
        val postMeal = GlucoseEntry("p", 200.0, GlucoseKind.POST_MEAL, t(1, 2, 9), t(1, 2, 9), HealthSource.MANUAL)
        assertNull(Insights.sleepGlucose(sleep, listOf(postMeal), zone))
        val suspect = sleepEndingOn(1, 3, 5.0).copy(isSuspect = true)
        assertNull(Insights.sleepGlucose(listOf(suspect), listOf(fasting(1, 3, 140.0)), zone))
    }

    @Test fun `the night that ends on the morning of the reading is the one compared`() {
        // Sleep ending 3 Jan at 06:00 belongs to the 3 Jan fasting reading, not the 4 Jan one.
        val sleep = (0 until 3).map { sleepEndingOn(1, 3 + it * 2, 5.0) } + (0 until 3).map { sleepEndingOn(1, 20 + it * 2, 8.0) }
        val glucoseNextDay = (0 until 3).map { fasting(1, 4 + it * 2, 140.0) } + (0 until 3).map { fasting(1, 21 + it * 2, 100.0) }
        assertNull(Insights.sleepGlucose(sleep, glucoseNextDay, zone))
    }

    // ---- medication, meals ----
    @Test fun `medicine taken versus missed on fasting sugar`() {
        val meds = (0 until 3).flatMap { listOf(MedicationEntry("t$it", true, null, t(2, 1 + it * 2, 8), t(2, 1 + it * 2, 8), HealthSource.MANUAL), MedicationEntry("m$it", false, null, t(2, 10 + it * 2, 8), t(2, 10 + it * 2, 8), HealthSource.MANUAL)) }
        val glucose = (0 until 3).flatMap { listOf(fasting(2, 1 + it * 2, 100.0), fasting(2, 10 + it * 2, 130.0)) }
        val insight = Insights.medicationGlucose(meds, glucose, zone)!!
        assertEquals(100.0, insight.takenAvg, 0.01); assertEquals(130.0, insight.missedAvg, 0.01)
    }

    @Test fun `meal timing needs two well-populated windows and a clear gap`() {
        fun post(d: Int, h: Int, v: Double) = GlucoseEntry("p$d$h", v, GlucoseKind.POST_MEAL, t(3, d, h), t(3, d, h), HealthSource.MANUAL)
        val lunch = (1..4).map { post(it, 13, 170.0) }
        val dinner = (1..4).map { post(it, 20, 140.0) }
        val insight = Insights.mealTiming(lunch + dinner, zone)!!
        assertEquals("Lunch", insight.mealLabel); assertEquals(170.0, insight.mealAvg, 0.01); assertEquals(140.0, insight.otherAvg, 0.01)
        assertNull(Insights.mealTiming(lunch, zone))
    }

    // ---- weekly summary ----
    private fun state(input: HealthInputs, nowMillis: Long = t(9, 29, 18)) = HealthStateBuilder.build(input, nowMillis, zone)

    @Test fun `an empty week has no summary`() {
        assertNull(Insights.weeklySummary(state(HealthInputs())))
    }

    @Test fun `five logged days in range is a great week`() {
        val g = (0 until 5).map { GlucoseEntry("g$it", 100.0, GlucoseKind.FASTING, t(9, 29 - it, 7), t(9, 29 - it, 7), HealthSource.MANUAL) }
        val s = Insights.weeklySummary(state(HealthInputs(glucose = g)))!!
        assertEquals("A great week", s.headline); assertEquals(WeeklyTone.POSITIVE, s.tone)
        assertEquals(true, s.detail.contains("Logged 5 of 7 days")); assertEquals(true, s.detail.contains("100%"))
    }

    @Test fun `a high blood pressure reading makes it a tougher week`() {
        val bp = listOf(BpEntry("b", 150, 95, null, null, t(9, 28, 8), t(9, 28, 8), HealthSource.MANUAL))
        assertEquals("A tougher week", Insights.weeklySummary(state(HealthInputs(bp = bp)))!!.headline)
    }

    @Test fun `few days logged builds the rhythm, three or four is steady`() {
        val one = listOf(GlucoseEntry("a", 100.0, GlucoseKind.FASTING, t(9, 29, 7), t(9, 29, 7), HealthSource.MANUAL))
        assertEquals("Building your rhythm", Insights.weeklySummary(state(HealthInputs(glucose = one)))!!.headline)
        val three = (0 until 3).map { GlucoseEntry("g$it", 100.0, GlucoseKind.FASTING, t(9, 29 - it, 7), t(9, 29 - it, 7), HealthSource.MANUAL) }
        assertEquals("A steady week", Insights.weeklySummary(state(HealthInputs(glucose = three)))!!.headline)
    }

    @Test fun `HbA1c does not count as a sugar day`() {
        val a1c = listOf(GlucoseEntry("a", 6.4, GlucoseKind.HBA1C, t(9, 29, 7), t(9, 29, 7), HealthSource.MANUAL))
        assertNull(Insights.weeklySummary(state(HealthInputs(glucose = a1c))))
    }

    // ---- direction / week fasting ----
    @Test fun `direction compares the recent half with the older half over 30 days and never guesses from little data`() {
        fun g(d: Int, v: Double) = GlucoseEntry("g$d", v, GlucoseKind.FASTING, t(9, d, 7), t(9, d, 7), HealthSource.MANUAL)
        assertNull(Insights.sugarDirection(state(HealthInputs(glucose = listOf(g(29, 100.0), g(28, 110.0), g(27, 120.0))))))
        val improving = state(HealthInputs(glucose = listOf(g(29, 100.0), g(28, 101.0), g(20, 130.0), g(19, 131.0))))
        assertEquals(SugarDirection.IMPROVING, Insights.sugarDirection(improving))
        val rising = state(HealthInputs(glucose = listOf(g(29, 140.0), g(28, 141.0), g(20, 110.0), g(19, 111.0))))
        assertEquals(SugarDirection.RISING, Insights.sugarDirection(rising))
        val stable = state(HealthInputs(glucose = listOf(g(29, 100.0), g(28, 101.0), g(20, 100.0), g(19, 102.0))))
        assertEquals(SugarDirection.STABLE, Insights.sugarDirection(stable))
        fun july(d: Int) = GlucoseEntry("j$d", 100.0, GlucoseKind.FASTING, t(7, d, 7), t(7, d, 7), HealthSource.MANUAL)
        val old = state(HealthInputs(glucose = listOf(july(1), july(2), july(3), july(4)))) // months old
        assertNull(Insights.sugarDirection(old))
    }

    @Test fun `this week's fasting average only counts fasting readings from the last seven days`() {
        fun g(d: Int, v: Double, k: GlucoseKind = GlucoseKind.FASTING) = GlucoseEntry("g$d$k", v, k, t(9, d, 7), t(9, d, 7), HealthSource.MANUAL)
        val s = state(HealthInputs(glucose = listOf(g(29, 100.0), g(27, 110.0), g(28, 200.0, GlucoseKind.POST_MEAL), g(15, 300.0))))
        assertEquals(2, s.week.fastingCount); assertEquals(105.0, s.week.fastingAverage!!, 0.001)
    }
}
