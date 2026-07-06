package com.nirogbhumi.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.Timestamp
import com.nirogbhumi.app.data.CloudResult
import com.nirogbhumi.app.notifications.EventReminderWorker
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import com.nirogbhumi.app.health.HealthConnectManager
import com.nirogbhumi.app.health.HealthConnectStatus
import com.nirogbhumi.app.health.computeSleepGlucoseInsight
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.canManageProgram
import com.nirogbhumi.app.ui.SugarLog
import com.nirogbhumi.app.ui.components.NirogCard
import com.nirogbhumi.app.ui.components.RowCard
import com.nirogbhumi.app.ui.theme.NirogColor
import com.nirogbhumi.app.ui.theme.NirogRadius
import com.nirogbhumi.app.ui.theme.NirogSpace
import com.nirogbhumi.app.ui.theme.NirogType
import kotlinx.coroutines.launch

// Screen 1: Sugar Metric detailed deepdive
@Composable
fun BloodSugarDetailScreen(state: NirogState) {
    DisposableEffect(Unit) {
        val subscription = state.repository.listenUserCollection("glucoseReadings", 30, orderByField = "measuredAt", descending = true) { result ->
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
            IconButton(onClick = { state.checkinStartStep = 0; state.currentScreen = "daily_checkin" }) {
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
                        label = { Text("Wellness concerns") },
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
private data class DailyProtocol(val id: String, val title: String, val autoCompletable: Boolean, val onOpen: (NirogState) -> Unit)

// Deliberately generic, safe daily wellness actions - no named herbs,
// supplements, or home remedies (e.g. "ashwagandha," "lemon water with
// ginger"), since recommending those without an expert reviewing the
// member's actual conditions/medications isn't something this app should
// do on its own. Four of five are auto-completable straight from real
// logged data (never a manual "trust me" toggle); tapping an incomplete
// one deep-links into the exact screen that logs it.
private val DAILY_PROTOCOLS = listOf(
    DailyProtocol("fasting_reading", "Log a fasting sugar reading", autoCompletable = true) { it.isQuickLogFastingOpen = true },
    DailyProtocol("checkin", "Complete today's check-in", autoCompletable = true) { it.checkinStartStep = 0; it.currentScreen = "daily_checkin" },
    DailyProtocol("walk", "Walk 10 minutes", autoCompletable = true) { it.currentScreen = "walk_timer" },
    DailyProtocol("sleep", "Log last night's sleep", autoCompletable = true) { it.currentScreen = "sleep_overview" },
    DailyProtocol("movement", "5-minute stretch or light movement", autoCompletable = false) {},
)

@Composable
fun ActiveJourneyScreen(state: NirogState) {
    var loggedReadingToday by remember { mutableStateOf(false) }
    var walkLoggedToday by remember { mutableStateOf(false) }
    var sleepLoggedToday by remember { mutableStateOf(false) }
    // Persisted like the "daily_post_dinner_walk" checklist item (a
    // checklistLogs doc keyed by day) instead of state.completedProtocols,
    // which lived only in memory and silently reset on every app restart
    // even though the row visually showed as checked off.
    var movementDoneToday by remember { mutableStateOf(false) }
    val movementDayKey = remember { com.nirogbhumi.app.ui.localDayKey(System.currentTimeMillis()) }
    val movementDocId = "daily_movement_stretch_$movementDayKey"

    DisposableEffect(state.repository.userId) {
        val todayKey = com.nirogbhumi.app.ui.localDayKey(System.currentTimeMillis())
        fun loggedToday(doc: com.nirogbhumi.app.data.CloudDocument): Boolean {
            val ts = (doc.values["measuredAt"] as? Timestamp) ?: (doc.values["createdAt"] as? Timestamp)
            return ts != null && com.nirogbhumi.app.ui.localDayKey(ts.toDate().time) == todayKey
        }
        val sugarSub = state.repository.listenUserCollection("glucoseReadings", 7, orderByField = "measuredAt", descending = true) { result ->
            if (result is CloudResult.Success) loggedReadingToday = result.value.any(::loggedToday)
        }
        val walkSub = state.repository.listenUserCollection("walkLogs", 5, orderByField = "createdAt", descending = true) { result ->
            if (result is CloudResult.Success) walkLoggedToday = result.value.any(::loggedToday)
        }
        val sleepSub = state.repository.listenUserCollection("sleepLogs", 5, orderByField = "createdAt", descending = true) { result ->
            if (result is CloudResult.Success) sleepLoggedToday = result.value.any(::loggedToday)
        }
        val checklistSub = state.repository.listenUserCollection("checklistLogs", 10, orderByField = "createdAt", descending = true) { result ->
            if (result is CloudResult.Success) {
                movementDoneToday = result.value.any { doc ->
                    doc.values["taskId"] == "daily_movement_stretch" && doc.values["status"] == "done" && loggedToday(doc)
                }
            }
        }
        onDispose { sugarSub.cancel(); walkSub.cancel(); sleepSub.cancel(); checklistSub.cancel() }
    }

    fun isDone(protocol: DailyProtocol): Boolean = when (protocol.id) {
        "fasting_reading" -> loggedReadingToday
        "checkin" -> state.checkedInToday
        "walk" -> walkLoggedToday
        "sleep" -> sleepLoggedToday
        else -> movementDoneToday
    }
    val completedCount = DAILY_PROTOCOLS.count { isDone(it) }

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
                            "${completedCount * 20}%",
                            fontSize = 32.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B3221)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "$completedCount of ${DAILY_PROTOCOLS.size} checked off today.",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF426820),
                        fontSize = 14.sp
                    )
                }
            }

            Text("Daily Protocols", fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))

            // Protocol checked card checklist - auto-completable items reflect
            // real logged data (tapping while incomplete deep-links to the
            // right logging screen instead of just self-reporting); only the
            // one non-loggable item is a manual toggle.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DAILY_PROTOCOLS.forEach { protocol ->
                    val checked = isDone(protocol)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (protocol.autoCompletable) {
                                    if (!checked) protocol.onOpen(state)
                                } else {
                                    val nextStatus = if (checked) "pending" else "done"
                                    movementDoneToday = !checked
                                    state.repository.upsertUserRecord("checklistLogs", movementDocId, mapOf(
                                        "taskId" to "daily_movement_stretch",
                                        "title" to protocol.title,
                                        "status" to nextStatus,
                                        "completedAt" to if (nextStatus == "done") com.google.firebase.firestore.FieldValue.serverTimestamp() else null,
                                    )) { result -> if (result is CloudResult.Failure) state.cloudMessage = result.message }
                                }
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
                            Text(protocol.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFF141E15))

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
    var sleepLogs by remember { mutableStateOf<List<com.nirogbhumi.app.data.CloudDocument>>(emptyList()) }
    var glucoseReadings by remember { mutableStateOf<List<com.nirogbhumi.app.data.CloudDocument>>(emptyList()) }
    DisposableEffect(state.repository.userId) {
        val sleepSub = state.repository.listenUserCollection("sleepLogs", limit = 60, orderByField = "createdAt", descending = true) { result ->
            if (result is CloudResult.Success) sleepLogs = result.value
        }
        val glucoseSub = state.repository.listenUserCollection("glucoseReadings", limit = 60, orderByField = "measuredAt", descending = true) { result ->
            if (result is CloudResult.Success) glucoseReadings = result.value
        }
        onDispose { sleepSub.cancel(); glucoseSub.cancel() }
    }
    val insight = remember(sleepLogs, glucoseReadings) { computeSleepGlucoseInsight(sleepLogs, glucoseReadings) }

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

            if (insight != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().border(width = 0.5.dp, color = Color(0xFF9CB79F).copy(alpha = 0.3f), shape = RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF4E9D3)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("YOUR OWN DATA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB9832B))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Fasting sugar has averaged %.0f mg/dL after shorter nights (under 6h, %d logged) vs %.0f mg/dL after longer ones (%d logged).".format(
                                insight.shortSleepAvg, insight.shortNights, insight.longSleepAvg, insight.longNights
                            ),
                            fontSize = 14.sp, color = Color(0xFF4B3B1B), lineHeight = 20.sp,
                        )
                    }
                }
            } else if (state.sugarLogs.size < 5) {
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

            // Data coverage: a real, live reason to come back - shows how
            // consistently the last 7 days have actually been logged, using
            // the same sleepLogs/glucoseReadings already fetched above.
            Text("This week's logging", fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
            val nowMillis = remember { System.currentTimeMillis() }
            val weekAgoDayKey = remember(nowMillis) { com.nirogbhumi.app.ui.localDayKey(nowMillis) - 6 }
            val sleepDaysThisWeek = remember(sleepLogs, nowMillis) {
                sleepLogs.mapNotNull { log ->
                    val ts = (log.values["measuredAt"] as? Timestamp) ?: (log.values["createdAt"] as? Timestamp)
                    ts?.toDate()?.time?.let { com.nirogbhumi.app.ui.localDayKey(it) }
                }.filter { it >= weekAgoDayKey }.toSet().size
            }
            val readingDaysThisWeek = remember(glucoseReadings, nowMillis) {
                glucoseReadings.mapNotNull { doc ->
                    val ts = (doc.values["measuredAt"] as? Timestamp) ?: (doc.values["createdAt"] as? Timestamp)
                    ts?.toDate()?.time?.let { com.nirogbhumi.app.ui.localDayKey(it) }
                }.filter { it >= weekAgoDayKey }.toSet().size
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    modifier = Modifier.weight(1f).border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.3f), shape = RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("$readingDaysThisWeek/7", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                        Text("days with a sugar reading", fontSize = 11.sp, color = Color(0xFF737972))
                    }
                }
                Card(
                    modifier = Modifier.weight(1f).border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.3f), shape = RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("$sleepDaysThisWeek/7", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                        Text("nights of sleep logged", fontSize = 11.sp, color = Color(0xFF737972))
                    }
                }
            }

            Divider(color = Color(0xFFC3C8C0).copy(alpha = 0.3f))

            // Experiment CTA - real progress: nights of 7+ hours actually
            // logged since starting, not a manually-incremented counter.
            Text("Resolve the correlation pattern", fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))

            val nightsSinceStart = remember(sleepLogs, state.experimentStartedAtMillis) {
                if (state.experimentStartedAtMillis <= 0) 0 else sleepLogs.count { log ->
                    val ts = (log.values["measuredAt"] as? Timestamp) ?: (log.values["createdAt"] as? Timestamp)
                    val hours = (log.values["duration"] as? Number)?.toDouble() ?: 0.0
                    ts != null && ts.toDate().time >= state.experimentStartedAtMillis && hours >= 7.0
                }
            }
            val experimentDaysElapsed = remember(state.experimentStartedAtMillis) {
                if (state.experimentStartedAtMillis <= 0) 0 else
                    (((System.currentTimeMillis() - state.experimentStartedAtMillis) / (1000L * 60 * 60 * 24)) + 1).toInt().coerceAtMost(7)
            }
            LaunchedEffect(experimentDaysElapsed) {
                if (state.isExperimentActive && experimentDaysElapsed >= 7) state.isExperimentActive = false
            }

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
                        text = if (state.isExperimentActive) "Experiment Active: Day $experimentDaysElapsed of 7" else "7-Day Rest Reset Experiment",
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B3221)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (state.isExperimentActive)
                            "$nightsSinceStart of $experimentDaysElapsed nights so far had 7+ hours logged. Keep logging sleep and sugar readings to see if it lines up with lower fasting numbers."
                        else
                            "We challenge you to log at least seven hours of restful sleep daily for the next week - tracked from your real sleep logs, not a manual checkbox.",
                        fontSize = 13.sp,
                        color = Color(0xFF434842),
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            state.isExperimentActive = !state.isExperimentActive
                            if (state.isExperimentActive) {
                                state.experimentStartedAtMillis = System.currentTimeMillis()
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
    var uploadingPhoto by remember { mutableStateOf(false) }
    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploadingPhoto = true
        state.repository.uploadPrivateFile("profile-photo", uri) { uploadResult ->
            when (uploadResult) {
                is com.nirogbhumi.app.data.CloudResult.Success -> {
                    val url = uploadResult.value
                    state.repository.saveProfile(mapOf("photoUrl" to url)) { saveResult ->
                        uploadingPhoto = false
                        when (saveResult) {
                            is com.nirogbhumi.app.data.CloudResult.Success -> state.photoUrl = url
                            is com.nirogbhumi.app.data.CloudResult.Failure -> state.cloudMessage = saveResult.message
                        }
                    }
                }
                is com.nirogbhumi.app.data.CloudResult.Failure -> {
                    uploadingPhoto = false
                    state.cloudMessage = uploadResult.message
                }
            }
        }
    }

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
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF314936))
                    .clickable(enabled = !uploadingPhoto) { photoPickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (state.photoUrl.isNotBlank()) {
                    coil.compose.AsyncImage(
                        model = state.photoUrl,
                        contentDescription = "Your profile photo",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                } else {
                    Text(
                        state.profileName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp
                    )
                }
                if (uploadingPhoto) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFC9A24B))
                            .border(1.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = "Change profile photo", tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
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

        SettingsSection(title = "Developer") {
            SettingsRow(Icons.Filled.Build, "Developer settings", showDivider = false) {
                state.currentScreen = "developer_settings"
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

/**
 * Developer Settings: version/build/channel visibility + a manual "check
 * for updates" trigger, so testers can verify a release landed without
 * waiting for the 30-minute foreground loop or the 6-hourly WorkManager
 * backstop.
 */
@Composable
fun DeveloperSettingsScreen(state: NirogState) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showReleaseNotes by remember { mutableStateOf(false) }
    var currentChannel by remember { mutableStateOf(com.nirogbhumi.app.update.UpdatePrefs.channel(context)) }
    var lastCheckMillis by remember { mutableStateOf(com.nirogbhumi.app.update.UpdatePrefs.lastCheckAtMillis(context)) }
    val currentVersionCode = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) info.longVersionCode.toInt()
            else @Suppress("DEPRECATION") info.versionCode
        }.getOrDefault(0)
    }

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
            IconButton(onClick = { state.currentScreen = "profile" }) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = Color(0xFF1B3221))
            }
            Text("Developer settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
        }

        Spacer(modifier = Modifier.height(8.dp))

        SettingsSection(title = "App Info") {
            DeveloperInfoRow("Version", "${com.nirogbhumi.app.BuildConfig.VERSION_NAME} (build $currentVersionCode)")
            DeveloperInfoRow("Package", context.packageName)
            DeveloperInfoRow("Git commit", com.nirogbhumi.app.BuildConfig.GIT_COMMIT, showDivider = false)
        }

        SettingsSection(title = "Update Channel") {
            com.nirogbhumi.app.update.UpdateChannelOption.entries.forEachIndexed { index, option ->
                DeveloperChannelRow(
                    label = option.label,
                    selected = option == currentChannel,
                    showDivider = index != com.nirogbhumi.app.update.UpdateChannelOption.entries.lastIndex,
                ) {
                    currentChannel = option
                    com.nirogbhumi.app.update.UpdatePrefs.setChannel(context, option)
                }
            }
        }

        SettingsSection(title = "Updates") {
            DeveloperInfoRow(
                "Last checked",
                if (lastCheckMillis > 0) android.text.format.DateUtils.getRelativeTimeSpanString(lastCheckMillis).toString() else "Never",
            )
            SettingsRow(
                Icons.Filled.Refresh,
                if (state.updateCheckBusy) "Checking…" else "Check for updates",
                showDivider = state.availableUpdate != null || state.updateCheckError.isNotBlank(),
            ) {
                if (state.updateCheckBusy) return@SettingsRow
                coroutineScope.launch {
                    state.updateCheckBusy = true
                    val result = com.nirogbhumi.app.update.UpdateManager.checkNow(context, currentVersionCode)
                    state.updateCheckBusy = false
                    lastCheckMillis = com.nirogbhumi.app.update.UpdatePrefs.lastCheckAtMillis(context)
                    result.onSuccess { info ->
                        state.updateCheckError = ""
                        if (info != null) state.availableUpdate = info
                    }.onFailure {
                        state.updateCheckError = it.message ?: "Couldn't check for updates"
                    }
                }
            }
            if (state.updateCheckError.isNotBlank()) {
                Text(
                    state.updateCheckError,
                    fontSize = 12.sp,
                    color = Color(0xFF8B2E2E),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else if (state.availableUpdate != null) {
                SettingsRow(Icons.Filled.Description, "View release notes", showDivider = false) {
                    showReleaseNotes = true
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showReleaseNotes) {
        val info = state.availableUpdate
        AlertDialog(
            onDismissRequest = { showReleaseNotes = false },
            title = { Text("What's new in ${info?.latestVersionName ?: ""}", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221)) },
            text = {
                Text(
                    info?.releaseNotes?.takeIf { it.isNotBlank() } ?: "No release notes were provided for this version.",
                    fontSize = 14.sp,
                    color = Color(0xFF434842),
                )
            },
            confirmButton = {
                Button(onClick = { showReleaseNotes = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936))) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }
}

@Composable
private fun DeveloperInfoRow(label: String, value: String, showDivider: Boolean = true) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 14.sp, color = Color(0xFF737972))
            Text(value, fontSize = 13.sp, color = Color(0xFF1B3221), fontWeight = FontWeight.SemiBold)
        }
        if (showDivider) {
            Divider(color = Color(0xFFF0ECE2), thickness = 1.dp, modifier = Modifier.padding(start = 16.dp))
        }
    }
}

