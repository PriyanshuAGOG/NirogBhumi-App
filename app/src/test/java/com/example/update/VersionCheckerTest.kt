package com.nirogbhumi.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCheckerTest {
    @Test
    fun `numeric segments compare correctly, not lexically`() {
        assertTrue(VersionChecker.isNewer("1.0.10", "1.0.9"))
        assertFalse(VersionChecker.isNewer("1.0.9", "1.0.10"))
    }

    @Test
    fun `minor version bump beats patch bump`() {
        assertTrue(VersionChecker.isNewer("1.3.0", "1.2.5"))
    }

    @Test
    fun `major version bump beats everything`() {
        assertTrue(VersionChecker.isNewer("2.0.0", "1.9.9"))
    }

    @Test
    fun `equal versions are not newer`() {
        assertFalse(VersionChecker.isNewer("1.2.3", "1.2.3"))
        assertEquals(0, VersionChecker.compare("1.2.3", "1.2.3"))
    }

    @Test
    fun `missing trailing segments are treated as zero`() {
        assertEquals(0, VersionChecker.compare("1.2", "1.2.0"))
        assertTrue(VersionChecker.isNewer("1.2.1", "1.2"))
    }

    @Test
    fun `a pre-release suffix does not break parsing`() {
        assertEquals(0, VersionChecker.compare("1.0.0-beta", "1.0.0"))
    }

    @Test
    fun `garbage input falls back to zero segments instead of throwing`() {
        assertEquals(0, VersionChecker.compare("not-a-version", "0.0.0"))
    }
}
