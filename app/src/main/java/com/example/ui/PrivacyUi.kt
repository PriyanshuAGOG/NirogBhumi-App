package com.nirogbhumi.app.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nirogbhumi.app.data.CloudResult
import com.nirogbhumi.app.data.FirebaseAuthGateway

/** Re-reads whether an account deletion is pending, for whoever is signed in. */
fun NirogState.refreshDeletionStatus() {
    if (repository.userId == null) { pendingDeletionMillis = null; return }
    repository.getPendingAccountDeletion { result -> if (result is CloudResult.Success) pendingDeletionMillis = result.value }
}

/** Keeps [NirogState.pendingDeletionMillis] current: on sign-in and every time the app returns to the foreground. */
@Composable
fun DeletionStatusLifecycle(state: NirogState) {
    LaunchedEffect(state.repository.userId) { state.refreshDeletionStatus() }
    LifecycleResumeEffect(Unit) {
        state.refreshDeletionStatus()
        onPauseOrDispose { }
    }
}

fun deletionDateLabel(millis: Long): String =
    java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(millis))

/** Shown on every main screen while a deletion is scheduled, with the way out one tap away. */
@Composable
fun PendingDeletionBanner(state: NirogState, modifier: Modifier = Modifier) {
    val scheduled = state.pendingDeletionMillis ?: return
    var busy by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().background(Color(0xFFF5DFD6)).padding(horizontal = 16.dp, vertical = 10.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Your account will be deleted on ${deletionDateLabel(scheduled)}.",
            fontSize = 13.sp, color = Color(0xFF7B332E), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
        )
        TextButton(
            enabled = !busy,
            onClick = {
                busy = true
                state.repository.cancelAccountDeletion { result ->
                    busy = false
                    if (result is CloudResult.Success) { state.pendingDeletionMillis = null; state.cloudMessage = "Deletion cancelled - your account stays as it was." }
                    else state.cloudMessage = (result as CloudResult.Failure).message
                }
            },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(if (busy) "Cancelling..." else "Keep my account", color = Color(0xFF314936), fontWeight = FontWeight.Bold) }
    }
}

/**
 * "Confirm it's you" before something irreversible: the account's password, or a fresh SMS code. (Google sign-in
 * accounts are asked to sign out and back in.) Calls [onConfirmed] once the server will see a recent sign-in.
 */
@Composable
fun ReauthDialog(onConfirmed: () -> Unit, onDismiss: () -> Unit, onSignOut: () -> Unit) {
    val activity = LocalContext.current as? Activity
    val method = remember { FirebaseAuthGateway.signInMethod() }
    val phone = remember { FirebaseAuthGateway.currentPhone() }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun fail(message: String) { busy = false; error = message }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Confirm it's you", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (method) {
                    SignInMethod.PASSWORD -> {
                        Text("For your security, enter your password to continue.", fontSize = 13.sp)
                        OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                    }
                    SignInMethod.PHONE -> if (verificationId == null) {
                        Text("For your security we'll text a code to ${maskPhone(phone)}.", fontSize = 13.sp)
                    } else {
                        Text("Enter the 6-digit code we sent to ${maskPhone(phone)}.", fontSize = 13.sp)
                        OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("Code") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
                    }
                    else -> Text("For your security, please sign out and sign in again, then come back here to finish.", fontSize = 13.sp)
                }
                error?.let { Text(it, color = Color(0xFF8B3E36), fontSize = 12.5.sp) }
            }
        },
        confirmButton = {
            when (method) {
                SignInMethod.PASSWORD -> Button(
                    enabled = !busy && password.isNotEmpty(),
                    onClick = { busy = true; error = null; FirebaseAuthGateway.reauthWithPassword(password, onSuccess = { busy = false; onConfirmed() }, onError = ::fail) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936)),
                ) { Text(if (busy) "Checking..." else "Confirm", color = Color.White) }
                SignInMethod.PHONE -> if (verificationId == null) Button(
                    enabled = !busy && activity != null,
                    onClick = {
                        busy = true; error = null
                        FirebaseAuthGateway.sendReauthOtp(activity!!, onSent = { id -> busy = false; if (id == "AUTO_VERIFIED") onConfirmed() else verificationId = id }, onError = ::fail)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936)),
                ) { Text(if (busy) "Sending..." else "Send code", color = Color.White) } else Button(
                    enabled = !busy && DeletionGuards.validOtp(code),
                    onClick = { busy = true; error = null; FirebaseAuthGateway.reauthWithOtp(verificationId!!, code, onSuccess = { busy = false; onConfirmed() }, onError = ::fail) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936)),
                ) { Text(if (busy) "Checking..." else "Confirm", color = Color.White) }
                else -> Button(onClick = onSignOut, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936))) { Text("Sign out", color = Color.White) }
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel", color = Color(0xFF737972)) } },
    )
}
