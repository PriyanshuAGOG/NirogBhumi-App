package com.nirogbhumi.app.update

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Deliberately independent of HealthRepository (the app's general Firestore
 * boundary): appUpdates/{channel} is public-read by design (see
 * firebase/firestore.rules) and needs to work even before/without sign-in,
 * unlike everything HealthRepository exposes.
 */
object UpdateRepository {
    suspend fun fetchLatest(channel: UpdateChannelOption): Result<UpdateInfo> =
        suspendCancellableCoroutine { continuation ->
            val app = runCatching { FirebaseApp.getInstance() }.getOrNull()
            if (app == null) {
                continuation.resume(Result.failure(IllegalStateException("Firebase is not configured")))
                return@suspendCancellableCoroutine
            }
            FirebaseFirestore.getInstance(app)
                .collection("appUpdates")
                .document(channel.id)
                .get()
                .addOnSuccessListener { snap ->
                    val info = snap.data?.let { UpdateInfo.fromMap(channel.id, it) }
                    if (info != null) {
                        continuation.resume(Result.success(info))
                    } else {
                        continuation.resume(Result.failure(IllegalStateException("No release published for the ${channel.label} channel yet")))
                    }
                }
                .addOnFailureListener { error ->
                    continuation.resume(Result.failure(error))
                }
        }
}
