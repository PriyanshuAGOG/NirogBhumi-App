package com.nirogbhumi.app.health

import com.google.firebase.Timestamp
import com.nirogbhumi.app.data.CloudDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

private fun istMillis(year: Int, month: Int, day: Int, hour: Int = 8): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
    cal.set(year, month, day, hour, 0, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun sleepLog(day: Long, hours: Double) =
    CloudDocument("s$day", mapOf("createdAt" to Timestamp(day / 1000, 0), "duration" to hours))

private fun fastingReading(day: Long, value: Int) =
    CloudDocument("g$day", mapOf("measuredAt" to Timestamp(day / 1000, 0), "readingType" to "fasting", "value" to value))

class TrendInsightsTest {
    @Test
    fun `returns null with fewer than 3 nights per bucket`() {
        val day1 = istMillis(2026, Calendar.JANUARY, 1)
        val day2 = istMillis(2026, Calendar.JANUARY, 2, hour = 7)
        val sleep = listOf(sleepLog(day1, 5.0))
        val glucose = listOf(fastingReading(day2, 140))
        assertNull(computeSleepGlucoseInsight(sleep, glucose))
    }

    @Test
    fun `returns null when both buckets are similar`() {
        val sleep = mutableListOf<CloudDocument>()
        val glucose = mutableListOf<CloudDocument>()
        for (i in 0 until 3) {
            val nightMillis = istMillis(2026, Calendar.JANUARY, 1 + i * 2)
            val morningMillis = istMillis(2026, Calendar.JANUARY, 2 + i * 2, hour = 7)
            sleep.add(sleepLog(nightMillis, 5.0))
            glucose.add(fastingReading(morningMillis, 100))
            val nightMillis2 = istMillis(2026, Calendar.JANUARY, 15 + i * 2)
            val morningMillis2 = istMillis(2026, Calendar.JANUARY, 16 + i * 2, hour = 7)
            sleep.add(sleepLog(nightMillis2, 8.0))
            glucose.add(fastingReading(morningMillis2, 102))
        }
        assertNull(computeSleepGlucoseInsight(sleep, glucose))
    }

    @Test
    fun `surfaces a meaningful difference once enough nights are logged`() {
        val sleep = mutableListOf<CloudDocument>()
        val glucose = mutableListOf<CloudDocument>()
        for (i in 0 until 3) {
            val shortNight = istMillis(2026, Calendar.JANUARY, 1 + i * 2)
            val shortMorning = istMillis(2026, Calendar.JANUARY, 2 + i * 2, hour = 7)
            sleep.add(sleepLog(shortNight, 5.0))
            glucose.add(fastingReading(shortMorning, 140))
            val longNight = istMillis(2026, Calendar.JANUARY, 15 + i * 2)
            val longMorning = istMillis(2026, Calendar.JANUARY, 16 + i * 2, hour = 7)
            sleep.add(sleepLog(longNight, 8.0))
            glucose.add(fastingReading(longMorning, 100))
        }
        val insight = computeSleepGlucoseInsight(sleep, glucose)
        assertEquals(140.0, insight!!.shortSleepAvg, 0.01)
        assertEquals(100.0, insight.longSleepAvg, 0.01)
        assertEquals(3, insight.shortNights)
        assertEquals(3, insight.longNights)
    }

    @Test
    fun `non-fasting readings are excluded from the correlation`() {
        val sleep = listOf(sleepLog(istMillis(2026, Calendar.JANUARY, 1), 5.0))
        val glucose = listOf(
            CloudDocument(
                "g1",
                mapOf(
                    "measuredAt" to Timestamp(istMillis(2026, Calendar.JANUARY, 2, hour = 7) / 1000, 0),
                    "readingType" to "post_meal",
                    "value" to 200,
                ),
            ),
        )
        assertNull(computeSleepGlucoseInsight(sleep, glucose))
    }
}
