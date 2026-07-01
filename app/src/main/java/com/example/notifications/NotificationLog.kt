package com.nirogbhumi.app.notifications

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Records that a notification happened (fired locally, or arrived via FCM) so the
 * in-app notification inbox has real history to show instead of nothing. Both the
 * local reminder worker and the FCM messaging service call this after they post the
 * actual system notification, since a client only ever writes its own history entry.
 */
object NotificationLog {
    fun record(category: String, title: String, body: String, route: String) {
        val app = runCatching { FirebaseApp.getInstance() }.getOrNull() ?: return
        val uid = FirebaseAuth.getInstance(app).currentUser?.uid ?: return
        FirebaseFirestore.getInstance(app).collection("notifications").add(
            mapOf(
                "userId" to uid,
                "category" to category,
                "title" to title,
                "body" to body,
                "route" to route,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
    }
}
