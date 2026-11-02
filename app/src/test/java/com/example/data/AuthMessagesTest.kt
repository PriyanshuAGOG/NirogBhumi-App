package com.nirogbhumi.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthMessagesTest {
    @Test
    fun `a recaptcha failure is explained in plain words and offers email`() {
        val text = AuthMessages.otpFailure(null, "This request is missing a valid app identifier / reCAPTCHA checks were unsuccessful")
        assertTrue(text.contains("genuine"))
        assertTrue(text.contains("email"))
        assertFalse(text.contains("reCAPTCHA", ignoreCase = true))
    }

    @Test
    fun `a badly typed number says what to enter`() {
        assertTrue(AuthMessages.otpFailure("ERROR_INVALID_PHONE_NUMBER", "The format of the phone number provided is incorrect").contains("10-digit"))
    }

    @Test
    fun `too many requests and quota are told apart`() {
        assertTrue(AuthMessages.otpFailure("ERROR_TOO_MANY_REQUESTS", null).contains("wait an hour"))
        assertTrue(AuthMessages.otpFailure("ERROR_QUOTA_EXCEEDED", null).contains("tomorrow"))
    }

    @Test
    fun `no internet is not blamed on the member`() {
        assertTrue(AuthMessages.otpFailure(null, "A network error (such as timeout) has occurred").contains("internet"))
        assertTrue(AuthMessages.codeCheckFailure(null, "network error").contains("internet"))
    }

    @Test
    fun `an unknown failure still gives a next step and never raw text`() {
        val text = AuthMessages.otpFailure(null, "An internal error has occurred. [ weird ]")
        assertTrue(text.contains("email"))
        assertFalse(text.contains("internal error"))
    }

    @Test
    fun `wrong and expired codes are told apart`() {
        assertTrue(AuthMessages.codeCheckFailure("ERROR_INVALID_VERIFICATION_CODE", null).contains("isn't right"))
        assertTrue(AuthMessages.codeCheckFailure("ERROR_SESSION_EXPIRED", null).contains("expired"))
        assertTrue(AuthMessages.codeCheckFailure(null, null).contains("new one"))
    }
}
