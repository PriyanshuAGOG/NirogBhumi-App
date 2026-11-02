package com.nirogbhumi.app.data

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import java.util.concurrent.TimeUnit

object FirebaseAuthGateway {
    private fun auth() = runCatching { FirebaseAuth.getInstance(FirebaseApp.getInstance()) }.getOrNull()

    fun googleSignInIntent(activity: Activity, onError: (String) -> Unit): Intent? {
        val clientIdRes = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
        if (clientIdRes == 0) {
            onError("Firebase Google Sign-in is not configured. Add google-services.json generated for this Android app.")
            return null
        }
        val clientId = activity.getString(clientIdRes)
        if (clientId.isBlank()) {
            onError("Firebase Google Sign-in is not configured. Add google-services.json generated for this Android app.")
            return null
        }
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(activity, options).signInIntent
    }

    fun google(data: Intent?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val auth = auth() ?: return onError("Firebase is not configured")
        GoogleSignIn.getSignedInAccountFromIntent(data)
            .addOnSuccessListener { account ->
                val token = account.idToken ?: return@addOnSuccessListener onError("Google Sign-in did not return an ID token")
                val credential = GoogleAuthProvider.getCredential(token, null)
                auth.signInWithCredential(credential)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(it.message ?: "Google Sign-in failed") }
            }
            .addOnFailureListener { onError(it.message ?: "Google Sign-in cancelled") }
    }

    /** Logs the real reason (no phone number) for logcat and Crashlytics, and returns what the member should read. */
    private fun otpFailureText(error: FirebaseException): String {
        val code = (error as? FirebaseAuthException)?.errorCode
        Log.w("NirogAuth", "Phone verification failed: ${error.javaClass.simpleName} code=$code message=${error.message}")
        return AuthMessages.otpFailure(code, error.message)
    }

    fun sendOtp(activity: Activity, phone: String, onSent: (String) -> Unit, onError: (String) -> Unit) {
        val auth = auth() ?: return onError("Firebase is not configured. Add google-services.json for this app.")
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                auth.signInWithCredential(credential).addOnSuccessListener { onSent("AUTO_VERIFIED") }
                    .addOnFailureListener { onError(it.message ?: "Automatic verification failed") }
            }
            override fun onVerificationFailed(error: FirebaseException) = onError(otpFailureText(error))
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
            .addOnFailureListener { onError(AuthMessages.codeCheckFailure((it as? FirebaseAuthException)?.errorCode, it.message)) }
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

    // ---- confirming it is really the member (before something irreversible) ----

    fun signInMethod(): com.nirogbhumi.app.ui.SignInMethod =
        com.nirogbhumi.app.ui.SignInMethods.from(auth()?.currentUser?.providerData?.map { it.providerId }.orEmpty())

    fun currentEmail(): String? = auth()?.currentUser?.email
    fun currentPhone(): String? = auth()?.currentUser?.phoneNumber

    /** Re-checks the password, then refreshes the sign-in token so the server sees a recent sign-in. */
    fun reauthWithPassword(password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = auth()?.currentUser ?: return onError("Sign in is required")
        val email = user.email ?: return onError("This account has no email address")
        user.reauthenticate(com.google.firebase.auth.EmailAuthProvider.getCredential(email, password))
            .addOnSuccessListener { user.getIdToken(true).addOnSuccessListener { onSuccess() }.addOnFailureListener { onError(it.message ?: "Please try again") } }
            .addOnFailureListener { onError("That password didn't match. Please try again.") }
    }

    /** Sends a fresh SMS code to the number on this account. `onSent` gets the verification id ("AUTO_VERIFIED" when the phone verified itself). */
    fun sendReauthOtp(activity: Activity, onSent: (String) -> Unit, onError: (String) -> Unit) {
        val auth = auth() ?: return onError("Firebase is not configured")
        val user = auth.currentUser ?: return onError("Sign in is required")
        val phone = user.phoneNumber ?: return onError("This account has no phone number")
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                user.reauthenticate(credential).addOnSuccessListener { onSent("AUTO_VERIFIED") }.addOnFailureListener { onError(it.message ?: "Automatic verification failed") }
            }
            override fun onVerificationFailed(error: FirebaseException) = onError(otpFailureText(error))
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) = onSent(id)
        }
        PhoneAuthProvider.verifyPhoneNumber(
            PhoneAuthOptions.newBuilder(auth).setPhoneNumber(phone).setTimeout(60, TimeUnit.SECONDS).setActivity(activity).setCallbacks(callbacks).build()
        )
    }

    fun reauthWithOtp(id: String, code: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = auth()?.currentUser ?: return onError("Sign in is required")
        if (id == "AUTO_VERIFIED") {
            user.getIdToken(true).addOnSuccessListener { onSuccess() }.addOnFailureListener { onError(it.message ?: "Please try again") }
            return
        }
        if (id.isBlank()) return onError("Ask for a new code first")
        user.reauthenticate(PhoneAuthProvider.getCredential(id, code))
            .addOnSuccessListener { user.getIdToken(true).addOnSuccessListener { onSuccess() }.addOnFailureListener { onError(it.message ?: "Please try again") } }
            .addOnFailureListener { onError("That code didn't match. Please try again.") }
    }

    // ---- one account, both sign-in methods ----

    fun missingContact(): MissingContact {
        val user = auth()?.currentUser ?: return MissingContact.NONE
        return AccountContacts.missing(user.providerData.map { it.providerId }, user.phoneNumber, user.email)
    }

    private fun failCode(error: Exception): String? = (error as? FirebaseAuthException)?.errorCode

    /** For an account created with the phone number: attaches an email and password, so the email also signs in to it. */
    fun linkEmailPassword(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = auth()?.currentUser ?: return onError("Please sign in again.")
        user.linkWithCredential(com.google.firebase.auth.EmailAuthProvider.getCredential(email.trim(), password))
            .addOnSuccessListener {
                runCatching { it.user?.sendEmailVerification() }   // best effort; the account works without it
                onSuccess()
            }
            .addOnFailureListener {
                Log.w("NirogAuth", "Linking email failed: code=${failCode(it)} message=${it.message}")
                onError(AuthMessages.linkFailure(failCode(it), it.message, "email"))
            }
    }

    /** Sends an SMS code to a number that should be attached to the signed-in account. `onSent` gets the verification id ("AUTO_VERIFIED" if the phone confirmed itself and the number is already attached). */
    fun sendLinkPhoneOtp(activity: Activity, phone: String, onSent: (String) -> Unit, onError: (String) -> Unit) {
        val auth = auth() ?: return onError("Firebase is not configured")
        val user = auth.currentUser ?: return onError("Please sign in again.")
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                user.linkWithCredential(credential).addOnSuccessListener { onSent("AUTO_VERIFIED") }
                    .addOnFailureListener { onError(AuthMessages.linkFailure(failCode(it), it.message, "phone number")) }
            }
            override fun onVerificationFailed(error: FirebaseException) = onError(otpFailureText(error))
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) = onSent(id)
        }
        PhoneAuthProvider.verifyPhoneNumber(
            PhoneAuthOptions.newBuilder(auth).setPhoneNumber(phone).setTimeout(60, TimeUnit.SECONDS).setActivity(activity).setCallbacks(callbacks).build()
        )
    }

    /** Checks the SMS code and attaches the number to the signed-in account. */
    fun linkPhone(id: String, code: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = auth()?.currentUser ?: return onError("Please sign in again.")
        if (id == "AUTO_VERIFIED") return onSuccess()
        if (id.isBlank()) return onError("Ask for a new code first.")
        user.linkWithCredential(PhoneAuthProvider.getCredential(id, code))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener {
                Log.w("NirogAuth", "Linking phone failed: code=${failCode(it)} message=${it.message}")
                val c = failCode(it).orEmpty()
                onError(if (c == "ERROR_INVALID_VERIFICATION_CODE" || c == "ERROR_SESSION_EXPIRED") AuthMessages.codeCheckFailure(c, it.message) else AuthMessages.linkFailure(c, it.message, "phone number"))
            }
    }

    fun signOut() { auth()?.signOut() }
}
