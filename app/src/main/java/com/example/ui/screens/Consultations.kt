package com.nirogbhumi.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.nirogbhumi.app.data.CloudDocument
import com.nirogbhumi.app.data.CloudResult
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.components.NirogCard
import com.nirogbhumi.app.ui.components.SectionLabel
import com.nirogbhumi.app.ui.components.StatusChip
import com.nirogbhumi.app.ui.components.StatusKind
import com.nirogbhumi.app.ui.theme.NirogColor
import com.nirogbhumi.app.ui.theme.NirogRadius
import com.nirogbhumi.app.ui.theme.NirogSpace
import com.nirogbhumi.app.ui.theme.NirogType

val ConsultationTypes = listOf("Diabetes lifestyle", "Diet review", "Yoga", "Naturopathy", "Follow-up")
val PreferredWindows = listOf("Morning", "Afternoon", "Evening", "Any time")
const val CONCERN_MIN = 10
const val CONCERN_MAX = 1000

/** A member's consultation request or booking (Firestore `consultations`). */
data class ConsultationItem(
  val id: String,
  val type: String,
  val concern: String,
  val status: String,
  val scheduledAtMillis: Long?,
  val expert: String,
  val mode: String?,
  val joinLink: String?,
  val location: String?,
  val feeNote: String?,
  val note: String?,
  val declineReason: String?,
  val createdAtMillis: Long,
)