@Composable
private fun DeveloperChannelRow(label: String, selected: Boolean, showDivider: Boolean, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 14.sp, color = Color(0xFF1B3221))
            if (selected) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = Color(0xFF314936), modifier = Modifier.size(20.dp))
            }
        }
        if (showDivider) {
            Divider(color = Color(0xFFF0ECE2), thickness = 1.dp, modifier = Modifier.padding(start = 16.dp))
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
fun DetailScreenHeader(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
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
fun EmptyStateCard(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
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
    var showAdd by remember { mutableStateOf(false) }
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
                            state.currentScreen = "family_member_detail"
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
            onClick = { showAdd = true },
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

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var relationship by remember { mutableStateOf("") }
        var age by remember { mutableStateOf("") }
        var city by remember { mutableStateOf("") }
        var diabetesStatus by remember { mutableStateOf("Not sure") }
        var consented by remember { mutableStateOf(false) }
        var saving by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!saving) showAdd = false },
            title = { Text("Add family member", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(relationship, { relationship = it }, label = { Text("Relationship") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(age, { age = it.filter(Char::isDigit) }, label = { Text("Age") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        OutlinedTextField(city, { city = it }, label = { Text("City") }, modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("No diabetes", "Prediabetes", "Type 2 diabetes", "Type 1 diabetes", "Not sure").forEach { t ->
                            Surface(shape = RoundedCornerShape(12.dp), color = if (diabetesStatus == t) Color(0xFF314936) else Color(0xFFEBF7E8), modifier = Modifier.clickable { diabetesStatus = t }) {
                                Text(t, color = if (diabetesStatus == t) Color.White else Color(0xFF1B3221), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { consented = !consented }) {
                        Checkbox(checked = consented, onCheckedChange = { consented = it }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF314936)))
                        Text("I have permission to manage this profile", fontSize = 12.5.sp, color = Color(0xFF434842))
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !saving && name.isNotBlank() && consented,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936)),
                    onClick = {
                        saving = true
                        state.repository.addHealthLog("profiles", mapOf(
                            "name" to name.trim(),
                            "relationship" to relationship.trim().ifBlank { null },
                            "age" to age.toIntOrNull(),
                            "city" to city.trim().ifBlank { null },
                            "selection" to diabetesStatus
                        )) { result ->
                            saving = false
                            if (result is com.nirogbhumi.app.data.CloudResult.Success) showAdd = false
                            else state.cloudMessage = (result as com.nirogbhumi.app.data.CloudResult.Failure).message
                        }
                    }
                ) { Text(if (saving) "Saving..." else "Save", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel", color = Color(0xFF737972)) } }
        )
    }
}

// A focused detail view for one family member's profile - not a full app-clone
// dashboard (that would need a parallel data model per profile, out of scope
// here), just their basics with an edit-free summary and a way to remove them.
@Composable
fun FamilyMemberDetailScreen(state: NirogState) {
    val values = state.selectedDocumentValues
    val name = values["name"]?.toString() ?: values["fullName"]?.toString() ?: "Family member"
    val relationship = values["relationship"]?.toString()?.ifBlank { null }
    val age = values["age"]?.toString()?.ifBlank { null }
    val city = values["city"]?.toString()?.ifBlank { null }
    val status = values["selection"]?.toString()?.ifBlank { null }
    var confirmingRemove by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F6EF))) {
        DetailScreenHeader(name, onBack = { state.currentScreen = "family_profiles" })
        Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(0.5.dp, Color(0xFFD8D0C0))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF9CB79F)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(name, fontFamily = FontFamily.Serif, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                    Text(relationship ?: "Family member", fontSize = 13.sp, color = Color(0xFF697169))
                    Spacer(modifier = Modifier.height(16.dp))
                    listOfNotNull(
                        age?.let { "Age" to it },
                        city?.let { "City" to it },
                        status?.let { "Diabetes status" to it.replace('_', ' ') }
                    ).forEach { (label, value) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, fontSize = 13.sp, color = Color(0xFF697169))
                            Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1B2219))
                        }
                    }
                }
            }
            Text(
                "This person's own health logs stay separate from yours. Full tracking under their own profile is coming soon.",
                fontSize = 12.5.sp, color = Color(0xFF8B9285), lineHeight = 18.sp
            )
            OutlinedButton(
                onClick = { confirmingRemove = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB4472F))
            ) { Text("Remove from family profiles") }
        }
    }

    if (confirmingRemove) {
        AlertDialog(
            onDismissRequest = { confirmingRemove = false },
            title = { Text("Remove $name?", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221)) },
            text = { Text("This removes them from your family profiles. This can't be undone.", fontSize = 13.sp, color = Color(0xFF434842)) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB4472F)),
                    onClick = {
                        state.repository.deleteUserRecord("profiles", state.selectedDocumentId) { result ->
                            confirmingRemove = false
                            if (result is com.nirogbhumi.app.data.CloudResult.Success) state.currentScreen = "family_profiles"
                            else state.cloudMessage = (result as com.nirogbhumi.app.data.CloudResult.Failure).message
                        }
                    }
                ) { Text("Remove", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { confirmingRemove = false }) { Text("Cancel", color = Color(0xFF737972)) } }
        )
    }
}

