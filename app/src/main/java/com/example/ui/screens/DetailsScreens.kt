package com.nirogbhumi.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import com.nirogbhumi.app.health.HealthConnectManager
import com.nirogbhumi.app.health.HealthConnectStatus
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.SugarLog
import kotlinx.coroutines.launch

// Screen 1: Sugar Metric detailed deepdive
@Composable
fun BloodSugarDetailScreen(state: NirogState) {
    var selectedLogType by remember { mutableStateOf("Fasting") } // "Fasting" or "Post-meal"
    var sugarInputText by remember { mutableStateOf("") }
    var isRecordingDialogueOpen by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val subscription = state.repository.listenUserCollection("glucoseReadings", 30) { result ->
            when (result) {
                is com.nirogbhumi.app.data.CloudResult.Success -> {
                    val synced = result.value.mapIndexedNotNull { index, doc ->
                        val readingType = doc.values["readingType"] as? String
                        // HbA1c is a lab percentage on a different scale than mg/dL readings,
                        // so it's excluded here to avoid corrupting the mg/dL trend/average.
                        if (readingType == "hba1c") return@mapIndexedNotNull null
                        val value = (doc.values["value"] as? Number)?.toInt() ?: return@mapIndexedNotNull null
                        val type = if (readingType == "fasting") "Fasting" else "Post-meal"
                        val timestamp = (doc.values["measuredAt"] as? com.google.firebase.Timestamp)
                            ?: (doc.values["createdAt"] as? com.google.firebase.Timestamp)
                        val time = timestamp?.toDate()?.let {
                            java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()).format(it)
                        } ?: "Synced"
                        val status = if (value > 130) "High" else if (value < 80) "Low" else "Normal"
                        SugarLog(index + 1, value, type, time, status)
                    }
                    if (synced.isNotEmpty()) {
                        state.sugarLogs.clear()
                        state.sugarLogs.addAll(synced)
                        state.fastingSugarValue = synced.firstOrNull { it.type == "Fasting" }?.value ?: state.fastingSugarValue
                    }
                }
                is com.nirogbhumi.app.data.CloudResult.Failure -> Unit
            }
        }
        onDispose { subscription.cancel() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Custom Top nav bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { state.currentScreen = "dashboard" }) {
                Icon(Icons.Outlined.ArrowBack, "Back", tint = Color(0xFF1B3221))
            }
            Text(
                "Blood Sugar Story",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color(0xFF1B3221)
            )
            IconButton(onClick = { isRecordingDialogueOpen = true }) {
                Icon(Icons.Filled.Add, "Log Reading", tint = Color(0xFF1B3221))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary Card - computed from real logged readings, not a fixed value
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.35f), shape = RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("CURRENT AVERAGE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF737972))
                    Spacer(modifier = Modifier.height(4.dp))
                    if (state.sugarLogs.isEmpty()) {
                        Text("No readings logged yet", fontSize = 16.sp, color = Color(0xFF737972), modifier = Modifier.padding(top = 8.dp))
                    } else {
                        val avg = state.sugarLogs.map { it.value }.average().toInt()
                        val normalPercent = (state.sugarLogs.count { it.status == "Normal" } * 100 / state.sugarLogs.size)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$avg", fontSize = 42.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("mg/dL", fontSize = 14.sp, color = Color(0xFF737972), modifier = Modifier.padding(bottom = 6.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Your readings were within a typical range $normalPercent% of the time across your last ${state.sugarLogs.size} logs.",
                            fontSize = 13.sp,
                            color = Color(0xFF434842),
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Trend chart built from real logged readings
            Text("Recent Trend", fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.3f), shape = RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val points = state.sugarLogs.take(7).reversed()
                    if (points.size < 2) {
                        Text(
                            "Log at least 2 readings to see a trend line here.",
                            fontSize = 13.sp,
                            color = Color(0xFF737972),
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    } else {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        ) {
                            val stepX = size.width / (points.size - 1)
                            val maxY = (points.maxOf { it.value } + 10).toFloat()
                            val minY = (points.minOf { it.value } - 10).coerceAtLeast(0).toFloat()
                            val heightRange = (maxY - minY).coerceAtLeast(1f)

                            val safeMinY = size.height - ((100f - minY) / heightRange * size.height)
                            val safeMaxY = size.height - ((140f - minY) / heightRange * size.height)

                            drawRect(
                                color = Color(0xFFE5F1E2).copy(alpha = 0.5f),
                                topLeft = androidx.compose.ui.geometry.Offset(0f, safeMaxY.coerceIn(0f, size.height)),
                                size = androidx.compose.ui.geometry.Size(size.width, (safeMinY - safeMaxY).coerceIn(0f, size.height))
                            )

                            var lastX = 0f
                            var lastY = 0f
                            points.forEachIndexed { i, log ->
                                val x = i * stepX
                                val fraction = (log.value - minY) / heightRange
                                val y = size.height - (fraction * size.height)

                                drawCircle(color = Color(0xFF1B3221), radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))

                                if (i > 0) {
                                    drawLine(
                                        color = Color(0xFF1B3221),
                                        start = androidx.compose.ui.geometry.Offset(lastX, lastY),
                                        end = androidx.compose.ui.geometry.Offset(x, y),
                                        strokeWidth = 2.dp.toPx()
                                    )
                                }
                                lastX = x
                                lastY = y
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            points.forEachIndexed { i, log ->
                                Text(
                                    log.type.take(4),
                                    fontSize = 10.sp,
                                    fontWeight = if (i == points.size - 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (i == points.size - 1) Color(0xFF1B3221) else Color(0xFF737972)
                                )
                            }
                        }
                    }
                }
            }

            // High/Normal History Rows List
            Text("Logged History", fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.sugarLogs.forEach { log ->
                    SugarLogHistoryRow(log)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Add Dialog Modal
        if (isRecordingDialogueOpen) {
            AlertDialog(
                onDismissRequest = { isRecordingDialogueOpen = false },
                title = { Text("Log New Reading", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221)) },
                confirmButton = {
                    Button(
                        onClick = {
                            if (selectedLogType == "HbA1c") {
                                val percentValue = sugarInputText.toDoubleOrNull()
                                if (percentValue == null) return@Button
                                state.repository.addHealthLog(
                                    "glucoseReadings",
                                    mapOf(
                                        "value" to percentValue,
                                        "unit" to "%",
                                        "readingType" to "hba1c",
                                        "measuredAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                        "source" to "manual"
                                    )
                                ) { result ->
                                    state.cloudMessage = when (result) {
                                        is com.nirogbhumi.app.data.CloudResult.Success -> "Synced securely"
                                        is com.nirogbhumi.app.data.CloudResult.Failure -> result.message
                                    }
                                }
                            } else {
                                val sugarInt = sugarInputText.toIntOrNull() ?: return@Button
                                val status = if (sugarInt > 130) "High" else if (sugarInt < 80) "Low" else "Normal"
                                state.sugarLogs.add(
                                    0,
                                    SugarLog(
                                        state.sugarLogs.size + 1,
                                        sugarInt,
                                        selectedLogType,
                                        "Today, Just Now",
                                        status
                                    )
                                )
                                state.fastingSugarValue = sugarInt
                                state.repository.addHealthLog(
                                    "glucoseReadings",
                                    mapOf(
                                        "value" to sugarInt,
                                        "unit" to "mg/dL",
                                        "readingType" to if (selectedLogType == "Fasting") "fasting" else "post_meal",
                                        "measuredAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                        "source" to "manual"
                                    )
                                ) { result ->
                                    state.cloudMessage = when (result) {
                                        is com.nirogbhumi.app.data.CloudResult.Success -> "Synced securely"
                                        is com.nirogbhumi.app.data.CloudResult.Failure -> result.message
                                    }
                                }
                            }
                            isRecordingDialogueOpen = false
                            sugarInputText = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936))
                    ) {
                        Text("Save Record", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isRecordingDialogueOpen = false }) {
                        Text("Cancel", color = Color(0xFF737972))
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Fasting", "Post-meal", "HbA1c").forEach { type ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selectedLogType == type) Color(0xFF314936) else Color(0xFFEBF7E8),
                                    modifier = Modifier.clickable { selectedLogType = type; sugarInputText = "" }.padding(4.dp)
                                ) {
                                    Text(type, color = if (selectedLogType == type) Color.White else Color(0xFF1B3221), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                                }
                            }
                        }

                        OutlinedTextField(
                            value = sugarInputText,
                            onValueChange = { value ->
                                sugarInputText = if (selectedLogType == "HbA1c") {
                                    value.filter { it.isDigit() || it == '.' }.let { candidate -> if (candidate.count { c -> c == '.' } <= 1) candidate else sugarInputText }
                                } else value.filter(Char::isDigit)
                            },
                            placeholder = { Text(if (selectedLogType == "HbA1c") "Value in % (e.g. 5.8)" else "Value in mg/dL (e.g. 105)", color = Color(0xFFC3C8C0)) },
                            keyboardOptions = KeyboardOptions(keyboardType = if (selectedLogType == "HbA1c") KeyboardType.Decimal else KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF8F6EF),
                                unfocusedContainerColor = Color(0xFFF8F6EF),
                                focusedBorderColor = Color(0xFF314936),
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

@Composable
fun SugarLogHistoryRow(log: SugarLog) {
    val isHigh = log.status == "High"
    val isLow = log.status == "Low"
    val statusColor = if (isHigh) Color(0xFFBA1A1A) else if (isLow) Color(0xFF43242A) else Color(0xFF426820)
    val statusContainerColor = if (isHigh) Color(0xFFFFDAD6) else if (isLow) Color(0xFFFFD9DE) else Color(0xFFE5F1E2)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.3f), shape = RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Circular rating outline
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(statusContainerColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${log.value}",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B3221),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(log.type, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221), fontSize = 15.sp)
                    Text(log.time, fontSize = 12.sp, color = Color(0xFF737972))
                }
            }

            // Flag badge
            Box(
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(log.status, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusColor)
            }
        }
    }
}

