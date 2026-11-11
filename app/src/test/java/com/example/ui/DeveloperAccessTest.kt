package com.nirogbhumi.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperAccessTest {
    @Test
    fun `the two team accounts see it when their email is verified`() {
        assertTrue(DeveloperAccess.isDeveloper("priyanshu@nirogbhumi.com", true))
        assertTrue(DeveloperAccess.isDeveloper("GK@NirogBhumi.com ", true))
    }

    @Test
    fun `an unverified email never unlocks it`() {
        assertFalse(DeveloperAccess.isDeveloper("priyanshu@nirogbhumi.com", false))
        assertFalse(DeveloperAccess.isDeveloper("gk@nirogbhumi.com", false))
    }

    @Test
    fun `testers and look-alike addresses do not see it`() {
        assertFalse(DeveloperAccess.isDeveloper("tester@gmail.com", true))
        assertFalse(DeveloperAccess.isDeveloper("priyanshu@nirogbhumi.com.evil.io", true))
        assertFalse(DeveloperAccess.isDeveloper("someone@nirogbhumi.com", true))
        assertFalse(DeveloperAccess.isDeveloper(null, true))
        assertFalse(DeveloperAccess.isDeveloper("", true))
    }
}