// Orders - custom list replacing the generic catalog rendering, with real status
// badges instead of the one-size-fits-all form/list layout.
@Composable
fun OrdersScreen(state: NirogState) {
    var records by remember { mutableStateOf<List<com.nirogbhumi.app.data.CloudDocument>?>(null) }
    DisposableEffect(Unit) {
        val subscription = state.repository.listenUserCollection("orders", 30, orderByField = "createdAt", descending = true) { result ->
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
        val subscription = state.repository.listenUserCollection("notifications", 30, orderByField = "createdAt", descending = true) { result ->
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
                    // Cloud Functions always stamp "type" (critical_alert, announcement,
                    // coach_message, report, weekly_digest, reminder) - this used to read
                    // a "category" field nothing ever wrote, so every notification fell
                    // through to the generic bell icon regardless of its real kind.
                    val type = record.values["type"]?.toString() ?: "update"
                    val title = record.values["title"]?.toString() ?: "Nirog Bhumi"
                    val body = record.values["body"]?.toString().orEmpty()
                    val route = record.values["route"]?.toString() ?: "dashboard"
                    val timestamp = (record.values["createdAt"] as? com.google.firebase.Timestamp)?.toDate()
                    val relative = timestamp?.let { relativeTimeLabel(it) } ?: ""
                    val icon = when (type) {
                        "reminder" -> Icons.Filled.NotificationsActive
                        "critical_alert" -> Icons.Filled.NotificationsActive
                        "order" -> Icons.Filled.ShoppingBag
                        "consultation" -> Icons.Filled.MedicalServices
                        "announcement" -> Icons.Filled.Campaign
                        "program" -> Icons.Filled.Checklist
                        "report", "weekly_digest" -> Icons.Filled.Insights
                        "coach_message", "expert_message" -> Icons.Filled.Person
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

fun relativeTimeLabel(date: java.util.Date): String {
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

// Data export & account deletion - both real, backed by the same Cloud
// Functions/Firestore request-queue path (requestDataExport/
// requestAccountDeletion -> dataExportRequests/deletionRequests, processed by
// existing scheduled Functions) rather than a generic form that goes nowhere.
@Composable
fun DataControlsScreen(state: NirogState) {
    var exporting by remember { mutableStateOf(false) }
    var exportRequested by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deletionRequested by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F6EF))) {
        DetailScreenHeader("Export or delete my data", onBack = { state.currentScreen = "profile" })
        Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(0.5.dp, Color(0xFFD8D0C0))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Icon(Icons.Filled.DownloadForOffline, contentDescription = null, tint = Color(0xFF1B3221))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Export your data", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1B3221))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "A copy of everything you've logged - readings, reports, program activity - as a file you can keep or share with a doctor.",
                        fontSize = 13.sp, color = Color(0xFF697169), lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    if (exportRequested) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF3F7D58), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Requested - you'll get a notification when it's ready.", fontSize = 13.sp, color = Color(0xFF3F7D58), fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Button(
                            enabled = !exporting,
                            onClick = {
                                exporting = true
                                state.repository.requestDataExport { result ->
                                    exporting = false
                                    if (result is com.nirogbhumi.app.data.CloudResult.Success) exportRequested = true
                                    else state.cloudMessage = (result as com.nirogbhumi.app.data.CloudResult.Failure).message
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936)),
                            shape = RoundedCornerShape(20.dp)
                        ) { Text(if (exporting) "Requesting..." else "Request my data", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5DFD6)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = Color(0xFFB4472F))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Delete my account", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF7B332E))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Permanently removes your logs, reports, and program activity after identity verification. This can't be undone.",
                        fontSize = 13.sp, color = Color(0xFF7B332E), lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    if (deletionRequested) {
                        Text("Deletion requested - pending approval and identity verification.", fontSize = 13.sp, color = Color(0xFF7B332E), fontWeight = FontWeight.SemiBold)
                    } else {
                        OutlinedButton(
                            enabled = !deleting,
                            onClick = { confirmingDelete = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB4472F))
                        ) { Text("Request account deletion") }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete your account?", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221)) },
            text = { Text("All your health logs and reports will be permanently deleted after verification. This can't be undone.", fontSize = 13.sp, color = Color(0xFF434842)) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB4472F)),
                    onClick = {
                        deleting = true
                        state.repository.requestAccountDeletion { result ->
                            deleting = false
                            confirmingDelete = false
                            if (result is com.nirogbhumi.app.data.CloudResult.Success) deletionRequested = true
                            else state.cloudMessage = (result as com.nirogbhumi.app.data.CloudResult.Failure).message
                        }
                    }
                ) { Text("Delete my account", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel", color = Color(0xFF737972)) } }
        )
    }
}

