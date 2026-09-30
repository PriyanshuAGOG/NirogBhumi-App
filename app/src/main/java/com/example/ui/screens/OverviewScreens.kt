package com.nirogbhumi.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldValue
import com.nirogbhumi.app.data.CloudResult
import com.nirogbhumi.app.health.domain.ActivityEntry
import com.nirogbhumi.app.health.domain.BpEntry
import com.nirogbhumi.app.health.domain.ClockText
import com.nirogbhumi.app.health.domain.HealthLabels
import com.nirogbhumi.app.health.domain.HealthMetric
import com.nirogbhumi.app.health.domain.HealthUiState
import com.nirogbhumi.app.health.domain.SleepEntry
import com.nirogbhumi.app.health.domain.TrendMetric
import com.nirogbhumi.app.health.domain.TrendRange
import com.nirogbhumi.app.health.domain.WeightEntry
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.collectHealth
import com.nirogbhumi.app.ui.components.EditWindowFootnote
import com.nirogbhumi.app.ui.components.HealthHistoryRow
import com.nirogbhumi.app.ui.components.MetricTrendCard
import com.nirogbhumi.app.ui.components.StaleDataNotice

private val Ink2 = Color(0xFF1B3221)
private val Green2 = Color(0xFF314936)
private val Paper2 = Color(0xFFF8F6EF)
private val Muted2 = Color(0xFF697169)

@Composable
private fun MetricSummaryCard(label: String, value: String, unit: String, sub: String) {
    Card(
        modifier = Modifier.fillMaxWidth().border(0.5.dp, Color(0xFFC3C8C0).copy(alpha = 0.35f), RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Muted2, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 42.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Ink2)
                if (unit.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(unit, fontSize = 14.sp, color = Muted2, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(sub, fontSize = 13.sp, color = Color(0xFF434842), lineHeight = 18.sp)
            }
        }
    }
}

/** Loading, "couldn't load" and empty states shared by every metric screen. Returns true when it drew one (the caller stops). */
@Composable
private fun MetricStatus(state: NirogState, health: HealthUiState, metric: HealthMetric, hasData: Boolean, emptyIcon: androidx.compose.ui.graphics.vector.ImageVector, emptyText: String): Boolean {
    val error = health.errors[metric]
    return when {
        health.isLoading && !hasData -> { LoadingRow(); true }
        error != null && !hasData -> {
            Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error, fontSize = 14.sp, color = Muted2, textAlign = TextAlign.Center)
                TextButton(onClick = { state.health.retry() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Try again", color = Green2, fontWeight = FontWeight.Bold) }
            }
            true
        }
        !hasData -> { EmptyStateCard(emptyIcon, emptyText); true }
        else -> false
    }
}

private fun correct(state: NirogState, collection: String, id: String, edits: Map<String, Any?>, report: (String?) -> Unit) {
    state.health.correct(collection, id, edits) { r -> report((r as? CloudResult.Failure)?.message) }
}

