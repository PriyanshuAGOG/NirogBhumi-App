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
