package com.nirogbhumi.app.data

import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Thin wrapper around FirebaseAnalytics for the handful of product events
 * worth tracking beyond the automatic screen_view already logged in
 * MainActivity. Never throws and never blocks a repository call on
 * analytics - a dropped event should never be visible to the user as a
 * failed check-in, message, etc.
 */
object AnalyticsLogger {
    private val analytics: FirebaseAnalytics?
        get() = runCatching { FirebaseAnalytics.getInstance(FirebaseApp.getInstance().applicationContext) }.getOrNull()

    fun log(event: String, params: Map<String, Any?> = emptyMap()) {
        runCatching {
            val bundle = android.os.Bundle()
            params.forEach { (key, value) ->
                when (value) {
                    is String -> bundle.putString(key, value)
                    is Int -> bundle.putInt(key, value)
                    is Long -> bundle.putLong(key, value)
                    is Double -> bundle.putDouble(key, value)
                    is Boolean -> bundle.putString(key, value.toString())
                    null -> Unit
                    else -> bundle.putString(key, value.toString())
                }
            }
            analytics?.logEvent(event, bundle)
        }
    }
}
