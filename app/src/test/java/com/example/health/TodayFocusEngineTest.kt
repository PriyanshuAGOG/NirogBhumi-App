package com.nirogbhumi.app.health

import org.junit.Assert.assertEquals
import org.junit.Test

class TodayFocusEngineTest {
    @Test
    fun `program member who hasn't checked in is nudged to check in first`() {
        val action = TodayFocusEngine.pick(
            isProgramActive = true,
            checkedInToday = false,
            loggedReadingToday = true,
            walkDoneToday = true,
        )
        assertEquals(TodayFocusActionId.CHECK_IN, action.id)
    }

    @Test
    fun `non-member with no reading is nudged to log one`() {
        val action = TodayFocusEngine.pick(
            isProgramActive = false,
            checkedInToday = false,
            loggedReadingToday = false,
            walkDoneToday = true,
        )
        assertEquals(TodayFocusActionId.LOG_READING, action.id)
    }

    @Test
    fun `reading logged but no walk yet is nudged to walk`() {
        val action = TodayFocusEngine.pick(
            isProgramActive = false,
            checkedInToday = true,
            loggedReadingToday = true,
            walkDoneToday = false,
        )
        assertEquals(TodayFocusActionId.WALK, action.id)
    }

    @Test
    fun `everything done today shows the all-caught-up state`() {
        val action = TodayFocusEngine.pick(
            isProgramActive = true,
            checkedInToday = true,
            loggedReadingToday = true,
            walkDoneToday = true,
        )
        assertEquals(TodayFocusActionId.ALL_DONE, action.id)
    }

    @Test
    fun `non-member is never nudged to check in even when unchecked`() {
        // isProgramActive gates the check-in branch entirely - a non-member
        // has no Daily Check-in step to complete, so it must never surface.
        val action = TodayFocusEngine.pick(
            isProgramActive = false,
            checkedInToday = false,
            loggedReadingToday = false,
            walkDoneToday = false,
        )
        assertEquals(TodayFocusActionId.LOG_READING, action.id)
    }
}