// Screen 3: Booking Stepper screen
@Composable
fun BookConsultationStepper(state: NirogState) {
    DisposableEffect(Unit) {
        val subscription = state.repository.listenPublicCollection("consultationSlots", 60) { result ->
            if (result is com.nirogbhumi.app.data.CloudResult.Success) state.cloudRecords["consultationSlots"] = result.value
            else if (result is com.nirogbhumi.app.data.CloudResult.Failure) state.cloudMessage = result.message
        }
        onDispose { subscription.cancel() }
    }
    val availableSlots = state.cloudRecords["consultationSlots"].orEmpty()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (state.consultStep > 1) {
                    state.consultStep--
                } else {
                    state.currentScreen = "care_hub"
                }
            }) {
                Icon(Icons.Outlined.ArrowBack, "Back", tint = Color(0xFF1B3221))
            }
            Text(
                "Book Consultation",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color(0xFF1B3221)
            )
        }

        // Horizontal visual stepper line
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            ConsultationStepBadge(1, "Service", state.consultStep >= 1)
            Divider(modifier = Modifier.width(48.dp), color = if (state.consultStep >= 2) Color(0xFF314936) else Color(0xFFC3C8C0))
            ConsultationStepBadge(2, "Details", state.consultStep >= 2)
            Divider(modifier = Modifier.width(48.dp), color = if (state.consultStep >= 3) Color(0xFF314936) else Color(0xFFC3C8C0))
            ConsultationStepBadge(3, "Payment", state.consultStep >= 3)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (state.consultStep) {
                1 -> {
                    Text("Select consultation type", fontSize = 18.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))

                    ConsultTypeOption("Metabolic Consultation", "Direct analysis of fasting / post-meal logs with metabolic specialist.", "30 min video • ₹499", state.selectedOnMedication == "Yes") {
                        state.selectedOnMedication = "Yes"
                        state.selectedConsultType = "diabetes_lifestyle"
                    }
                    ConsultTypeOption("Ayurvedic Doctor Consult", "Personalized assessment of Prakriti element cycles and balancing tea protocols.", "45 min video • ₹650", state.selectedOnMedication == "No") {
                        state.selectedOnMedication = "No"
                        state.selectedConsultType = "naturopathy"
                    }
                }

                2 -> {
                    Text("Select Date & Pre-consultation details", fontSize = 18.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                    if (availableSlots.isEmpty()) {
                        Surface(color = Color(0xFFF5E7D1), shape = RoundedCornerShape(12.dp)) {
                            Text("No consultation slots are open right now. Please check again later or contact support.", Modifier.padding(14.dp), color = Color(0xFF624B20))
                        }
                    }

                    // Simulated Date picker chips horizontal
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableSlots.mapNotNull { it.values["dateLabel"]?.toString() }.distinct().forEach { date ->
                            val select = state.selectedConsultDate == date
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (select) Color(0xFF1B3221) else Color(0xFF737972).copy(alpha = 0.4f)),
                                color = if (select) Color(0xFF314936) else Color.White,
                                modifier = Modifier.clickable { state.selectedConsultDate = date }
                            ) {
                                Text(
                                    date,
                                    fontWeight = FontWeight.Bold,
                                    color = if (select) Color.White else Color(0xFF1B3221),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // Simulated Time grid
                    Text("Available Time Slots", fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableSlots.filter { it.values["dateLabel"]?.toString() == state.selectedConsultDate }.forEach { slot ->
                            val sTime = slot.values["timeLabel"]?.toString() ?: return@forEach
                            val sActive = state.selectedConsultTime == sTime
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (sActive) Color(0xFF426820) else Color(0xFFE5F1E2),
                                modifier = Modifier.clickable { state.selectedConsultTime = sTime; state.selectedConsultSlotId = slot.id }.width(104.dp)
                            ) {
                                Text(
                                    sTime,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = if (sActive) Color.White else Color(0xFF1B3221),
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                        }
                    }

                    Divider(color = Color(0xFFC3C8C0).copy(alpha = 0.3f))

                    Text("Describe your wellness concerns", fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))

                    OutlinedTextField(
                        value = state.userConcernText,
                        onValueChange = { state.userConcernText = it },
                        placeholder = { Text("List any current issues, blood pressure metrics etc.", color = Color(0xFFC3C8C0)) },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = Color(0xFF314936),
                            unfocusedBorderColor = Color(0xFFC3C8C0).copy(alpha = 0.5f)
                        )
                    )
                }

                3 -> {
                    // Booking Success Layout
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(72.dp).background(Color(0xFFE5F1E2), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.CheckCircle, "Booked Successfully", tint = Color(0xFF426820), modifier = Modifier.size(48.dp))
                        }

                        Text("Appointment Requested!", fontSize = 24.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(width = 0.5.dp, color = Color(0xFFC3C8C0), shape = RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("CONSULTATION SUMMARY", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF737972))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Spaceman Clinic", fontWeight = FontWeight.Bold, color = Color(0xFF1B3221), fontSize = 16.sp)
                                Text("Dr. Sharma (Ayurveda Expert)", color = Color(0xFF434842), fontSize = 14.sp)
                                Text("Date: ${state.selectedConsultDate} June", color = Color(0xFF434842), fontSize = 14.sp)
                                Text("Time: ${state.selectedConsultTime}", color = Color(0xFF434842), fontSize = 14.sp)
                                if (state.userConcernText.isNotEmpty()) {
                                    Text("Reason: \"${state.userConcernText}\"", fontSize = 13.sp, color = Color(0xFF434842))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }

        // Sticky bottom button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Button(
                onClick = {
                    if (state.consultStep == 1) {
                        state.consultStep++
                    } else if (state.consultStep == 2) {
                        state.formValues["pre_consultation.concern"] = state.userConcernText
                        state.currentScreen = "pre_consultation"
                    } else {
                        state.currentScreen = "care_hub"
                    }
                },
                enabled = state.consultStep != 2 || state.selectedConsultSlotId.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936)),
                shape = RoundedCornerShape(27.dp)
            ) {
                val consultPrice = if (state.selectedConsultType == "naturopathy") "₹650" else "₹499"
                Text(
                    text = if (state.consultStep == 1) "Confirm Service" else if (state.consultStep == 2) "Confirm Details & Pay ($consultPrice)" else "Back to Hub",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ConsultationStepBadge(stepNum: Int, label: String, active: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(if (active) Color(0xFF314936) else Color(0xFFC3C8C0), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("$stepNum", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, color = if (active) Color(0xFF1B3221) else Color(0xFF737972))
    }
}

@Composable
fun ConsultTypeOption(title: String, desc: String, info: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (selected) Color(0xFF314936) else Color(0xFF737972).copy(alpha = 0.25f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFE5F1E2) else Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1B3221))
                if (selected) {
                    Icon(Icons.Filled.CheckCircle, "Selected", tint = Color(0xFF314936))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(desc, fontSize = 13.sp, color = Color(0xFF434842), lineHeight = 18.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(info, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF426820))
        }
    }
}

