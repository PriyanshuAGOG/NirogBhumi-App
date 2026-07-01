package com.nirogbhumi.app.data

import android.net.Uri
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.functions.FirebaseFunctions
import java.util.UUID

data class CloudDocument(val id: String, val values: Map<String, Any?>)
fun interface CloudSubscription { fun cancel() }

sealed interface CloudResult<out T> {
    data class Success<T>(val value: T) : CloudResult<T>
    data class Failure(val message: String, val cause: Throwable? = null) : CloudResult<Nothing>
}

/** The single cloud boundary used by UI/view-models. It is safe to construct without Firebase credentials. */
interface HealthRepository {
    val isCloudConfigured: Boolean
    val userId: String?
    fun saveProfile(values: Map<String, Any?>, done: (CloudResult<Unit>) -> Unit)
    fun addHealthLog(collection: String, values: Map<String, Any?>, done: (CloudResult<String>) -> Unit)
    fun uploadPrivateFile(folder: String, uri: Uri, done: (CloudResult<String>) -> Unit)
    fun listenUserCollection(collection: String, limit: Long = 30, update: (CloudResult<List<CloudDocument>>) -> Unit): CloudSubscription
    fun listenPublicCollection(collection: String, limit: Long = 30, update: (CloudResult<List<CloudDocument>>) -> Unit): CloudSubscription
    fun requestDataExport(done: (CloudResult<Unit>) -> Unit)
    fun requestAccountDeletion(done: (CloudResult<Unit>) -> Unit)
    fun createPaymentOrder(kind: String, entityId: String, done: (CloudResult<Map<String, Any?>>) -> Unit)
    fun upsertUserRecord(collection: String, documentId: String, values: Map<String, Any?>, done: (CloudResult<Unit>) -> Unit = {})
    fun getPrivateDownloadUrl(storagePath: String, done: (CloudResult<String>) -> Unit)
    fun redeemProgramCode(code: String, done: (CloudResult<Map<String, Any?>>) -> Unit)

    // Care+ (program members only): one shared announcement feed, plus one chat room
    // per program so members only see conversation relevant to the program they joined.
    fun listenAnnouncements(update: (CloudResult<List<CloudDocument>>) -> Unit): CloudSubscription
    fun postAnnouncement(title: String, body: String, done: (CloudResult<Unit>) -> Unit)
    fun listenProgramChat(programId: String, update: (CloudResult<List<CloudDocument>>) -> Unit): CloudSubscription
    fun sendProgramChatMessage(programId: String, text: String, senderName: String, done: (CloudResult<Unit>) -> Unit)
    fun reportChatMessage(messageId: String, programId: String, reportedText: String, reportedUserId: String, done: (CloudResult<Unit>) -> Unit)
}

class FirebaseHealthRepository : HealthRepository {
    private val app get() = runCatching { FirebaseApp.getInstance() }.getOrNull()
    private val auth get() = app?.let(FirebaseAuth::getInstance)
    private val db get() = app?.let(FirebaseFirestore::getInstance)
    private val storage get() = app?.let(FirebaseStorage::getInstance)
    private val functions get() = app?.let { FirebaseFunctions.getInstance(it, "asia-south1") }

    override val isCloudConfigured get() = app != null
    override val userId get() = auth?.currentUser?.uid

    override fun saveProfile(values: Map<String, Any?>, done: (CloudResult<Unit>) -> Unit) {
        val uid = userId ?: return done(CloudResult.Failure("Sign in is required"))
        val payload = values + mapOf("userId" to uid, "updatedAt" to FieldValue.serverTimestamp())
        db?.collection("users")?.document(uid)?.set(payload, SetOptions.merge())
            ?.addOnSuccessListener { done(CloudResult.Success(Unit)) }
            ?.addOnFailureListener { done(CloudResult.Failure(it.message ?: "Profile could not be saved", it)) }
            ?: done(CloudResult.Failure("Firebase is not configured"))
    }

