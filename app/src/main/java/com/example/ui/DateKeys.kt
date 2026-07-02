package com.nirogbhumi.app.ui

/**
 * Shared local-calendar-day bucketing, used everywhere "today" needs to mean the
 * same thing: Rhythm's logged-day grid, the Batch Pulse day key, and the
 * checked-in-today signal that gates the Daily Check-in prompt. Keeping this in
 * one place avoids two screens disagreeing about whether "today" has rolled over.
 */
fun localDayKey(millis: Long): Long {
  val tz = java.util.TimeZone.getDefault()
  return (millis + tz.getOffset(millis)) / 86_400_000L
}
