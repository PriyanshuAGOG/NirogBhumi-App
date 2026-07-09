package com.nirogbhumi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldValue
import com.nirogbhumi.app.data.CloudResult
import com.nirogbhumi.app.health.HealthConnectManager
import com.nirogbhumi.app.health.TodaySyncSummary
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.SugarLog
import kotlinx.coroutines.launch

private val Green = Color(0xFF314936)
private val DeepInk = Color(0xFF1B3221)
private val PaperBg = Color(0xFFF8F6EF)
private val Muted = Color(0xFF697169)

/**
 * The core zero-friction daily loop: one guided flow through the few numbers that
 * actually matter (sugar -> BP -> weight), each step optional. The whole point is
 * that a user never has to hunt across the app to do their daily logging - they
 * tap one button, answer at most three simple questions, and they're done in under
 * two minutes. One-off logging still lives in the Track tab's quick-log chips.
 */
@Composable
fun DailyCheckInScreen(state: NirogState) {
    val context = LocalContext.current
    // step: 0 = sugar, 1 = bp, 2 = weight, 3 = medication, 4 = done summary. Quick-log
    // entry points (a chip, a tile) can jump straight to the relevant step via
    // checkinStartStep, so there's exactly one logging flow instead of parallel
    // per-metric dialogs. Medication was appended last (not inserted earlier) so
    // existing checkinStartStep=0/1/2 jump targets never shifted.
    var step by remember { mutableStateOf(state.checkinStartStep) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val widgetScope = rememberCoroutineScope()

    // Device pre-fill: a fast, silent check for today's steps/sleep already synced
    // from a connected watch/band, so a connected user has less left to log by hand.
    var syncSummary by remember { mutableStateOf<TodaySyncSummary?>(null) }
    LaunchedEffect(Unit) {
        val manager = HealthConnectManager(context, state.repository)
        val summary = runCatching { manager.syncToday() }.getOrNull()
        if (summary != null) {
            syncSummary = summary
            state.stepsLogged = summary.totalSteps
        }
    }

    // Collected results, for the closing summary. Null = skipped.
    var sugarResult by remember { mutableStateOf<String?>(null) }
    var bpResult by remember { mutableStateOf<String?>(null) }
    var weightResult by remember { mutableStateOf<String?>(null) }

    // Live inputs
    var sugarType by remember { mutableStateOf("Fasting") }
    var sugarInput by remember { mutableStateOf("") }
    var systolic by remember { mutableStateOf("") }
    var diastolic by remember { mutableStateOf("") }
    var weightInput by remember { mutableStateOf("") }
    var medicationTaken by remember { mutableStateOf<Boolean?>(null) }
    var medicationName by remember { mutableStateOf("") }
    var medicationResult by remember { mutableStateOf<String?>(null) }

    // Real Firestore doc ids for whatever's already been saved this session -
    // null means "not saved yet" (addHealthLog creates); non-null means a tap
    // on "Edit" from the closing summary should correct that same document
    // (updateHealthLog) instead of logging a duplicate reading. The raw input
    // fields above are never cleared after saving, so jumping back to an
    // already-completed step already shows the previously entered value with
    // no extra re-population logic needed.
    var sugarDocId by remember { mutableStateOf<String?>(null) }
    var bpDocId by remember { mutableStateOf<String?>(null) }
    var weightDocId by remember { mutableStateOf<String?>(null) }
    var medicationDocId by remember { mutableStateOf<String?>(null) }
    // True only while correcting a single already-completed step reached via
    // an Edit tap on the closing summary - the next successful save should
    // return straight to that summary instead of continuing forward through
    // the remaining steps a second time.
    var editingFromSummary by remember { mutableStateOf(false) }

    fun advance() { error = null; step = if (editingFromSummary) { editingFromSummary = false; 4 } else step + 1 }
    fun editStep(target: Int) { error = null; editingFromSummary = true; step = target }

    // Resume-mid-flow: if the process gets reclaimed by the OS partway through
    // (a call, a notification, low memory) rather than the member deliberately
    // leaving, routeAfterAuthSuccess (WelcomeFlow.kt) reads this same pref on
    // the next cold start and reopens straight to this exact step instead of
    // the dashboard. Cleared once the flow reaches the closing summary (step
    // 4) or the member taps Close on purpose - a stale value would otherwise
    // resume a check-in that's already finished or intentionally abandoned.
    val resumePrefs = remember { context.getSharedPreferences("nirog_prefs", android.content.Context.MODE_PRIVATE) }
    LaunchedEffect(step) {
        if (step in 0..3) {
            resumePrefs.edit()
                .putLong("checkin_resume_daykey", com.nirogbhumi.app.ui.localDayKey(System.currentTimeMillis()))
                .putInt("checkin_resume_step", step)
                .apply()
        } else {
            resumePrefs.edit().remove("checkin_resume_daykey").remove("checkin_resume_step").apply()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(PaperBg)
    ) {
        // Header: close + progress
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                resumePrefs.edit().remove("checkin_resume_daykey").remove("checkin_resume_step").apply()
                state.currentScreen = "dashboard"
            }) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = DeepInk)
            }
            if (step < 4) {
                Text("Daily Check-in", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DeepInk)
                Spacer(Modifier.weight(1f))
                Text("Step ${step + 1} of 4", fontSize = 13.sp, color = Muted)
            }
        }

        if (step < 4) {
            // Progress bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(4) { i ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(if (i <= step) Green else Color(0xFFDDE3D8), RoundedCornerShape(2.dp))
                    )
                }
            }
        }

        syncSummary?.let { summary ->
            if (step < 4) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    color = Color(0xFFE4EFE4),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Watch, contentDescription = null, tint = Color(0xFF3F7D58), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        val parts = mutableListOf<String>()
                        if (summary.totalSteps > 0) parts += "${summary.totalSteps} steps"
                        if (summary.sleepHours > 0 || summary.sleepMinutes > 0) parts += "${summary.sleepHours}h ${summary.sleepMinutes}m sleep"
                        Text(
                            "${parts.joinToString(" & ")} already synced from your watch",
                            fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3F7D58)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            when (step) {
                0 -> CheckInStep(
                    icon = Icons.Filled.Bloodtype,
                    tint = Color(0xFFBA1A1A),
                    title = "Blood sugar",
                    helper = "Log a reading, or skip if you haven't measured today."
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Fasting", "Post-meal", "HbA1c").forEach { type ->
                            TypeChip(type, sugarType == type) { sugarType = type; sugarInput = "" }
                        }
                    }
                    val sugarVoice = com.nirogbhumi.app.ui.components.rememberVoiceInputLauncher(
                        prompt = if (sugarType == "HbA1c") "Say your HbA1c, e.g. \"6.5\"" else "Say your sugar reading, e.g. \"110\"",
                        onResult = { heard ->
                            val parsed = com.nirogbhumi.app.ui.components.parseSpokenNumber(heard)
                            if (parsed != null) sugarInput = if (sugarType == "HbA1c") parsed else parsed.substringBefore(".")
                            else error = "Didn't catch a number - try again or type it in."
                        },
                        onUnavailable = { error = "Voice entry isn't available on this device." },
                    )
                    BigInput(
                        value = sugarInput,
                        onChange = { v -> sugarInput = if (sugarType == "HbA1c") v.filter { it.isDigit() || it == '.' } else v.filter(Char::isDigit) },
                        suffix = if (sugarType == "HbA1c") "%" else "mg/dL",
                        keyboard = if (sugarType == "HbA1c") KeyboardType.Decimal else KeyboardType.Number,
                        onVoiceInput = sugarVoice,
                    )
                }
                1 -> CheckInStep(
                    icon = Icons.Filled.Favorite,
                    tint = Color(0xFF426820),
                    title = "Blood pressure",
                    helper = "Enter both numbers, or skip."
                ) {
                    val bpVoice = com.nirogbhumi.app.ui.components.rememberVoiceInputLauncher(
                        prompt = "Say both numbers, e.g. \"120 over 80\"",
                        onResult = { heard ->
                            val parsed = com.nirogbhumi.app.ui.components.parseSpokenTwoNumbers(heard)
                            if (parsed != null) { systolic = parsed.first.toString(); diastolic = parsed.second.toString() }
                            else error = "Didn't catch both numbers - try \"120 over 80\", or type them in."
                        },
                        onUnavailable = { error = "Voice entry isn't available on this device." },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) { BigInput(systolic, { systolic = it.filter(Char::isDigit) }, "SYS", KeyboardType.Number) }
                        Box(Modifier.weight(1f)) { BigInput(diastolic, { diastolic = it.filter(Char::isDigit) }, "DIA", KeyboardType.Number, onVoiceInput = bpVoice) }
                    }
                }
                2 -> CheckInStep(
                    icon = Icons.Filled.MonitorWeight,
                    tint = Color(0xFF4B6450),
                    title = "Weight",
                    helper = "Optional — weekly is plenty for most people."
                ) {
                    val weightVoice = com.nirogbhumi.app.ui.components.rememberVoiceInputLauncher(
                        prompt = "Say your weight in kilograms, e.g. \"72\"",
                        onResult = { heard ->
                            val parsed = com.nirogbhumi.app.ui.components.parseSpokenNumber(heard)
                            if (parsed != null) weightInput = parsed else error = "Didn't catch a number - try again or type it in."
                        },
                        onUnavailable = { error = "Voice entry isn't available on this device." },
                    )
                    BigInput(weightInput, { weightInput = it.filter { c -> c.isDigit() || c == '.' } }, "kg", KeyboardType.Decimal, onVoiceInput = weightVoice)
                }
                3 -> CheckInStep(
                    icon = Icons.Filled.Medication,
                    tint = Color(0xFF6D4C1E),
                    title = "Medication",
                    helper = "Did you take today's medication?"
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TypeChip("Taken", medicationTaken == true) { medicationTaken = true }
                        TypeChip("Missed", medicationTaken == false) { medicationTaken = false }
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = medicationName,
                        onValueChange = { medicationName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Medication name (optional)") },
                        placeholder = { Text("Which one? (optional)", color = Color(0xFFC3C8C0)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Green, unfocusedBorderColor = Color(0xFFD8D0C0), focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
                    )
                }
                else -> CheckInDone(state, sugarResult, bpResult, weightResult, medicationResult, onEdit = ::editStep)
            }

            error?.let {
                Surface(color = Color(0xFFF7E7E3), shape = RoundedCornerShape(12.dp)) {
                    Text(it, Modifier.padding(12.dp), fontSize = 13.sp, color = Color(0xFF7B332E))
                }
            }
        }

        if (step < 4) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        // Save the current step (only if a value was entered), then advance.
                        when (step) {
                            0 -> {
                                if (sugarInput.isBlank()) { advance(); return@Button }
                                if (sugarType == "HbA1c") {
                                    val v = sugarInput.toDoubleOrNull() ?: run { error = "Enter a valid value"; return@Button }
                                    if (v < 3.0 || v > 20.0) { error = "Enter a value between 3 and 20%"; return@Button }
                                    saving = true
                                    val values = mapOf("value" to v, "unit" to "%", "readingType" to "hba1c", "measuredAt" to FieldValue.serverTimestamp(), "source" to "manual")
                                    val existing = sugarDocId
                                    if (existing == null) {
                                        state.repository.addHealthLog("glucoseReadings", values) { r ->
                                            saving = false
                                            if (r is CloudResult.Success) { sugarDocId = r.value; sugarResult = "HbA1c $v%"; advance() } else error = (r as CloudResult.Failure).message
                                        }
                                    } else {
                                        state.repository.updateHealthLog("glucoseReadings", existing, values) { r ->
                                            saving = false
                                            if (r is CloudResult.Success) { sugarResult = "HbA1c $v%"; advance() } else error = (r as CloudResult.Failure).message
                                        }
                                    }
                                } else {
                                    val v = sugarInput.toIntOrNull() ?: run { error = "Enter a valid number"; return@Button }
                                    if (v < 20 || v > 800) { error = "Enter a value between 20 and 800 mg/dL"; return@Button }
                                    saving = true
                                    val status = if (v > 130) "High" else if (v < 80) "Low" else "Normal"
                                    val values = mapOf("value" to v, "unit" to "mg/dL", "readingType" to if (sugarType == "Fasting") "fasting" else "post_meal", "measuredAt" to FieldValue.serverTimestamp(), "source" to "manual")
                                    val existing = sugarDocId
                                    if (existing == null) {
                                        state.fastingSugarValue = v
                                        state.sugarLogs.add(0, SugarLog(state.sugarLogs.size + 1, v, sugarType, "Today, Just Now", status))
                                        state.repository.addHealthLog("glucoseReadings", values) { r ->
                                            saving = false
                                            if (r is CloudResult.Success) {
                                                sugarDocId = r.value; sugarResult = "$sugarType $v mg/dL"; advance()
                                                widgetScope.launch { com.nirogbhumi.app.widget.updateHealthQuickLogWidget(context, v, status) }
                                            } else error = (r as CloudResult.Failure).message
                                        }
                                    } else {
                                        state.fastingSugarValue = v
                                        if (state.sugarLogs.isNotEmpty()) state.sugarLogs[0] = state.sugarLogs[0].copy(value = v, type = sugarType, status = status)
                                        state.repository.updateHealthLog("glucoseReadings", existing, values) { r ->
                                            saving = false
                                            if (r is CloudResult.Success) {
                                                sugarResult = "$sugarType $v mg/dL"; advance()
                                                widgetScope.launch { com.nirogbhumi.app.widget.updateHealthQuickLogWidget(context, v, status) }
                                            } else error = (r as CloudResult.Failure).message
                                        }
                                    }
                                }
                            }
                            1 -> {
                                if (systolic.isBlank() && diastolic.isBlank()) { advance(); return@Button }
                                val sys = systolic.toIntOrNull(); val dia = diastolic.toIntOrNull()
                                if (sys == null || dia == null) { error = "Enter both numbers, or skip"; return@Button }
                                if (sys < 60 || sys > 260 || dia < 30 || dia > 180) { error = "Enter a plausible BP (systolic 60-260, diastolic 30-180)"; return@Button }
                                saving = true
                                val values = mapOf("systolic" to sys, "diastolic" to dia, "measuredAt" to FieldValue.serverTimestamp(), "source" to "manual")
                                val existing = bpDocId
                                if (existing == null) {
                                    state.repository.addHealthLog("bpReadings", values) { r ->
                                        saving = false
                                        if (r is CloudResult.Success) { bpDocId = r.value; state.latestBpReading = "$sys/$dia"; bpResult = "$sys/$dia mmHg"; advance() } else error = (r as CloudResult.Failure).message
                                    }
                                } else {
                                    state.repository.updateHealthLog("bpReadings", existing, values) { r ->
                                        saving = false
                                        if (r is CloudResult.Success) { state.latestBpReading = "$sys/$dia"; bpResult = "$sys/$dia mmHg"; advance() } else error = (r as CloudResult.Failure).message
                                    }
                                }
                            }
                            2 -> {
                                if (weightInput.isBlank()) { advance(); return@Button }
                                val w = weightInput.toDoubleOrNull() ?: run { error = "Enter a valid weight"; return@Button }
                                if (w < 20.0 || w > 300.0) { error = "Enter a weight between 20 and 300 kg"; return@Button }
                                saving = true
                                val values = mapOf("valueKg" to w, "measuredAt" to FieldValue.serverTimestamp(), "source" to "manual")
                                val existing = weightDocId
                                if (existing == null) {
                                    state.repository.addHealthLog("weightLogs", values) { r ->
                                        saving = false
                                        if (r is CloudResult.Success) { weightDocId = r.value; state.profileWeight = weightInput; weightResult = "$w kg"; advance() } else error = (r as CloudResult.Failure).message
                                    }
                                } else {
                                    state.repository.updateHealthLog("weightLogs", existing, values) { r ->
                                        saving = false
                                        if (r is CloudResult.Success) { state.profileWeight = weightInput; weightResult = "$w kg"; advance() } else error = (r as CloudResult.Failure).message
                                    }
                                }
                            }
                            3 -> {
                                val taken = medicationTaken ?: run { advance(); return@Button }
                                saving = true
                                val name = medicationName.trim().take(80)
                                val values = mapOf("taken" to taken, "name" to name.ifBlank { null }, "measuredAt" to FieldValue.serverTimestamp(), "source" to "manual")
                                val existing = medicationDocId
                                if (existing == null) {
                                    state.repository.addHealthLog("medicationLogs", values) { r ->
                                        saving = false
                                        if (r is CloudResult.Success) {
                                            medicationDocId = r.value
                                            medicationResult = (if (taken) "Taken" else "Missed") + if (name.isNotBlank()) " · $name" else ""
                                            advance()
                                        } else error = (r as CloudResult.Failure).message
                                    }
                                } else {
                                    state.repository.updateHealthLog("medicationLogs", existing, values) { r ->
                                        saving = false
                                        if (r is CloudResult.Success) {
                                            medicationResult = (if (taken) "Taken" else "Missed") + if (name.isNotBlank()) " · $name" else ""
                                            advance()
                                        } else error = (r as CloudResult.Failure).message
                                    }
                                }
                            }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Green),
                    shape = RoundedCornerShape(27.dp)
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text(
                        if (editingFromSummary) "Save change" else if (step == 3) "Finish" else "Save & next",
                        fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp,
                    )
                }
                if (!editingFromSummary) {
                    TextButton(onClick = { if (!saving) advance() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (step == 3) "Skip & finish" else "Skip this", color = Muted, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    TextButton(onClick = { if (!saving) { editingFromSummary = false; step = 4 } }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel", color = Muted, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckInStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    helper: String,
    input: @Composable () -> Unit
) {
    Spacer(Modifier.height(8.dp))
    Box(
        modifier = Modifier.size(64.dp).background(tint.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = title, tint = tint, modifier = Modifier.size(30.dp)) }
    Text(title, fontFamily = FontFamily.Serif, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = DeepInk)
    Text(helper, fontSize = 14.sp, color = Muted, lineHeight = 20.sp)
    Spacer(Modifier.height(8.dp))
    input()
}

@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Green else Color(0xFFEBF7E8),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(label, color = if (selected) Color.White else DeepInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
}

@Composable
private fun BigInput(value: String, onChange: (String) -> Unit, suffix: String, keyboard: KeyboardType, onVoiceInput: (() -> Unit)? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DeepInk),
        label = { Text(suffix) },
        suffix = { Text(suffix, fontSize = 14.sp, color = Muted) },
        trailingIcon = onVoiceInput?.let {
            {
                IconButton(onClick = it) {
                    Icon(Icons.Filled.Mic, contentDescription = "Say the value instead", tint = Muted)
                }
            }
        },
        placeholder = { Text("—", fontSize = 28.sp, color = Color(0xFFC3C8C0)) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Green, unfocusedBorderColor = Color(0xFFD8D0C0), focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
    )
}

@Composable
private fun ColumnScope.CheckInDone(state: NirogState, sugar: String?, bp: String?, weight: String?, medication: String?, onEdit: (Int) -> Unit) {
    val logged = listOfNotNull(
        sugar?.let { Triple("Blood sugar", it, 0) },
        bp?.let { Triple("Blood pressure", it, 1) },
        weight?.let { Triple("Weight", it, 2) },
        medication?.let { Triple("Medication", it, 3) }
    )
    // Smart reminder timing: a genuine completion (not an empty skip-through)
    // feeds the hour into checkinHourHint, then re-aligns the on-device
    // reminder to it if the member has that reminder turned on.
    val context = LocalContext.current
    // Milestone moments: a one-time acknowledgment beat, not a persistent
    // badge - only ever surfaces right here, right after the check-in that
    // actually reached the milestone, so it can't repeat on a later screen
    // view of the same day's already-completed check-in.
    var milestoneStreak by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        if (logged.isEmpty()) return@LaunchedEffect
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        state.repository.recordCheckinCompletion(hour) { result ->
            val hint = (result as? CloudResult.Success)?.value ?: return@recordCheckinCompletion
            if (com.nirogbhumi.app.notifications.ReminderScheduler.isEnabled(context, com.nirogbhumi.app.notifications.ReminderType.DAILY_CHECKIN)) {
                com.nirogbhumi.app.notifications.ReminderScheduler.scheduleSmart(context, hint)
            }
            state.repository.peekCheckinStreak { streakResult ->
                val streak = (streakResult as? CloudResult.Success)?.value ?: return@peekCheckinStreak
                if (streak == 7 || streak == 30 || streak == 100) milestoneStreak = streak
            }
        }
    }
    Spacer(Modifier.height(32.dp))
    Box(
        modifier = Modifier.size(80.dp).background(Color(0xFFE4EFDB), CircleShape).align(Alignment.CenterHorizontally),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Green, modifier = Modifier.size(44.dp)) }
    Text(
        if (logged.isEmpty()) "All caught up" else "Nicely done",
        fontFamily = FontFamily.Serif, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = DeepInk,
        modifier = Modifier.align(Alignment.CenterHorizontally)
    )
    Text(
        if (logged.isEmpty()) "Nothing to log right now — come back when you have a reading." else "Here's what you logged today.",
        fontSize = 14.sp, color = Muted, textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.CenterHorizontally)
    )
    milestoneStreak?.let { streak ->
        Spacer(Modifier.height(16.dp))
        Surface(
            Modifier.fillMaxWidth(),
            color = Color(0xFFF4E9D3),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎉", fontSize = 24.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "$streak-day rhythm!",
                    fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF7A5A1E),
                )
                Text(
                    "That's a real, earned pattern - keep it going at whatever pace works for you.",
                    fontSize = 12.sp, color = Color(0xFF8A6C2E), textAlign = TextAlign.Center,
                )
            }
        }
    }
    if (logged.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        logged.forEach { (label, value, stepIndex) ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onEdit(stepIndex) },
                color = Color.White, shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFD8D0C0)),
            ) {
                Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 14.sp, color = Muted, modifier = Modifier.weight(1f))
                    Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DeepInk)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Edit, contentDescription = "Edit $label", tint = Muted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { state.currentScreen = "body_report" },
        modifier = Modifier.fillMaxWidth().height(54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Green),
        shape = RoundedCornerShape(27.dp)
    ) { Text("See your Body Report", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp) }
}
