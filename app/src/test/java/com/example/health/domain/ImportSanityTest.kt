package com.nirogbhumi.app.health.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class ImportSanityTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun total(day: Int, steps: Long): DailyStepTotal {
        val date = LocalDate.of(2026, 10, day)
        val start = date.atStartOfDay(zone).toInstant()
        return DailyStepTotal(date, steps, start, start.plusSeconds(86_400))
    }

    @Test fun `a normal day is kept, a quiet day is skipped silently, an impossible day is set aside`() {
        val r = ImportSanity.stepDays(listOf(total(1, 6_200), total(2, 0), total(3, 250_000), total(4, 60_000)), zone)
        assertEquals(listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 4)), r.accepted.map { it.date })
        assertEquals(listOf(LocalDate.of(2026, 10, 3)), r.rejectedDates)
        assertEquals(6_200L, r.accepted.first().steps)
    }

    @Test fun `days come back in date order and carry the local day start`() {
        val r = ImportSanity.stepDays(listOf(total(5, 1_000), total(2, 2_000)), zone)
        assertEquals(listOf(2, 5), r.accepted.map { it.date.dayOfMonth })
        assertEquals(LocalDate.of(2026, 10, 2).atStartOfDay(zone).toInstant(), r.accepted.first().dayStart)
    }

    @Test fun `step bounds`() {
        assertFalse(ImportSanity.stepsOk(0)); assertTrue(ImportSanity.stepsOk(1)); assertTrue(ImportSanity.stepsOk(60_000)); assertFalse(ImportSanity.stepsOk(60_001))
    }

    @Test fun `glucose, weight and blood pressure bounds match the rules`() {
        assertTrue(ImportSanity.glucoseOk(20.0)); assertFalse(ImportSanity.glucoseOk(19.9)); assertTrue(ImportSanity.glucoseOk(800.0)); assertFalse(ImportSanity.glucoseOk(800.1))
        assertTrue(ImportSanity.weightOk(20.0)); assertFalse(ImportSanity.weightOk(19.0)); assertFalse(ImportSanity.weightOk(301.0))
        assertTrue(ImportSanity.bpOk(120.0, 80.0)); assertFalse(ImportSanity.bpOk(80.0, 120.0)); assertFalse(ImportSanity.bpOk(120.0, 120.0)); assertFalse(ImportSanity.bpOk(300.0, 80.0))
    }

    @Test fun `sleep must be a believable length and not in the future`() {
        val now = Instant.parse("2026-10-20T06:00:00Z")
        assertTrue(ImportSanity.sleepOk(now.minusSeconds(8 * 3600), now.minusSeconds(60), now))
        assertFalse(ImportSanity.sleepOk(now.minusSeconds(5 * 60), now.minusSeconds(60), now))        // 4 minutes
        assertFalse(ImportSanity.sleepOk(now.minusSeconds(30 * 3600), now.minusSeconds(60), now))     // 30 hours
        assertFalse(ImportSanity.sleepOk(now.plusSeconds(3600), now.plusSeconds(9 * 3600), now))      // tomorrow
    }

    @Test fun `a reading time in the future is refused beyond a small clock slack`() {
        val now = Instant.parse("2026-10-20T06:00:00Z")
        assertTrue(ImportSanity.timeOk(now.plusSeconds(600), now)); assertFalse(ImportSanity.timeOk(now.plusSeconds(3600), now))
    }

    @Test fun `overlapping sleep from two apps keeps the longer session only`() {
        data class S(val a: Instant, val b: Instant)
        val base = Instant.parse("2026-10-19T17:00:00Z")
        val phoneApp = S(base, base.plusSeconds(7 * 3600))
        val watchApp = S(base.plusSeconds(600), base.plusSeconds(8 * 3600))
        val nap = S(base.plusSeconds(20 * 3600), base.plusSeconds(21 * 3600))
        val kept = ImportSanity.withoutOverlappingSleep(listOf(phoneApp, watchApp, nap), { it.a }, { it.b })
        assertEquals(listOf(watchApp, nap), kept)
    }

    @Test fun `source names are friendly and unknown apps are described plainly`() {
        val names = HealthSources.describe(listOf("com.sec.android.app.shealth", "com.google.android.apps.fitness", "com.random.app", "com.google.android.apps.fitness"))
        assertEquals(listOf("Google Fit", "Samsung Health", "another app on this phone"), names)
    }
}
