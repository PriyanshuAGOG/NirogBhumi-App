package com.nirogbhumi.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirogbhumi.app.data.CloudDocument
import com.nirogbhumi.app.data.CloudResult
import com.nirogbhumi.app.data.FirebaseAuthGateway
import com.nirogbhumi.app.ui.DeletionGuards
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.ReauthDialog
import com.nirogbhumi.app.ui.deletionDateLabel
import com.nirogbhumi.app.ui.refreshDeletionStatus
import com.nirogbhumi.app.web.UrlPolicy

private val DcInk = Color(0xFF1B3221)
private val DcMuted = Color(0xFF697169)
private val DcGreen = Color(0xFF314936)
private val DcDanger = Color(0xFF7B332E)

/**
 * Export or delete my data. Both go through Cloud Functions (which rate-limit and do the work).
 *
 * Export: ask once, see "preparing", then "ready" with a Download button that fetches a fresh 15-minute link;
 * an email with a link is also sent when the account has an address (phone-only accounts are told here instead).
 *
 * Deletion: type DELETE MY ACCOUNT, confirm it is really you (password or SMS code), and the account is scheduled
 * for 7 days ahead. A banner on every main screen shows the date with a one-tap way to keep the account. What is
 * erased and what is kept is decided on the server (firebase/functions/src/accountDeletion.ts).
 */
