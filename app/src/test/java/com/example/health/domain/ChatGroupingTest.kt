package com.nirogbhumi.app.health.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGroupingTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 10, 20, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    private fun at(day: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, zone).toInstant().toEpochMilli()
    private fun key(sender: String, millis: Long?) = ChatKey(sender, millis)

    @Test fun `three quick messages from one person are one group, shown newest first`() {
        val p = ChatGrouping.positions(listOf(key("a", at(20, 9, 4)), key("a", at(20, 9, 2)), key("a", at(20, 9, 0))), zone, now)
        // index 0 is the newest (bottom of the chat): it ends the group; index 2 is the oldest: it starts it.
        assertEquals(listOf(false, false, true), p.map { it.startsGroup })
        assertEquals(listOf(true, false, false), p.map { it.endsGroup })
    }

    @Test fun `a different sender or a long pause starts a new group`() {
        val p = ChatGrouping.positions(listOf(key("b", at(20, 9, 6)), key("a", at(20, 9, 5)), key("a", at(20, 8, 0))), zone, now)
        assertTrue(p[0].startsGroup && p[0].endsGroup)    // b alone
        assertTrue(p[1].startsGroup && p[1].endsGroup)    // a at 9:05 is more than 5 minutes after 8:00
        assertTrue(p[2].startsGroup)
    }

    @Test fun `the day label shows once, on the earliest message of each day`() {
        // newest first: 20th 9:00 | 19th 22:00 | 19th 21:59 | 18th 8:00
        val p = ChatGrouping.positions(listOf(key("a", at(20, 9, 0)), key("a", at(19, 22, 0)), key("a", at(19, 21, 59)), key("a", at(18, 8, 0))), zone, now)
        assertEquals(listOf("Today", null, "Yesterday", "Sun 18 Oct"), p.map { it.dayLabel })
    }

    @Test fun `the same person across midnight is two groups`() {
        val p = ChatGrouping.positions(listOf(key("a", at(20, 0, 2)), key("a", at(19, 23, 59))), zone, now)
        assertTrue(p[0].startsGroup); assertTrue(p[1].endsGroup)
    }

    @Test fun `a message still waiting for its server time joins the previous message from the same sender`() {
        val p = ChatGrouping.positions(listOf(key("a", null), key("a", at(20, 9, 0))), zone, now)
        assertFalse(p[0].startsGroup); assertTrue(p[0].endsGroup)
        assertNull(p[0].dayLabel)
    }

    @Test fun `empty chat and single message`() {
        assertTrue(ChatGrouping.positions(emptyList(), zone, now).isEmpty())
        val one = ChatGrouping.positions(listOf(key("a", at(20, 9, 0))), zone, now).single()
        assertTrue(one.startsGroup && one.endsGroup); assertEquals("Today", one.dayLabel)
    }

    @Test fun `day labels`() {
        assertEquals("Today", ChatGrouping.dayLabel(at(20, 1, 0), now, zone))
        assertEquals("Yesterday", ChatGrouping.dayLabel(at(19, 23, 0), now, zone))
        assertEquals("Mon 12 Oct", ChatGrouping.dayLabel(at(12, 10, 0), now, zone))
        assertEquals("3 Mar 2025", ChatGrouping.dayLabel(ZonedDateTime.of(2025, 3, 3, 10, 0, 0, 0, zone).toInstant().toEpochMilli(), now, zone))
    }
}