// ---------- Blood Pressure ----------
@Composable
fun BpOverviewScreen(state: NirogState) {
    val health = state.collectHealth()
    val now = System.currentTimeMillis()
    var editing by remember { mutableStateOf<BpEntry?>(null) }
    val latest = health.latestBp

    Column(Modifier.fillMaxSize().background(Paper2)) {
        DetailScreenHeader("Blood Pressure", onBack = { state.currentScreen = "dashboard" }, trailing = {
            IconButton(onClick = { state.checkinStartStep = 1; state.currentScreen = "daily_checkin" }) { Icon(Icons.Filled.Add, "Add reading", tint = Ink2) }
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StaleDataNotice(health)
            if (!MetricStatus(state, health, HealthMetric.BP, latest != null, Icons.Filled.Favorite, "No blood pressure readings yet. Tap + to add one.") && latest != null) {
                val s = latest.systolic; val d = latest.diastolic
                val status = when {
                    s >= 140 || d >= 90 -> "This reading is on the higher side. One reading isn't a diagnosis — measure again calmly and talk to your doctor if it stays high."
                    s < 90 || d < 60 -> "This reading is on the lower side. If you feel unwell, sit down and consult your doctor."
                    else -> "This reading is within a typical range. Measure at a consistent time for the clearest trend."
                }
                MetricSummaryCard("Latest reading", HealthLabels.bp(latest), "mmHg", "Upper BP / Lower BP. $status")
                MetricTrendCard(health, TrendMetric.BP)
                Text("History", fontWeight = FontWeight.Bold, color = Ink2)
                health.bp.take(20).forEach { e ->
                    HealthHistoryRow(
                        title = "Upper ${e.systolic} / Lower ${e.diastolic} mmHg",
                        subtitle = HealthLabels.dayAndTime(e.measuredAtMillis, now, health.zone),
                        entry = e, nowMillis = now, onEdit = { editing = e },
                    )
                }
                EditWindowFootnote()
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    editing?.let { e -> BpEditDialog(e, { editing = null }) { edits, report -> correct(state, "bpReadings", e.id, edits, report) } }
}

// ---------- Weight ----------
@Composable
fun WeightOverviewScreen(state: NirogState) {
    val health = state.collectHealth()
    val now = System.currentTimeMillis()
    var editing by remember { mutableStateOf<WeightEntry?>(null) }
    val latest = health.latestWeight

    Column(Modifier.fillMaxSize().background(Paper2)) {
        DetailScreenHeader("Weight", onBack = { state.currentScreen = "dashboard" }, trailing = {
            IconButton(onClick = { state.checkinStartStep = 2; state.currentScreen = "daily_checkin" }) { Icon(Icons.Filled.Add, "Add weight", tint = Ink2) }
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StaleDataNotice(health)
            if (!MetricStatus(state, health, HealthMetric.WEIGHT, latest != null, Icons.Filled.MonitorWeight, "No weight logged yet. Tap + to add one.") && latest != null) {
                val change = health.month.weightChangeKg
                val sub = when {
                    change == null -> "Log your weight a few times to see how it changes."
                    com.nirogbhumi.app.health.domain.TrendText.isMeaningfulWeightChange(change) ->
                        "${if (change < 0) "Down" else "Up"} ${"%.1f".format(java.util.Locale.US, kotlin.math.abs(change))} kg over the last 30 days."
                    else -> "Steady over the last 30 days."
                }
                MetricSummaryCard("Latest weight", "%.1f".format(java.util.Locale.US, latest.valueKg), "kg", sub)
                MetricTrendCard(health, TrendMetric.WEIGHT)
                Text("History", fontWeight = FontWeight.Bold, color = Ink2)
                health.weight.take(20).forEach { e ->
                    HealthHistoryRow(
                        title = HealthLabels.weight(e.valueKg),
                        subtitle = HealthLabels.dayAndTime(e.measuredAtMillis, now, health.zone),
                        entry = e, nowMillis = now, onEdit = { editing = e },
                    )
                }
                EditWindowFootnote()
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    editing?.let { e -> WeightEditDialog(e, { editing = null }) { edits, report -> correct(state, "weightLogs", e.id, edits, report) } }
}

// ---------- Sleep ----------
@Composable
fun SleepOverviewScreen(state: NirogState) {
    val health = state.collectHealth()
    val now = System.currentTimeMillis()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SleepEntry?>(null) }
    val latest = health.lastSleep

    Column(Modifier.fillMaxSize().background(Paper2)) {
        DetailScreenHeader("Sleep", onBack = { state.currentScreen = "dashboard" }, trailing = {
            IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "Add sleep", tint = Ink2) }
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StaleDataNotice(health)
            if (!MetricStatus(state, health, HealthMetric.SLEEP, health.sleep.isNotEmpty(), Icons.Filled.Bedtime, "No sleep logged yet. Tap + to record last night.") && health.sleep.isNotEmpty()) {
                if (latest != null) {
                    val h = latest.durationMinutes / 60.0
                    val sub = when {
                        h in 7.0..9.0 -> "That's a healthy amount of rest. Consistency night to night matters as much as the total."
                        h < 6.0 -> "That's a little short. Even 30 minutes earlier to bed can help your next-day energy and sugar."
                        else -> "Logged. Aim for a consistent sleep and wake time for the steadiest rhythm."
                    }
                    val whenText = HealthLabels.day(latest.measuredAtMillis, now, health.zone).let { if (it == "Today") "Last night" else it }
                    MetricSummaryCard(whenText, ClockText.duration(latest.durationMinutes), "", listOfNotNull(HealthLabels.sleepRange(latest, health.zone), sub).joinToString("\n"))
                }
                MetricTrendCard(health, TrendMetric.SLEEP, title = "Hours slept")
                Text("History", fontWeight = FontWeight.Bold, color = Ink2)
                health.sleep.take(20).forEach { e ->
                    HealthHistoryRow(
                        title = ClockText.duration(e.durationMinutes),
                        subtitle = listOfNotNull(HealthLabels.sleepRange(e, health.zone), HealthLabels.day(e.measuredAtMillis, now, health.zone)).joinToString(" · "),
                        entry = e, nowMillis = now, onEdit = { editing = e },
                        note = if (e.isSuspect) "This sleep looks too long, so it isn't counted in your averages." else null,
                    )
                }
                EditWindowFootnote()
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (adding) {
        SleepEditorDialog(null, health.zone, "Add sleep", { adding = false }) { start, end, minutes, report ->
            state.health.logSleep(start, end, minutes) { r -> report((r as? CloudResult.Failure)?.message) }
        }
    }
    editing?.let { e ->
        SleepEditorDialog(e, health.zone, "Edit sleep", { editing = null }) { start, end, minutes, report ->
            correct(state, "sleepLogs", e.id, com.nirogbhumi.app.health.domain.HealthPayloads.sleep(start, end, minutes), report)
        }
    }
}

// ---------- Walking & Activity ----------
@Composable
fun WalkingActivityScreen(state: NirogState) {
    val health = state.collectHealth()
    val now = System.currentTimeMillis()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ActivityEntry?>(null) }
    val stepsToday = health.today.stepsToday

    Column(Modifier.fillMaxSize().background(Paper2)) {
        DetailScreenHeader("Walking & Activity", onBack = { state.currentScreen = "dashboard" }, trailing = {
            IconButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, "Log activity", tint = Ink2) }
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StaleDataNotice(health)
            // Steps counted by a connected device (Health Connect) - never estimated from minutes.
            MetricSummaryCard(
                "Steps today",
                if (stepsToday > 0) String.format(java.util.Locale.US, "%,d", stepsToday) else "—",
                if (stepsToday > 0) "steps" else "",
                if (stepsToday > 0) "Synced from your connected device." else "Connect a band or watch under Settings › Devices to sync steps automatically."
            )
            if (health.today.activityMinutesToday > 0) {
                MetricSummaryCard("Active today", "${health.today.activityMinutesToday}", "min", "From the walks and workouts you logged.")
            }
            if (stepsToday == 0L) {
                OutlinedButton(onClick = { state.currentScreen = "device_hub" }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(24.dp)) {
                    Text("Connect a device")
                }
            }
            state.walkMilestoneCount?.let { count ->
                Surface(Modifier.fillMaxWidth(), color = Color(0xFFF4E9D3), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎉", fontSize = 24.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("$count walks logged!", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF7A5A1E))
                        Text(
                            "A real, earned milestone - keep moving at whatever pace works for you.",
                            fontSize = 12.sp, color = Color(0xFF8A6C2E), textAlign = TextAlign.Center,
                        )
                    }
                }
                DisposableEffect(Unit) { onDispose { state.walkMilestoneCount = null } }
            }
            if (health.activity.isNotEmpty()) MetricTrendCard(health, TrendMetric.WALKING, title = "Walking")
            Text("Logged activity", fontWeight = FontWeight.Bold, color = Ink2)
            if (!MetricStatus(state, health, HealthMetric.ACTIVITY, health.activity.isNotEmpty(), Icons.Filled.DirectionsWalk, "No activity logged yet. Tap + to add a walk, yoga, or workout.")) {
                health.activity.take(20).forEach { e ->
                    HealthHistoryRow(
                        title = HealthLabels.activity(e),
                        subtitle = HealthLabels.dayAndTime(e.measuredAtMillis, now, health.zone),
                        entry = e, nowMillis = now, onEdit = { editing = e },
                    )
                }
                EditWindowFootnote()
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAdd) ActivityLogDialog(state) { showAdd = false }
    editing?.let { e -> ActivityEditDialog(e, { editing = null }) { edits, report -> correct(state, "walkLogs", e.id, edits, report) } }
}

/**
 * The single, shared activity-logging dialog - opened from Walking & Activity's own "+" and from
 * Track's "Activity" quick-log chip. It records what the member did (type and minutes); steps only
 * ever come from a connected device, so nothing here is turned into an invented step count.
 */
@Composable
fun ActivityLogDialog(state: NirogState, onDismiss: () -> Unit) {
    var activityType by remember { mutableStateOf("Walk") }
    var minutes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val minutesValue = minutes.toIntOrNull()
    val error = if (minutes.isNotBlank() && (minutesValue == null || minutesValue !in 1..HealthLimits.ACTIVITY_MAX_MINUTES)) "Enter minutes between 1 and 600." else null

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Log activity", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Ink2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Walk", "Yoga", "Exercise", "Cycling", "Other").forEach { t ->
                        Surface(shape = RoundedCornerShape(12.dp), color = if (activityType == t) Green2 else Color(0xFFEBF7E8), modifier = Modifier.heightIn(min = 48.dp).clickable { activityType = t }) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(t, color = if (activityType == t) Color.White else Ink2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
                OutlinedTextField(
                    minutes, { minutes = it.filter(Char::isDigit).take(3) }, label = { Text("How many minutes?") },
                    isError = error != null, supportingText = { error?.let { Text(it, color = Color(0xFFB4472F)) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(enabled = !saving && minutesValue != null && error == null, colors = ButtonDefaults.buttonColors(containerColor = Green2), modifier = Modifier.heightIn(min = 48.dp), onClick = {
                val m = minutesValue ?: return@Button
                saving = true
                state.health.logWalk(m, activityType) { r ->
                    saving = false
                    if (r is CloudResult.Success) onDismiss() else state.cloudMessage = (r as CloudResult.Failure).message
                }
            }) { Text(if (saving) "Saving..." else "Save", color = Color.White) }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel", color = Muted2) } }
    )
}

@Composable
private fun LoadingRow() {
    Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF9CB79F))
        Spacer(Modifier.width(8.dp))
        Text("Loading...", fontSize = 13.sp, color = Muted2)
    }
}

@Composable
private fun HistoryRow(value: String, time: String) {
    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(0.5.dp, Color(0xFFD8D0C0))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(value, fontWeight = FontWeight.SemiBold, color = Ink2, modifier = Modifier.weight(1f))
            Text(time, fontSize = 12.sp, color = Muted2)
        }
    }
}

// ---------- Lab Reports ----------
// A purpose-built screen replacing the old generic catalog template - same
// underlying labReports schema (reportType/reportDate/labName/notes) so
// existing data and the Health File's report list keep working unchanged.
@Composable
fun LabReportsScreen(state: NirogState) {
    val health = state.collectHealth()
    val now = System.currentTimeMillis()
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Paper2)) {
        DetailScreenHeader("Lab Reports", onBack = { state.currentScreen = "dashboard" }, trailing = {
            IconButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, "Add report", tint = Ink2) }
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StaleDataNotice(health)
            Text("Kept together and private - only you can see these.", fontSize = 13.sp, color = Muted2)
            if (!MetricStatus(state, health, HealthMetric.LAB_REPORTS, health.labReports.isNotEmpty(), Icons.Filled.Science, "No lab reports yet. Tap + to keep one on file for your next doctor visit.")) {
                health.labReports.forEach { rec ->
                    HistoryRow(rec.title, HealthLabels.day(rec.measuredAtMillis, now, health.zone))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAdd) {
        var reportType by remember { mutableStateOf("HbA1c") }
        var labName by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var fileUrl by remember { mutableStateOf<String?>(null) }
        var uploading by remember { mutableStateOf(false) }
        var saving by remember { mutableStateOf(false) }
        val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                uploading = true
                state.repository.uploadPrivateFile("lab-reports", uri) { result ->
                    uploading = false
                    when (result) {
                        is CloudResult.Success -> fileUrl = result.value
                        is CloudResult.Failure -> state.cloudMessage = result.message
                    }
                }
            }
        }
        AlertDialog(
            onDismissRequest = { if (!saving && !uploading) showAdd = false },
            title = { Text("Add lab report", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Ink2) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("HbA1c", "Lipid profile", "Kidney function", "Other").forEach { t ->
                            Surface(shape = RoundedCornerShape(12.dp), color = if (reportType == t) Green2 else Color(0xFFEBF7E8), modifier = Modifier.heightIn(min = 48.dp).clickable { reportType = t }) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(t, color = if (reportType == t) Color.White else Ink2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { uploadLauncher.launch("*/*") },
                        enabled = !uploading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                uploading -> "Uploading..."
                                fileUrl != null -> "File attached securely"
                                else -> "Choose PDF or photo"
                            }
                        )
                    }
                    OutlinedTextField(labName, { labName = it }, label = { Text("Lab name (optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(enabled = !saving && !uploading, colors = ButtonDefaults.buttonColors(containerColor = Green2), modifier = Modifier.heightIn(min = 48.dp), onClick = {
                    saving = true
                    state.repository.addHealthLog("labReports", mapOf(
                        "reportType" to reportType,
                        "labName" to labName.ifBlank { null },
                        "notes" to notes.ifBlank { null },
                        "fileUrl" to fileUrl,
                        "measuredAt" to FieldValue.serverTimestamp(),
                        "source" to "manual"
                    )) { r ->
                        saving = false
                        if (r is CloudResult.Success) showAdd = false else state.cloudMessage = (r as CloudResult.Failure).message
                    }
                }) { Text(if (saving) "Saving..." else "Save", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel", color = Muted2) } }
        )
    }
}

// ---------- 30-day trends (one metric at a time, same chart as every other screen) ----------
@Composable
fun TrendsScreen(state: NirogState) {
    val health = state.collectHealth()
    var metric by rememberSaveable { mutableStateOf(TrendMetric.GLUCOSE) }
    Column(Modifier.fillMaxSize().background(Paper2)) {
        DetailScreenHeader("Your trends", onBack = { state.currentScreen = "dashboard" })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StaleDataNotice(health)
            Text("Pick what you want to look at. Days you did not log are left blank, never guessed.", fontSize = 14.sp, color = Muted2, lineHeight = 20.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrendMetric.entries.forEach { m ->
                    FilterChip(
                        selected = metric == m,
                        onClick = { metric = m },
                        label = { Text(m.title) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            MetricTrendCard(health, metric, title = metric.title, initialRange = TrendRange.MONTH)
            Spacer(Modifier.height(24.dp))
        }
    }
}