fun parseConsultations(docs: List<CloudDocument>): List<ConsultationItem> = docs.map { doc ->
  val v = doc.values
  fun text(key: String) = (v[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }
  ConsultationItem(
    id = doc.id,
    type = text("consultationType") ?: "Consultation",
    concern = text("concern").orEmpty(),
    status = (v["status"] as? String) ?: "pending",
    scheduledAtMillis = (v["scheduledAt"] as? Timestamp)?.toDate()?.time,
    expert = text("expertName").orEmpty(),
    mode = text("mode"),
    joinLink = safeResourceLink(v["joinLink"] as? String),
    location = text("location"),
    feeNote = text("feeNote"),
    note = text("note"),
    declineReason = text("declineReason"),
    createdAtMillis = (v["createdAt"] as? Timestamp)?.toDate()?.time ?: 0L,
  )
}.sortedWith(
  // Things that need attention first: confirmed (soonest), then requests, then history (newest).
  compareBy<ConsultationItem> { statusRank(it.status) }
    .thenBy { if (it.status == "confirmed") it.scheduledAtMillis ?: Long.MAX_VALUE else 0L }
    .thenByDescending { it.createdAtMillis },
)

private fun statusRank(status: String) = when (status) {
  "confirmed" -> 0
  "pending", "payment_pending" -> 1
  else -> 2
}

fun consultationStatusLabel(status: String): Pair<String, StatusKind> = when (status) {
  "confirmed" -> "Confirmed" to StatusKind.InRange
  "pending", "payment_pending" -> "Waiting for confirmation" to StatusKind.Attention
  "declined" -> "Not scheduled" to StatusKind.Neutral
  "cancelled" -> "Cancelled" to StatusKind.Neutral
  "completed" -> "Completed" to StatusKind.Synced
  else -> status.replaceFirstChar { it.uppercase() } to StatusKind.Neutral
}

/** Only a request that hasn't been finished can be withdrawn. */
fun canCancelConsultation(status: String) = status == "pending" || status == "payment_pending" || status == "confirmed"

fun consultationModeLabel(mode: String?) = when (mode) {
  "video" -> "Video call"
  "phone" -> "Phone call"
  "in_person" -> "In person"
  else -> null
}

private fun formatWhen(millis: Long): String =
  java.text.SimpleDateFormat("EEE, d MMM 'at' h:mm a", java.util.Locale.getDefault()).format(java.util.Date(millis))

// ---------------------------------------------------------------------------
// Request a consultation
// ---------------------------------------------------------------------------

@Composable
fun RequestConsultationScreen(state: NirogState) {
  var submitting by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  RequestConsultationContent(
    submitting = submitting,
    error = error,
    onBack = { state.currentScreen = "dashboard" },
    onSubmit = { type, concern, window, shareLogs ->
      submitting = true
      error = null
      state.repository.addHealthLog(
        "consultations",
        mapOf(
          "consultationType" to type,
          "concern" to concern.trim(),
          "preferredWindow" to window,
          "shareRecentLogs" to shareLogs,
          "status" to "pending",
          "paymentStatus" to "pending",
          "source" to "app",
        ),
      ) { result ->
        submitting = false
        when (result) {
          is CloudResult.Success -> {
            state.cloudMessage = "Request sent. We'll confirm a time soon."
            state.currentScreen = "my_consultations"
          }
          is CloudResult.Failure -> error = "We couldn't send your request. Check your connection and try again."
        }
      }
    },
  )
}

@Composable
fun RequestConsultationContent(
  submitting: Boolean,
  error: String?,
  onBack: () -> Unit,
  onSubmit: (type: String, concern: String, window: String, shareLogs: Boolean) -> Unit,
) {
  var type by remember { mutableStateOf(ConsultationTypes.first()) }
  var concern by remember { mutableStateOf("") }
  var window by remember { mutableStateOf(PreferredWindows.last()) }
  var shareLogs by remember { mutableStateOf(true) }
  var understood by remember { mutableStateOf(false) }
  val concernOk = concern.trim().length in CONCERN_MIN..CONCERN_MAX
  val canSubmit = concernOk && understood && !submitting

  Column(Modifier.fillMaxSize().background(NirogColor.surface)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = NirogSpace.sm, vertical = NirogSpace.sm), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NirogColor.forest) }
      Column {
        Text("Request a consultation", style = NirogType.sectionHeading, color = NirogColor.forest)
        Text("Our team will confirm a time with you", style = NirogType.caption, color = NirogColor.inkMuted)
      }
    }
    Column(
      Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = NirogSpace.lg),
      verticalArrangement = Arrangement.spacedBy(NirogSpace.lg),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(NirogSpace.sm)) {
        SectionLabel("What kind of support?")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(NirogSpace.sm)) {
          ConsultationTypes.forEach { option -> Pill(option, type == option) { type = option } }
        }
      }
      Column(verticalArrangement = Arrangement.spacedBy(NirogSpace.sm)) {
        SectionLabel("What would you like help with?")
        OutlinedTextField(
          value = concern,
          onValueChange = { if (it.length <= CONCERN_MAX) concern = it },
          modifier = Modifier.fillMaxWidth(),
          minLines = 4,
          placeholder = { Text("For example: my fasting sugar is high on weekends and I'm not sure what to change.") },
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
          supportingText = {
            Text(
              if (concern.isNotEmpty() && !concernOk) "A few more words help the expert prepare (at least $CONCERN_MIN characters)." else "${concern.length}/$CONCERN_MAX",
              style = NirogType.caption,
            )
          },
          isError = concern.isNotEmpty() && !concernOk,
        )
      }
      Column(verticalArrangement = Arrangement.spacedBy(NirogSpace.sm)) {
        SectionLabel("When suits you?")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(NirogSpace.sm)) {
          PreferredWindows.forEach { option -> Pill(option, window == option) { window = option } }
        }
      }
      NirogCard {
        Column(verticalArrangement = Arrangement.spacedBy(NirogSpace.xs)) {
          CheckRow("Let the expert see my recent readings", shareLogs) { shareLogs = it }
          CheckRow("I understand this is not emergency care. If I feel very unwell I will contact emergency services or my doctor.", understood) { understood = it }
        }
      }
      if (error != null) Text(error, style = NirogType.body, color = NirogColor.statusCritical)
      Button(
        onClick = { onSubmit(type, concern, window, shareLogs) },
        enabled = canSubmit,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = NirogColor.forest),
        shape = NirogRadius.pillShape,
      ) { Text(if (submitting) "Sending…" else "Send request", color = Color.White) }
      Text(
        "There's no charge to request. If a fee applies, the team will tell you when they confirm - you decide whether to go ahead.",
        style = NirogType.caption, color = NirogColor.inkMuted, modifier = Modifier.padding(bottom = NirogSpace.xxl),
      )
    }
  }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  // One toggleable row (Checkbox itself is not separately clickable) so TalkBack reads
  // "<label>, checkbox, checked" as a single control and a tap anywhere on the row toggles it.
  Row(
    Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = checked, onCheckedChange = null)
    Text(label, style = NirogType.body, color = NirogColor.inkSecondary, modifier = Modifier.weight(1f))
  }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
  Box(
    Modifier
      .background(if (selected) NirogColor.forest else NirogColor.surfaceSunken, NirogRadius.pillShape)
      .clickable(onClick = onClick)
      .padding(horizontal = NirogSpace.lg, vertical = NirogSpace.sm),
  ) {
    Text(label, style = NirogType.caption, color = if (selected) Color.White else NirogColor.inkSecondary)
  }
}

