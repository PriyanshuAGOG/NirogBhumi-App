package com.nirogbhumi.app.data

/**
 * Every account should be reachable by BOTH a phone number (SMS code) and an email (with a password), so a member who
 * signed up one way can always sign in the other way and land in the same account. This says which one is missing.
 *
 * Pure Kotlin so it is unit-tested; the screen and [FirebaseAuthGateway] do the linking.
 */
enum class MissingContact {
    /** Nothing to add. */
    NONE,

    /** Signed up with the phone number: ask for an email and a password and attach them to this account. */
    EMAIL,

    /** Signed up with email (or Google): ask for the phone number, check it with an SMS code, and attach it. */
    PHONE,
}

object AccountContacts {
    private const val PASSWORD = "password"
    private const val GOOGLE = "google.com"
    private const val PHONE = "phone"

    /**
     * @param providerIds the sign-in methods attached to the account ("phone", "password", "google.com")
     * @param phoneNumber the account's verified phone number, if any
     * @param email the account's email, if any
     */
    fun missing(providerIds: List<String>, phoneNumber: String?, email: String?): MissingContact {
        val hasPhone = !phoneNumber.isNullOrBlank() || PHONE in providerIds
        val hasEmailSignIn = PASSWORD in providerIds || GOOGLE in providerIds
        return when {
            !hasPhone -> MissingContact.PHONE
            !hasEmailSignIn || email.isNullOrBlank() -> MissingContact.EMAIL
            else -> MissingContact.NONE
        }
    }

    /** Ten digits, without 0 or +91, as the sign-in screen asks for it. */
    fun isValidIndianMobile(digits: String): Boolean = digits.length == 10 && digits.all { it.isDigit() } && digits[0] in '6'..'9'

    fun isValidEmail(email: String): Boolean = email.trim().let {
        it.length <= 254 && Regex("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$").matches(it)
    }

    const val MIN_PASSWORD = 8
    fun passwordProblem(password: String): String? = when {
        password.length < MIN_PASSWORD -> "Use at least $MIN_PASSWORD characters."
        password.all { it.isDigit() } -> "Add a letter too, so it is harder to guess."
        else -> null
    }
}
