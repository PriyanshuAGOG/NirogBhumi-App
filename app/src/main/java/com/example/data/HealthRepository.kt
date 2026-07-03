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
    fun deleteUserRecord(collection: String, documentId: String, done: (CloudResult<Unit>) -> Unit)
    fun getPrivateDownloadUrl(storagePath: String, done: (CloudResult<String>) -> Unit)
    fun redeemProgramCode(code: String, done: (CloudResult<Map<String, Any?>>) -> Unit)

    // Care+ (program members only): one shared announcement feed, plus one chat room
    // per program so members only see conversation relevant to the program they joined.
    fun listenAnnouncements(programId: String, update: (CloudResult<List<CloudDocument>>) -> Unit): CloudSubscription
    fun postAnnouncement(programId: String, title: String, body: String, done: (CloudResult<Unit>) -> Unit)
    fun listenProgramChat(programId: String, update: (CloudResult<List<CloudDocument>>) -> Unit): CloudSubscription
    fun sendProgramChatMessage(
        programId: String,
        text: String,
        senderName: String,
        replyToId: String? = null,
        replyToSender: String? = null,
        replyToText: String? = null,
        done: (CloudResult<Unit>) -> Unit,
    )
    fun reportChatMessage(messageId: String, programId: String, reportedText: String, reportedUserId: String, done: (CloudResult<Unit>) -> Unit)
    // Toggles the caller's own reaction on a message - add=true unions their uid
    // into reactions.<emoji>, add=false removes it. Never touches message text.
    fun toggleChatReaction(messageId: String, emoji: String, add: Boolean, done: (CloudResult<Unit>) -> Unit)

    // Batch Pulse: today's PII-free "N of M checked in" + collective walking
    // minutes for the caller's program. Written only by Cloud Functions.
    fun listenBatchPulse(programId: String, update: (CloudResult<CloudDocument?>) -> Unit): CloudSubscription

    // Program calendar events (created/edited by staff in the admin console).
    // Members only ever read these - editing is console-only, per the PRD.
    fun listenProgramEvents(programId: String, update: (CloudResult<List<CloudDocument>>) -> Unit): CloudSubscription
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

    // Goes through the redeemProgramCode Cloud Function rather than a direct
    // client-side lookup + self-scoped write: "programs" now requires staff
    // access to read (its code field would otherwise let any signed-in user
    // list every program's invite code and self-enroll), and
    // "programMembers" is staff-write-only (otherwise a member could plant a
    // roster entry with forged consistency stats). The function validates
    // the code and performs both writes itself under the Admin SDK.
    override fun redeemProgramCode(code: String, done: (CloudResult<Map<String, Any?>>) -> Unit) {
        val callable = functions?.getHttpsCallable("redeemProgramCode")
            ?: return done(CloudResult.Failure("Firebase is not configured"))
        callable.call(mapOf("code" to code.trim().uppercase()))
            .addOnSuccessListener { result ->
                @Suppress("UNCHECKED_CAST")
                val value = result.data as? Map<String, Any?> ?: emptyMap()
                done(CloudResult.Success(value))
            }
            .addOnFailureListener { done(CloudResult.Failure(it.message ?: "That program code wasn't recognized", it)) }
    }

    override fun listenAnnouncements(programId: String, update: (CloudResult<List<CloudDocument>>) -> Unit): CloudSubscription {
        val database = db ?: run { update(CloudResult.Failure("Firebase is not configured")); return CloudSubscription {} }
        // Scoped to the caller's own program - without this filter, members of
        // different programs would see each other's announcements mixed together.
        val registration = database.collection("announcements")
            .whereEqualTo("programId", programId)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) update(CloudResult.Failure(error.message ?: "Could not load announcements", error))
                else update(CloudResult.Success(snapshot?.documents.orEmpty().map { CloudDocument(it.id, it.data.orEmpty()) }))
            }
        return CloudSubscription { registration.remove() }
    }

    override fun postAnnouncement(programId: String, title: String, body: String, done: (CloudResult<Unit>) -> Unit) {
        val uid = userId ?: return done(CloudResult.Failure("Sign in is required"))
        val database = db ?: return done(CloudResult.Failure("Firebase is not configured"))
        database.collection("announcements").add(
            mapOf(
                "programId" to programId,
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

    override fun sendProgramChatMessage(
        programId: String,
        text: String,
        senderName: String,
        replyToId: String?,
        replyToSender: String?,
        replyToText: String?,
        done: (CloudResult<Unit>) -> Unit,
    ) {
        val uid = userId ?: return done(CloudResult.Failure("Sign in is required"))
        val database = db ?: return done(CloudResult.Failure("Firebase is not configured"))
        val replyTo = if (replyToId != null) mapOf(
            "id" to replyToId,
            "sender" to replyToSender,
            // Quoted preview only - trimmed so a reply can't smuggle an
            // unbounded copy of an old message into every new one.
            "text" to replyToText?.take(160),
        ) else null
        database.collection("programChatMessages").add(
            mapOf(
                "programId" to programId,
                "userId" to uid,
                "senderName" to senderName,
                "text" to text,
                "replyTo" to replyTo,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).addOnSuccessListener { done(CloudResult.Success(Unit)) }
            .addOnFailureListener { done(CloudResult.Failure(it.message ?: "Message could not be sent", it)) }
    }

    override fun toggleChatReaction(messageId: String, emoji: String, add: Boolean, done: (CloudResult<Unit>) -> Unit) {
        val uid = userId ?: return done(CloudResult.Failure("Sign in is required"))
        val database = db ?: return done(CloudResult.Failure("Firebase is not configured"))
        val change = if (add) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid)
        database.collection("programChatMessages").document(messageId)
            .update("reactions.$emoji", change)
            .addOnSuccessListener { done(CloudResult.Success(Unit)) }
            .addOnFailureListener { done(CloudResult.Failure(it.message ?: "Reaction could not be saved", it)) }
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

    override fun listenBatchPulse(programId: String, update: (CloudResult<CloudDocument?>) -> Unit): CloudSubscription {
        val database = db ?: run { update(CloudResult.Failure("Firebase is not configured")); return CloudSubscription {} }
        // Doc id must match the Cloud Functions' Asia/Kolkata day bucket exactly,
        // since that's what recordBatchCheckin() and the daily aggregation write to.
        val dayKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        }.format(java.util.Date())
        val registration = database.collection("batchStats").document("${programId}_$dayKey")
            .addSnapshotListener { snapshot, error ->
                if (error != null) update(CloudResult.Failure(error.message ?: "Could not load batch pulse", error))
                else update(CloudResult.Success(snapshot?.takeIf { it.exists() }?.let { CloudDocument(it.id, it.data.orEmpty()) }))
            }
        return CloudSubscription { registration.remove() }
    }

    override fun listenProgramEvents(programId: String, update: (CloudResult<List<CloudDocument>>) -> Unit): CloudSubscription {
        val database = db ?: run { update(CloudResult.Failure("Firebase is not configured")); return CloudSubscription {} }
        val registration = database.collection("programEvents")
            .whereEqualTo("programId", programId)
            .orderBy("startsAt", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) update(CloudResult.Failure(error.message ?: "Could not load the program calendar", error))
                else update(CloudResult.Success(snapshot?.documents.orEmpty().map { CloudDocument(it.id, it.data.orEmpty()) }))
            }
        return CloudSubscription { registration.remove() }
    }

    override fun upsertUserRecord(collection: String, documentId: String, values: Map<String, Any?>, done: (CloudResult<Unit>) -> Unit) {
        val allowed = setOf("glucoseReadings", "bpReadings", "sleepLogs", "walkLogs", "weightLogs", "deviceConnections", "checklistLogs")
        if (collection !in allowed) return done(CloudResult.Failure("Unsupported synced record"))
        val uid = userId ?: return done(CloudResult.Failure("Sign in is required"))
        val normalizedId = documentId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(120)
        // Always uid-prefixed: the id is otherwise just a caller-chosen string
        // (e.g. a stable per-day key), and without this prefix two different
        // users' upserts with the same id would silently overwrite each other
        // in the same document, since Firestore document ids aren't implicitly
        // scoped per user the way the userId field is.
        val safeId = "${uid}_$normalizedId"
        db?.collection(collection)?.document(safeId)?.set(values + mapOf("userId" to uid, "profileId" to (values["profileId"] ?: uid), "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            ?.addOnSuccessListener { done(CloudResult.Success(Unit)) }
            ?.addOnFailureListener { done(CloudResult.Failure(it.message ?: "Synced record could not be saved", it)) }
            ?: done(CloudResult.Failure("Firebase is not configured"))
    }

    override fun deleteUserRecord(collection: String, documentId: String, done: (CloudResult<Unit>) -> Unit) {
        val allowed = setOf("profiles", "labReports", "walkLogs")
        if (collection !in allowed) return done(CloudResult.Failure("Unsupported record"))
        if (userId == null) return done(CloudResult.Failure("Sign in is required"))
        db?.collection(collection)?.document(documentId)?.delete()
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