// ---------------------------------------------------------------------------
// My consultations
// ---------------------------------------------------------------------------

@Composable
fun MyConsultationsScreen(state: NirogState) {
  var items by remember { mutableStateOf<List<ConsultationItem>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }
  var cancellingId by remember { mutableStateOf<String?>(null) }
  val context = LocalContext.current

  DisposableEffect(state.repository.userId) {
    // Unordered on purpose (sorted on the device) so no composite index is needed.
    val sub = state.repository.listenUserCollection("consultations", 50, null) { result ->
      loading = false
      when (result) {
        is CloudResult.Success -> { items = parseConsultations(result.value); error = null }
        is CloudResult.Failure -> error = "We couldn't load your consultations. Check your connection and try again."
      }
    }
    onDispose { sub.cancel() }
  }

  MyConsultationsContent(
    items = items,
    loading = loading,
    error = error,
    cancellingId = cancellingId,
    onBack = { state.currentScreen = "dashboard" },
    onNewRequest = { state.currentScreen = "request_consultation" },
    onContactSupport = { state.currentScreen = "support" },
    onOpenLink = { url ->
      runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { state.cloudMessage = "Couldn't open that link on this device." }
    },
    onCancel = { id ->
      cancellingId = id
      state.repository.cancelConsultation(id) { result ->
        cancellingId = null
        state.cloudMessage = if (result is CloudResult.Success) "Cancelled." else (result as CloudResult.Failure).message
      }
    },
  )
}

@Composable
fun MyConsultationsContent(
  items: List<ConsultationItem>,
  loading: Boolean,
  error: String?,
  cancellingId: String?,
  onBack: () -> Unit,
  onNewRequest: () -> Unit,
  onContactSupport: () -> Unit,
  onOpenLink: (String) -> Unit,
  onCancel: (String) -> Unit,
) {
  var confirmCancel by remember { mutableStateOf<ConsultationItem?>(null) }

  Column(Modifier.fillMaxSize().background(NirogColor.surface)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = NirogSpace.sm, vertical = NirogSpace.sm), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NirogColor.forest) }
      Column {
        Text("My consultations", style = NirogType.sectionHeading, color = NirogColor.forest)
        Text("Requests and booked sessions", style = NirogType.caption, color = NirogColor.inkMuted)
      }
    }
    Column(
      Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = NirogSpace.lg),
      verticalArrangement = Arrangement.spacedBy(NirogSpace.md),
    ) {
      Button(
        onClick = onNewRequest,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = NirogColor.forest),
        shape = NirogRadius.pillShape,
      ) { Text("Request a consultation", color = Color.White) }

      when {
        loading && items.isEmpty() -> InfoCard("Loading your consultations…", "")
        error != null && items.isEmpty() -> InfoCard("Couldn't load your consultations", error)
        items.isEmpty() -> InfoCard("No consultations yet", "Request one and our team will confirm a time with you. You'll get a notification when it's confirmed.")
        else -> items.forEach { item ->
          ConsultationCard(item, cancelling = cancellingId == item.id, onOpenLink = onOpenLink, onCancel = { confirmCancel = item }, onContactSupport = onContactSupport)
        }
      }
      Spacer(Modifier.size(NirogSpace.xxl))
    }
  }

  confirmCancel?.let { item ->
    AlertDialog(
      onDismissRequest = { confirmCancel = null },
      title = { Text("Cancel this consultation?") },
      text = { Text(if (item.status == "confirmed") "Your booked session will be cancelled and the expert told. You can request another time whenever you like." else "Your request will be withdrawn. You can send a new one any time.") },
      confirmButton = { TextButton(onClick = { onCancel(item.id); confirmCancel = null }) { Text("Cancel consultation", color = NirogColor.statusCritical) } },
      dismissButton = { TextButton(onClick = { confirmCancel = null }) { Text("Keep it") } },
    )
  }
}

