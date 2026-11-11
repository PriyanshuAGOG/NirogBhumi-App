package com.nirogbhumi.app.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.functions.FirebaseFunctions
import org.json.JSONObject
import java.util.UUID

/**
 * Sends problems to the admin console's Error reports even when nobody is signed in (the SMS code not sending, a refused
 * sign-in, a crash on the first screen). Signed-in failures are still written straight to Firestore by the repository;
 * this is the route for everything that cannot be.
 *
 * It sends text only: a screen name, a plain message, an error code, a short technical line, the app version, the phone
 * model and a random per-install id. Never a phone number, email address or health value.
 */
object ClientErrorReporter {
    private const val PREFS = "nirog_diagnostics"
    private val throttle = ClientErrorRules.Throttle()
    @Volatile private var appContext: Context? = null

    /** Call once the Firebase app exists. Also sends a crash that was saved the last time the app died. */
    fun init(context: Context) {
        appContext = context.applicationContext
        flushPendingCrash()
    }

    fun report(screen: String, message: String, code: String? = null, detail: String? = null) {
        val context = appContext ?: return
        if (!throttle.allow(ClientErrorRules.fingerprint(screen, code, message), System.currentTimeMillis())) return
        send(context, payload(context, screen, message, code, detail))
    }

    /** Called from the uncaught-exception handler: only saves a note (the app is dying); it is sent on the next launch. */
    fun recordCrash(context: Context, error: Throwable) {
        val frames = error.stackTrace.take(4).map { "${it.className.substringAfterLast('.')}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
        val (message, detail) = ClientErrorRules.crashSummary(error.javaClass.simpleName, frames)
        val note = JSONObject().put("screen", "crash").put("message", message).put("detail", detail).put("at", System.currentTimeMillis())
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("pending_crash", note.toString()).commit()
    }

    private fun flushPendingCrash() {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val text = prefs.getString("pending_crash", null) ?: return
        prefs.edit().remove("pending_crash").apply()
        runCatching {
            val note = JSONObject(text)
            send(context, payload(context, "crash", "The app closed unexpectedly: " + note.optString("message"), null, note.optString("detail")))
        }
    }

    private fun payload(context: Context, screen: String, message: String, code: String?, detail: String?): Map<String, Any?> = mapOf(
        "screen" to ClientErrorRules.clean(screen, 60),
        "message" to ClientErrorRules.clean(message, 500),
        "code" to code?.let { ClientErrorRules.clean(it, 80) },
        "detail" to ClientErrorRules.clean(detail, 300),
        "appVersion" to com.nirogbhumi.app.BuildConfig.VERSION_NAME,
        "versionCode" to versionCode(context),
        "device" to ClientErrorRules.clean("${Build.MANUFACTURER} ${Build.MODEL}", 60),
        "androidSdk" to Build.VERSION.SDK_INT,
        "installId" to installId(context),
    )

    private fun send(context: Context, data: Map<String, Any?>) {
        runCatching {
            val app = FirebaseApp.getInstance()
            FirebaseFunctions.getInstance(app, "asia-south1").getHttpsCallable("reportClientError").call(data)
                .addOnFailureListener { Log.w("NirogReport", "Could not send an error report: ${it.message}") }
        }.onFailure { Log.w("NirogReport", "Could not send an error report: ${it.message}") }
    }

    private fun versionCode(context: Context): Int = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
    }.getOrDefault(0)

    /** A random id made on first use. It names an installation, not a person or a device, and is not tied to the account. */
    private fun installId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString("install_id", null) ?: UUID.randomUUID().toString().replace("-", "").take(32).also { prefs.edit().putString("install_id", it).apply() }
    }
}