// Screen 4: Active Journey Screen (reversal checklist progress)
@Composable
fun ActiveJourneyScreen(state: NirogState) {
    val protocolsList = listOf(
        "Fasting Blood Sugar",
        "Warm Lemon Water with Ginger",
        "Mandukasana Posture Sequence",
        "Brisk 10-Min Walk post lunch",
        "Bedtime Ashwagandha milk loop"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { state.currentScreen = "dashboard" }) {
                Icon(Icons.Outlined.ArrowBack, "Back", tint = Color(0xFF1B3221))
            }
            Text(
                "My Onboarding Journey",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color(0xFF1B3221)
            )
            // Balances the back button so the title stays centered - not a tappable control (no menu action exists yet)
            Spacer(modifier = Modifier.size(48.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Circle Progress Ring layout
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.35f), shape = RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("CURRENT WEEK COMPLETION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF737972))

                    Spacer(modifier = Modifier.height(16.dp))

                    // Draw rings using Box and Canvas custom shapes
                    Box(
                        modifier = Modifier.size(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // background track ring
                            drawCircle(color = Color(0xFFE5F1E2), radius = size.minDimension / 2.3f)
                        }
                        // Core value representation
                        Text(
                            "${state.activeJourneyProgress}%",
                            fontSize = 32.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B3221)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "${state.completedProtocols.size} of 5 Protocols checked off.",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF426820),
                        fontSize = 14.sp
                    )
                }
            }

            Text("Daily Protocols", fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))

            // Protocol checked card checklist
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                protocolsList.forEach { protocol ->
                    val checked = state.completedProtocols.contains(protocol)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (checked) {
                                    state.completedProtocols.remove(protocol)
                                } else {
                                    state.completedProtocols.add(protocol)
                                }
                                state.activeJourneyProgress = (state.completedProtocols.size * 20)
                            }
                            .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.25f), shape = RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = if (checked) Color(0xFFEBF7E8) else Color.White),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(protocol, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFF141E15))

                            if (checked) {
                                Icon(Icons.Filled.CheckCircle, "Completed", tint = Color(0xFF426820))
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color.Transparent, CircleShape)
                                        .border(2.dp, Color(0xFFC3C8C0), CircleShape)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// Screen 5: Detailed Insight sleeping-correlation dashboard
