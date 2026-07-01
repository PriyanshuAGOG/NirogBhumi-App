package com.nirogbhumi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldValue
import com.nirogbhumi.app.data.CloudResult
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.SugarLog

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
    // step: 0 = sugar, 1 = bp, 2 = weight, 3 = done summary
    var step by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

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

    fun advance() { error = null; step++ }

    Column(
        modifier = Modifier.fillMaxSize().background(PaperBg)
    ) {
        // Header: close + progress
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { state.currentScreen = "dashboard" }) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = DeepInk)
            }
            if (step < 3) {
                Text("Daily Check-in", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DeepInk)
                Spacer(Modifier.weight(1f))
                Text("Step ${step + 1} of 3", fontSize = 13.sp, color = Muted)
            }
        }

        if (step < 3) {
            // Progress bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(if (i <= step) Green else Color(0xFFDDE3D8), RoundedCornerShape(2.dp))
                    )
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
                    BigInput(
                        value = sugarInput,
                        onChange = { v -> sugarInput = if (sugarType == "HbA1c") v.filter { it.isDigit() || it == '.' } else v.filter(Char::isDigit) },
                        suffix = if (sugarType == "HbA1c") "%" else "mg/dL",
                        keyboard = if (sugarType == "HbA1c") KeyboardType.Decimal else KeyboardType.Number
                    )
                }
                1 -> CheckInStep(
                    icon = Icons.Filled.Favorite,
                    tint = Color(0xFF426820),
                    title = "Blood pressure",
                    helper = "Enter both numbers, or skip."
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) { BigInput(systolic, { systolic = it.filter(Char::isDigit) }, "SYS", KeyboardType.Number) }
                        Box(Modifier.weight(1f)) { BigInput(diastolic, { diastolic = it.filter(Char::isDigit) }, "DIA", KeyboardType.Number) }
                    }
                }
                2 -> CheckInStep(
                    icon = Icons.Filled.MonitorWeight,
                    tint = Color(0xFF4B6450),
                    title = "Weight",
                    helper = "Optional — weekly is plenty for most people."
                ) {
                    BigInput(weightInput, { weightInput = it.filter { c -> c.isDigit() || c == '.' } }, "kg", KeyboardType.Decimal)
                }
                else -> CheckInDone(state, sugarResult, bpResult, weightResult)
            }

            error?.let {
                Surface(color = Color(0xFFF7E7E3), shape = RoundedCornerShape(12.dp)) {
                    Text(it, Modifier.padding(12.dp), fontSize = 13.sp, color = Color(0xFF7B332E))
                }
            }
        }

        if (step < 3) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        // Save the current step (only if a value was entered), then advance.
                        when (step) {
                            0 -> {
                                if (sugarInput.isBlank()) { advance(); return@Button }
                                if (sugarType == "HbA1c") {
                                    val v = sugarInput.toDoubleOrNull() ?: run { error = "Enter a valid value"; return@Button }
                                    saving = true
                                    state.repository.addHealthLog("glucoseReadings", mapOf("value" to v, "unit" to "%", "readingType" to "hba1c", "measuredAt" to FieldValue.serverTimestamp(), "source" to "manual")) { r ->
                                        saving = false
                                        if (r is CloudResult.Success) { sugarResult = "HbA1c $v%"; advance() } else error = (r as CloudResult.Failure).message
                                    }
                                } else {
                                    val v = sugarInput.toIntOrNull() ?: run { error = "Enter a valid number"; return@Button }
                                    saving = true
                                    state.repository.addHealthLog("glucoseReadings", mapOf("value" to v, "unit" to "mg/dL", "readingType" to if (sugarType == "Fasting") "fasting" else "post_meal", "measuredAt" to FieldValue.serverTimestamp(), "source" to "manual")) { r ->
                                        saving = false
                                        if (r is CloudResult.Success) {
                                            state.fastingSugarValue = v
                                            state.sugarLogs.add(0, SugarLog(state.sugarLogs.size + 1, v, sugarType, "Today, Just Now", if (v > 130) "High" else if (v < 80) "Low" else "Normal"))
                                            sugarResult = "$sugarType $v mg/dL"; advance()
                                        } else error = (r as CloudResult.Failure).message
                                    }
                                }
                            }
                            1 -> {
                                if (systolic.isBlank() && diastolic.isBlank()) { advance(); return@Button }
                                val sys = systolic.toIntOrNull(); val dia = diastolic.toIntOrNull()
                                if (sys == null || dia == null) { error = "Enter both numbers, or skip"; return@Button }
                                saving = true
                                state.repository.addHealthLog("bpReadings", mapOf("systolic" to sys, "diastolic" to dia, "measuredAt" to FieldValue.serverTimestamp(), "source" to "manual")) { r ->
                                    saving = false
                                    if (r is CloudResult.Success) { state.latestBpReading = "$sys/$dia"; bpResult = "$sys/$dia mmHg"; advance() } else error = (r as CloudResult.Failure).message
                                }
                            }
                            2 -> {
                                if (weightInput.isBlank()) { advance(); return@Button }
                                val w = weightInput.toDoubleOrNull() ?: run { error = "Enter a valid weight"; return@Button }
                                saving = true
                                state.repository.addHealthLog("weightLogs", mapOf("valueKg" to w, "measuredAt" to FieldValue.serverTimestamp(), "source" to "manual")) { r ->
                                    saving = false
                                    if (r is CloudResult.Success) { state.profileWeight = weightInput; weightResult = "$w kg"; advance() } else error = (r as CloudResult.Failure).message
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
                    else Text(if (step == 2) "Finish" else "Save & next", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                }
                TextButton(onClick = { if (!saving) advance() }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (step == 2) "Skip & finish" else "Skip this", color = Muted, fontWeight = FontWeight.SemiBold)
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
    input: @Composable ColumnScope.() -> Unit
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
private fun BigInput(value: String, onChange: (String) -> Unit, suffix: String, keyboard: KeyboardType) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DeepInk),
        suffix = { Text(suffix, fontSize = 14.sp, color = Muted) },
        placeholder = { Text("—", fontSize = 28.sp, color = Color(0xFFC3C8C0)) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Green, unfocusedBorderColor = Color(0xFFD8D0C0), focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
    )
}

@Composable
private fun ColumnScope.CheckInDone(state: NirogState, sugar: String?, bp: String?, weight: String?) {
    val logged = listOfNotNull(
        sugar?.let { "Blood sugar" to it },
        bp?.let { "Blood pressure" to it },
        weight?.let { "Weight" to it }
    )
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
    if (logged.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        logged.forEach { (label, value) ->
            Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFD8D0C0))) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 14.sp, color = Muted, modifier = Modifier.weight(1f))
                    Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DeepInk)
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { state.currentScreen = "dashboard" },
        modifier = Modifier.fillMaxWidth().height(54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Green),
        shape = RoundedCornerShape(27.dp)
    ) { Text("Back to Today", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp) }
}
