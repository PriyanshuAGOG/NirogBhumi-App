package com.nirogbhumi.app.data

// Plain Kotlin cloud types (no Firebase/Android imports) shared by the repository, the health store and tests.

/** Shown when the server refuses a correction (window over, or not a reading the member may edit). */
const val CORRECTION_REFUSED = "This entry can no longer be changed. Entries can be edited for 60 minutes after you log them."

data class CloudDocument(val id: String, val values: Map<String, Any?>)
fun interface CloudSubscription { fun cancel() }

/**
 * A live result plus what Firestore knows about its freshness: [fromCache] means the server has
 * not confirmed it (offline or still loading), [hasPendingWrites] means it includes this device's
 * own writes the server has not acknowledged yet. Timestamps are already plain java.util.Date.
 */
data class HealthSnapshot(val documents: List<CloudDocument>, val fromCache: Boolean, val hasPendingWrites: Boolean)

sealed interface CloudResult<out T> {
    data class Success<T>(val value: T) : CloudResult<T>
    data class Failure(val message: String, val cause: Throwable? = null) : CloudResult<Nothing>
}

/**
 * The slice of the repository the health store needs. Kept small so the store can be tested with a
 * ten-line fake instead of the full (sixty-method) HealthRepository.
 */
interface HealthLogBackend {
    val userId: String?
    fun listenHealthSnapshot(collection: String, limit: Long, update: (CloudResult<HealthSnapshot>) -> Unit): CloudSubscription
    fun addHealthLog(collection: String, values: Map<String, Any?>, done: (CloudResult<String>) -> Unit)
    fun updateHealthLog(collection: String, documentId: String, values: Map<String, Any?>, done: (CloudResult<Unit>) -> Unit)
}

/** A short-lived link to a completed data export, or only its storage path when links cannot be signed yet. */
data class ExportLink(val url: String?, val storagePath: String)
