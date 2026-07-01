package com.nirogbhumi.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.SugarLog

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
                        val value = (doc.values["value"] as? Number)?.toInt() ?: return@mapIndexedNotNull null
                        val readingType = doc.values["readingType"] as? String
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
            // Summary Card
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
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("105", fontSize = 42.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("mg/dL", fontSize = 14.sp, color = Color(0xFF737972), modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Stable: Your readings are within standard parameters 92% of the time over the past 30 days.",
                        fontSize = 13.sp,
                        color = Color(0xFF434842),
                        lineHeight = 18.sp
                    )
                }
            }

            // Custom Drawn Line Chart Simulation for last 30d
            Text("Last 30 Days Trend", fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.3f), shape = RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Simulating a trend line graph using canvas draw
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        val points = listOf(140f, 136f, 120f, 125f, 110f, 98f, 105f)
                        val stepX = size.width / 6
                        val maxY = 160f
                        val minY = 70f
                        val heightRange = maxY - minY

                        // Draw target safe corridor shaded area
                        val safeMinY = size.height - ((100f - minY) / heightRange * size.height)
                        val safeMaxY = size.height - ((140f - minY) / heightRange * size.height)

                        drawRect(
                            color = Color(0xFFE5F1E2).copy(alpha = 0.5f),
                            topLeft = androidx.compose.ui.geometry.Offset(0f, safeMaxY),
                            size = androidx.compose.ui.geometry.Size(size.width, safeMinY - safeMaxY)
                        )

                        // Draw trend lines
                        var lastX = 0f
                        var lastY = 0f
                        points.forEachIndexed { i, pt ->
                            val x = i * stepX
                            val fraction = (pt - minY) / heightRange
                            val y = size.height - (fraction * size.height)

                            // dot
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

                    // Labels below
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Week 1", fontSize = 11.sp, color = Color(0xFF737972))
                        Text("Week 2", fontSize = 11.sp, color = Color(0xFF737972))
                        Text("Week 3", fontSize = 11.sp, color = Color(0xFF737972))
                        Text("Today", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
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
                            val sugarInt = sugarInputText.toIntOrNull() ?: 100
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
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedLogType == "Fasting") Color(0xFF314936) else Color(0xFFEBF7E8),
                                modifier = Modifier.clickable { selectedLogType = "Fasting" }.padding(4.dp)
                            ) {
                                Text("Fasting", color = if (selectedLogType == "Fasting") Color.White else Color(0xFF1B3221), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedLogType == "Post-meal") Color(0xFF314936) else Color(0xFFEBF7E8),
                                modifier = Modifier.clickable { selectedLogType = "Post-meal" }.padding(4.dp)
                            ) {
                                Text("Post-meal", color = if (selectedLogType == "Post-meal") Color.White else Color(0xFF1B3221), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            }
                        }

                        OutlinedTextField(
                            value = sugarInputText,
                            onValueChange = { sugarInputText = it },
                            placeholder = { Text("Value in mg/dL (e.g. 105)", color = Color(0xFFC3C8C0)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

// Screen 2: Food Impact detailed journal log
@Composable
fun FoodJournalScreen(state: NirogState) {
    var customMealInput by remember { mutableStateOf("") }
    var selectedMealCategory by remember { mutableStateOf("Breakfast") }

    val loggedMeals = remember {
        mutableStateListOf(
            Triple("Breakfast", "2 Oats Roti + Dal Sabzi", "8.2 Impact (Stable)"),
            Triple("Lunch", "1 Salad Cup + Brown Rice", "9.1 Impact (Stable)")
        )
    }

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
                "Food Journal",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color(0xFF1B3221)
            )
            Icon(Icons.Filled.Restaurant, "Meal Tracker", tint = Color(0xFF1B3221))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Meal Log Timeline",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B3221)
            )

            // Timeline rows representation
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                loggedMeals.forEach { (cat, desc, impact) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.35f), shape = RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(cat.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF426820))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(desc, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1B3221))
                            }

                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFE5F1E2), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(impact, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                            }
                        }
                    }
                }
            }

            Divider(color = Color(0xFFC3C8C0).copy(alpha = 0.3f))

            Text(
                "Quick Add Meal Entry",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B3221)
            )

            // Quick add inputs
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.4f), shape = RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Meal selectors
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("Breakfast", "Lunch", "Dinner").forEach { meal ->
                            val active = selectedMealCategory == meal
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (active) Color(0xFF314936) else Color(0xFFF1FDEE),
                                modifier = Modifier
                                    .clickable { selectedMealCategory = meal }
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                            ) {
                                Text(
                                    meal,
                                    color = if (active) Color.White else Color(0xFF1B3221),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = customMealInput,
                        onValueChange = { customMealInput = it },
                        placeholder = { Text("What did you eat? (e.g. 2 Oats Roti, Sabzi)", color = Color(0xFFC3C8C0)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF8F6EF),
                            unfocusedContainerColor = Color(0xFFF8F6EF),
                            focusedBorderColor = Color(0xFF314936),
                            unfocusedBorderColor = Color.Transparent
                        )
                    )

                    Button(
                        onClick = {
                            if (customMealInput.isNotEmpty()) {
                                loggedMeals.add(Triple(selectedMealCategory, customMealInput, "8.0 Impact (Stable)"))
                                customMealInput = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936)),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Text("Add Journal Entry", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
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
                Text(
                    text = if (state.consultStep == 1) "Confirm Service" else if (state.consultStep == 2) "Confirm Details & Pay (₹499)" else "Back to Hub",
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
            IconButton(onClick = { }) { Icon(Icons.Filled.MoreVert, "More Options", tint = Color(0xFF1B3221)) }
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
            Text("Sleep affects Fasting Sugar levels", fontSize = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))

            Text(
                "Analysis of your sleep records versus mornings where your glucose reading spikes indicates strong alignment. Deprived sleeping patterns (less than 6 hours) increases systemic cortisol levels which drives morning fasting sugar upward.",
                fontSize = 14.sp,
                color = Color(0xFF434842),
                lineHeight = 20.sp
            )

            // Correlation Bars
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.35f), shape = RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("When Sleep is < 6 Hours", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF141E15))
                            Text("112 mg/dL Avg", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFBA1A1A))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(12.dp).background(Color(0xFFF8F6EF), RoundedCornerShape(6.dp))) {
                            Box(modifier = Modifier.fillMaxWidth(0.85f).height(12.dp).background(Color(0xFFBA1A1A), RoundedCornerShape(6.dp)))
                        }
                    }

                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("When Sleep is > 7 Hours", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF141E15))
                            Text("94 mg/dL Avg", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF426820))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(12.dp).background(Color(0xFFF8F6EF), RoundedCornerShape(6.dp))) {
                            Box(modifier = Modifier.fillMaxWidth(0.55f).height(12.dp).background(Color(0xFF426820), RoundedCornerShape(6.dp)))
                        }
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
fun ProfileScreen(state: NirogState) {
    var showSignOutConfirm by remember { mutableStateOf(false) }

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
            SettingsRow(Icons.Filled.Description, "Legal & policies", showDivider = false) { state.currentScreen = "legal_center" }
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
