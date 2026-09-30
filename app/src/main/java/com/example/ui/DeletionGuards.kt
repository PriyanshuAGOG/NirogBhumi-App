package com.nirogbhumi.app.ui

/** Small rules around deleting an account, kept apart from the screen so they can be tested. */
object DeletionGuards {
    const val PHRASE = "DELETE MY ACCOUNT"

    /** The member must type the phrase (capitals; stray spaces are forgiven, other words are not). */
    fun phraseMatches(typed: String?): Boolean = typed?.trim()?.replace(Regex("\\s+"), " ") == PHRASE

    /** The server answers a stale sign-in with this marker (see requestAccountDeletion in the functions). */
    const val REAUTH_MARKER = "REAUTH_REQUIRED"
    fun isReauthRequired(message: String?): Boolean = message?.contains(REAUTH_MARKER) == true

    /** A 6-digit SMS code, digits only. */
    fun validOtp(code: String?): Boolean = code?.let { it.length == 6 && it.all(Char::isDigit) } == true
}

/** How this member signed in, which decides how they confirm it is really them. */
enum class SignInMethod { PASSWORD, PHONE, GOOGLE, OTHER }

object SignInMethods {
    /** From Firebase provider ids (password, phone, google.com). Email+password wins when several are linked, then phone. */
    fun from(providerIds: Collection<String>): SignInMethod = when {
        "password" in providerIds -> SignInMethod.PASSWORD
        "phone" in providerIds -> SignInMethod.PHONE
        "google.com" in providerIds -> SignInMethod.GOOGLE
        else -> SignInMethod.OTHER
    }
}

/** "+91 ••••• 4321": enough to recognise the number, not enough to read it aloud to someone. */
fun maskPhone(phone: String?): String {
    val digits = phone.orEmpty()
    if (digits.length < 7) return "your phone"
    val country = digits.takeWhile { it == '+' || it.isDigit() }.take(if (digits.startsWith("+")) 3 else 0)
    return "${country.ifEmpty { "" }} ••••• ${digits.takeLast(4)}".trim()
}
