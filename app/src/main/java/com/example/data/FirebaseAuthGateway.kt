package com.nirogbhumi.app.data

import android.app.Activity
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

object FirebaseAuthGateway {
    private fun auth() = runCatching { FirebaseAuth.getInstance(FirebaseApp.getInstance()) }.getOrNull()

    fun sendOtp(activity: Activity, phone: String, onSent: (String) -> Unit, onError: (String) -> Unit) {
        val auth = auth() ?: return onError("Firebase is not configured. Add google-services.json for this app.")
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                auth.signInWithCredential(credential).addOnSuccessListener { onSent("AUTO_VERIFIED") }
                    .addOnFailureListener { onError(it.message ?: "Automatic verification failed") }
            }
            override fun onVerificationFailed(error: FirebaseException) = onError(error.message ?: "OTP could not be sent")
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) = onSent(id)
        }
        PhoneAuthProvider.verifyPhoneNumber(
            PhoneAuthOptions.newBuilder(auth).setPhoneNumber(phone).setTimeout(60, TimeUnit.SECONDS)
                .setActivity(activity).setCallbacks(callbacks).build()
        )
    }

    fun verifyOtp(id: String, code: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val auth = auth() ?: return onError("Firebase is not configured")
        if (id.isBlank()) return onError("Request a new OTP first")
        if (id == "AUTO_VERIFIED") return onSuccess()
        auth.signInWithCredential(PhoneAuthProvider.getCredential(id, code))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Invalid OTP") }
    }

    fun email(email: String, password: String, create: Boolean, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val auth = auth() ?: return onError("Firebase is not configured")
        val task = if (create) auth.createUserWithEmailAndPassword(email, password) else auth.signInWithEmailAndPassword(email, password)
        task.addOnSuccessListener { onSuccess() }.addOnFailureListener { onError(it.message ?: "Authentication failed") }
    }

    fun resetPassword(email: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val auth = auth() ?: return onError("Firebase is not configured")
        auth.sendPasswordResetEmail(email).addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Reset email could not be sent") }
    }
}
