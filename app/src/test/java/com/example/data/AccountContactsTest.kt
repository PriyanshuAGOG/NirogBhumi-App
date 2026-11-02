package com.nirogbhumi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountContactsTest {
    @Test
    fun `a phone-only account is asked for an email`() {
        assertEquals(MissingContact.EMAIL, AccountContacts.missing(listOf("firebase", "phone"), "+919876543210", null))
    }

    @Test
    fun `an email-only account is asked for a phone number`() {
        assertEquals(MissingContact.PHONE, AccountContacts.missing(listOf("firebase", "password"), null, "a@b.in"))
    }

    @Test
    fun `a Google account counts as having an email and is asked for a phone number`() {
        assertEquals(MissingContact.PHONE, AccountContacts.missing(listOf("firebase", "google.com"), null, "a@gmail.com"))
    }

    @Test
    fun `an account with both is left alone`() {
        assertEquals(MissingContact.NONE, AccountContacts.missing(listOf("firebase", "phone", "password"), "+919876543210", "a@b.in"))
        assertEquals(MissingContact.NONE, AccountContacts.missing(listOf("firebase", "google.com", "phone"), "+919876543210", "a@gmail.com"))
    }

    @Test
    fun `a phone account whose email has no sign-in method is still asked for one`() {
        assertEquals(MissingContact.EMAIL, AccountContacts.missing(listOf("firebase", "phone"), "+919876543210", "a@b.in"))
    }

    @Test
    fun `indian mobile numbers are ten digits starting 6 to 9`() {
        assertTrue(AccountContacts.isValidIndianMobile("9876543210"))
        assertFalse(AccountContacts.isValidIndianMobile("0876543210"))
        assertFalse(AccountContacts.isValidIndianMobile("987654321"))
        assertFalse(AccountContacts.isValidIndianMobile("98765x3210"))
    }

    @Test
    fun `email shape is checked`() {
        assertTrue(AccountContacts.isValidEmail("priya.k+test@nirogbhumi.com"))
        assertFalse(AccountContacts.isValidEmail("no-at-sign"))
        assertFalse(AccountContacts.isValidEmail("a@b"))
        assertFalse(AccountContacts.isValidEmail("a b@c.in"))
    }

    @Test
    fun `passwords need length and a letter`() {
        assertNotNull(AccountContacts.passwordProblem("short1"))
        assertNotNull(AccountContacts.passwordProblem("123456789"))
        assertNull(AccountContacts.passwordProblem("walk2daily"))
    }

    @Test
    fun `link failures name what could not be added`() {
        assertTrue(AuthMessages.linkFailure("ERROR_EMAIL_ALREADY_IN_USE", null, "email").contains("another account"))
        assertTrue(AuthMessages.linkFailure("ERROR_CREDENTIAL_ALREADY_IN_USE", null, "phone number").contains("phone number"))
        assertTrue(AuthMessages.linkFailure("ERROR_WEAK_PASSWORD", null, "email").contains("stronger"))
        assertTrue(AuthMessages.linkFailure("ERROR_REQUIRES_RECENT_LOGIN", null, "email").contains("sign in again"))
        assertTrue(AuthMessages.linkFailure(null, "A network error occurred", "email").contains("internet"))
        assertFalse(AuthMessages.linkFailure(null, "boom internal", "email").contains("internal"))
    }
}