// Read-only summary of onboarding consent - matches Legal Center's promise
// that optional consent can be reviewed/withdrawn here. Required consent
// (health data storage, medical disclaimer) can't be withdrawn without
// deleting the account, since the app can't function without it.
@Composable
fun PrivacyConsentScreen(state: NirogState) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F6EF))) {
        DetailScreenHeader("Privacy & consent", onBack = { state.currentScreen = "profile" })
        Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("What you've agreed to, and what's optional.", fontSize = 13.sp, color = Color(0xFF697169))
            ConsentRow("Health data storage", "Required to track your readings and reports.", state.consentHealthData, required = true)
            ConsentRow("Expert review", "Lets an assigned expert see your logs when you book care or join a program.", state.consentExpertReview, required = false)
            ConsentRow("Medical disclaimer acknowledgement", "You understand this app doesn't replace medical advice.", state.consentMedicalDisclaimer, required = true)
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = { state.legalReturnRoute = "privacy_consent"; state.currentScreen = "legal_center" }) {
                Text("Read the full privacy policy", color = Color(0xFF314936), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ConsentRow(title: String, description: String, granted: Boolean, required: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.5.dp, Color(0xFFD8D0C0))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (granted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (granted) Color(0xFF3F7D58) else Color(0xFF9CB79F),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color(0xFF1B2219))
                    if (required) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(color = Color(0xFFEBF7E8), shape = RoundedCornerShape(8.dp)) {
                            Text("REQUIRED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF426820), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Text(description, fontSize = 12.sp, color = Color(0xFF8B9285), lineHeight = 16.sp)
            }
        }
    }
}

