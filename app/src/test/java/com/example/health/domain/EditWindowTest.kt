package com.nirogbhumi.app.health.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditWindowTest {
    private val created = 1_800_000_000_000L
    private val minute = 60_000L

    private fun at(minutesAfter: Double, source: HealthSource = HealthSource.MANUAL, createdAt: Long? = created) =
        EditWindow.editability(source, createdAt, created + (minutesAfter * minute).toLong())

    @Test fun `the window is sixty minutes everywhere`() {
        assertEquals(60L, EDIT_WINDOW_MINUTES)
    }

    @Test fun `immediately after logging`() = assertTrue(at(0.0) is Editability.Editable)

    @Test fun `thirty minutes later`() = assertTrue(at(30.0) is Editability.Editable)

    @Test fun `fifty nine minutes later with the time remaining`() {
        val e = at(59.0) as Editability.Editable
        assertEquals(minute, e.remainingMillis)
    }

    @Test fun `exactly sixty minutes is already too late, matching the server rule`() {
        assertEquals(Editability.Expired, at(60.0))
    }

    @Test fun `sixty one minutes and a day later`() {
        assertEquals(Editability.Expired, at(61.0))
        assertEquals(Editability.Expired, at(24 * 60.0))
    }

    @Test fun `imported readings are read-only even when brand new`() {
        val e = at(0.0, HealthSource.HEALTH_CONNECT)
        assertEquals(Editability.ImportedReadOnly(HealthSource.HEALTH_CONNECT), e)
        assertTrue(at(0.0, HealthSource.DEVICE) is Editability.ImportedReadOnly)
    }

    @Test fun `a write the server has not stamped yet is editable`() {
        val e = EditWindow.editability(HealthSource.MANUAL, null, created + 10 * 60 * minute) as Editability.Editable
        assertEquals(EDIT_WINDOW_MILLIS, e.remainingMillis)
    }

    @Test fun `hints are plain and only shown when editing is not possible`() {
        assertNull(EditWindow.hint(at(1.0)))
        assertEquals("Editing is available for 60 minutes after you log an entry.", EditWindow.hint(at(90.0)))
        assertTrue(EditWindow.hint(at(0.0, HealthSource.HEALTH_CONNECT))!!.contains("Update it in the source app"))
    }

    @Test fun `works for any entry type`() {
        val w = WeightEntry("w", 70.0, created, created, HealthSource.MANUAL)
        assertTrue(EditWindow.isEditable(w, created + 5 * minute))
        assertFalse(EditWindow.isEditable(w, created + 61 * minute))
        assertNotNull(EditWindow.editability(w, created))
    }
}