    override fun addHealthLog(collection: String, values: Map<String, Any?>, done: (CloudResult<String>) -> Unit) {
        val allowed = setOf(
            "profiles", "glucoseReadings", "bpReadings", "sleepLogs", "walkLogs", "weightLogs",
            "labReports", "consultations", "orders", "checklistLogs", "supportRequests", "notifications"
        )
        if (collection !in allowed) return done(CloudResult.Failure("Unsupported health log"))
        val uid = userId ?: return done(CloudResult.Failure("Sign in is required"))
        val ref = db?.collection(collection)?.document()
            ?: return done(CloudResult.Failure("Firebase is not configured"))
        ref.set(values + mapOf("userId" to uid, "profileId" to (values["profileId"] ?: uid), "createdAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { done(CloudResult.Success(ref.id)) }
            .addOnFailureListener { done(CloudResult.Failure(it.message ?: "Log could not be saved", it)) }
    }

    override fun uploadPrivateFile(folder: String, uri: Uri, done: (CloudResult<String>) -> Unit) {
        val uid = userId ?: return done(CloudResult.Failure("Sign in is required"))
        val path = "users/$uid/$folder/${UUID.randomUUID()}"
        val ref = storage?.reference?.child(path) ?: return done(CloudResult.Failure("Firebase is not configured"))
        ref.putFile(uri).continueWithTask { task ->
            if (!task.isSuccessful) throw task.exception ?: IllegalStateException("Upload failed")
            ref.downloadUrl
        }.addOnSuccessListener { done(CloudResult.Success(it.toString())) }
            .addOnFailureListener { done(CloudResult.Failure(it.message ?: "Upload failed", it)) }
    }

    override fun listenUserCollection(collection: String, limit: Long, update: (CloudResult<List<CloudDocument>>) -> Unit): CloudSubscription {
        val uid = userId ?: run { update(CloudResult.Failure("Sign in is required")); return CloudSubscription {} }
        val allowed = setOf("profiles", "glucoseReadings", "bpReadings", "sleepLogs", "walkLogs", "weightLogs", "labReports", "dailyActions", "weeklyReports", "sugarStories", "consultations", "userPrograms", "programPlans", "checklistLogs", "expertNotes", "notifications", "deviceConnections", "orders", "supportRequests", "dataExportRequests", "deletionRequests")
        if (collection !in allowed) { update(CloudResult.Failure("Unsupported collection")); return CloudSubscription {} }
        val query = db?.collection(collection)?.whereEqualTo("userId", uid)?.limit(limit)
            ?: run { update(CloudResult.Failure("Firebase is not configured")); return CloudSubscription {} }
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) update(CloudResult.Failure(error.message ?: "Could not load data", error))
            else update(CloudResult.Success(snapshot?.documents.orEmpty().map { CloudDocument(it.id, it.data.orEmpty()) }))
        }
        return CloudSubscription { registration.remove() }
    }

