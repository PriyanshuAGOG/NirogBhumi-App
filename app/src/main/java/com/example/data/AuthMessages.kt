package com.nirogbhumi.app.data

/**
 * What a member sees when sign-in or the SMS code fails, in plain words and with a next step. Firebase's own messages
 * ("reCAPTCHA checks were unsuccessful", "An internal error has occurred") mean nothing to a member.
 *
 * Pure Kotlin so it is unit-tested; [FirebaseAuthGateway] passes the error code and message it gets.
 */
object AuthMessages {
    private const val EMAIL_SENTENCE = "You can also sign in with email instead."

    fun otpFailure(errorCode: String?, message: String?): String {
        val code = errorCode.orEmpty().uppercase()
        val text = message.orEmpty().lowercase()
        return when {
            code == "ERROR_INVALID_PHONE_NUMBER" || "invalid format" in text || "invalid phone" in text ->
                "That phone number doesn't look right. Enter the 10-digit mobile number without 0 or +91."
            code == "ERROR_TOO_MANY_REQUESTS" || "too many" in text || "unusual activity" in text || "blocked all requests" in text ->
                "Too many attempts from this phone. Please wait an hour and try again. $EMAIL_SENTENCE"
            code == "ERROR_QUOTA_EXCEEDED" || "quota" in text ->
                "We can't send more codes right now. Please try again tomorrow. $EMAIL_SENTENCE"
            "recaptcha" in text || "app identifier" in text || "play integrity" in text || "safetynet" in text || "app verification" in text ->
                "We couldn't confirm that this app installation is genuine, so the code wasn't sent. $EMAIL_SENTENCE Please tell us if this keeps happening."
            "network" in text || code == "ERROR_NETWORK_REQUEST_FAILED" ->
                "No internet connection. Check your network and try again."
            code == "ERROR_OPERATION_NOT_ALLOWED" || "sms unable to be sent" in text || "region" in text ->
                "Codes can't be sent to this number right now. $EMAIL_SENTENCE"
            else -> "We couldn't send the code. Please check the number and try again. $EMAIL_SENTENCE"
        }
    }

    /** Failures while attaching the second sign-in method (email or phone) to an account. [what] is "email" or "phone number". */
    fun linkFailure(errorCode: String?, message: String?, what: String): String {
        val code = errorCode.orEmpty().uppercase()
        val text = message.orEmpty().lowercase()
        return when {
            code == "ERROR_EMAIL_ALREADY_IN_USE" || code == "ERROR_CREDENTIAL_ALREADY_IN_USE" || code == "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ||
                "already in use" in text || "already associated" in text || "already exists" in text ->
                "That $what already belongs to another account. Use a different one, or sign out and sign in with it."
            code == "ERROR_PROVIDER_ALREADY_LINKED" -> "This account already has a $what."
            code == "ERROR_WEAK_PASSWORD" -> "Choose a stronger password: at least ${AccountContacts.MIN_PASSWORD} characters with a letter."
            code == "ERROR_INVALID_EMAIL" -> "That email address doesn't look right."
            code == "ERROR_REQUIRES_RECENT_LOGIN" -> "For your safety please sign in again, then add your $what."
            "network" in text || code == "ERROR_NETWORK_REQUEST_FAILED" -> "No internet connection. Check your network and try again."
            else -> "We couldn't add your $what. Please check it and try again."
        }
    }

    fun codeCheckFailure(errorCode: String?, message: String?): String {
        val code = errorCode.orEmpty().uppercase()
        val text = message.orEmpty().lowercase()
        return when {
            code == "ERROR_INVALID_VERIFICATION_CODE" || "invalid" in text && "code" in text -> "That code isn't right. Check the SMS and try again."
            code == "ERROR_SESSION_EXPIRED" || "expired" in text -> "That code has expired. Ask for a new one."
            "network" in text -> "No internet connection. Check your network and try again."
            else -> "We couldn't check that code. Ask for a new one and try again."
        }
    }
}
