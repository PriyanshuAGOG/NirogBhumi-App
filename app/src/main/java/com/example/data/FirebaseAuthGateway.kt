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
                    .addOnFailureListener {
                        reportAuth("google", it.message ?: "Google Sign-in failed", it)
                        onError(it.message ?: "Google Sign-in failed")
                    }
            }
            .addOnFailureListener { onError(it.message ?: "Google Sign-in cancelled") }
    }

    /**
     * A build that did not come from the Play Store cannot pass Google's silent app check, so the SDK tries it, waits for it to
     * fail, and only then shows the "I'm not a robot" page. Tester builds go straight to that page instead, which removes the long
     * wait. Play Store builds keep the silent check (members never see a page).
     */
    private fun prepareForPhoneVerification(auth: FirebaseAuth, direct: Boolean) {
        if (com.nirogbhumi.app.BuildConfig.DEBUG) {
            runCatching { auth.firebaseAuthSettings.forceRecaptchaFlowForTesting(direct) }
                .onFailure { Log.w("NirogAuth", "Could not switch the human check mode: ${it.message}") }
        }
    }

    /**
     * Starts the SMS check. On a tester build the first try goes straight to the web human check; if Google refuses that for
     * a reason about the installation itself, it is tried once more the normal way before the member is told it failed.
     */
    private fun startPhoneVerification(
        auth: FirebaseAuth, activity: Activity, phone: String,
        onCompleted: (PhoneAuthCredential) -> Unit, onCodeSent: (String) -> Unit, onFailed: (String) -> Unit,
        retried: Boolean = false,
    ) {
        prepareForPhoneVerification(auth, direct = !retried)
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) = onCompleted(credential)
            override fun onVerificationFailed(error: FirebaseException) {
                val code = (error as? FirebaseAuthException)?.errorCode
                if (!retried && com.nirogbhumi.app.BuildConfig.DEBUG && AuthMessages.isAppVerificationProblem(code, error.message)) {
                    Log.w("NirogAuth", "Direct human check refused (${error.javaClass.simpleName} $code); retrying the normal way")
                    startPhoneVerification(auth, activity, phone, onCompleted, onCodeSent, onFailed, retried = true)
                } else {
                    onFailed(otpFailureText(error))
                }
            }
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) = onCodeSent(id)
        }
        PhoneAuthProvider.verifyPhoneNumber(
            PhoneAuthOptions.newBuilder(auth).setPhoneNumber(phone).setTimeout(60, TimeUnit.SECONDS).setActivity(activity).setCallbacks(callbacks).build()
        )
    }

    /** Sends one sign-in problem to the admin console (works before anyone is signed in). */
    private fun reportAuth(stage: String, friendly: String, error: Exception?) {
        val code = (error as? FirebaseAuthException)?.errorCode
        ClientErrorReporter.report("sign_in:$stage", friendly, code, AuthMessages.technicalDetail(error?.javaClass?.simpleName.orEmpty(), code, error?.message))
    }

    /** Logs the real reason (no phone number) for logcat and Crashlytics, and returns what the member should read. */
    private fun otpFailureText(error: FirebaseException): String {
        val code = (error as? FirebaseAuthException)?.errorCode
        Log.w("NirogAuth", "Phone verification failed: ${error.javaClass.simpleName} code=$code message=${error.message}")
        val friendly = AuthMessages.otpFailure(code, error.message)
        reportAuth("phone_send", friendly, error)
        // Tester builds also show the real reason, so a screenshot from a tester tells us exactly what Google answered.
        return if (com.nirogbhumi.app.BuildConfig.DEBUG) friendly + "\n\nFor the team: " + AuthMessages.technicalDetail(error.javaClass.simpleName, code, error.message) else friendly
    }

    fun sendOtp(activity: Activity, phone: String, onSent: (String) -> Unit, onError: (String) -> Unit) {
        val auth = auth() ?: return onError("Firebase is not configured. Add google-services.json for this app.")
        startPhoneVerification(auth, activity, phone,
            onCompleted = { credential ->
                auth.signInWithCredential(credential).addOnSuccessListener { onSent("AUTO_VERIFIED") }
                    .addOnFailureListener {
                        val friendly = AuthMessages.codeCheckFailure((it as? FirebaseAuthException)?.errorCode, it.message)
                        reportAuth("phone_auto_verify", friendly, it)
                        onError(friendly)
                    }
            },
            onCodeSent = onSent, onFailed = onError)
    }

    fun verifyOtp(id: String, code: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val auth = auth() ?: return onError("Firebase is not configured")
        if (id.isBlank()) return onError("Request a new OTP first")
        if (id == "AUTO_VERIFIED") return onSuccess()
        auth.signInWithCredential(PhoneAuthProvider.getCredential(id, code))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener {
                val friendly = AuthMessages.codeCheckFailure((it as? FirebaseAuthException)?.errorCode, it.message)
                reportAuth("phone_code_check", friendly, it)
                onError(friendly)
            }
    }

    fun email(email: String, password: String, create: Boolean, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val auth = auth() ?: return onError("Firebase is not configured")
        val task = if (create) auth.createUserWithEmailAndPassword(email, password) else auth.signInWithEmailAndPassword(email, password)
        task.addOnSuccessListener { onSuccess() }.addOnFailureListener {
            val message = it.message ?: "Authentication failed"
            reportAuth(if (create) "email_sign_up" else "email_sign_in", message, it)
            onError(message)
        }
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
                val friendly = AuthMessages.linkFailure(failCode(it), it.message, "email")
                reportAuth("link_email", friendly, it)
                onError(friendly)
            }
    }

    /** Sends an SMS code to a number that should be attached to the signed-in account. `onSent` gets the verification id ("AUTO_VERIFIED" if the phone confirmed itself and the number is already attached). */
    fun sendLinkPhoneOtp(activity: Activity, phone: String, onSent: (String) -> Unit, onError: (String) -> Unit) {
        val auth = auth() ?: return onError("Firebase is not configured")
        val user = auth.currentUser ?: return onError("Please sign in again.")
        startPhoneVerification(auth, activity, phone,
            onCompleted = { credential ->
                user.linkWithCredential(credential).addOnSuccessListener { onSent("AUTO_VERIFIED") }
                    .addOnFailureListener {
                        val friendly = AuthMessages.linkFailure(failCode(it), it.message, "phone number")
                        reportAuth("link_phone_auto", friendly, it)
                        onError(friendly)
                    }
            },
            onCodeSent = onSent, onFailed = onError)
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
                val friendly = if (c == "ERROR_INVALID_VERIFICATION_CODE" || c == "ERROR_SESSION_EXPIRED") AuthMessages.codeCheckFailure(c, it.message) else AuthMessages.linkFailure(c, it.message, "phone number")
                reportAuth("link_phone", friendly, it)
                onError(friendly)
            }
    }

    fun signOut() { auth()?.signOut() }
}
