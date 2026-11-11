package com.nirogbhumi.app.ui

/**
 * Who may see the Developer settings screen: the two team accounts only, and only when the email is verified, so nobody can
 * unlock it by signing up with a look-alike address they do not own. This only hides a screen; it protects no data (the data
 * is protected by Firestore/Storage rules and server checks).
 */
object DeveloperAccess {
    private val TEAM_EMAILS = setOf("priyanshu@nirogbhumi.com", "gk@nirogbhumi.com")

    fun isDeveloper(email: String?, emailVerified: Boolean): Boolean =
        emailVerified && email?.trim()?.lowercase() in TEAM_EMAILS
}
