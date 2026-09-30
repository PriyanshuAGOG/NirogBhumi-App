package com.nirogbhumi.app.health.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class SleepProblem {
    /** Bedtime and wake-up time are the same clock time. */
    SAME_TIME,
    /** The period is longer than a single sleep can plausibly be. */
    TOO_LONG,
    /** The period is shorter than a nap worth recording. */
    TOO_SHORT,
    /** The wake-up time has not happened yet. */
    IN_FUTURE,
}

sealed interface SleepComposition {
    data class Valid(val startAt: Instant, val endAt: Instant, val minutes: Int) : SleepComposition
    data class Invalid(val problem: SleepProblem) : SleepComposition
}

object SleepTimes {
    /** A little slack for clock drift between the phone and the moment the member taps Save. */
    private const val FUTURE_TOLERANCE_MINUTES = 2L

    /**
     * Turns "went to bed at 10:30 PM, woke at 6:30 AM on [wakeDate]" into real instants.
     *
     * The bedtime is the latest occurrence of that clock time strictly before waking, so a night
     * that crosses midnight works and a same-day nap works. Identical clock times are rejected
     * rather than silently becoming 24 hours. Duration is measured between instants, so a daylight
     * saving change inside the night is counted correctly.
     */
    fun compose(bedtime: LocalTime, wakeTime: LocalTime, wakeDate: LocalDate, zone: ZoneId, now: Instant): SleepComposition {
        if (bedtime == wakeTime) return SleepComposition.Invalid(SleepProblem.SAME_TIME)
        val wake = ZonedDateTime.of(wakeDate, wakeTime, zone)
        var start = ZonedDateTime.of(wakeDate, bedtime, zone)
        if (!start.isBefore(wake)) start = ZonedDateTime.of(wakeDate.minusDays(1), bedtime, zone)
        val minutes = Duration.between(start.toInstant(), wake.toInstant()).toMinutes()
        if (wake.toInstant().isAfter(now.plus(Duration.ofMinutes(FUTURE_TOLERANCE_MINUTES)))) return SleepComposition.Invalid(SleepProblem.IN_FUTURE)
        if (minutes > SleepLimits.MAX_MINUTES) return SleepComposition.Invalid(SleepProblem.TOO_LONG)
        if (minutes < SleepLimits.MIN_MINUTES) return SleepComposition.Invalid(SleepProblem.TOO_SHORT)
        return SleepComposition.Valid(start.toInstant(), wake.toInstant(), minutes.toInt())
    }

    /** The day the member most likely means: today, unless that wake-up time is still in the future. */
    fun defaultWakeDate(wakeTime: LocalTime, zone: ZoneId, now: Instant): LocalDate {
        val today = ZonedDateTime.ofInstant(now, zone).toLocalDate()
        val candidate = ZonedDateTime.of(today, wakeTime, zone).toInstant()
        return if (candidate.isAfter(now.plus(Duration.ofMinutes(FUTURE_TOLERANCE_MINUTES)))) today.minusDays(1) else today
    }

    /** Plain-language explanation of a problem, shown next to the time pickers. */
    fun message(problem: SleepProblem): String = when (problem) {
        SleepProblem.SAME_TIME -> "Wake-up time must be different from sleep time."
        SleepProblem.TOO_LONG -> "Please check these times. This sleep looks longer than expected."
        SleepProblem.TOO_SHORT -> "Please check these times. This sleep is very short."
        SleepProblem.IN_FUTURE -> "That wake-up time hasn't happened yet."
    }
}

/** 12-hour clock text with explicit AM/PM, independent of the phone's 12/24-hour setting. */
object ClockText {
    fun format12(hour: Int, minute: Int): String {
        require(hour in 0..23 && minute in 0..59) { "invalid time $hour:$minute" }
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        return "$h12:${minute.toString().padStart(2, '0')} ${if (hour < 12) "AM" else "PM"}"
    }

    fun format12(time: LocalTime): String = format12(time.hour, time.minute)

    fun format12(millis: Long, zone: ZoneId): String =
        format12(ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), zone).toLocalTime())

    /** "7h 35m", "8h", "45m". */
    fun duration(minutes: Int): String {
        val m = minutes.coerceAtLeast(0)
        val h = m / 60
        val r = m % 60
        return when {
            h == 0 -> "${r}m"
            r == 0 -> "${h}h"
            else -> "${h}h ${r}m"
        }
    }

    /** Spoken form for screen readers: "7 hours 35 minutes". */
    fun durationSpoken(minutes: Int): String {
        val m = minutes.coerceAtLeast(0)
        val h = m / 60
        val r = m % 60
        val hPart = if (h == 1) "1 hour" else "$h hours"
        val rPart = if (r == 1) "1 minute" else "$r minutes"
        return when {
            h == 0 -> rPart
            r == 0 -> hPart
            else -> "$hPart $rPart"
        }
    }
}