    override fun listenPublicCollection(collection: String, limit: Long, update: (CloudResult<List<CloudDocument>>) -> Unit): CloudSubscription {
        if (collection !in setOf("contentItems", "products", "programs", "consultationSlots")) { update(CloudResult.Failure("Unsupported public collection")); return CloudSubscription {} }
        val base = db?.collection(collection)
            ?: run { update(CloudResult.Failure("Firebase is not configured")); return CloudSubscription {} }
        val query = when (collection) {
            "contentItems" -> base.whereEqualTo("status", "published").limit(limit)
            "products" -> base.whereEqualTo("active", true).limit(limit)
            "consultationSlots" -> base.whereEqualTo("active", true).limit(limit)
            else -> base.limit(limit)
        }
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) update(CloudResult.Failure(error.message ?: "Could not load data", error))
            else update(CloudResult.Success(snapshot?.documents.orEmpty().map { CloudDocument(it.id, it.data.orEmpty()) }))
        }
        return CloudSubscription { registration.remove() }
    }

    override fun requestDataExport(done: (CloudResult<Unit>) -> Unit) = createRequest("dataExportRequests", done)
    override fun requestAccountDeletion(done: (CloudResult<Unit>) -> Unit) = createRequest("deletionRequests", done)

    override fun createPaymentOrder(kind: String, entityId: String, done: (CloudResult<Map<String, Any?>>) -> Unit) {
        val callable = functions?.getHttpsCallable("createPaymentOrder") ?: return done(CloudResult.Failure("Firebase is not configured"))
        callable.call(mapOf("kind" to kind, "entityId" to entityId))
            .addOnSuccessListener { result ->
                @Suppress("UNCHECKED_CAST")
                done(CloudResult.Success(result.data as? Map<String, Any?> ?: emptyMap()))
            }
            .addOnFailureListener { done(CloudResult.Failure(it.message ?: "Payment could not be initialized", it)) }
    }

    override fun getPrivateDownloadUrl(storagePath: String, done: (CloudResult<String>) -> Unit) {
        val uid = userId ?: return done(CloudResult.Failure("Sign in is required"))
        if (!storagePath.startsWith("users/$uid/")) return done(CloudResult.Failure("Invalid private file path"))
        storage?.reference?.child(storagePath)?.downloadUrl
            ?.addOnSuccessListener { done(CloudResult.Success(it.toString())) }
            ?.addOnFailureListener { done(CloudResult.Failure(it.message ?: "Export could not be opened", it)) }
            ?: done(CloudResult.Failure("Firebase is not configured"))
    }

    // Validated directly against the public "programs" collection instead of a Cloud
    // Function - this project's Cloud Functions can't be deployed without Blaze billing,
    // and a client-side lookup plus a self-scoped profile write is a reasonable tradeoff
    // for a small, invite-only beta rather than leaving enrollment entirely broken.
    override fun redeemProgramCode(code: String, done: (CloudResult<Map<String, Any?>>) -> Unit) {
        val uid = userId ?: return done(CloudResult.Failure("Sign in is required"))
        val database = db ?: return done(CloudResult.Failure("Firebase is not configured"))
        database.collection("programs")
            .whereEqualTo("code", code.trim().uppercase())
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val programDoc = snapshot.documents.firstOrNull()
                if (programDoc == null) {
                    done(CloudResult.Failure("That program code wasn't recognized"))
                    return@addOnSuccessListener
                }
                val programId = programDoc.id
                val programName = programDoc.getString("name") ?: "Nirog Bhumi Program"
                val durationDays = (programDoc.get("durationDays") as? Number)?.toLong() ?: 0L
                val enrollment = mapOf(
                    "programActive" to true,
                    "activeProgramId" to programId,
                    "activeProgramName" to programName,
                    "programDurationDays" to durationDays,
                    "programStartedAt" to FieldValue.serverTimestamp()
                )
                database.collection("users").document(uid).set(enrollment, SetOptions.merge())
                    .addOnSuccessListener { done(CloudResult.Success(enrollment)) }
                    .addOnFailureListener { done(CloudResult.Failure(it.message ?: "Enrollment could not be saved", it)) }
            }
            .addOnFailureListener { done(CloudResult.Failure(it.message ?: "Program code could not be verified", it)) }
    }

    override fun listenAnnouncements(update: (CloudResult<List<CloudDocument>>) -> Unit): CloudSubscription {
        val database = db ?: run { update(CloudResult.Failure("Firebase is not configured")); return CloudSubscription {} }
        val registration = database.collection("announcements")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) update(CloudResult.Failure(error.message ?: "Could not load announcements", error))
                else update(CloudResult.Success(snapshot?.documents.orEmpty().map { CloudDocument(it.id, it.data.orEmpty()) }))
            }
        return CloudSubscription { registration.remove() }
    }

    override fun postAnnouncement(title: String, body: String, done: (CloudResult<Unit>) -> Unit) {
        val uid = userId ?: return done(CloudResult.Failure("Sign in is required"))
        val database = db ?: return done(CloudResult.Failure("Firebase is not configured"))
        database.collection("announcements").add(
            mapOf(
                "title" to title,
                "body" to body,
                "authorId" to uid,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).addOnSuccessListener { done(CloudResult.Success(Unit)) }
            .addOnFailureListener { done(CloudResult.Failure(it.message ?: "Announcement could not be posted", it)) }
    }

    override fun listenProgramChat(programId: String, update: (CloudResult<List<CloudDocument>>) -> Unit): CloudSubscription {
        val database = db ?: run { update(CloudResult.Failure("Firebase is not configured")); return CloudSubscription {} }
        val registration = database.collection("programChatMessages")
            .whereEqualTo("programId", programId)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) update(CloudResult.Failure(error.message ?: "Could not load messages", error))
                else update(CloudResult.Success(snapshot?.documents.orEmpty().map { CloudDocument(it.id, it.data.orEmpty()) }))
            }
        return CloudSubscription { registration.remove() }
    }

    override fun sendProgramChatMessage(programId: String, text: String, senderName: String, done: (CloudResult<Unit>) -> Unit) {
        val uid = userId ?: return done(CloudResult.Failure("Sign in is required"))
        val database = db ?: return done(CloudResult.Failure("Firebase is not configured"))
        database.collection("programChatMessages").add(
            mapOf(
                "programId" to programId,
                "userId" to uid,
                "senderName" to senderName,
                "text" to text,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).addOnSuccessListener { done(CloudResult.Success(Unit)) }
            .addOnFailureListener { done(CloudResult.Failure(it.message ?: "Message could not be sent", it)) }
    }

    override fun reportChatMessage(messageId: String, programId: String, reportedText: String, reportedUserId: String, done: (CloudResult<Unit>) -> Unit) {
        val uid = userId ?: return done(CloudResult.Failure("Sign in is required"))
        val database = db ?: return done(CloudResult.Failure("Firebase is not configured"))
        database.collection("reportedMessages").add(
            mapOf(
                "messageId" to messageId,
                "programId" to programId,
                "reportedText" to reportedText,
                "reportedUserId" to reportedUserId,
                "reporterId" to uid,
                "status" to "open",
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).addOnSuccessListener { done(CloudResult.Success(Unit)) }
            .addOnFailureListener { done(CloudResult.Failure(it.message ?: "Report could not be submitted", it)) }
    }

    override fun upsertUserRecord(collection: String, documentId: String, values: Map<String, Any?>, done: (CloudResult<Unit>) -> Unit) {
        val allowed = setOf("glucoseReadings", "bpReadings", "sleepLogs", "walkLogs", "weightLogs", "deviceConnections")
        if (collection !in allowed) return done(CloudResult.Failure("Unsupported synced record"))
        val uid = userId ?: return done(CloudResult.Failure("Sign in is required"))
        val normalizedId = documentId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(120)
        val safeId = if (collection == "deviceConnections") "${uid}_$normalizedId" else normalizedId
        db?.collection(collection)?.document(safeId)?.set(values + mapOf("userId" to uid, "profileId" to (values["profileId"] ?: uid), "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            ?.addOnSuccessListener { done(CloudResult.Success(Unit)) }
            ?.addOnFailureListener { done(CloudResult.Failure(it.message ?: "Synced record could not be saved", it)) }
            ?: done(CloudResult.Failure("Firebase is not configured"))
    }

    private fun createRequest(collection: String, done: (CloudResult<Unit>) -> Unit) {
        val uid = userId ?: return done(CloudResult.Failure("Sign in is required"))
        db?.collection(collection)?.add(mapOf("userId" to uid, "status" to "requested", "createdAt" to FieldValue.serverTimestamp()))
            ?.addOnSuccessListener { done(CloudResult.Success(Unit)) }
            ?.addOnFailureListener { done(CloudResult.Failure(it.message ?: "Request failed", it)) }
            ?: done(CloudResult.Failure("Firebase is not configured"))
    }
}