@Composable
fun DataControlsScreen(state: NirogState) {
    val context = LocalContext.current
    var exports by remember { mutableStateOf<List<CloudDocument>?>(null) }
    var exportBusy by remember { mutableStateOf(false) }
    var downloadBusy by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var typedPhrase by remember { mutableStateOf("") }
    var needsReauth by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var showDeletionExplainer by remember { mutableStateOf(false) }
    val hasEmail = remember { !FirebaseAuthGateway.currentEmail().isNullOrBlank() }

    DisposableEffect(state.repository.userId) {
        val sub = state.repository.listenUserCollection("dataExportRequests", 5, orderByField = "createdAt", descending = true) { result ->
            if (result is CloudResult.Success) exports = result.value
        }
        state.refreshDeletionStatus()
        onDispose { sub.cancel() }
    }
    val latest = exports?.firstOrNull()
    val exportStatus = latest?.values?.get("status") as? String
    val scheduledForMillis = state.pendingDeletionMillis

    fun openLink(url: String) {
        if (!UrlPolicy.isSafeToOpenExternally(url)) { exportMessage = "That link can't be opened."; return }
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { exportMessage = "No app could open the download. Try again from a different browser." }
    }

    fun download() {
        val doc = latest ?: return
        downloadBusy = true; exportMessage = null
        state.repository.getExportDownloadLink(doc.id) { result ->
            when (result) {
                is CloudResult.Success -> {
                    val link = result.value
                    if (link.url != null) { downloadBusy = false; openLink(link.url) }
                    else state.repository.getPrivateDownloadUrl(link.storagePath) { fallback ->
                        downloadBusy = false
                        when (fallback) {
                            is CloudResult.Success -> openLink(fallback.value)
                            is CloudResult.Failure -> exportMessage = "We couldn't prepare the download. Please try again."
                        }
                    }
                }
                is CloudResult.Failure -> { downloadBusy = false; exportMessage = "We couldn't prepare the download. Please try again." }
            }
        }
    }

    fun scheduleDeletion() {
        busy = true; deleteError = null
        state.repository.requestAccountDeletion { result ->
            busy = false
            when (result) {
                is CloudResult.Success -> { confirmingDelete = false; typedPhrase = ""; state.pendingDeletionMillis = result.value }
                is CloudResult.Failure -> {
                    if (DeletionGuards.isReauthRequired(result.message)) { confirmingDelete = false; needsReauth = true }
                    else { deleteError = result.message }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F6EF)).verticalScroll(rememberScrollState())) {
        DetailScreenHeader("Export or delete my data", onBack = { state.currentScreen = "profile" })
        Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(0.5.dp, Color(0xFFD8D0C0))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Icon(Icons.Filled.DownloadForOffline, contentDescription = null, tint = DcInk)
                    Spacer(Modifier.height(8.dp))
                    Text("Export your data", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DcInk)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "A ZIP file with everything you've logged: your readings as spreadsheets, plus reports, coach chats and program activity in one data file. Keep it, or share it with a doctor.",
                        fontSize = 13.sp, color = DcMuted, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    when {
                        exports == null -> Text("Checking...", fontSize = 13.sp, color = DcMuted)
                        exportStatus == "requested" || exportStatus == "processing" -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = DcGreen)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (hasEmail) "Preparing your export. We'll email you a link and notify you here when it's ready." else "Preparing your export. We'll notify you here when it's ready (this account has no email address).",
                                fontSize = 13.sp, color = DcGreen, fontWeight = FontWeight.SemiBold,
                            )
                        }
                        exportStatus == "completed" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF3F7D58), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Your export is ready.", fontSize = 13.sp, color = Color(0xFF3F7D58), fontWeight = FontWeight.SemiBold)
                            }
                            if ((latest?.values?.get("emailStatus") as? String) == "queued") Text("We've also emailed you a link that works for a few hours.", fontSize = 12.5.sp, color = DcMuted)
                            Button(
                                enabled = !downloadBusy,
                                onClick = { download() },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DcGreen), shape = RoundedCornerShape(20.dp),
                            ) { Text(if (downloadBusy) "Preparing download..." else "Download (ZIP)", color = Color.White, fontWeight = FontWeight.Bold) }
                            TextButton(
                                enabled = !exportBusy,
                                onClick = { exportBusy = true; exportMessage = null; state.repository.requestDataExport { r -> exportBusy = false; if (r is CloudResult.Failure) exportMessage = r.message } },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text("Make a fresh export", color = DcGreen, fontWeight = FontWeight.SemiBold) }
                        }
                        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (exportStatus == "failed") Text("We couldn't prepare your last export. Please try again.", fontSize = 13.sp, color = Color(0xFF8B3E36))
                            Button(
                                enabled = !exportBusy,
                                onClick = { exportBusy = true; exportMessage = null; state.repository.requestDataExport { r -> exportBusy = false; if (r is CloudResult.Failure) exportMessage = r.message } },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DcGreen), shape = RoundedCornerShape(20.dp),
                            ) { Text(if (exportBusy) "Requesting..." else "Request my data", color = Color.White, fontWeight = FontWeight.Bold) }
                            if (!hasEmail) Text("This account has no email address, so we'll tell you here in the app when it's ready.", fontSize = 12.sp, color = DcMuted)
                        }
                    }
                    exportMessage?.let { Spacer(Modifier.height(8.dp)); Text(it, fontSize = 12.5.sp, color = Color(0xFF8B3E36)) }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5DFD6)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = Color(0xFFB4472F))
                    Spacer(Modifier.height(8.dp))
                    Text("Delete my account", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DcDanger)
                    Spacer(Modifier.height(4.dp))
                    if (scheduledForMillis != null) {
                        Text(
                            "Your account is scheduled to be permanently deleted on ${deletionDateLabel(scheduledForMillis)}. Until then everything is still here and you can keep using the app.",
                            fontSize = 13.sp, color = DcDanger, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            enabled = !busy,
                            onClick = {
                                busy = true
                                state.repository.cancelAccountDeletion { result ->
                                    busy = false
                                    if (result is CloudResult.Success) { state.pendingDeletionMillis = null; state.cloudMessage = "Deletion cancelled - your account stays exactly as it was." }
                                    else state.cloudMessage = (result as CloudResult.Failure).message
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DcGreen), shape = RoundedCornerShape(20.dp)
                        ) { Text(if (busy) "Cancelling..." else "Keep my account - cancel deletion", color = Color.White, fontWeight = FontWeight.Bold) }
                    } else {
                        Text(
                            "Removes your account, name, contact details, reports, chats and login. You'll have 7 days to change your mind; after that it can't be undone.",
                            fontSize = 13.sp, color = DcDanger, lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { showDeletionExplainer = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("What exactly gets deleted?", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = DcDanger, textDecoration = TextDecoration.Underline)
                        }
                        OutlinedButton(
                            enabled = !busy,
                            onClick = { typedPhrase = ""; deleteError = null; confirmingDelete = true },
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB4472F))
                        ) { Text("Delete my account") }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmingDelete = false },
            title = { Text("Delete your account?", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = DcInk) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("In 7 days your account, name, contact details, uploaded reports, chats and login will be permanently deleted. You can cancel from this screen, or from the banner at the top of the app, any time before then.", fontSize = 13.sp, color = Color(0xFF434842))
                    Text("To confirm, type ${DeletionGuards.PHRASE} below.", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DcInk)
                    OutlinedTextField(
                        value = typedPhrase, onValueChange = { typedPhrase = it.take(40) }, singleLine = true,
                        label = { Text(DeletionGuards.PHRASE) }, modifier = Modifier.fillMaxWidth(),
                        isError = typedPhrase.isNotBlank() && !DeletionGuards.phraseMatches(typedPhrase),
                    )
                    deleteError?.let { Text(it, fontSize = 12.5.sp, color = Color(0xFF8B3E36)) }
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy && DeletionGuards.phraseMatches(typedPhrase),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB4472F)),
                    onClick = { scheduleDeletion() }
                ) { Text(if (busy) "Scheduling..." else "Delete my account", color = Color.White) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmingDelete = false }) { Text("Keep my account", color = Color(0xFF737972)) } }
        )
    }

    if (needsReauth) {
        ReauthDialog(
            onConfirmed = { needsReauth = false; scheduleDeletion() },
            onDismiss = { needsReauth = false },
            onSignOut = {
                runCatching { FirebaseAuthGateway.signOut() }
                needsReauth = false
                state.currentScreen = "welcome"
            },
        )
    }

    if (showDeletionExplainer) {
        AlertDialog(
            onDismissRequest = { showDeletionExplainer = false },
            title = { Text("What gets deleted", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = DcInk) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Deleted: your name, email, phone number, profile, family profiles, uploaded reports and photos, consultations, program activity, coach messages, chat messages and voice notes, support requests, notifications, and your login.",
                        fontSize = 13.sp, color = Color(0xFF434842), lineHeight = 18.sp
                    )
                    Text(
                        "Your health readings (sugar, blood pressure, sleep, walks, weight, medicines, check-ins) are deleted too, unless you switched on \"Anonymized research\" in Privacy & consent. In that case they stay with every link back to you removed and are only used in combined statistics.",
                        fontSize = 13.sp, color = Color(0xFF434842), lineHeight = 18.sp
                    )
                    Text(
                        "Kept because the law requires it: payment and invoice records (with your identity removed where we can) and security logs.",
                        fontSize = 13.sp, color = Color(0xFF434842), lineHeight = 18.sp
                    )
                    Text(
                        "Not instant: copies in our cloud provider's backups and in service logs are cleared on their own schedule, which can take a few months. We can't reach in to remove them sooner, and they are not used for anything.",
                        fontSize = 13.sp, color = Color(0xFF434842), lineHeight = 18.sp
                    )
                    Text(
                        "Already downloaded an export? That file is yours to keep or delete; deleting your account doesn't reach files you saved.",
                        fontSize = 13.sp, color = Color(0xFF434842), lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeletionExplainer = false }) { Text("Got it", color = DcGreen, fontWeight = FontWeight.Bold) }
            }
        )
    }
}
