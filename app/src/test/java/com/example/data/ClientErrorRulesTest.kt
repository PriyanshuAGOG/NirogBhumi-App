package com.nirogbhumi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientErrorRulesTest {
    @Test
    fun `phone numbers and emails never leave the phone`() {
        val text = ClientErrorRules.clean("Failed for +91 98765 43210 and asha@example.org", 500)
        assertFalse(text.contains("98765"))
        assertFalse(text.contains("asha@"))
        assertTrue(text.contains("[number]") && text.contains("[email]"))
    }

    @Test
    fun `text is clipped and whitespace tidied`() {
        assertEquals(10, ClientErrorRules.clean("a   b\n\nc".repeat(50), 10).length)
        assertEquals("a b c", ClientErrorRules.clean("a   b\n\nc", 50))
        assertEquals("", ClientErrorRules.clean(null, 50))
    }

    @Test
    fun `the same problem with different numbers is one fingerprint`() {
        assertEquals(
            ClientErrorRules.fingerprint("sign_in:x", "E", "code 123456 expired"),
            ClientErrorRules.fingerprint("sign_in:x", "E", "code 654321 expired"),
        )
        assertFalse(ClientErrorRules.fingerprint("sign_in:x", "E", "a") == ClientErrorRules.fingerprint("sign_in:y", "E", "a"))
    }

    @Test
    fun `the same problem is let through once a minute, others are not held back`() {
        val t = ClientErrorRules.Throttle(gapMillis = 60_000)
        assertTrue(t.allow("a", 1_000))
        assertFalse(t.allow("a", 30_000))
        assertTrue(t.allow("b", 30_000))
        assertTrue(t.allow("a", 61_001))
    }

    @Test
    fun `the throttle memory stays bounded`() {
        val t = ClientErrorRules.Throttle(gapMillis = 60_000, capacity = 3)
        (1..10).forEach { assertTrue(t.allow("p$it", it.toLong())) }
        assertTrue(t.allow("p1", 20L))   // forgotten long ago, so allowed again
    }

    @Test
    fun `a crash note names the place but never the exception text`() {
        val (message, detail) = ClientErrorRules.crashSummary("IllegalStateException", listOf("Home.render(Home.kt:42)", "Main.run(Main.kt:7)"))
        assertEquals("IllegalStateException at Home.render(Home.kt:42)", message)
        assertEquals("Home.render(Home.kt:42) <- Main.run(Main.kt:7)", detail)
    }
}
