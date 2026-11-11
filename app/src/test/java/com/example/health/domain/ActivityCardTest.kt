package com.nirogbhumi.app.health.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityCardTest {
    @Test fun `a walk logged by minutes shows minutes, not no data`() {
        val c = ActivityCard.forToday(steps = 0, minutes = 30)
        assertTrue(c.hasData)
        assertEquals("30", c.value); assertEquals("min", c.unit); assertEquals("Logged today", c.note)
        assertEquals("30 min", c.oneLine)
    }

    @Test fun `steps alone show steps with thousands separators`() {
        val c = ActivityCard.forToday(steps = 6200, minutes = 0)
        assertEquals("6,200", c.value); assertEquals("steps", c.unit); assertEquals("6,200 steps", c.oneLine)
    }

    @Test fun `steps and minutes together show both`() {
        val c = ActivityCard.forToday(steps = 4000, minutes = 20)
        assertEquals("4,000", c.value); assertEquals("Plus 20 min logged", c.note); assertTrue(c.hasData)
    }

    @Test fun `nothing logged says no data yet`() {
        val c = ActivityCard.forToday(0, 0)
        assertFalse(c.hasData); assertEquals("No data yet", c.note); assertEquals("—", c.oneLine)
    }
}