@Composable
fun InsightDetailScreen(state: NirogState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { state.currentScreen = "dashboard" }) {
                Icon(Icons.Outlined.ArrowBack, "Back", tint = Color(0xFF1B3221))
            }
            Text(
                "Sugar story correlation",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color(0xFF1B3221)
            )
            Icon(Icons.Filled.Spa, "Insight Correlations", tint = Color(0xFF1B3221))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("How sleep can affect fasting sugar", fontSize = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))

            Text(
                "This is general guidance, not an analysis of your specific data yet: short sleep (under 6 hours) is commonly linked to higher morning cortisol, which can push fasting glucose up. Keep logging both sleep and sugar readings - once you have enough of both, we'll show your own personal comparison here instead of general guidance.",
                fontSize = 14.sp,
                color = Color(0xFF434842),
                lineHeight = 20.sp
            )

            if (state.sugarLogs.size < 5) {
                Card(
                    modifier = Modifier.fillMaxWidth().border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.35f), shape = RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Insights, contentDescription = null, tint = Color(0xFF9CB79F), modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Not enough data yet for your personal comparison (${state.sugarLogs.size}/5 sugar readings logged).",
                            fontSize = 13.sp,
                            color = Color(0xFF737972),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Divider(color = Color(0xFFC3C8C0).copy(alpha = 0.3f))

            // Experiment CTA
            Text("Resolve the correlation pattern", fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 0.5.dp, color = Color(0xFF9CB79F).copy(alpha = 0.3f), shape = RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEBF7E8)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ACTIVE EXPERIMENT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF426820))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (state.isExperimentActive) "Experiment Active: Day ${state.experimentDayCount} of 7" else "7-Day Rest Reset Experiment",
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B3221)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "We challenge you to log at least seven hours of restful sleep daily for the next week. Observe if this aligns fasting sugars down to sub-100 ranges.",
                        fontSize = 13.sp,
                        color = Color(0xFF434842),
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            state.isExperimentActive = !state.isExperimentActive
                            if (state.isExperimentActive) {
                                state.experimentDayCount = 1
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (state.isExperimentActive) "Stop Experiment" else "Start 7-Day Experiment",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun DeviceSyncScreen(state: NirogState) {
    val context = LocalContext.current
    val healthConnect = remember { HealthConnectManager(context.applicationContext, state.repository) }
    val coroutineScope = rememberCoroutineScope()

    var isConnected by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var lastSyncMessage by remember { mutableStateOf<String?>(null) }
    var lastError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isConnected = runCatching { healthConnect.hasPermissions() }.getOrDefault(false)
    }

    val permissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        isConnected = granted.isNotEmpty()
        if (granted.isEmpty()) lastError = "No categories were approved. You can connect anytime from here."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F6EF))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { state.currentScreen = "profile" }) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = Color(0xFF1B3221))
            }
            Text("Connected Devices", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
        }

        Text(
            "Bring in data from your smart band, watch, or health app. This syncs through Android Health Connect, so it works with Samsung Health, Fitbit, Google Fit, Mi Band/Zepp, Garmin, and most other wearables that already share data with Health Connect.",
            fontSize = 14.sp,
            color = Color(0xFF434842),
            lineHeight = 20.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth().border(BorderStroke(0.5.dp, Color(0xFFD8D0C0)), RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(if (isConnected) Color(0xFFE0ECDD) else Color(0xFFF1EDE6)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isConnected) Icons.Filled.CheckCircle else Icons.Filled.Watch,
                            contentDescription = null,
                            tint = if (isConnected) Color(0xFF314936) else Color(0xFF737972),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            if (isConnected) "Connected" else "Not connected",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF1B3221)
                        )
                        Text(
                            when (healthConnect.status) {
                                HealthConnectStatus.AVAILABLE -> "Health Connect"
                                HealthConnectStatus.NEEDS_INSTALL_OR_UPDATE -> "Health Connect app needed"
                                HealthConnectStatus.UNSUPPORTED -> "Not supported on this device"
                            },
                            fontSize = 12.sp,
                            color = Color(0xFF737972)
                        )
                    }
                }

                when (healthConnect.status) {
                    HealthConnectStatus.NEEDS_INSTALL_OR_UPDATE -> Button(
                        onClick = { HealthConnectManager.openHealthConnectInstall(context) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936)),
                        shape = RoundedCornerShape(24.dp)
                    ) { Text("Install Health Connect", color = Color.White, fontWeight = FontWeight.Bold) }

                    HealthConnectStatus.UNSUPPORTED -> Text(
                        "Your device or Android version doesn't support Health Connect. You can still log everything manually from Track.",
                        fontSize = 13.sp, color = Color(0xFF8B2E2E)
                    )

                    HealthConnectStatus.AVAILABLE -> {
                        if (!isConnected) {
                            Button(
                                onClick = { permissionLauncher.launch(HealthConnectManager.permissions) },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936)),
                                shape = RoundedCornerShape(24.dp)
                            ) { Text("Connect", color = Color.White, fontWeight = FontWeight.Bold) }
                        } else {
                            Button(
                                enabled = !isSyncing,
                                onClick = {
                                    isSyncing = true
                                    lastError = null
                                    coroutineScope.launch {
                                        runCatching { healthConnect.syncLastThirtyDays() }
                                            .onSuccess { summary ->
                                                lastSyncMessage = "Synced ${summary.steps} steps, ${summary.sleep} sleep, ${summary.glucose} glucose, ${summary.bloodPressure} BP, ${summary.weight} weight records"
                                            }
                                            .onFailure { lastError = it.message ?: "Sync failed" }
                                        isSyncing = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936)),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                if (isSyncing) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                else Text("Sync Now", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                lastSyncMessage?.let {
                    Text(it, fontSize = 12.sp, color = Color(0xFF426820), fontWeight = FontWeight.Medium)
                }
                lastError?.let {
                    Text(it, fontSize = 12.sp, color = Color(0xFF8B2E2E), fontWeight = FontWeight.Medium)
                }
            }
        }

        Text("What syncs automatically", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
        listOf(
            "Steps" to Icons.Filled.DirectionsWalk,
            "Sleep" to Icons.Filled.Bedtime,
            "Heart rate" to Icons.Filled.Favorite,
            "Weight" to Icons.Filled.MonitorWeight,
            "Blood glucose (supported CGMs)" to Icons.Filled.Bloodtype,
            "Blood pressure (supported cuffs)" to Icons.Filled.Favorite
        ).forEach { (label, icon) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Icon(icon, contentDescription = null, tint = Color(0xFF426820), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(label, fontSize = 13.sp, color = Color(0xFF434842))
            }
        }

        Text(
            "Prefer manual entry? You can always log everything yourself from the Track tab in under a minute a day.",
            fontSize = 12.sp,
            color = Color(0xFF737972),
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}

@Composable
fun ProfileScreen(state: NirogState) {
    var showSignOutConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var updateCheckMessage by remember { mutableStateOf<String?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F6EF))
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { state.currentScreen = "dashboard" }) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = Color(0xFF1B3221))
            }
            Text("Profile & Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
        }

        // Profile header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF314936)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    state.profileName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(state.profileName.ifBlank { "Your name" }, fontSize = 18.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                Text(
                    state.userEmail.ifBlank { state.userMobile.ifBlank { "Complete your profile" } },
                    fontSize = 13.sp, color = Color(0xFF737972)
                )
            }
            TextButton(onClick = { state.currentScreen = "profile_edit" }) {
                Text("Edit", color = Color(0xFF314936), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        SettingsSection(title = "Account") {
            SettingsRow(Icons.Filled.Person, "Personal details") { state.currentScreen = "profile_edit" }
            SettingsRow(Icons.Filled.FamilyRestroom, "Family profiles") { state.currentScreen = "family_profiles" }
            SettingsRow(Icons.Filled.Devices, "Devices & sync") { state.currentScreen = "device_hub" }
        }

        SettingsSection(title = "Activity") {
            SettingsRow(Icons.Filled.ShoppingBag, "Orders") { state.currentScreen = "orders" }
        }

        SettingsSection(title = "Notifications & Privacy") {
            SettingsRow(Icons.Filled.Notifications, "Notification settings") { state.currentScreen = "notification_settings" }
            SettingsRow(Icons.Filled.Shield, "Privacy & consent") { state.currentScreen = "privacy_consent" }
            SettingsRow(Icons.Filled.DownloadForOffline, "Export or delete my data") { state.currentScreen = "data_controls" }
        }

        SettingsSection(title = "Support") {
            SettingsRow(Icons.Filled.HelpOutline, "Help & support") { state.currentScreen = "support" }
            SettingsRow(Icons.Filled.Description, "Legal & policies") { state.currentScreen = "legal_center" }
            SettingsRow(Icons.Filled.SystemUpdate, "Check for updates", showDivider = false) {
                if (checkingUpdate) return@SettingsRow
                checkingUpdate = true
                val activity = context as? android.app.Activity
                if (activity == null) {
                    checkingUpdate = false
                    updateCheckMessage = "Couldn't check for updates right now."
                    return@SettingsRow
                }
                com.google.firebase.appdistribution.FirebaseAppDistribution.getInstance()
                    .updateIfNewReleaseAvailable()
                    .addOnSuccessListener {
                        checkingUpdate = false
                        updateCheckMessage = "You're on the latest build available to testers."
                    }
                    .addOnFailureListener { error ->
                        checkingUpdate = false
                        updateCheckMessage = "Update check failed: ${error.message ?: "unknown error"}. " +
                            "If this is your first check, you may need to sign in as a tester in the browser tab that just opened."
                    }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = { showSignOutConfirm = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        ) {
            Text("Sign Out", color = Color(0xFF8B2E2E), fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign out?", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
            text = { Text("You'll need to sign in again to see your tracked data.") },
            confirmButton = {
                Button(
                    onClick = {
                        runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().signOut() }
                        showSignOutConfirm = false
                        state.currentScreen = "welcome"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B2E2E))
                ) { Text("Sign Out", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) { Text("Cancel") }
            }
        )
    }

    updateCheckMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { updateCheckMessage = null },
            title = { Text("Update check", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221)) },
            text = { Text(msg, fontSize = 14.sp, color = Color(0xFF434842)) },
            confirmButton = {
                Button(onClick = { updateCheckMessage = null }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936))) {
                    Text("OK", color = Color.White)
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF737972), letterSpacing = 0.5.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(0.5.dp, Color(0xFFD8D0C0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF314936), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Text(label, fontSize = 14.sp, color = Color(0xFF1B3221), modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFFC3C8C0))
        }
        if (showDivider) {
            Divider(color = Color(0xFFF0ECE2), thickness = 1.dp, modifier = Modifier.padding(start = 50.dp))
        }
    }
}

