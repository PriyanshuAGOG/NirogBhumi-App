package com.nirogbhumi.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.nirogbhumi.app.data.CloudDocument
import com.nirogbhumi.app.data.CloudResult
import com.nirogbhumi.app.ui.NirogState

private val Ink2 = Color(0xFF1B3221)
private val Green2 = Color(0xFF314936)
private val Paper2 = Color(0xFFF8F6EF)
private val Muted2 = Color(0xFF697169)

private fun docTime(values: Map<String, Any?>): java.util.Date? =
    (values["measuredAt"] as? Timestamp ?: values["createdAt"] as? Timestamp)?.toDate()

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

private enum class TrendRange(val days: Int, val label: String) {
    WEEK(7, "7D"), MONTH(30, "30D"), QUARTER(90, "90D")
}

/**
 * Shared 7/30/90-day trend chart for the metric detail screens (BP, Sleep,
 * Blood Sugar) - one implementation instead of three near-identical Canvas
 * blocks. Buckets raw readings into one point per local calendar day (the
 * latest reading that day) rather than plotting every raw reading, since a
 * 90-day range can otherwise pack in far too many points to read; days with
 * no reading simply produce no point; there's no real time-scale x-axis
 * (points are spaced evenly), matching the simplification the rest of the
 * app's hand-rolled charts (Weekly Rhythm, onboarding sparklines) already use.
 */
@Composable
fun RangeTrendChart(readings: List<Pair<Long, Float>>, unit: String) {
    var range by remember { mutableStateOf(TrendRange.WEEK) }
    val now = remember { System.currentTimeMillis() }
    val bucketed = remember(readings, range) {
        val cutoff = now - range.days.toLong() * 86_400_000L
        readings
            .filter { it.first >= cutoff }
            .groupBy { com.nirogbhumi.app.ui.localDayKey(it.first) }
            .toSortedMap()
            .map { (_, vals) -> vals.maxBy { it.first }.second }
    }
    Card(
        modifier = Modifier.fillMaxWidth().border(0.5.dp, Color(0xFFC3C8C0).copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Trend", fontWeight = FontWeight.SemiBold, color = Ink2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TrendRange.entries.forEach { r ->
                        val selected = range == r
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Green2 else Color(0xFFEEE8DC))
                                .clickable { range = r }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(r.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else Muted2)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (bucketed.size < 2) {
                Text(
                    "Not enough $unit readings in the last ${range.days} days to chart a trend yet.",
                    fontSize = 13.sp, color = Muted2, modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                    val min = bucketed.min(); val spread = (bucketed.max() - min).coerceAtLeast(1f)
                    repeat(4) { row -> val y = size.height * row / 3f; drawLine(Color(0xFFD8D0C0), Offset(0f, y), Offset(size.width, y), 1f) }
                    bucketed.zipWithNext().forEachIndexed { i, pair ->
                        val x1 = size.width * i / (bucketed.size - 1); val x2 = size.width * (i + 1) / (bucketed.size - 1)
                        val y1 = size.height - ((pair.first - min) / spread * size.height); val y2 = size.height - ((pair.second - min) / spread * size.height)
                        drawLine(Green2, Offset(x1, y1), Offset(x2, y2), 4f)
                        drawCircle(Ink2, 4.dp.toPx(), Offset(x1, y1))
                    }
                    drawCircle(Ink2, 4.dp.toPx(), Offset(size.width.toFloat(), size.height - ((bucketed.last() - min) / spread * size.height)))
                }
                Spacer(Modifier.height(6.dp))
                Text("${bucketed.size} day(s) with a reading in the last ${range.days} days", fontSize = 11.sp, color = Muted2)
            }
        }
    }
}

