package com.nirogbhumi.app.health.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimesTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private fun now(y: Int, mo: Int, d: Int, h: Int, mi: Int, zone: ZoneId = ist): Instant = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant()
    private val morning = now(2026, 9, 29, 8, 0)

    private fun compose(bed: String, wake: String, date: LocalDate = LocalDate.of(2026, 9, 29), zone: ZoneId = ist, at: Instant = morning) =
        SleepTimes.compose(LocalTime.parse(bed), LocalTime.parse(wake), date, zone, at)

    private fun valid(r: SleepComposition) = r as SleepComposition.Valid
    private fun problem(r: SleepComposition) = (r as SleepComposition.Invalid).problem

    @Test fun `a night that crosses midnight`() {
        val r = valid(compose("22:30", "06:30"))
        assertEquals(480, r.minutes)
        assertEquals(now(2026, 9, 28, 22, 30), r.startAt)
        assertEquals(now(2026, 9, 29, 6, 30), r.endAt)
    }

    @Test fun `bedtime after midnight stays on the wake day`() {
        assertEquals(330, valid(compose("01:00", "06:30")).minutes)
    }

    @Test fun `a same-day nap`() {
        val r = valid(compose("13:00", "14:30", at = now(2026, 9, 29, 16, 0)))
        assertEquals(90, r.minutes)
        assertEquals(now(2026, 9, 29, 13, 0), r.startAt)
    }

    @Test fun `noon and midnight`() {
        assertEquals(360, valid(compose("00:00", "06:00")).minutes)
        assertEquals(720, valid(compose("12:00", "00:00")).minutes)
        assertEquals(480, valid(compose("20:00", "04:00")).minutes)
    }

    @Test fun `identical times are rejected instead of becoming 24 hours`() {
        assertEquals(SleepProblem.SAME_TIME, problem(compose("22:30", "22:30")))
        assertEquals(SleepProblem.SAME_TIME, problem(compose("06:00", "06:00")))
    }

    @Test fun `wake just before bedtime would be nearly 24 hours and is rejected as too long`() {
        assertEquals(SleepProblem.TOO_LONG, problem(compose("07:00", "06:59")))
    }

    @Test fun `eighteen hours is the most a single sleep can be`() {
        assertEquals(1080, valid(compose("12:30", "06:30")).minutes)
        assertEquals(SleepProblem.TOO_LONG, problem(compose("12:29", "06:30")))
    }

    @Test fun `a few minutes is not a sleep`() {
        assertEquals(SleepProblem.TOO_SHORT, problem(compose("06:25", "06:30")))
        assertEquals(10, valid(compose("06:20", "06:30")).minutes)
    }

    @Test fun `a wake time that has not happened yet is rejected`() {
        assertEquals(SleepProblem.IN_FUTURE, problem(compose("22:30", "09:00", at = morning)))
        // two minutes of clock slack is allowed
        assertEquals(510, valid(compose("00:30", "09:00", at = now(2026, 9, 29, 8, 59))).minutes)
    }

    @Test fun `default wake date is today unless that time is still to come`() {
        assertEquals(LocalDate.of(2026, 9, 29), SleepTimes.defaultWakeDate(LocalTime.of(6, 30), ist, morning))
        assertEquals(LocalDate.of(2026, 9, 28), SleepTimes.defaultWakeDate(LocalTime.of(9, 30), ist, morning))
    }

    @Test fun `duration is real elapsed time across a daylight saving change`() {
        val ny = ZoneId.of("America/New_York")
        // Clocks jump forward at 2:00 AM on 8 Mar 2026: 10:30 PM to 6:30 AM is 7 hours, not 8.
        val spring = SleepTimes.compose(LocalTime.of(22, 30), LocalTime.of(6, 30), LocalDate.of(2026, 3, 8), ny, now(2026, 3, 8, 12, 0, ny))
        assertEquals(420, valid(spring).minutes)
        // Clocks fall back on 1 Nov 2026: the same clock times are 9 hours.
        val fall = SleepTimes.compose(LocalTime.of(22, 30), LocalTime.of(6, 30), LocalDate.of(2026, 11, 1), ny, now(2026, 11, 1, 12, 0, ny))
        assertEquals(540, valid(fall).minutes)
    }

    @Test fun `every problem has a plain-language message`() {
        SleepProblem.entries.forEach { assertTrue(SleepTimes.message(it).isNotBlank()) }
        assertEquals("Wake-up time must be different from sleep time.", SleepTimes.message(SleepProblem.SAME_TIME))
    }

    // ---- AM/PM ----
    @Test fun `times read with explicit AM or PM`() {
        assertEquals("10:30 PM", ClockText.format12(22, 30))
        assertEquals("6:30 AM", ClockText.format12(6, 30))
        assertEquals("12:00 AM", ClockText.format12(0, 0))
        assertEquals("12:00 PM", ClockText.format12(12, 0))
        assertEquals("12:05 PM", ClockText.format12(12, 5))
        assertEquals("1:07 AM", ClockText.format12(1, 7))
        assertEquals("11:59 PM", ClockText.format12(LocalTime.of(23, 59)))
        assertEquals("6:30 AM", ClockText.format12(now(2026, 9, 29, 6, 30).toEpochMilli(), ist))
    }

    @Test fun `durations read naturally for people and for screen readers`() {
        assertEquals("7h 35m", ClockText.duration(455)); assertEquals("8h", ClockText.duration(480)); assertEquals("45m", ClockText.duration(45))
        assertEquals("7 hours 35 minutes", ClockText.durationSpoken(455)); assertEquals("1 hour", ClockText.durationSpoken(60))
        assertEquals("1 hour 1 minute", ClockText.durationSpoken(61)); assertEquals("45 minutes", ClockText.durationSpoken(45))
    }
}