// Contact support - writes a real supportRequests doc (rules already permit
// self-scoped create/read) instead of a generic form with no clear outcome.
@Composable
fun SupportScreen(state: NirogState) {
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F6EF))) {
        DetailScreenHeader("Help & support", onBack = { state.currentScreen = "profile" })
        Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (sent) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEBF7E8)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF3F7D58))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Message sent", fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("We'll get back to you as soon as we can.", fontSize = 13.sp, color = Color(0xFF4B6450))
                    }
                }
            } else {
                Text("What can we help with?", fontSize = 13.sp, color = Color(0xFF697169))
                OutlinedTextField(subject, { subject = it }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(message, { message = it }, label = { Text("Message") }, minLines = 5, modifier = Modifier.fillMaxWidth())
                Button(
                    enabled = !sending && subject.isNotBlank() && message.isNotBlank(),
                    onClick = {
                        sending = true
                        state.repository.addHealthLog("supportRequests", mapOf(
                            "subject" to subject.trim(),
                            "message" to message.trim(),
                            "status" to "open"
                        )) { result ->
                            sending = false
                            if (result is com.nirogbhumi.app.data.CloudResult.Success) sent = true
                            else state.cloudMessage = (result as com.nirogbhumi.app.data.CloudResult.Failure).message
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936)),
                    shape = RoundedCornerShape(20.dp)
                ) { Text(if (sending) "Sending..." else "Send message", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
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
                    // setEnabled() already schedules a safe elapsed-interval
                    // fallback; upgrade it to the learned time once the hint
                    // loads (fire-and-forget - the fallback already covers
                    // the case where this fails or the member has no hint yet).
                    if (checked && type == com.nirogbhumi.app.notifications.ReminderType.DAILY_CHECKIN) {
                        state.repository.peekCheckinHourHint { result ->
                            val hint = (result as? CloudResult.Success)?.value
                            com.nirogbhumi.app.notifications.ReminderScheduler.scheduleSmart(context, hint)
                        }
                    }
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

// Care+ program calendar: a real month-grid, since "is there a session on the
// 14th" is a calendar-shaped question, not a linear-scroll one. Days with a
// scheduled programEvent get a dot marker; tapping any day shows that day's
// schedule below the grid, defaulting to today's on open.
@Composable
fun ProgramCalendarScreen(state: NirogState) {
    val context = LocalContext.current
    val totalDays = state.programDurationDays.toInt().coerceAtLeast(1)
    val startMillis = state.programStartedAtMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
    val dayNumber = (((System.currentTimeMillis() - startMillis) / (1000L * 60 * 60 * 24)) + 1).toInt().coerceIn(1, totalDays)

    var events by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    DisposableEffect(state.activeProgramId) {
        if (state.activeProgramId.isBlank()) return@DisposableEffect onDispose {}
        val sub = state.repository.listenProgramEvents(state.activeProgramId) { result ->
            if (result is CloudResult.Success) events = result.value.map { it.values }
        }
        onDispose { sub.cancel() }
    }

    val zone = remember { java.time.ZoneId.systemDefault() }
    val today = remember { java.time.LocalDate.now(zone) }
    var visibleMonth by remember { mutableStateOf(java.time.YearMonth.from(today)) }
    var selectedDate by remember { mutableStateOf(today) }

    val eventsByDay = remember(events) {
        events.mapNotNull { event ->
            val date = (event["startsAt"] as? Timestamp)?.toDate()?.toInstant()?.atZone(zone)?.toLocalDate()
            if (date != null) date to event else null
        }.groupBy({ it.first }, { it.second })
    }

    Column(modifier = Modifier.fillMaxSize().background(NirogColor.surface)) {
        DetailScreenHeader(state.activeProgramName.ifBlank { "Program Calendar" }, onBack = { state.currentScreen = "dashboard" })

        Text(
            "Day $dayNumber of $totalDays", style = NirogType.caption, color = NirogColor.inkMuted,
            modifier = Modifier.padding(horizontal = NirogSpace.xl)
        )
        Spacer(Modifier.height(NirogSpace.sm))

        MonthGridCalendar(
            visibleMonth = visibleMonth,
            today = today,
            selectedDate = selectedDate,
            eventsByDay = eventsByDay,
            onMonthChange = { visibleMonth = it },
            onDaySelected = { selectedDate = it },
        )

        Spacer(Modifier.height(NirogSpace.lg))
        Divider(color = NirogColor.surfaceSunken, thickness = 1.dp)
        Spacer(Modifier.height(NirogSpace.md))

        DaySchedulePanel(
            date = selectedDate,
            isToday = selectedDate == today,
            dayEvents = eventsByDay[selectedDate].orEmpty().sortedBy { (it["startsAt"] as? Timestamp)?.toDate()?.time ?: 0L },
            onRemind = { title, startsAt ->
                EventReminderWorker.schedule(context, "${title}_${startsAt.time}", title, "Starting now - $title", startsAt.time)
            },
        )
    }
}

@Composable
private fun MonthGridCalendar(
    visibleMonth: java.time.YearMonth,
    today: java.time.LocalDate,
    selectedDate: java.time.LocalDate,
    eventsByDay: Map<java.time.LocalDate, List<Map<String, Any?>>>,
    onMonthChange: (java.time.YearMonth) -> Unit,
    onDaySelected: (java.time.LocalDate) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = NirogSpace.lg)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onMonthChange(visibleMonth.minusMonths(1)) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month", tint = NirogColor.inkPrimary)
            }
            Text(
                "${visibleMonth.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())} ${visibleMonth.year}",
                style = NirogType.sectionHeading, color = NirogColor.inkPrimary,
            )
            IconButton(onClick = { onMonthChange(visibleMonth.plusMonths(1)) }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next month", tint = NirogColor.inkPrimary)
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = NirogSpace.sm)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(label, style = NirogType.overline, color = NirogColor.inkMuted, maxLines = 1)
                }
            }
        }

        val firstOfMonth = visibleMonth.atDay(1)
        val leadingBlanks = firstOfMonth.dayOfWeek.value - 1 // Monday=1..Sunday=7
        val daysInMonth = visibleMonth.lengthOfMonth()
        val totalCells = leadingBlanks + daysInMonth
        val rows = (totalCells + 6) / 7

        Column(modifier = Modifier.padding(top = NirogSpace.xs)) {
            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        val dayOfMonth = cellIndex - leadingBlanks + 1
                        Box(
                            modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (dayOfMonth in 1..daysInMonth) {
                                val date = visibleMonth.atDay(dayOfMonth)
                                val isToday = date == today
                                val isSelected = date == selectedDate
                                val hasEvents = eventsByDay.containsKey(date)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .then(
                                            when {
                                                isSelected -> Modifier.background(NirogColor.forest)
                                                isToday -> Modifier.border(1.5.dp, NirogColor.forest, CircleShape)
                                                else -> Modifier
                                            }
                                        )
                                        .clickable { onDaySelected(date) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            dayOfMonth.toString(),
                                            style = NirogType.body,
                                            color = if (isSelected) NirogColor.onAccent else NirogColor.inkPrimary,
                                            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                        )
                                        if (hasEvents) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 2.dp)
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) NirogColor.onAccent else NirogColor.gold)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.DaySchedulePanel(
    date: java.time.LocalDate,
    isToday: Boolean,
    dayEvents: List<Map<String, Any?>>,
    onRemind: (String, java.util.Date) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = NirogSpace.xl)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = NirogSpace.sm)) {
            Text(
                date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                style = NirogType.bodyStrong, color = NirogColor.inkPrimary, modifier = Modifier.weight(1f),
            )
            if (isToday) {
                Surface(color = NirogColor.forest, shape = NirogRadius.pillShape) {
                    Text("TODAY", style = NirogType.overline, color = NirogColor.onAccent, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }

        if (dayEvents.isEmpty()) {
            EmptyStateCard(Icons.Filled.EventAvailable, "No sessions scheduled for this day.")
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(NirogSpace.sm),
            ) {
                dayEvents.forEach { event ->
                    val title = event["title"] as? String ?: "Program event"
                    val type = event["type"] as? String
                    val description = event["description"] as? String
                    val startsAt = (event["startsAt"] as? Timestamp)?.toDate()
                    val location = event["location"] as? String
                    NirogCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(title, style = NirogType.bodyStrong, color = NirogColor.inkPrimary, modifier = Modifier.weight(1f))
                            if (type != null) {
                                Surface(color = NirogColor.goldSoft, shape = NirogRadius.pillShape) {
                                    Text(type.uppercase(), style = NirogType.overline, color = NirogColor.gold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }
                        }
                        if (startsAt != null) {
                            Text(
                                java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(startsAt),
                                style = NirogType.caption, color = NirogColor.inkMuted, modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        if (!location.isNullOrBlank()) {
                            Text(location, style = NirogType.caption, color = NirogColor.inkMuted)
                        }
                        if (!description.isNullOrBlank()) {
                            Text(description, style = NirogType.body, color = NirogColor.inkSecondary, modifier = Modifier.padding(top = 6.dp))
                        }
                        if (startsAt != null) {
                            TextButton(onClick = { onRemind(title, startsAt) }, modifier = Modifier.padding(top = 4.dp)) {
                                Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(14.dp), tint = NirogColor.forest)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Remind me", style = NirogType.secondary, color = NirogColor.forest)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(NirogSpace.xxl))
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

    DisposableEffect(state.activeProgramId) {
        if (state.activeProgramId.isBlank()) { records = emptyList(); return@DisposableEffect onDispose {} }
        val subscription = state.repository.listenAnnouncements(state.activeProgramId) { result ->
            records = when (result) {
                is com.nirogbhumi.app.data.CloudResult.Success -> result.value
                is com.nirogbhumi.app.data.CloudResult.Failure -> emptyList()
            }
        }
        // Best-effort: opening this screen is "read" for the unread badge on
        // Chat Hub, whether or not the member is enrolled (fails silently
        // for non-members - they have no roster doc to mark anyway).
        state.repository.markProgramRead(state.activeProgramId, "lastReadAnnouncementsAt") {}
        onDispose { subscription.cancel() }
    }

    Column(modifier = Modifier.fillMaxSize().background(NirogColor.surface)) {
        DetailScreenHeader(
            "Announcements",
            onBack = { state.currentScreen = "dashboard" },
            trailing = {
                if (state.canManageProgram(state.activeProgramId)) {
                    IconButton(onClick = { showComposer = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "New announcement", tint = NirogColor.forest)
                    }
                }
            }
        )
        Text(
            "Updates from your coach · every member is notified",
            style = NirogType.caption, color = NirogColor.inkMuted,
            modifier = Modifier.padding(horizontal = NirogSpace.lg)
        )
        Spacer(Modifier.height(NirogSpace.sm))
        Column(modifier = Modifier.fillMaxSize().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = NirogSpace.lg)) {
            when {
                records == null -> Row(modifier = Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = NirogColor.forestSoft)
                    Spacer(modifier = Modifier.width(NirogSpace.sm))
                    Text("Loading...", style = NirogType.secondary, color = NirogColor.inkSecondary)
                }
                records!!.isEmpty() -> EmptyStateCard(Icons.Filled.Campaign, "No announcements yet. Updates from the Nirog Bhumi team will show up here.")
                else -> Column(verticalArrangement = Arrangement.spacedBy(NirogSpace.md)) {
                    records!!.forEach { record ->
                        val timestamp = (record.values["createdAt"] as? com.google.firebase.Timestamp)?.toDate()
                        NirogCard {
                            Text(record.values["title"]?.toString() ?: "Announcement", style = NirogType.bodyStrong, color = NirogColor.inkPrimary)
                            Spacer(modifier = Modifier.height(NirogSpace.xs))
                            Text(record.values["body"]?.toString().orEmpty(), style = NirogType.body, color = NirogColor.inkSecondary)
                            if (timestamp != null) {
                                Spacer(modifier = Modifier.height(NirogSpace.sm))
                                Text(relativeTimeLabel(timestamp), style = NirogType.overline, color = NirogColor.inkMuted)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(NirogSpace.xxl))
        }
    }

    if (showComposer) {
        AlertDialog(
            onDismissRequest = { if (!posting) showComposer = false },
            title = { Text("New Announcement", style = NirogType.cardTitle, color = NirogColor.inkPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(NirogSpace.md)) {
                    Text("Every member of this program gets a push notification when you post.", style = NirogType.caption, color = NirogColor.inkMuted)
                    OutlinedTextField(value = composeTitle, onValueChange = { composeTitle = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = composeBody, onValueChange = { composeBody = it }, label = { Text("Message") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    enabled = !posting && composeTitle.isNotBlank() && composeBody.isNotBlank(),
                    onClick = {
                        posting = true
                        state.repository.postAnnouncement(state.activeProgramId, composeTitle.trim(), composeBody.trim()) { result ->
                            posting = false
                            if (result is com.nirogbhumi.app.data.CloudResult.Success) {
                                composeTitle = ""; composeBody = ""; showComposer = false
                            } else state.cloudMessage = (result as com.nirogbhumi.app.data.CloudResult.Failure).message
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NirogColor.forest)
                ) { Text(if (posting) "Posting..." else "Post", color = NirogColor.onAccent) }
            },
            dismissButton = { TextButton(onClick = { showComposer = false }, enabled = !posting) { Text("Cancel", color = NirogColor.inkSecondary) } }
        )
    }
}

private val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "🙏")

// Single-token @mentions ("@Priya", not "@Priya Sharma") - there's no roster
// autocomplete yet, so this is rendering-only highlighting of whatever the
// sender typed, not a validated reference to a real member.
private val MENTION_REGEX = Regex("(?<=^|\\s)@[\\p{L}0-9_]+")

private fun mentionAnnotatedText(text: String, mentionColor: Color): androidx.compose.ui.text.AnnotatedString =
    buildAnnotatedString {
        var last = 0
        for (match in MENTION_REGEX.findAll(text)) {
            append(text.substring(last, match.range.first))
            withStyle(SpanStyle(color = mentionColor, fontWeight = FontWeight.Bold)) {
                append(match.value)
            }
            last = match.range.last + 1
        }
        append(text.substring(last))
    }

// One MediaPlayer per visible bubble (released via DisposableEffect when the
// item scrolls out of the LazyColumn) - deliberately doesn't pause other
// bubbles' playback when one starts, unlike WhatsApp. Acceptable v1 scope
// cut: voice notes are short and this is a rare multi-tap scenario.
@Composable
private fun VoiceNoteBubble(url: String, durationSec: Int, isMine: Boolean) {
    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var elapsedSec by remember { mutableStateOf(0) }
    val player = remember { android.media.MediaPlayer() }

    DisposableEffect(url) {
        onDispose { runCatching { player.release() } }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            kotlinx.coroutines.delay(500)
            elapsedSec = runCatching { player.currentPosition / 1000 }.getOrDefault(elapsedSec)
        }
    }

    fun togglePlayback() {
        if (isPlaying) {
            runCatching { player.pause() }
            isPlaying = false
            return
        }
        if (isPrepared) {
            runCatching { player.start() }
            isPlaying = true
            return
        }
        runCatching {
            player.setDataSource(url)
            player.setOnPreparedListener {
                isPrepared = true
                it.start()
                isPlaying = true
            }
            player.setOnCompletionListener {
                isPlaying = false
                elapsedSec = 0
                runCatching { player.seekTo(0) }
            }
            player.prepareAsync()
        }
    }

    Row(
        modifier = Modifier
            .widthIn(min = 160.dp)
            .clip(NirogRadius.pillShape)
            .background(if (isMine) Color.White.copy(alpha = 0.12f) else NirogColor.surfaceSunken)
            .clickable { togglePlayback() }
            .semantics { contentDescription = if (isPlaying) "Pause voice note" else "Play voice note" }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (isPlaying) "⏸" else "▶", style = NirogType.cardTitle, color = if (isMine) NirogColor.onAccent else NirogColor.forest)
        Spacer(Modifier.width(8.dp))
        val shownSec = if (isPlaying || elapsedSec > 0) elapsedSec else durationSec
        Column(modifier = Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { if (durationSec > 0) (shownSec.toFloat() / durationSec).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(NirogRadius.pillShape),
                color = if (isMine) NirogColor.onAccent else NirogColor.forest,
                trackColor = if (isMine) Color.White.copy(alpha = 0.25f) else NirogColor.surface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "%d:%02d".format(shownSec / 60, shownSec % 60),
                style = NirogType.caption,
                color = if (isMine) NirogColor.onAccent.copy(alpha = 0.85f) else NirogColor.inkSecondary,
            )
        }
    }
}

@Composable
private fun TypingIndicatorRow(names: List<String>) {
    val label = when (names.size) {
        1 -> "${names[0]} is typing"
        2 -> "${names[0]} and ${names[1]} are typing"
        else -> "${names.size} people are typing"
    }
    Row(
        modifier = Modifier
            .padding(horizontal = NirogSpace.lg, vertical = NirogSpace.xs)
            .clip(NirogRadius.pillShape)
            .background(NirogColor.surfaceSunken)
            .padding(horizontal = NirogSpace.md, vertical = NirogSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val transition = rememberInfiniteTransition(label = "typing-dots")
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { index ->
                val bounce by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 600, delayMillis = index * 150),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "dot$index",
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .graphicsLayer { translationY = -bounce * 4f }
                        .clip(CircleShape)
                        .background(NirogColor.forestSoft)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = NirogType.caption, color = NirogColor.inkMuted)
    }
}

// The no-arg MediaRecorder() constructor is deprecated from API 31 onward in
// favor of the context-aware overload; isolated in its own function so the
// suppression is scoped to exactly the one legacy call path instead of an
// entire block.
@Suppress("DEPRECATION")
private fun newMediaRecorder(context: android.content.Context): android.media.MediaRecorder =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) android.media.MediaRecorder(context) else android.media.MediaRecorder()

// Care+ community chat - one shared room per program, not one global room, so
// conversation stays relevant to the program a member actually joined.
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ProgramChatScreen(state: NirogState) {
    var records by remember { mutableStateOf<List<com.nirogbhumi.app.data.CloudDocument>?>(null) }
    var messageInput by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<com.nirogbhumi.app.data.CloudDocument?>(null) }
    var replyTarget by remember { mutableStateOf<com.nirogbhumi.app.data.CloudDocument?>(null) }
    var reportTarget by remember { mutableStateOf<com.nirogbhumi.app.data.CloudDocument?>(null) }
    var reporting by remember { mutableStateOf(false) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var uploadingPhoto by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingElapsedSec by remember { mutableStateOf(0) }
    var uploadingAudio by remember { mutableStateOf(false) }
    var typingStatuses by remember { mutableStateOf<List<com.nirogbhumi.app.data.CloudDocument>>(emptyList()) }
    var typingTickMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var viewingPhotoUrl by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val myUid = state.repository.userId
    val context = LocalContext.current
    val recorderHolder = remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    val recordingFileHolder = remember { mutableStateOf<java.io.File?>(null) }
    // The modern system Photo Picker (androidx.activity 1.7+) - unlike
    // ACTION_GET_CONTENT it needs no storage permission at all on any OS
    // version and is Google's current recommendation, so there's no
    // permission-grant edge case that can silently swallow a pick.
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) pendingPhotoUri = uri
    }

    fun startRecording() {
        val file = java.io.File.createTempFile("voice_note_", ".m4a", context.cacheDir)
        val recorder = newMediaRecorder(context).apply {
            setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
        }
        // prepare()/start() throw if the mic is held by another app or the
        // device has no microphone - previously swallowed by runCatching
        // while isRecording was still set true unconditionally, leaving the
        // UI stuck showing "Recording..." for a voice note that never
        // actually started and could never be stopped successfully.
        val started = runCatching { recorder.prepare(); recorder.start() }.isSuccess
        if (!started) {
            runCatching { recorder.release() }
            file.delete()
            state.cloudMessage = "Couldn't access the microphone - it may be in use by another app"
            return
        }
        recorderHolder.value = recorder
        recordingFileHolder.value = file
        recordingElapsedSec = 0
        isRecording = true
    }

    fun stopRecordingAndSend() {
        isRecording = false
        val recorder = recorderHolder.value
        val file = recordingFileHolder.value
        recorderHolder.value = null
        recordingFileHolder.value = null
        val durationSec = recordingElapsedSec
        val stopped = runCatching { recorder?.apply { stop(); release() } }.isSuccess
        if (!stopped || file == null || durationSec < 1) {
            file?.delete()
            state.cloudMessage = if (durationSec < 1) "Voice note was too short to send" else "Could not record - please try again"
            return
        }
        uploadingAudio = true
        state.repository.uploadProgramChatAudio(state.activeProgramId, Uri.fromFile(file)) { result ->
            file.delete()
            when (result) {
                is com.nirogbhumi.app.data.CloudResult.Success -> {
                    state.repository.sendProgramChatMessage(
                        programId = state.activeProgramId,
                        text = "",
                        senderName = state.profileName.ifBlank { "Member" },
                        audioUrl = result.value,
                        audioDurationSec = durationSec,
                    ) { sendResult ->
                        uploadingAudio = false
                        if (sendResult is com.nirogbhumi.app.data.CloudResult.Success) {
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        } else state.cloudMessage = (sendResult as com.nirogbhumi.app.data.CloudResult.Failure).message
                    }
                }
                is com.nirogbhumi.app.data.CloudResult.Failure -> {
                    uploadingAudio = false
                    state.cloudMessage = result.message
                }
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else state.cloudMessage = "Microphone permission is needed to send a voice note"
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            kotlinx.coroutines.delay(1000)
            recordingElapsedSec += 1
        }
    }

    DisposableEffect(state.activeProgramId) {
        val subscription = state.repository.listenProgramChat(state.activeProgramId) { result ->
            records = when (result) {
                is com.nirogbhumi.app.data.CloudResult.Success -> result.value
                is com.nirogbhumi.app.data.CloudResult.Failure -> emptyList()
            }
        }
        state.repository.markProgramRead(state.activeProgramId, "lastReadGeneralAt") {}
        onDispose { subscription.cancel() }
    }

    // "X is typing..." - listen for the whole program, then filter to recent
    // (last 6s) and exclude the caller's own doc. Leaving without an explicit
    // stop-typing write (crash, force-close) self-heals via that staleness
    // window rather than needing a server-side cleanup job.
    DisposableEffect(state.activeProgramId) {
        val subscription = state.repository.listenTypingStatus(state.activeProgramId) { result ->
            if (result is com.nirogbhumi.app.data.CloudResult.Success) typingStatuses = result.value
        }
        onDispose {
            subscription.cancel()
            state.repository.setTypingStatus(state.activeProgramId, "", isTyping = false) {}
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            typingTickMillis = System.currentTimeMillis()
        }
    }
    LaunchedEffect(messageInput) {
        if (messageInput.isBlank()) {
            state.repository.setTypingStatus(state.activeProgramId, "", isTyping = false) {}
        } else {
            kotlinx.coroutines.delay(400)
            state.repository.setTypingStatus(state.activeProgramId, state.profileName.ifBlank { "Member" }, isTyping = true) {}
        }
    }
    val activeTypers = remember(typingStatuses, typingTickMillis, myUid) {
        typingStatuses.filter { doc ->
            doc.values["uid"] != myUid &&
                (doc.values["updatedAt"] as? com.google.firebase.Timestamp)?.let { typingTickMillis - it.toDate().time < 6000 } == true
        }.mapNotNull { it.values["name"]?.toString() }
    }

    Column(modifier = Modifier.fillMaxSize().background(NirogColor.surface)) {
        DetailScreenHeader("General", onBack = { state.currentScreen = "dashboard" })
        Text(
            "${state.activeProgramName.ifBlank { "Your program" }} · long-press a message for options",
            style = NirogType.caption, color = NirogColor.inkMuted,
            modifier = Modifier.padding(horizontal = NirogSpace.lg)
        )
        Spacer(modifier = Modifier.height(NirogSpace.sm))

        // records is already ordered newest-first by createdAt, so the first
        // pinned hit is the most recently *sent* pinned message - not
        // necessarily the most recently *pinned* one, but close enough for a
        // single-banner v1 without needing to parse pinnedAt timestamps.
        records?.firstOrNull { it.values["pinned"] == true }?.let { pinned ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NirogSpace.lg, vertical = NirogSpace.xs)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NirogColor.goldSoft.copy(alpha = 0.25f))
                    .padding(horizontal = NirogSpace.md, vertical = NirogSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("📌", style = NirogType.body)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        pinned.values["senderName"]?.toString() ?: "Member",
                        style = NirogType.overline, color = NirogColor.forest,
                    )
                    Text(
                        pinned.values["text"]?.toString().orEmpty(),
                        style = NirogType.caption, color = NirogColor.inkSecondary, maxLines = 2,
                    )
                }
                if (state.canManageProgram(state.activeProgramId)) {
                    IconButton(onClick = {
                        state.repository.togglePinMessage(pinned.id, state.activeProgramId, pinned = false) { result ->
                            if (result is com.nirogbhumi.app.data.CloudResult.Failure) state.cloudMessage = result.message
                        }
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Unpin message", tint = NirogColor.inkMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        when {
            records == null -> Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = NirogColor.forestSoft)
            }
            records!!.isEmpty() -> Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = NirogSpace.lg), contentAlignment = Alignment.Center) {
                EmptyStateCard(Icons.Filled.Forum, "No messages yet. Say hello to your program community!")
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = NirogSpace.lg),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(NirogSpace.sm)
            ) {
                items(records!!, key = { it.id }) { record ->
                    val isMine = record.values["userId"] == myUid
                    val senderName = record.values["senderName"]?.toString() ?: "Member"
                    val text = record.values["text"]?.toString().orEmpty()
                    val photoUrl = record.values["photoUrl"]?.toString()
                    val audioUrl = record.values["audioUrl"]?.toString()
                    val audioDurationSec = (record.values["audioDurationSec"] as? Number)?.toInt() ?: 0
                    val sentAt = (record.values["createdAt"] as? Timestamp)?.toDate()
                    @Suppress("UNCHECKED_CAST")
                    val replyTo = record.values["replyTo"] as? Map<String, Any?>
                    @Suppress("UNCHECKED_CAST")
                    val reactions = (record.values["reactions"] as? Map<String, Any?>).orEmpty()

                    Column(
                        modifier = Modifier.fillMaxWidth().animateItem(),
                        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            if (!isMine) {
                                Box(
                                    modifier = Modifier.size(28.dp).clip(CircleShape).background(NirogColor.forestSoft),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        senderName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                        style = NirogType.caption, color = NirogColor.forest, fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 260.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isMine) NirogColor.forest else NirogColor.surfaceCard)
                                    .then(if (isMine) Modifier else Modifier.border(0.5.dp, NirogColor.surfaceSunken, RoundedCornerShape(16.dp)))
                                    .combinedClickable(onClick = {}, onLongClick = { actionTarget = record })
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                if (!isMine) Text(senderName, style = NirogType.overline, color = NirogColor.forestSoft)
                                if (replyTo != null) {
                                    Column(
                                        modifier = Modifier
                                            .padding(top = 4.dp, bottom = 6.dp)
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isMine) Color.White.copy(alpha = 0.12f) else NirogColor.surfaceSunken
                                            )
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            replyTo["sender"]?.toString() ?: "Member",
                                            style = NirogType.overline,
                                            color = if (isMine) NirogColor.goldSoft else NirogColor.gold,
                                        )
                                        Text(
                                            replyTo["text"]?.toString().orEmpty(),
                                            style = NirogType.caption,
                                            color = if (isMine) NirogColor.onAccent.copy(alpha = 0.85f) else NirogColor.inkSecondary,
                                            maxLines = 2,
                                        )
                                    }
                                }
                                if (photoUrl != null) {
                                    coil.compose.AsyncImage(
                                        model = photoUrl, contentDescription = "Photo from $senderName - tap to view full screen",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 220.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { viewingPhotoUrl = photoUrl }
                                            .then(if (text.isNotBlank()) Modifier.padding(bottom = 6.dp) else Modifier),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    )
                                }
                                if (audioUrl != null) {
                                    VoiceNoteBubble(audioUrl, audioDurationSec, isMine)
                                }
                                if (text.isNotBlank()) {
                                    Text(
                                        mentionAnnotatedText(text, mentionColor = if (isMine) NirogColor.goldSoft else NirogColor.gold),
                                        style = NirogType.body.copy(color = if (isMine) NirogColor.onAccent else NirogColor.inkPrimary),
                                    )
                                }
                            }
                        }

                        if (sentAt != null) {
                            Text(
                                java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(sentAt),
                                style = NirogType.caption, color = NirogColor.inkMuted,
                                modifier = Modifier.padding(top = 2.dp, start = if (isMine) 0.dp else 34.dp, end = if (isMine) 4.dp else 0.dp),
                            )
                        }

                        if (reactions.isNotEmpty()) {
                            Row(
                                modifier = Modifier.padding(top = 4.dp, start = if (isMine) 0.dp else 34.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                reactions.forEach { (emoji, uidsAny) ->
                                    val uids = (uidsAny as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                                    if (uids.isEmpty()) return@forEach
                                    val mine = myUid != null && myUid in uids
                                    Row(
                                        modifier = Modifier
                                            .clip(NirogRadius.pillShape)
                                            .background(if (mine) NirogColor.goldSoft else NirogColor.surfaceSunken)
                                            .clickable {
                                                state.repository.toggleChatReaction(record.id, emoji, add = !mine) {}
                                            }
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(emoji, style = NirogType.caption)
                                        Spacer(Modifier.width(3.dp))
                                        Text("${uids.size}", style = NirogType.overline, color = NirogColor.inkSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (activeTypers.isNotEmpty()) {
            TypingIndicatorRow(activeTypers)
        }

        replyTarget?.let { target ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NirogSpace.lg, vertical = NirogSpace.xs)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NirogColor.surfaceSunken)
                    .padding(horizontal = NirogSpace.md, vertical = NirogSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Replying to ${target.values["senderName"]?.toString() ?: "Member"}", style = NirogType.overline, color = NirogColor.forest)
                    Text(target.values["text"]?.toString().orEmpty(), style = NirogType.caption, color = NirogColor.inkSecondary, maxLines = 1)
                }
                IconButton(onClick = { replyTarget = null }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel reply", tint = NirogColor.inkMuted, modifier = Modifier.size(18.dp))
                }
            }
        }

        pendingPhotoUri?.let { uri ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NirogSpace.lg, vertical = NirogSpace.xs)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NirogColor.surfaceSunken)
                    .padding(horizontal = NirogSpace.md, vertical = NirogSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                coil.compose.AsyncImage(
                    model = uri, contentDescription = "Photo to send",
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
                Spacer(Modifier.width(8.dp))
                Text("Photo ready to send", style = NirogType.caption, color = NirogColor.inkSecondary, modifier = Modifier.weight(1f))
                IconButton(onClick = { pendingPhotoUri = null }, enabled = !uploadingPhoto) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = NirogColor.inkMuted, modifier = Modifier.size(18.dp))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(NirogSpace.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                enabled = !uploadingPhoto && !sending && !isRecording && !uploadingAudio,
                onClick = { photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.semantics { contentDescription = "Attach a photo" },
            ) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = NirogColor.forest)
            }
            // Mic hides once there's something to send (text or a pending
            // photo) - WhatsApp-style mic-to-send handoff - rather than
            // showing mic and send simultaneously at all times.
            val showMic = messageInput.isBlank() && pendingPhotoUri == null
            if (showMic || isRecording) {
                val recordingPulse = rememberInfiniteTransition(label = "recording-pulse")
                val pulseScale by recordingPulse.animateFloat(
                    initialValue = 1f, targetValue = 1.18f,
                    animationSpec = infiniteRepeatable(animation = tween(600), repeatMode = RepeatMode.Reverse),
                    label = "recording-pulse-scale",
                )
                IconButton(
                    enabled = !uploadingPhoto && !sending && !uploadingAudio,
                    onClick = {
                        if (isRecording) {
                            stopRecordingAndSend()
                        } else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            startRecording()
                        } else {
                            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier
                        .then(if (isRecording) Modifier.graphicsLayer(scaleX = pulseScale, scaleY = pulseScale) else Modifier)
                        .semantics { contentDescription = if (isRecording) "Stop and send voice note" else "Record a voice note" },
                ) {
                    if (uploadingAudio) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = NirogColor.forestSoft)
                    } else {
                        Icon(
                            if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = null,
                            tint = if (isRecording) NirogColor.statusCritical else NirogColor.forest,
                        )
                    }
                }
            }
            if (isRecording) {
                Text(
                    "%d:%02d".format(recordingElapsedSec / 60, recordingElapsedSec % 60),
                    style = NirogType.caption, color = NirogColor.statusCritical,
                    modifier = Modifier.padding(end = NirogSpace.xs),
                )
            }
            OutlinedTextField(
                value = messageInput,
                onValueChange = { messageInput = it },
                enabled = !isRecording,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Message your program" },
                placeholder = { Text(if (isRecording) "Recording..." else "Message your program...") },
                shape = NirogRadius.pillShape,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NirogColor.forest, unfocusedBorderColor = NirogColor.surfaceSunken, focusedContainerColor = NirogColor.surfaceCard, unfocusedContainerColor = NirogColor.surfaceCard)
            )
            Spacer(modifier = Modifier.width(NirogSpace.sm))
            IconButton(
                enabled = !sending && !uploadingPhoto && !isRecording && !uploadingAudio && (messageInput.isNotBlank() || pendingPhotoUri != null),
                onClick = {
                    val text = messageInput.trim()
                    val photo = pendingPhotoUri
                    if ((text.isBlank() && photo == null) || sending || uploadingPhoto || isRecording || uploadingAudio) return@IconButton
                    val reply = replyTarget

                    fun send(photoUrl: String?) {
                        sending = true
                        state.repository.sendProgramChatMessage(
                            programId = state.activeProgramId,
                            text = text,
                            senderName = state.profileName.ifBlank { "Member" },
                            replyToId = reply?.id,
                            replyToSender = reply?.values?.get("senderName")?.toString(),
                            replyToText = reply?.values?.get("text")?.toString(),
                            photoUrl = photoUrl,
                        ) { result ->
                            sending = false
                            if (result is com.nirogbhumi.app.data.CloudResult.Success) {
                                messageInput = ""
                                replyTarget = null
                                pendingPhotoUri = null
                                coroutineScope.launch { listState.animateScrollToItem(0) }
                            } else state.cloudMessage = (result as com.nirogbhumi.app.data.CloudResult.Failure).message
                        }
                    }

                    if (photo != null) {
                        uploadingPhoto = true
                        state.repository.uploadProgramChatPhoto(state.activeProgramId, photo) { result ->
                            uploadingPhoto = false
                            when (result) {
                                is com.nirogbhumi.app.data.CloudResult.Success -> send(result.value)
                                is com.nirogbhumi.app.data.CloudResult.Failure -> state.cloudMessage = result.message
                            }
                        }
                    } else {
                        send(null)
                    }
                },
                modifier = Modifier.size(48.dp).background(
                    if (messageInput.isNotBlank() || pendingPhotoUri != null) NirogColor.forest else NirogColor.surfaceSunken,
                    CircleShape,
                )
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = NirogColor.onAccent, modifier = Modifier.size(20.dp))
            }
        }
    }

    actionTarget?.let { target ->
        val isMine = target.values["userId"] == myUid
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text("Message options", style = NirogType.cardTitle, color = NirogColor.inkPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(NirogSpace.md)) {
                    Text("React", style = NirogType.overline, color = NirogColor.inkMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(NirogSpace.md)) {
                        @Suppress("UNCHECKED_CAST")
                        val reactions = (target.values["reactions"] as? Map<String, Any?>).orEmpty()
                        QUICK_REACTIONS.forEach { emoji ->
                            val already = ((reactions[emoji] as? List<*>)?.contains(myUid)) == true
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (already) NirogColor.goldSoft else NirogColor.surfaceSunken)
                                    .clickable {
                                        state.repository.toggleChatReaction(target.id, emoji, add = !already) {}
                                        actionTarget = null
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Text(emoji, style = NirogType.cardTitle) }
                        }
                    }
                    RowCard(
                        title = "Reply",
                        leading = {
                            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                Text("↩", style = NirogType.cardTitle, color = NirogColor.forest)
                            }
                        },
                        onClick = { replyTarget = target; actionTarget = null },
                    )
                    if (state.canManageProgram(state.activeProgramId)) {
                        val isPinned = target.values["pinned"] == true
                        RowCard(
                            title = if (isPinned) "Unpin message" else "Pin message",
                            leading = {
                                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                    Text("📌", style = NirogType.cardTitle)
                                }
                            },
                            onClick = {
                                state.repository.togglePinMessage(target.id, state.activeProgramId, pinned = !isPinned) { result ->
                                    if (result is com.nirogbhumi.app.data.CloudResult.Failure) state.cloudMessage = result.message
                                }
                                actionTarget = null
                            },
                        )
                    }
                    if (!isMine) {
                        RowCard(
                            title = "Report",
                            leading = { Icon(Icons.Filled.Flag, contentDescription = null, tint = NirogColor.statusCritical) },
                            onClick = { reportTarget = target; actionTarget = null },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionTarget = null }) { Text("Close", color = NirogColor.inkSecondary) }
            },
        )
    }

    reportTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!reporting) reportTarget = null },
            icon = { Icon(Icons.Filled.Flag, contentDescription = null, tint = NirogColor.statusCritical) },
            title = { Text("Report message?", style = NirogType.cardTitle, color = NirogColor.inkPrimary) },
            text = { Text("This message will be sent to the Nirog Bhumi team to review. Thanks for helping keep the community safe.", style = NirogType.body, color = NirogColor.inkSecondary) },
            confirmButton = {
                Button(
                    enabled = !reporting,
                    colors = ButtonDefaults.buttonColors(containerColor = NirogColor.statusCritical),
                    onClick = {
                        reporting = true
                        state.repository.reportChatMessage(
                            messageId = target.id,
                            programId = state.activeProgramId,
                            reportedText = target.values["text"]?.toString().orEmpty(),
                            reportedUserId = target.values["userId"]?.toString().orEmpty()
                        ) { result ->
                            reporting = false
                            reportTarget = null
                            state.cloudMessage = if (result is com.nirogbhumi.app.data.CloudResult.Success) "Thanks — this message has been reported." else (result as com.nirogbhumi.app.data.CloudResult.Failure).message
                        }
                    }
                ) { Text(if (reporting) "Reporting..." else "Report", color = NirogColor.onAccent) }
            },
            dismissButton = { TextButton(onClick = { reportTarget = null }, enabled = !reporting) { Text("Cancel", color = NirogColor.inkSecondary) } }
        )
    }

    viewingPhotoUrl?.let { url ->
        Dialog(onDismissRequest = { viewingPhotoUrl = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { viewingPhotoUrl = null },
                contentAlignment = Alignment.Center,
            ) {
                coil.compose.AsyncImage(
                    model = url, contentDescription = "Full screen photo",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
                IconButton(
                    onClick = { viewingPhotoUrl = null },
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(NirogSpace.md),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
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