@Composable
fun ProfileEditScreen(state: NirogState) {
    var editName by remember { mutableStateOf(state.profileName) }
    var editAge by remember { mutableStateOf(state.profileAge) }
    var editGender by remember { mutableStateOf(state.profileGender) }
    var editHeight by remember { mutableStateOf(state.profileHeight) }
    var editWeight by remember { mutableStateOf(state.profileWeight) }
    var editCity by remember { mutableStateOf(state.profileCity) }
    var editLanguage by remember { mutableStateOf(state.profileLanguage) }

    var editDiabetes by remember { mutableStateOf(state.selectedDiabetesStatus) }
    var editBp by remember { mutableStateOf(state.selectedBpStatus) }
    var editMedication by remember { mutableStateOf(state.selectedOnMedication) }
    var editDoctor by remember { mutableStateOf(state.selectedDoctorSupervision) }
    var editGoal by remember { mutableStateOf(state.selectedGoal) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F2E8))
            .verticalScroll(rememberScrollState())
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF1FDEE))
                .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.5f))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { state.currentScreen = "profile" }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF1B3221)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Profile & Health Details",
                fontSize = 20.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B3221)
            )
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // General Info Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(0.5.dp, Color(0xFFD8D0C0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Personal Details",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B3221)
                    )

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Full Name", color = Color(0xFF1B3221).copy(alpha = 0.7f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF314936),
                            unfocusedBorderColor = Color(0xFFD8D0C0)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = editAge,
                            onValueChange = { editAge = it },
                            label = { Text("Age", color = Color(0xFF1B3221).copy(alpha = 0.7f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF314936),
                                unfocusedBorderColor = Color(0xFFD8D0C0)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editGender,
                            onValueChange = { editGender = it },
                            label = { Text("Gender", color = Color(0xFF1B3221).copy(alpha = 0.7f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF314936),
                                unfocusedBorderColor = Color(0xFFD8D0C0)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = editHeight,
                            onValueChange = { editHeight = it },
                            label = { Text("Height (cm)", color = Color(0xFF1B3221).copy(alpha = 0.7f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF314936),
                                unfocusedBorderColor = Color(0xFFD8D0C0)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editWeight,
                            onValueChange = { editWeight = it },
                            label = { Text("Weight (kg)", color = Color(0xFF1B3221).copy(alpha = 0.7f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF314936),
                                unfocusedBorderColor = Color(0xFFD8D0C0)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = editCity,
                            onValueChange = { editCity = it },
                            label = { Text("City", color = Color(0xFF1B3221).copy(alpha = 0.7f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF314936),
                                unfocusedBorderColor = Color(0xFFD8D0C0)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editLanguage,
                            onValueChange = { editLanguage = it },
                            label = { Text("Language", color = Color(0xFF1B3221).copy(alpha = 0.7f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF314936),
                                unfocusedBorderColor = Color(0xFFD8D0C0)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Health Status Cards Selector
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(0.5.dp, Color(0xFFD8D0C0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Metabolic Profile",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B3221)
                    )

                    // Diabetes Selection
                    Column {
                        Text("Diabetes Status", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("None", "Prediabetes", "Type 2", "Type 1", "Not sure").forEach { choice ->
                                val isSelected = editDiabetes == choice || (choice == "Type 2" && editDiabetes == "Type 2 diabetes")
                                Button(
                                    onClick = { editDiabetes = choice },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) Color(0xFF314936) else Color(0xFFF1EDE6),
                                        contentColor = if (isSelected) Color.White else Color(0xFF1B3221)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(choice, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // BP Selection
                    Column {
                        Text("Blood Pressure Status", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Normal", "High BP", "Low BP", "Not sure").forEach { choice ->
                                val isSelected = editBp == choice
                                Button(
                                    onClick = { editBp = choice },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) Color(0xFF314936) else Color(0xFFF1EDE6),
                                        contentColor = if (isSelected) Color.White else Color(0xFF1B3221)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                ) {
                                    Text(choice, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // On Medication
                    Column {
                        Text("Are you on Medication?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Yes", "No", "Not sure").forEach { choice ->
                                val isSelected = editMedication == choice
                                Button(
                                    onClick = { editMedication = choice },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) Color(0xFF314936) else Color(0xFFF1EDE6),
                                        contentColor = if (isSelected) Color.White else Color(0xFF1B3221)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(choice, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Under Doctor Supervision
                    Column {
                        Text("Are you under Doctor Supervision?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Yes", "No", "Not sure").forEach { choice ->
                                val isSelected = editDoctor == choice
                                Button(
                                    onClick = { editDoctor = choice },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) Color(0xFF314936) else Color(0xFFF1EDE6),
                                        contentColor = if (isSelected) Color.White else Color(0xFF1B3221)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(choice, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Selected Goal
                    Column {
                        Text("Your Primary Goal", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                        Spacer(modifier = Modifier.height(6.dp))
                        listOf("Manage blood sugar levels", "Control sugar and reverse naturally", "Improve overall metabolic health", "Track and manage parent's diabetes").forEach { choice ->
                            val isSelected = editGoal == choice
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { editGoal = choice }
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.5.dp,
                                        color = if (isSelected) Color(0xFF314936) else Color(0xFFD8D0C0),
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFF1FDEE) else Color.White),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = choice,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = Color(0xFF1B3221),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Save Buttons
            Button(
                onClick = {
                    if (editName.isBlank()) { state.cloudMessage = "Name cannot be empty"; return@Button }
                    state.profileName = editName
                    state.profileAge = editAge
                    state.profileGender = editGender
                    state.profileHeight = editHeight
                    state.profileWeight = editWeight
                    state.profileCity = editCity
                    state.profileLanguage = editLanguage
                    state.selectedDiabetesStatus = editDiabetes
                    state.selectedBpStatus = editBp
                    state.selectedOnMedication = editMedication
                    state.selectedDoctorSupervision = editDoctor
                    state.selectedGoal = editGoal
                    state.repository.saveProfile(mapOf(
                        "fullName" to editName, "age" to editAge.toIntOrNull(), "gender" to editGender,
                        "heightCm" to editHeight.toDoubleOrNull(), "weightKg" to editWeight.toDoubleOrNull(),
                        "city" to editCity, "preferredLanguage" to editLanguage,
                        "diabetesStatus" to editDiabetes, "bpStatus" to editBp,
                        "onMedication" to editMedication, "doctorSupervision" to editDoctor,
                        "primaryGoal" to editGoal
                    )) { result -> state.cloudMessage = when (result) {
                        is com.nirogbhumi.app.data.CloudResult.Success -> "Profile synced securely"
                        is com.nirogbhumi.app.data.CloudResult.Failure -> result.message
                    } }
                    state.currentScreen = "dashboard"
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936)),
                shape = RoundedCornerShape(26.dp)
            ) {
                Text(
                    text = "Save changes",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            TextButton(
                onClick = { state.currentScreen = "dashboard" },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Cancel",
                    color = Color(0xFF1B3221).copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun DetailScreenHeader(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = Color(0xFF1B3221))
        }
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221), modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun EmptyStateCard(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF9CB79F), modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(message, fontSize = 14.sp, color = Color(0xFF697169), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

// Family Profiles - custom list replacing the generic catalog rendering, with a
// pinned "My profile" entry plus real family member cards from Firestore.
@Composable
fun FamilyProfilesScreen(state: NirogState) {
    var records by remember { mutableStateOf<List<com.nirogbhumi.app.data.CloudDocument>?>(null) }
    DisposableEffect(Unit) {
        val subscription = state.repository.listenUserCollection("profiles", 30) { result ->
            records = when (result) {
                is com.nirogbhumi.app.data.CloudResult.Success -> result.value
                is com.nirogbhumi.app.data.CloudResult.Failure -> emptyList()
            }
        }
        onDispose { subscription.cancel() }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF8F6EF)).verticalScroll(rememberScrollState())
    ) {
        DetailScreenHeader("Family Profiles", onBack = { state.currentScreen = "profile" })

        Text(
            "Care for family without mixing health records.",
            fontSize = 13.sp, color = Color(0xFF697169),
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(0.5.dp, Color(0xFFD8D0C0))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF314936)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.profileName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.profileName.ifBlank { "You" }, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                    Text("My profile · this device", fontSize = 12.sp, color = Color(0xFF737972))
                }
                Surface(color = Color(0xFFE4EFDB), shape = RoundedCornerShape(10.dp)) {
                    Text("Active", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF314936), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "FAMILY MEMBERS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF737972),
            letterSpacing = 0.5.sp, modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        when {
            records == null -> Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF9CB79F))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Loading...", fontSize = 13.sp, color = Color(0xFF697169))
            }
            records!!.isEmpty() -> EmptyStateCard(Icons.Filled.FamilyRestroom, "No family members added yet. Add one to track their health separately from your own.")
            else -> Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                records!!.forEach { record ->
                    val name = record.values["name"]?.toString() ?: record.values["fullName"]?.toString() ?: "Family member"
                    val relationship = record.values["relationship"]?.toString()?.ifBlank { null }
                    val status = record.values["selection"]?.toString()?.ifBlank { null }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            state.selectedDocumentId = record.id
                            state.selectedDocumentValues = record.values
                            state.currentScreen = "family_dashboard"
                        },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(0.5.dp, Color(0xFFD8D0C0))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF9CB79F)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                                Text(relationship ?: "Family member", fontSize = 12.sp, color = Color(0xFF737972))
                            }
                            status?.let {
                                Surface(color = Color(0xFFF5E7D1), shape = RoundedCornerShape(10.dp)) {
                                    Text(it.replace('_', ' '), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6E4D16), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFFC3C8C0))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = { state.currentScreen = "add_family" },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936)),
            shape = RoundedCornerShape(26.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add family member", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

// Orders - custom list replacing the generic catalog rendering, with real status
// badges instead of the one-size-fits-all form/list layout.
@Composable
fun OrdersScreen(state: NirogState) {
    var records by remember { mutableStateOf<List<com.nirogbhumi.app.data.CloudDocument>?>(null) }
    DisposableEffect(Unit) {
        val subscription = state.repository.listenUserCollection("orders", 30) { result ->
            records = when (result) {
                is com.nirogbhumi.app.data.CloudResult.Success -> result.value
                is com.nirogbhumi.app.data.CloudResult.Failure -> emptyList()
            }
        }
        onDispose { subscription.cancel() }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F6EF)).verticalScroll(rememberScrollState())) {
        DetailScreenHeader("Orders", onBack = { state.currentScreen = "profile" })

        when {
            records == null -> Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF9CB79F))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Loading your orders...", fontSize = 13.sp, color = Color(0xFF697169))
            }
            records!!.isEmpty() -> EmptyStateCard(Icons.Filled.ShoppingBag, "No orders yet. The Nirog Bhumi store is coming soon.")
            else -> Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                records!!.sortedByDescending { (it.values["createdAt"] as? com.google.firebase.Timestamp)?.seconds ?: 0 }.forEach { record ->
                    val status = (record.values["orderStatus"] ?: record.values["status"])?.toString() ?: "pending"
                    val itemCount = (record.values["items"] as? List<*>)?.size ?: 0
                    val date = (record.values["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.let {
                        java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(it)
                    } ?: "Recently placed"
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            state.selectedDocumentId = record.id
                            state.selectedDocumentValues = record.values
                            state.currentScreen = "order_detail"
                        },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(0.5.dp, Color(0xFFD8D0C0))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Order …${record.id.takeLast(6)}", fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                                OrderStatusBadge(status)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                if (itemCount > 0) "$itemCount item${if (itemCount != 1) "s" else ""} · $date" else date,
                                fontSize = 12.sp, color = Color(0xFF737972)
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun OrderStatusBadge(status: String) {
    val normalized = status.lowercase()
    val (bg, fg, label) = when {
        normalized.contains("cancel") -> Triple(Color(0xFFF7E7E3), Color(0xFF8B3E36), "Cancelled")
        normalized.contains("deliver") -> Triple(Color(0xFFE4EFDB), Color(0xFF314936), "Delivered")
        normalized.contains("ship") -> Triple(Color(0xFFDDF3F3), Color(0xFF215C5C), "Shipped")
        normalized.contains("confirm") -> Triple(Color(0xFFE4EFDB), Color(0xFF314936), "Confirmed")
        else -> Triple(Color(0xFFF5E7D1), Color(0xFF6E4D16), "Processing")
    }
    Surface(color = bg, shape = RoundedCornerShape(10.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

// Notification Inbox - what the top-bar bell icon opens: real notification history
// (local reminders that fired, and server-driven updates received via FCM) instead
// of jumping straight to a settings form with nothing to look at.
@Composable
fun NotificationInboxScreen(state: NirogState) {
    var records by remember { mutableStateOf<List<com.nirogbhumi.app.data.CloudDocument>?>(null) }
    DisposableEffect(Unit) {
        val subscription = state.repository.listenUserCollection("notifications", 30) { result ->
            records = when (result) {
                is com.nirogbhumi.app.data.CloudResult.Success -> result.value
                is com.nirogbhumi.app.data.CloudResult.Failure -> emptyList()
            }
        }
        onDispose { subscription.cancel() }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F6EF)).verticalScroll(rememberScrollState())) {
        DetailScreenHeader(
            "Notifications",
            onBack = { state.currentScreen = "dashboard" },
            trailing = {
                IconButton(onClick = { state.currentScreen = "notification_settings" }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Notification settings", tint = Color(0xFF1B3221))
                }
            }
        )

        when {
            records == null -> Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF9CB79F))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Loading...", fontSize = 13.sp, color = Color(0xFF697169))
            }
            records!!.isEmpty() -> EmptyStateCard(Icons.Filled.NotificationsNone, "No notifications yet. Health reminders, order and consultation updates will show up here.")
            else -> Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                records!!.sortedByDescending { (it.values["createdAt"] as? com.google.firebase.Timestamp)?.seconds ?: 0 }.forEach { record ->
                    val category = record.values["category"]?.toString() ?: "update"
                    val title = record.values["title"]?.toString() ?: "Nirog Bhumi"
                    val body = record.values["body"]?.toString().orEmpty()
                    val route = record.values["route"]?.toString() ?: "dashboard"
                    val timestamp = (record.values["createdAt"] as? com.google.firebase.Timestamp)?.toDate()
                    val relative = timestamp?.let { relativeTimeLabel(it) } ?: ""
                    val icon = when (category) {
                        "reminder" -> Icons.Filled.NotificationsActive
                        "order" -> Icons.Filled.ShoppingBag
                        "consultation" -> Icons.Filled.MedicalServices
                        "program" -> Icons.Filled.Checklist
                        "report" -> Icons.Filled.Insights
                        "expert_message" -> Icons.Filled.Person
                        else -> Icons.Filled.Notifications
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { state.currentScreen = route },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(0.5.dp, Color(0xFFD8D0C0))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFF1FDEE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, contentDescription = null, tint = Color(0xFF426820), modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221), fontSize = 14.sp)
                                if (body.isNotBlank()) Text(body, fontSize = 12.sp, color = Color(0xFF697169), maxLines = 2)
                                if (relative.isNotBlank()) Text(relative, fontSize = 11.sp, color = Color(0xFF9CB79F), modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun relativeTimeLabel(date: java.util.Date): String {
    val diffMs = System.currentTimeMillis() - date.time
    val minutes = diffMs / 60000
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)}d ago"
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(date)
    }
}

// Notification Settings - custom Switch-based screen replacing the generic
// checklist form. Health reminders are real, on-device WorkManager schedules
// (see ReminderScheduler/ReminderWorker) so they fire even without connectivity;
// "Updates" are server-driven categories the backend consults before sending FCM.
@Composable
fun NotificationSettingsScreen(state: NirogState) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var permissionDeniedNotice by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (!granted) permissionDeniedNotice = true }

    fun ensureNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && activity != null &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var reminderStates by remember {
        mutableStateOf(com.nirogbhumi.app.notifications.ReminderType.entries.associateWith { com.nirogbhumi.app.notifications.ReminderScheduler.isEnabled(context, it) })
    }
    var quietStart by remember { mutableStateOf(com.nirogbhumi.app.notifications.ReminderScheduler.quietHoursStart(context)) }
    var quietEnd by remember { mutableStateOf(com.nirogbhumi.app.notifications.ReminderScheduler.quietHoursEnd(context)) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val updateCategories = listOf("consultation" to "Consultation updates", "program" to "Program reminders", "order" to "Order updates")
    var updateStates by remember {
        mutableStateOf(updateCategories.associate { (key, _) -> key to com.nirogbhumi.app.notifications.ReminderScheduler.isUpdateCategoryEnabled(context, key) })
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F6EF)).verticalScroll(rememberScrollState())) {
        DetailScreenHeader("Notification Settings", onBack = { state.currentScreen = "profile" })
        Text(
            "Keep reminders useful and quiet.", fontSize = 13.sp, color = Color(0xFF697169),
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))
        SettingsSection(title = "Health Reminders (on this device)") {
            com.nirogbhumi.app.notifications.ReminderType.entries.forEachIndexed { index, type ->
                ReminderToggleRow(
                    label = type.title,
                    checked = reminderStates[type] == true,
                    showDivider = index != com.nirogbhumi.app.notifications.ReminderType.entries.lastIndex
                ) { checked ->
                    if (checked) ensureNotificationPermission()
                    com.nirogbhumi.app.notifications.ReminderScheduler.setEnabled(context, type, checked)
                    reminderStates = reminderStates + (type to checked)
                }
            }
        }

        SettingsSection(title = "Quiet Hours") {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showStartPicker = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Starts at", color = Color(0xFF1B3221))
                Text(quietStart, fontWeight = FontWeight.Bold, color = Color(0xFF314936))
            }
            Divider(color = Color(0xFFF0ECE2), thickness = 1.dp)
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showEndPicker = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ends at", color = Color(0xFF1B3221))
                Text(quietEnd, fontWeight = FontWeight.Bold, color = Color(0xFF314936))
            }
        }

        SettingsSection(title = "Updates") {
            updateCategories.forEachIndexed { index, (key, label) ->
                ReminderToggleRow(
                    label = label,
                    checked = updateStates[key] == true,
                    showDivider = index != updateCategories.lastIndex
                ) { checked ->
                    com.nirogbhumi.app.notifications.ReminderScheduler.setUpdateCategoryEnabled(context, key, checked)
                    updateStates = updateStates + (key to checked)
                    val selected = updateCategories.filter { (k, _) -> updateStates[k] == true }.map { it.second }
                    state.repository.saveProfile(
                        mapOf(
                            "notificationPreferences" to mapOf(
                                "enabledUpdateTypes" to selected,
                                "quietHoursStart" to quietStart,
                                "quietHoursEnd" to quietEnd
                            ),
                            "preferencesUpdatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                    ) { }
                }
            }
        }

        if (permissionDeniedNotice) {
            Surface(
                color = Color(0xFFF5E7D1), shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    "Notifications are turned off for Nirog Bhumi in system settings, so reminders won't be visible until you allow them.",
                    modifier = Modifier.padding(12.dp), fontSize = 12.sp, color = Color(0xFF6E4D16)
                )
            }
        }

        Text(
            "Health reminders run on this device and work even offline. Quiet hours pause all reminders during that window.",
            fontSize = 11.sp, color = Color(0xFF6B736C), lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showStartPicker) {
        TimePickerAlertDialog(
            initial = quietStart,
            onDismiss = { showStartPicker = false },
            onConfirm = { value ->
                quietStart = value
                com.nirogbhumi.app.notifications.ReminderScheduler.setQuietHours(context, value, quietEnd)
                showStartPicker = false
            }
        )
    }
    if (showEndPicker) {
        TimePickerAlertDialog(
            initial = quietEnd,
            onDismiss = { showEndPicker = false },
            onConfirm = { value ->
                quietEnd = value
                com.nirogbhumi.app.notifications.ReminderScheduler.setQuietHours(context, quietStart, value)
                showEndPicker = false
            }
        )
    }
}

@Composable
private fun ReminderToggleRow(label: String, checked: Boolean, showDivider: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 14.sp, color = Color(0xFF1B3221), modifier = Modifier.weight(1f))
            Switch(
                checked = checked, onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF314936))
            )
        }
        if (showDivider) Divider(color = Color(0xFFF0ECE2), thickness = 1.dp, modifier = Modifier.padding(start = 16.dp))
    }
}

// Care+ program calendar - a simple day-by-day agenda rather than a full month-grid
// widget, since a program's meaningful unit is "day N of the program," not a
// specific calendar date; today is highlighted so members always know where they are.
@Composable
fun ProgramCalendarScreen(state: NirogState) {
    val totalDays = state.programDurationDays.toInt().coerceAtLeast(1)
    val startMillis = state.programStartedAtMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
    val todayIndex = (((System.currentTimeMillis() - startMillis) / (1000L * 60 * 60 * 24)) + 1).toInt().coerceIn(1, totalDays)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (todayIndex - 3).coerceAtLeast(0))

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F6EF))) {
        DetailScreenHeader(state.activeProgramName.ifBlank { "Program Calendar" }, onBack = { state.currentScreen = "dashboard" })
        Text(
            "Day $todayIndex of $totalDays", fontSize = 13.sp, color = Color(0xFF697169),
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(totalDays) { index ->
                val dayNumber = index + 1
                val dayDate = java.util.Date(startMillis + (dayNumber - 1) * 24L * 60 * 60 * 1000)
                val isToday = dayNumber == todayIndex
                val isPast = dayNumber < todayIndex
                Card(
                    modifier = Modifier.fillMaxWidth().then(
                        if (isToday) Modifier.border(1.5.dp, Color(0xFF314936), RoundedCornerShape(16.dp)) else Modifier
                    ),
                    colors = CardDefaults.cardColors(containerColor = if (isToday) Color(0xFFEBF7E8) else Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Day $dayNumber", fontWeight = FontWeight.Bold, color = Color(0xFF1B3221), fontSize = 14.sp)
                            Text(
                                java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault()).format(dayDate),
                                fontSize = 12.sp, color = Color(0xFF697169)
                            )
                        }
                        if (isToday) {
                            Surface(color = Color(0xFF314936), shape = RoundedCornerShape(10.dp)) {
                                Text("TODAY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        } else if (isPast) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Past", tint = Color(0xFF9CB79F), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

// Care+ admin announcement feed - read-only for regular program members, with a
// compose action shown only when the signed-in account actually has the admin
// custom claim (server-verified, not a client-trusted flag).
@Composable
fun AnnouncementsScreen(state: NirogState) {
    var records by remember { mutableStateOf<List<com.nirogbhumi.app.data.CloudDocument>?>(null) }
    var showComposer by remember { mutableStateOf(false) }
    var composeTitle by remember { mutableStateOf("") }
    var composeBody by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val subscription = state.repository.listenAnnouncements { result ->
            records = when (result) {
                is com.nirogbhumi.app.data.CloudResult.Success -> result.value
                is com.nirogbhumi.app.data.CloudResult.Failure -> emptyList()
            }
        }
        onDispose { subscription.cancel() }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F6EF))) {
        DetailScreenHeader(
            "Announcements",
            onBack = { state.currentScreen = "dashboard" },
            trailing = {
                if (state.isAdmin) {
                    IconButton(onClick = { showComposer = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "New announcement", tint = Color(0xFF1B3221))
                    }
                }
            }
        )
        Column(modifier = Modifier.fillMaxSize().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            when {
                records == null -> Row(modifier = Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF9CB79F))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Loading...", fontSize = 13.sp, color = Color(0xFF697169))
                }
                records!!.isEmpty() -> EmptyStateCard(Icons.Filled.Campaign, "No announcements yet. Updates from the Nirog Bhumi team will show up here.")
                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    records!!.forEach { record ->
                        val timestamp = (record.values["createdAt"] as? com.google.firebase.Timestamp)?.toDate()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(0.5.dp, Color(0xFFD8D0C0))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(record.values["title"]?.toString() ?: "Announcement", fontWeight = FontWeight.Bold, color = Color(0xFF1B3221), fontSize = 15.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(record.values["body"]?.toString().orEmpty(), fontSize = 13.sp, color = Color(0xFF434842), lineHeight = 18.sp)
                                if (timestamp != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(relativeTimeLabel(timestamp), fontSize = 11.sp, color = Color(0xFF9CB79F))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showComposer) {
        AlertDialog(
            onDismissRequest = { if (!posting) showComposer = false },
            title = { Text("New Announcement", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = composeTitle, onValueChange = { composeTitle = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = composeBody, onValueChange = { composeBody = it }, label = { Text("Message") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    enabled = !posting && composeTitle.isNotBlank() && composeBody.isNotBlank(),
                    onClick = {
                        posting = true
                        state.repository.postAnnouncement(composeTitle.trim(), composeBody.trim()) { result ->
                            posting = false
                            if (result is com.nirogbhumi.app.data.CloudResult.Success) {
                                composeTitle = ""; composeBody = ""; showComposer = false
                            } else state.cloudMessage = (result as com.nirogbhumi.app.data.CloudResult.Failure).message
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936))
                ) { Text(if (posting) "Posting..." else "Post", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { showComposer = false }, enabled = !posting) { Text("Cancel", color = Color(0xFF737972)) } }
        )
    }
}

// Care+ community chat - one shared room per program, not one global room, so
// conversation stays relevant to the program a member actually joined.
@Composable
fun ProgramChatScreen(state: NirogState) {
    var records by remember { mutableStateOf<List<com.nirogbhumi.app.data.CloudDocument>?>(null) }
    var messageInput by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(state.activeProgramId) {
        val subscription = state.repository.listenProgramChat(state.activeProgramId) { result ->
            records = when (result) {
                is com.nirogbhumi.app.data.CloudResult.Success -> result.value
                is com.nirogbhumi.app.data.CloudResult.Failure -> emptyList()
            }
        }
        onDispose { subscription.cancel() }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F6EF))) {
        DetailScreenHeader("Community Chat", onBack = { state.currentScreen = "dashboard" })
        Text(
            state.activeProgramName.ifBlank { "Your program" }, fontSize = 12.sp, color = Color(0xFF697169),
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        when {
            records == null -> Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF9CB79F))
            }
            records!!.isEmpty() -> Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                EmptyStateCard(Icons.Filled.Forum, "No messages yet. Say hello to your program community!")
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(records!!) { record ->
                    val isMine = record.values["userId"] == state.repository.userId
                    val senderName = record.values["senderName"]?.toString() ?: "Member"
                    val text = record.values["text"]?.toString().orEmpty()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .background(if (isMine) Color(0xFF314936) else Color.White, RoundedCornerShape(16.dp))
                                .then(if (isMine) Modifier else Modifier.border(0.5.dp, Color(0xFFD8D0C0), RoundedCornerShape(16.dp)))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            if (!isMine) Text(senderName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF426820))
                            Text(text, fontSize = 14.sp, color = if (isMine) Color.White else Color(0xFF1B3221))
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageInput,
                onValueChange = { messageInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message your program...") },
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF314936), unfocusedBorderColor = Color(0xFFD8D0C0), focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val text = messageInput.trim()
                    if (text.isBlank() || sending) return@IconButton
                    sending = true
                    state.repository.sendProgramChatMessage(state.activeProgramId, text, state.profileName.ifBlank { "Member" }) { result ->
                        sending = false
                        if (result is com.nirogbhumi.app.data.CloudResult.Success) {
                            messageInput = ""
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        } else state.cloudMessage = (result as com.nirogbhumi.app.data.CloudResult.Failure).message
                    }
                },
                modifier = Modifier.size(48.dp).background(Color(0xFF314936), CircleShape)
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// The in-app store was removed until it's actually ready to launch, so every
// entry point that used to lead into it lands here instead of a dead end.
@Composable
fun ComingSoonScreen(state: NirogState) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF8F6EF))
    ) {
        DetailScreenHeader("Nirog Bhumi Store", onBack = { state.currentScreen = "dashboard" })
        Column(
            modifier = Modifier.fillMaxSize().weight(1f).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(Color(0xFFEEE8DC)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ShoppingBag, contentDescription = null, tint = Color(0xFF314936), modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text("Coming Soon", fontFamily = FontFamily.Serif, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "We're building a thoughtful collection of wellness tools and kits. Check back soon.",
                fontSize = 14.sp, color = Color(0xFF697169), textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerAlertDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val parts = initial.split(":").mapNotNull { it.toIntOrNull() }
    val pickerState = rememberTimePickerState(
        initialHour = parts.getOrElse(0) { 21 },
        initialMinute = parts.getOrElse(1) { 0 },
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a time", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221)) },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            Button(
                onClick = { onConfirm(String.format(java.util.Locale.US, "%02d:%02d", pickerState.hour, pickerState.minute)) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936))
            ) { Text("Set", color = Color.White) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF737972)) } }
    )
}

private fun openWebUrl(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    }
}

// Articles - replaces the generic Firestore-backed catalog list with real posts
// pulled live from nirogbhumi.com's public WordPress API. Reading the rest of an
// article opens the real page on the site rather than re-rendering raw post HTML
// natively, since that HTML can contain arbitrary embeds that don't translate
// reliably to Compose.
@Composable
fun ArticlesScreen(state: NirogState) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<Result<List<com.nirogbhumi.app.content.NirogBhumiArticle>>?>(null) }
    LaunchedEffect(Unit) {
        result = com.nirogbhumi.app.content.NirogBhumiContentApi.fetchArticles()
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F6EF)).verticalScroll(rememberScrollState())) {
        DetailScreenHeader("Learn", onBack = { state.currentScreen = "dashboard" })
        Text(
            "The latest from nirogbhumi.com.", fontSize = 13.sp, color = Color(0xFF697169),
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))

        when (val current = result) {
            null -> Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF9CB79F))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Loading articles from nirogbhumi.com...", fontSize = 13.sp, color = Color(0xFF697169))
            }
            else -> current.fold(
                onSuccess = { articles ->
                    if (articles.isEmpty()) {
                        EmptyStateCard(Icons.Filled.MenuBook, "No articles available from nirogbhumi.com right now.")
                    } else {
                        Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            articles.forEach { article -> ArticleCard(article) { openWebUrl(context, article.link) } }
                        }
                    }
                },
                onFailure = {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        EmptyStateCard(Icons.Filled.CloudOff, "Couldn't reach nirogbhumi.com right now. Check your connection and try again.")
                        OutlinedButton(
                            onClick = { openWebUrl(context, "https://nirogbhumi.com") },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) { Text("Open nirogbhumi.com instead") }
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ArticleCard(article: com.nirogbhumi.app.content.NirogBhumiArticle, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(0.5.dp, Color(0xFFD8D0C0))
    ) {
        Column {
            article.imageUrl?.let { url ->
                coil.compose.AsyncImage(
                    model = url, contentDescription = article.title,
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(article.title, fontFamily = FontFamily.Serif, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                if (article.excerpt.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(article.excerpt, fontSize = 13.sp, color = Color(0xFF434842), maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(article.dateLabel, fontSize = 11.sp, color = Color(0xFF737972))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Read on nirogbhumi.com", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF314936))
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF314936), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
