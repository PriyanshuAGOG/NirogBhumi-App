package com.nirogbhumi.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletionGuardsTest {
    @Test fun thePhraseMustBeTypedInCapitals() {
        assertTrue(DeletionGuards.phraseMatches("DELETE MY ACCOUNT"))
        assertTrue(DeletionGuards.phraseMatches("  DELETE MY ACCOUNT  "))
        assertTrue(DeletionGuards.phraseMatches("DELETE   MY ACCOUNT"))
        assertFalse(DeletionGuards.phraseMatches("delete my account"))
        assertFalse(DeletionGuards.phraseMatches("Delete my account"))
        assertFalse(DeletionGuards.phraseMatches("DELETE MY"))
        assertFalse(DeletionGuards.phraseMatches("DELETE MY ACCOUNT NOW"))
        assertFalse(DeletionGuards.phraseMatches(""))
        assertFalse(DeletionGuards.phraseMatches(null))
    }

    @Test fun staleSignInMarkerIsRecognised() {
        assertTrue(DeletionGuards.isReauthRequired("REAUTH_REQUIRED"))
        assertTrue(DeletionGuards.isReauthRequired("FAILED_PRECONDITION: REAUTH_REQUIRED"))
        assertFalse(DeletionGuards.isReauthRequired("Sign in required"))
        assertFalse(DeletionGuards.isReauthRequired(null))
    }

    @Test fun otpIsSixDigits() {
        assertTrue(DeletionGuards.validOtp("123456"))
        assertFalse(DeletionGuards.validOtp("12345")); assertFalse(DeletionGuards.validOtp("1234567")); assertFalse(DeletionGuards.validOtp("12a456")); assertFalse(DeletionGuards.validOtp(null))
    }

    @Test fun signInMethodFollowsProviders() {
        assertEquals(SignInMethod.PASSWORD, SignInMethods.from(listOf("firebase", "password")))
        assertEquals(SignInMethod.PHONE, SignInMethods.from(listOf("phone")))
        assertEquals(SignInMethod.PASSWORD, SignInMethods.from(listOf("phone", "password")))
        assertEquals(SignInMethod.GOOGLE, SignInMethods.from(listOf("google.com")))
        assertEquals(SignInMethod.OTHER, SignInMethods.from(emptyList()))
    }

    @Test fun phoneIsMasked() {
        assertEquals("+91 ••••• 4321", maskPhone("+919876544321"))
        assertEquals("your phone", maskPhone(null))
        assertEquals("your phone", maskPhone("123"))
        assertFalse(maskPhone("+919876544321").contains("98765"))
    }
}
