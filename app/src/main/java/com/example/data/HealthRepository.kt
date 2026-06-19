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
            "profiles", "glucoseReadings", "bpReadings", "sleepLogs", "walkLogs", "weightLogs", "waterLogs",
            "mealLogs", "medications", "medicationLogs", "labReports", "consultations", "orders", "checklistLogs", "supportRequests"
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
        val allowed = setOf("profiles", "glucoseReadings", "bpReadings", "sleepLogs", "walkLogs", "weightLogs", "waterLogs", "mealLogs", "medications", "medicationLogs", "labReports", "dailyActions", "weeklyReports", "sugarStories", "consultations", "userPrograms", "programPlans", "checklistLogs", "expertNotes", "notifications", "deviceConnections", "carts", "orders", "supportRequests", "dataExportRequests", "deletionRequests")
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

    override fun redeemProgramCode(code: String, done: (CloudResult<Map<String, Any?>>) -> Unit) {
        val callable = functions?.getHttpsCallable("redeemProgramCode") ?: return done(CloudResult.Failure("Firebase is not configured"))
        callable.call(mapOf("code" to code.trim().uppercase()))
            .addOnSuccessListener { result ->
                @Suppress("UNCHECKED_CAST")
                done(CloudResult.Success(result.data as? Map<String, Any?> ?: emptyMap()))
            }
            .addOnFailureListener { done(CloudResult.Failure(it.message ?: "Program code could not be verified", it)) }
    }

    override fun upsertUserRecord(collection: String, documentId: String, values: Map<String, Any?>, done: (CloudResult<Unit>) -> Unit) {
        val allowed = setOf("glucoseReadings", "bpReadings", "sleepLogs", "walkLogs", "weightLogs", "deviceConnections")
        if (collection !in allowed) return done(CloudResult.Failure("Unsupported synced record"))
        val uid = userId ?: return done(CloudResult.Failure("Sign in is required"))
        val safeId = documentId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(120)
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