@Composable
private fun InfoCard(title: String, message: String) {
  NirogCard {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
      Text(title, style = NirogType.sectionHeading, color = NirogColor.forest)
      if (message.isNotBlank()) {
        Spacer(Modifier.size(NirogSpace.sm))
        Text(message, style = NirogType.body, color = NirogColor.inkSecondary)
      }
    }
  }
}

@Composable
private fun ConsultationCard(item: ConsultationItem, cancelling: Boolean, onOpenLink: (String) -> Unit, onCancel: () -> Unit, onContactSupport: () -> Unit) {
  val (statusText, statusKind) = consultationStatusLabel(item.status)
  NirogCard {
    Column(verticalArrangement = Arrangement.spacedBy(NirogSpace.sm)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(item.type, style = NirogType.sectionHeading, color = NirogColor.inkPrimary, modifier = Modifier.weight(1f))
        StatusChip(statusText, statusKind)
      }
      if (item.status == "confirmed" && item.scheduledAtMillis != null) {
        Text(formatWhen(item.scheduledAtMillis), style = NirogType.body, color = NirogColor.forest)
        val how = listOfNotNull(item.expert.takeIf { it.isNotBlank() }?.let { "with $it" }, consultationModeLabel(item.mode)).joinToString(" · ")
        if (how.isNotBlank()) Text(how, style = NirogType.caption, color = NirogColor.inkMuted)
        item.location?.let { Text(it, style = NirogType.caption, color = NirogColor.inkSecondary) }
        item.feeNote?.let { Text(it, style = NirogType.caption, color = NirogColor.inkSecondary) }
        item.note?.let { Text("“$it”", style = NirogType.body, color = NirogColor.inkSecondary) }
        item.joinLink?.let { url ->
          Button(onClick = { onOpenLink(url) }, colors = ButtonDefaults.buttonColors(containerColor = NirogColor.forest), shape = NirogRadius.pillShape) {
            Text("Join video call", color = Color.White)
          }
        }
      } else if (item.concern.isNotBlank()) {
        Text(item.concern, style = NirogType.body, color = NirogColor.inkSecondary)
      }
      if (item.status == "declined") {
        Text(item.declineReason ?: "We couldn't schedule this one.", style = NirogType.body, color = NirogColor.inkSecondary)
        TextButton(onClick = onContactSupport) { Text("Contact support", color = NirogColor.forest) }
      }
      if (canCancelConsultation(item.status)) {
        TextButton(onClick = onCancel, enabled = !cancelling) {
          Text(if (cancelling) "Cancelling…" else if (item.status == "confirmed") "Cancel this booking" else "Withdraw request", color = NirogColor.statusCritical)
        }
      }
    }
  }
}
