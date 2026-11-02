package com.nirogbhumi.app.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirogbhumi.app.data.AccountContacts
import com.nirogbhumi.app.data.CloudResult
import com.nirogbhumi.app.data.FirebaseAuthGateway
import com.nirogbhumi.app.data.MissingContact
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.components.OtpCodeField

/**
 * Asks for the sign-in method this account does not have yet, so the phone number AND the email both open the SAME account:
 * a phone sign-up adds an email and password; an email (or Google) sign-up adds a phone number checked with an SMS code.
 *
 * New accounts must finish it (the skip link only appears after something goes wrong, so a failing SMS never traps anyone);
 * existing accounts are asked once per app start and can say "Not now".
 */
@Composable
fun LinkContactScreen(state: NirogState) {
    val missing = remember { FirebaseAuthGateway.missingContact() }
    val activity = LocalActivity.current
    val required = state.linkContactRequired
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun proceed() { state.currentScreen = state.linkContactNext }

    if (missing == MissingContact.NONE) {
        LaunchedEffect(Unit) { proceed() }
        return
    }

    fun saved(values: Map<String, Any?>) {
        // The sign-in method is already attached. Copying it into the profile happens in the background (Firestore queues
        // the write if the connection is poor), so the member is never left waiting on it.
        state.repository.saveProfile(values) { result ->
            if (result is CloudResult.Failure) android.util.Log.w("NirogAuth", "Profile copy of contact failed: ${result.message}")
        }
        busy = false
        proceed()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Paper).padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (missing == MissingContact.EMAIL) "Add your email" else "Add your mobile number",
            fontSize = 26.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (missing == MissingContact.EMAIL)
                "So you can sign in with your email as well as your phone. Either one opens the same account."
            else
                "So you can sign in with your phone number as well as your email. Either one opens the same account.",
            fontSize = 14.sp, color = Ink.copy(alpha = 0.65f), textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, Line.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DeepGreen, unfocusedBorderColor = Line)
                if (missing == MissingContact.EMAIL) {
                    OutlinedTextField(
                        value = email, onValueChange = { email = it.trim(); error = "" }, singleLine = true, label = { Text("Email") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp), colors = fieldColors,
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it; error = "" }, singleLine = true, label = { Text("Choose a password") },
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        supportingText = { Text("At least ${AccountContacts.MIN_PASSWORD} characters, with a letter.") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = fieldColors,
                    )
                    val problem = if (password.isEmpty()) null else AccountContacts.passwordProblem(password)
                    val ready = AccountContacts.isValidEmail(email) && password.isNotEmpty() && problem == null && !busy
                    if (problem != null) Text(problem, color = Color(0xFF8B2E2E), fontSize = 12.sp)
                    PrimaryAction("Save and continue", ready, busy) {
                        busy = true; error = ""
                        FirebaseAuthGateway.linkEmailPassword(email, password,
                            onSuccess = { saved(mapOf("email" to email.trim())) },
                            onError = { busy = false; error = it })
                    }
                } else if (verificationId.isEmpty()) {
                    OutlinedTextField(
                        value = phone, onValueChange = { if (it.all(Char::isDigit) && it.length <= 10) { phone = it; error = "" } },
                        singleLine = true, label = { Text("Mobile number (+91)") }, placeholder = { Text("10-digit number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp), colors = fieldColors,
                    )
                    PrimaryAction("Send code", AccountContacts.isValidIndianMobile(phone) && !busy, busy) {
                        val host = activity ?: return@PrimaryAction
                        busy = true; error = ""
                        FirebaseAuthGateway.sendLinkPhoneOtp(host, "+91$phone",
                            onSent = { id ->
                                if (id == "AUTO_VERIFIED") saved(mapOf("phone" to "+91$phone"))
                                else { busy = false; verificationId = id }
                            },
                            onError = { busy = false; error = it })
                    }
                } else {
                    Text("We sent a 6-digit code to +91 $phone.", fontSize = 13.sp, color = Ink.copy(alpha = 0.7f))
                    fun verify(entered: String) {
                        if (busy) return
                        busy = true; error = ""
                        FirebaseAuthGateway.linkPhone(verificationId, entered,
                            onSuccess = { saved(mapOf("phone" to "+91$phone")) },
                            onError = { busy = false; error = it; code = "" })
                    }
                    OtpCodeField(value = code, onValueChange = { code = it; error = "" }, isError = error.isNotBlank(), enabled = !busy, onComplete = { verify(it) })
                    PrimaryAction("Verify and continue", code.length == 6 && !busy, busy) { verify(code) }
                    TextButton(onClick = { verificationId = ""; code = ""; error = "" }, enabled = !busy) {
                        Text("Change the number or send a new code", color = DeepGreen, fontSize = 13.sp)
                    }
                }
                if (busy) Text(
                    if (missing == MissingContact.EMAIL) "Securing your account. This can take a few seconds on a slow connection."
                    else if (verificationId.isEmpty()) "Sending your code. Google may ask you to tap \"I'm not a robot\" in your browser first: do it, then come back here."
                    else "Checking your code…",
                    color = Ink.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 16.sp,
                )
                if (error.isNotBlank()) Text(error, color = Color(0xFF8B2E2E), fontSize = 12.sp)
            }
        }

        // Never a dead end: optional for existing accounts, and for new ones as soon as something has gone wrong.
        if (!required || error.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { proceed() }) {
                Text(if (required) "I'll do this later" else "Not now", color = Ink.copy(alpha = 0.6f), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun PrimaryAction(label: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DeepGreen, disabledContainerColor = Line.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(26.dp),
    ) { Text(if (busy) "Please wait…" else label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White) }
}