// ---------- Blood Pressure ----------
@Composable
fun BpOverviewScreen(state: NirogState) {
    var records by remember { mutableStateOf<List<CloudDocument>?>(null) }
    DisposableEffect(Unit) {
        // 120, not 30 - a 90-day trend chart needs enough history for members
        // logging BP most days.
        val sub = state.repository.listenUserCollection("bpReadings", 120, orderByField = "createdAt", descending = true) { r ->
            records = if (r is CloudResult.Success) r.value else emptyList()
            if (r is CloudResult.Success) r.value.firstOrNull()?.let { d ->
                val s = (d.values["systolic"] as? Number)?.toInt(); val di = (d.values["diastolic"] as? Number)?.toInt()
                if (s != null && di != null) state.latestBpReading = "$s/$di"
            }
        }
        onDispose { sub.cancel() }
    }
    val sorted = (records ?: emptyList()).sortedByDescending { docTime(it.values)?.time ?: 0 }
    val latest = sorted.firstOrNull()

    Column(Modifier.fillMaxSize().background(Paper2)) {
        DetailScreenHeader("Blood Pressure", onBack = { state.currentScreen = "dashboard" }, trailing = {
            IconButton(onClick = { state.checkinStartStep = 1; state.currentScreen = "daily_checkin" }) { Icon(Icons.Filled.Add, "Add reading", tint = Ink2) }
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when {
                records == null -> LoadingRow()
                latest == null -> EmptyStateCard(Icons.Filled.Favorite, "No blood pressure readings yet. Tap + to add one.")
                else -> {
                    val s = (latest.values["systolic"] as? Number)?.toInt() ?: 0
                    val d = (latest.values["diastolic"] as? Number)?.toInt() ?: 0
                    val status = when {
                        s >= 140 || d >= 90 -> "This reading is on the higher side. One reading isn't a diagnosis — measure again calmly and talk to your doctor if it stays high."
                        s < 90 || d < 60 -> "This reading is on the lower side. If you feel unwell, sit down and consult your doctor."
                        else -> "This reading is within a typical range. Measure at a consistent time for the clearest trend."
                    }
                    MetricSummaryCard("Latest reading", "$s/$d", "mmHg", status)
                    val trendReadings = sorted.mapNotNull { rec ->
                        val time = docTime(rec.values)?.time ?: return@mapNotNull null
                        val sv = (rec.values["systolic"] as? Number)?.toFloat() ?: return@mapNotNull null
                        time to sv
                    }
                    RangeTrendChart(trendReadings, "systolic")
                    Text("History", fontWeight = FontWeight.Bold, color = Ink2)
                    sorted.take(20).forEach { rec ->
                        val sv = (rec.values["systolic"] as? Number)?.toInt() ?: 0
                        val dv = (rec.values["diastolic"] as? Number)?.toInt() ?: 0
                        HistoryRow("$sv/$dv mmHg", docTime(rec.values)?.let { relativeTimeLabel(it) } ?: "Synced")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------- Sleep ----------
@Composable
fun SleepOverviewScreen(state: NirogState) {
    var records by remember { mutableStateOf<List<CloudDocument>?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        // 120, not 30 - a 90-day trend chart needs enough history for members
        // logging sleep most nights.
        val sub = state.repository.listenUserCollection("sleepLogs", 120, orderByField = "createdAt", descending = true) { r -> records = if (r is CloudResult.Success) r.value else emptyList() }
        onDispose { sub.cancel() }
    }
    val sorted = (records ?: emptyList()).sortedByDescending { docTime(it.values)?.time ?: 0 }
    val latest = sorted.firstOrNull()
    fun durationHours(v: Map<String, Any?>): Double? = (v["duration"] as? Number)?.toDouble() ?: (v["durationHours"] as? Number)?.toDouble()

    Column(Modifier.fillMaxSize().background(Paper2)) {
        DetailScreenHeader("Sleep", onBack = { state.currentScreen = "dashboard" }, trailing = {
            IconButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, "Add sleep", tint = Ink2) }
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when {
                records == null -> LoadingRow()
                latest == null -> EmptyStateCard(Icons.Filled.Bedtime, "No sleep logged yet. Tap + to record last night.")
                else -> {
                    val h = durationHours(latest.values) ?: 0.0
                    val hrs = h.toInt(); val mins = ((h - hrs) * 60).toInt()
                    val sub = when {
                        h in 7.0..9.0 -> "That's a healthy amount of rest. Consistency night to night matters as much as the total."
                        h < 6.0 -> "That's a little short. Even 30 minutes earlier to bed can help your next-day energy and sugar."
                        else -> "Logged. Aim for a consistent sleep and wake time for the steadiest rhythm."
                    }
                    MetricSummaryCard("Last night", "${hrs}h ${mins}m", "", sub)
                    val trendReadings = sorted.mapNotNull { rec ->
                        val time = docTime(rec.values)?.time ?: return@mapNotNull null
                        val hv = durationHours(rec.values)?.toFloat() ?: return@mapNotNull null
                        time to hv
                    }
                    RangeTrendChart(trendReadings, "sleep")
                    Text("History", fontWeight = FontWeight.Bold, color = Ink2)
                    sorted.take(20).forEach { rec ->
                        val dh = durationHours(rec.values) ?: 0.0
                        HistoryRow("${dh.toInt()}h ${((dh - dh.toInt()) * 60).toInt()}m", docTime(rec.values)?.let { relativeTimeLabel(it) } ?: "Synced")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAdd) {
        var sleepTime by remember { mutableStateOf("22:30") }
        var wakeTime by remember { mutableStateOf("06:30") }
        var pickStart by remember { mutableStateOf(false) }
        var pickEnd by remember { mutableStateOf(false) }
        var saving by remember { mutableStateOf(false) }
        val mins = sleepDurationMinutesPublic(sleepTime, wakeTime)
        AlertDialog(
            onDismissRequest = { if (!saving) showAdd = false },
            title = { Text("Add sleep", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Ink2) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { pickStart = true }, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Went to sleep", color = Ink2); Text(sleepTime, fontWeight = FontWeight.Bold, color = Green2)
                    }
                    Divider(color = Color(0xFFF0ECE2))
                    Row(Modifier.fillMaxWidth().clickable { pickEnd = true }, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Woke up", color = Ink2); Text(wakeTime, fontWeight = FontWeight.Bold, color = Green2)
                    }
                    if (mins != null) Text("Duration: ${mins / 60}h ${mins % 60}m", fontSize = 13.sp, color = Muted2)
                }
            },
            confirmButton = {
                Button(enabled = !saving && mins != null, colors = ButtonDefaults.buttonColors(containerColor = Green2), onClick = {
                    val hours = (mins ?: 0) / 60.0
                    saving = true
                    state.repository.addHealthLog("sleepLogs", mapOf("sleepTime" to sleepTime, "wakeTime" to wakeTime, "duration" to hours, "measuredAt" to FieldValue.serverTimestamp(), "source" to "manual")) { r ->
                        saving = false
                        if (r is CloudResult.Success) {
                            state.sleepHours = ((mins ?: 0) / 60).toInt(); state.sleepMinutes = ((mins ?: 0) % 60).toInt(); showAdd = false
                        } else state.cloudMessage = (r as CloudResult.Failure).message
                    }
                }) { Text(if (saving) "Saving..." else "Save", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel", color = Muted2) } }
        )
        if (pickStart) TimePickerAlertDialog(sleepTime, { pickStart = false }, { sleepTime = it; pickStart = false })
        if (pickEnd) TimePickerAlertDialog(wakeTime, { pickEnd = false }, { wakeTime = it; pickEnd = false })
    }
}

private fun sleepDurationMinutesPublic(sleepTime: String, wakeTime: String): Long? {
    val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    val s = runCatching { java.time.LocalTime.parse(sleepTime, fmt) }.getOrNull() ?: return null
    val e = runCatching { java.time.LocalTime.parse(wakeTime, fmt) }.getOrNull() ?: return null
    return java.time.Duration.between(s, e).toMinutes().let { if (it <= 0) it + 24 * 60 else it }
}

// ---------- Walking & Activity ----------
@Composable
fun WalkingActivityScreen(state: NirogState) {
    var records by remember { mutableStateOf<List<CloudDocument>?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val sub = state.repository.listenUserCollection("walkLogs", 30, orderByField = "createdAt", descending = true) { r -> records = if (r is CloudResult.Success) r.value else emptyList() }
        onDispose { sub.cancel() }
    }
    val sorted = (records ?: emptyList()).sortedByDescending { docTime(it.values)?.time ?: 0 }

    Column(Modifier.fillMaxSize().background(Paper2)) {
        DetailScreenHeader("Walking & Activity", onBack = { state.currentScreen = "dashboard" }, trailing = {
            IconButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, "Log activity", tint = Ink2) }
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Steps from connected devices (Health Connect)
            MetricSummaryCard(
                "Steps today",
                if (state.stepsLogged > 0) String.format("%,d", state.stepsLogged) else "—",
                if (state.stepsLogged > 0) "steps" else "",
                if (state.stepsLogged > 0) "Synced from your connected device." else "Connect a band or watch under Settings › Devices to sync steps automatically."
            )
            if (state.stepsLogged == 0) {
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
            Text("Logged activity", fontWeight = FontWeight.Bold, color = Ink2)
            when {
                records == null -> LoadingRow()
                sorted.isEmpty() -> EmptyStateCard(Icons.Filled.DirectionsWalk, "No activity logged yet. Tap + to add a walk, yoga, or workout.")
                else -> sorted.take(20).forEach { rec ->
                    val type = (rec.values["activityType"] as? String)?.replaceFirstChar { it.uppercase() } ?: "Walk"
                    val minutes = (rec.values["minutes"] as? Number)?.toInt() ?: 0
                    HistoryRow("$type · $minutes min", docTime(rec.values)?.let { relativeTimeLabel(it) } ?: "Synced")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAdd) {
        ActivityLogDialog(state) { showAdd = false }
    }
}

/**
 * The single, shared activity-logging dialog - opened from Walking & Activity's
 * own "+" and from Track's "Activity" quick-log chip, so there's exactly one
 * place this flow is implemented instead of two that could drift apart.
 *
 * When no connected device has already supplied today's step count, minutes
 * are converted into a clearly-labelled estimate rather than left blank -
 * the estimate is never presented as if it were a real device reading.
 */
@Composable
fun ActivityLogDialog(state: NirogState, onDismiss: () -> Unit) {
    var activityType by remember { mutableStateOf("Walk") }
    var minutes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    val stepsPerMinute = when (activityType) {
        "Walk" -> 100
        "Yoga" -> 20
        "Cycling", "Other" -> 0
        else -> 90 // Exercise
    }
    val minutesValue = minutes.toIntOrNull()
    val willEstimate = state.stepsLogged == 0 && stepsPerMinute > 0 && (minutesValue ?: 0) > 0

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Log activity", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Ink2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Walk", "Yoga", "Exercise", "Cycling", "Other").forEach { t ->
                        Surface(shape = RoundedCornerShape(12.dp), color = if (activityType == t) Green2 else Color(0xFFEBF7E8), modifier = Modifier.clickable { activityType = t }) {
                            Text(t, color = if (activityType == t) Color.White else Ink2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                        }
                    }
                }
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("Duration (minutes)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                if (willEstimate) {
                    Text(
                        "No connected device today - about ${minutesValue!! * stepsPerMinute} steps estimated from duration.",
                        fontSize = 12.sp, color = Muted2
                    )
                }
            }
        },
        confirmButton = {
            Button(enabled = !saving && minutes.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Green2), onClick = {
                val m = minutes.toIntOrNull() ?: return@Button
                saving = true
                val estimatedSteps = if (state.stepsLogged == 0) m * stepsPerMinute else 0
                state.repository.addHealthLog("walkLogs", mapOf("activityType" to activityType.lowercase(), "minutes" to m, "estimatedSteps" to estimatedSteps.takeIf { it > 0 }, "measuredAt" to FieldValue.serverTimestamp(), "source" to "manual")) { r ->
                    saving = false
                    if (r is CloudResult.Success) {
                        if (estimatedSteps > 0) state.stepsLogged += estimatedSteps
                        onDismiss()
                    } else state.cloudMessage = (r as CloudResult.Failure).message
                }
            }) { Text(if (saving) "Saving..." else "Save", color = Color.White) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted2) } }
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
    var records by remember { mutableStateOf<List<CloudDocument>?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val sub = state.repository.listenUserCollection("labReports", 30, orderByField = "createdAt", descending = true) { r -> records = if (r is CloudResult.Success) r.value else emptyList() }
        onDispose { sub.cancel() }
    }
    val sorted = (records ?: emptyList()).sortedByDescending { docTime(it.values)?.time ?: 0 }

    Column(Modifier.fillMaxSize().background(Paper2)) {
        DetailScreenHeader("Lab Reports", onBack = { state.currentScreen = "dashboard" }, trailing = {
            IconButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, "Add report", tint = Ink2) }
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Kept together and private - only you can see these.", fontSize = 13.sp, color = Muted2)
            when {
                records == null -> LoadingRow()
                sorted.isEmpty() -> EmptyStateCard(Icons.Filled.Science, "No lab reports yet. Tap + to keep one on file for your next doctor visit.")
                else -> sorted.forEach { rec ->
                    val type = rec.values["reportType"] as? String ?: "Report"
                    val lab = rec.values["labName"] as? String
                    HistoryRow(
                        if (!lab.isNullOrBlank()) "$type · $lab" else type,
                        docTime(rec.values)?.let { relativeTimeLabel(it) } ?: "Synced"
                    )
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
                            Surface(shape = RoundedCornerShape(12.dp), color = if (reportType == t) Green2 else Color(0xFFEBF7E8), modifier = Modifier.clickable { reportType = t }) {
                                Text(t, color = if (reportType == t) Color.White else Ink2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { uploadLauncher.launch("*/*") },
                        enabled = !uploading,
                        modifier = Modifier.fillMaxWidth(),
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
                Button(enabled = !saving && !uploading, colors = ButtonDefaults.buttonColors(containerColor = Green2), onClick = {
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
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel", color = Muted2) } }
        )
    }
}
