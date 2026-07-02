package com.nirogbhumi.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nirogbhumi.app.data.CloudResult
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.SugarLog
import com.nirogbhumi.app.ui.components.SectionLabel
import com.nirogbhumi.app.ui.theme.NirogColor
import com.nirogbhumi.app.ui.theme.NirogSpace
import com.nirogbhumi.app.ui.theme.NirogType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainHub(state: NirogState) {
    // Shared "has anything real been logged today" signal, computed once here
    // (not per-tab) so Today's focus card and Track's check-in card always agree,
    // and a completed check-in never re-prompts with an empty-feeling "do this
    // now" card - it shows a genuine done-for-today state instead.
    DisposableEffect(Unit) {
        val todayKey = com.nirogbhumi.app.ui.localDayKey(System.currentTimeMillis())
        fun anyToday(docs: List<com.nirogbhumi.app.data.CloudDocument>): Boolean = docs.any { doc ->
            val ts = (doc.values["createdAt"] as? com.google.firebase.Timestamp)
                ?: (doc.values["measuredAt"] as? com.google.firebase.Timestamp)
            ts != null && com.nirogbhumi.app.ui.localDayKey(ts.toDate().time) == todayKey
        }
        val flags = booleanArrayOf(false, false, false)
        fun recompute() { state.checkedInToday = flags.any { it } }
        val subs = listOf(
            state.repository.listenUserCollection("glucoseReadings", 5) { r ->
                if (r is CloudResult.Success) { flags[0] = anyToday(r.value); recompute() }
            },
            state.repository.listenUserCollection("bpReadings", 5) { r ->
                if (r is CloudResult.Success) { flags[1] = anyToday(r.value); recompute() }
            },
            state.repository.listenUserCollection("weightLogs", 5) { r ->
                if (r is CloudResult.Success) { flags[2] = anyToday(r.value); recompute() }
            },
        )
        onDispose { subs.forEach { it.cancel() } }
    }

    Scaffold(
        topBar = {
            NirogTopAppBar(
                profileName = state.profileName,
                onProfileClick = {
                    state.currentScreen = "profile"
                },
                onNotificationClick = {
                    state.currentScreen = "notifications"
                },
                activeTab = state.activeTab,
                onChatClick = {
                    state.currentScreen = "chat_hub"
                }
            )
        },
        bottomBar = {
            NirogBottomNavigationBar(
                activeTab = state.activeTab,
                onTabSelect = { state.activeTab = it }
            )
        },
        containerColor = Color(0xFFF8F6EF),
        // Top/bottom bars already apply statusBarsPadding()/navigationBarsPadding()
        // internally, so Scaffold must not reserve those insets a second time here -
        // that double-reservation was the cause of the large empty gaps above the
        // greeting and below the bottom nav bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state.activeTab) {
                "Today" -> TodayTab(state)
                "Track" -> TrackTab(state)
                "Insights" -> InsightsTab(state)
                "Care" -> CareTab(state)
                "Learn" -> LearnTab(state)
            }

            if (state.shouldShowTour) {
                OnboardingTourOverlay(state)
            }
        }
    }
}

private fun markTourSeen(context: android.content.Context) {
    context.getSharedPreferences("nirog_prefs", android.content.Context.MODE_PRIVATE)
        .edit().putBoolean("onboarding_tour_seen", true).apply()
}

@Composable
fun OnboardingTourOverlay(state: NirogState) {
    val context = LocalContext.current
    val step = state.currentTourStep
    val title = when (step) {
        0 -> "1. Your Daily Action"
        1 -> "2. Fast Metric Logging"
        2 -> "3. Custom Sugar Stories"
        else -> "4. Expert Naturopathy & Yoga"
    }
    val description = when (step) {
        0 -> "Nirog Bhumi is designed to be calm and save screen time. You receive exactly ONE personalized lifestyle task per day. Focus on completing this single action!"
        1 -> "Log fasting sugar, sleep, steps, or water in under 10 seconds. Simply click 'Today's Vitals' cards to log yours. Easy tracking, built for outcomes."
        2 -> "Visit 'Insights' to read your 'Sugar Story'. We automatically analyze how sleep, walking after meals, and late-dinner habits impact your fasting glucose."
        else -> "Tap the Care tab to open your lifestyle program, assigned yoga and naturopathy routines, and expert consultations."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(enabled = true) { /* Consume taps */ },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .background(Color(0xFFF8F6EF), RoundedCornerShape(24.dp))
                .border(1.dp, Color(0xFF314936), RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon Sparkle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFE0ECDD), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Stars,
                    contentDescription = "Feature Sparkle",
                    tint = Color(0xFF314936),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                fontSize = 20.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B3221)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                fontSize = 14.sp,
                color = Color(0xFF434842),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Step dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0..3) {
                    Box(
                        modifier = Modifier
                            .size(if (i == step) 10.dp else 6.dp)
                            .background(
                                color = if (i == step) Color(0xFF314936) else Color(0xFF1B3221).copy(alpha = 0.25f),
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // CTA Button
            Button(
                onClick = {
                    if (step < 3) {
                        state.currentTourStep++
                        when (state.currentTourStep) {
                            1 -> state.activeTab = "Today"
                            2 -> state.activeTab = "Insights"
                            3 -> state.activeTab = "Care"
                        }
                    } else {
                        state.shouldShowTour = false
                        markTourSeen(context)
                        state.activeTab = "Today"
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = if (step < 3) "Next Key Feature" else "Start My 2-Min Routine",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            if (step > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        state.currentTourStep--
                        when (state.currentTourStep) {
                            0 -> state.activeTab = "Today"
                            1 -> state.activeTab = "Today"
                            2 -> state.activeTab = "Insights"
                        }
                    }
                ) {
                    Text("Previous", color = Color(0xFF314936), fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        state.shouldShowTour = false
                        markTourSeen(context)
                    }
                ) {
                    Text("Skip Tour", color = Color(0xFF1B3221).copy(alpha = 0.5f))
                }
            }
        }
    }
}

// Custom Top App Bar matching Nirog Bhumi layout
@Composable
fun NirogTopAppBar(
    profileName: String,
    onProfileClick: () -> Unit,
    onNotificationClick: () -> Unit,
    activeTab: String = "Today",
    onChatClick: () -> Unit = {},
) {
    Surface(
        color = Color(0xFFF1FDEE),
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Initials avatar - no placeholder/stranger photo until the user uploads their own
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF314936))
                        .border(1.dp, Color(0xFFC3C8C0), CircleShape)
                        .clickable { onProfileClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = profileName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                NirogLogo(modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Nirog Bhumi",
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B3221)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // On the Care+ tab the top-right control is the batch chat hub
                // (Announcements + General), not the notification bell.
                if (activeTab == "Care") {
                    IconButton(onClick = onChatClick) {
                        Icon(
                            imageVector = Icons.Filled.Forum,
                            contentDescription = "Batch messages",
                            tint = Color(0xFF1B3221)
                        )
                    }
                } else {
                    IconButton(onClick = onNotificationClick) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = "Notifications",
                            tint = Color(0xFF1B3221)
                        )
                    }
                }
            }
        }
    }
}

// Custom Bottom Navigation matching paper and ink layout
@Composable
fun NirogBottomNavigationBar(activeTab: String, onTabSelect: (String) -> Unit) {
    Surface(
        color = Color(0xFFF1FDEE).copy(alpha = 0.95f),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 4.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NirogBottomNavItem("Today", Icons.Outlined.CalendarToday, Icons.Filled.CalendarToday, activeTab == "Today") { onTabSelect("Today") }
            NirogBottomNavItem("Track", Icons.Outlined.AddCircle, Icons.Filled.AddCircle, activeTab == "Track") { onTabSelect("Track") }
            NirogBottomNavItem("Insights", Icons.Outlined.Insights, Icons.Filled.Insights, activeTab == "Insights") { onTabSelect("Insights") }
            NirogBottomNavItem("Care", Icons.Outlined.MedicalServices, Icons.Filled.MedicalServices, activeTab == "Care") { onTabSelect("Care") }
            NirogBottomNavItem("Learn", Icons.Outlined.MenuBook, Icons.Filled.MenuBook, activeTab == "Learn") { onTabSelect("Learn") }
        }
    }
}

@Composable
fun NirogBottomNavItem(
    label: String,
    outlinedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    filledIcon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isActive) filledIcon else outlinedIcon,
            contentDescription = label,
            tint = if (isActive) Color(0xFF1B3221) else Color(0xFF434842).copy(alpha = 0.55f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) Color(0xFF1B3221) else Color(0xFF434842).copy(alpha = 0.55f)
        )
    }
}

// TAB 1: Today Tab Dashboard
@Composable
fun TodayTab(state: NirogState) {
    DisposableEffect(Unit) {
        val sugarSub = state.repository.listenUserCollection("glucoseReadings", 7) { result ->
            if (result is com.nirogbhumi.app.data.CloudResult.Success) {
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
        }
        val bpSub = state.repository.listenUserCollection("bpReadings", 1) { result ->
            if (result is com.nirogbhumi.app.data.CloudResult.Success) {
                val latest = result.value.firstOrNull()
                val systolic = (latest?.values?.get("systolic") as? Number)?.toInt()
                val diastolic = (latest?.values?.get("diastolic") as? Number)?.toInt()
                if (systolic != null && diastolic != null) state.latestBpReading = "$systolic/$diastolic"
            }
        }
        // Restores today's checklist state from Firestore on every open - without
        // this, "Mark Complete" only ever lived in memory and silently reset the
        // moment the app was reopened, even though it visually said "Completed".
        val checklistSub = state.repository.listenUserCollection("checklistLogs", 10) { result ->
            if (result is com.nirogbhumi.app.data.CloudResult.Success) {
                val todayKey = com.nirogbhumi.app.ui.localDayKey(System.currentTimeMillis())
                val walkDoneToday = result.value.any { doc ->
                    val ts = (doc.values["completedAt"] as? com.google.firebase.Timestamp) ?: (doc.values["updatedAt"] as? com.google.firebase.Timestamp)
                    doc.values["taskId"] == "daily_post_dinner_walk" &&
                        doc.values["status"] == "done" &&
                        ts != null && com.nirogbhumi.app.ui.localDayKey(ts.toDate().time) == todayKey
                }
                if (walkDoneToday && !state.dailyRitualsCompleted.contains("Walk")) state.dailyRitualsCompleted.add("Walk")
                else if (!walkDoneToday) state.dailyRitualsCompleted.remove("Walk")
            }
        }
        onDispose { sugarSub.cancel(); bpSub.cancel(); checklistSub.cancel() }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome header
        Column {
            val calendar = remember { java.util.Calendar.getInstance() }
            val greetingWord = when (calendar.get(java.util.Calendar.HOUR_OF_DAY)) {
                in 4..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                else -> "Good evening"
            }
            val firstName = state.profileName.trim().substringBefore(" ").ifBlank { "there" }
            Text(
                text = "$greetingWord, $firstName",
                fontSize = 24.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B3221)
            )
            Text(
                text = remember { java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault()).format(java.util.Date()) },
                fontSize = 14.sp,
                color = Color(0xFF434842),
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // Highlight daily task card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 0.5.dp, color = Color(0xFF9CB79F).copy(alpha = 0.4f), shape = RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF314936)),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "ONE ACTION FOR TODAY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB2CEB4),
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                val isWalkTaskDone = state.dailyRitualsCompleted.contains("Walk")

                Text(
                    text = if (isWalkTaskDone) "Walk logged and completed!" else "Walk 15 minutes after dinner",
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(18.dp))

                if (isWalkTaskDone) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                            .padding(vertical = 14.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFFBFEE95), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Completed for today", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        TextButton(onClick = {
                            state.dailyRitualsCompleted.remove("Walk")
                            val dayKey = com.nirogbhumi.app.ui.localDayKey(System.currentTimeMillis())
                            state.repository.upsertUserRecord("checklistLogs", "daily_post_dinner_walk_$dayKey", mapOf(
                                "taskId" to "daily_post_dinner_walk",
                                "title" to "Walk 15 minutes after dinner",
                                "status" to "pending",
                                "completedAt" to null
                            )) { result -> if (result is com.nirogbhumi.app.data.CloudResult.Failure) state.cloudMessage = result.message }
                        }) {
                            Text("Undo", color = Color(0xFFB2CEB4), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            state.dailyRitualsCompleted.add("Walk")
                            val dayKey = com.nirogbhumi.app.ui.localDayKey(System.currentTimeMillis())
                            state.repository.upsertUserRecord("checklistLogs", "daily_post_dinner_walk_$dayKey", mapOf(
                                "taskId" to "daily_post_dinner_walk",
                                "title" to "Walk 15 minutes after dinner",
                                "status" to "done",
                                "completedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )) { result -> if (result is com.nirogbhumi.app.data.CloudResult.Failure) state.cloudMessage = result.message }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            text = "Mark Complete",
                            color = Color(0xFF314936),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Quick Vitals Header with a gentle prompt into the guided check-in
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today's Vitals",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B3221)
            )
            TextButton(onClick = { state.checkinStartStep = 0; state.currentScreen = "daily_checkin" }) {
                Text(
                    if (state.checkedInToday) "Add more" else "Log now",
                    color = Color(0xFF314936), fontWeight = FontWeight.Bold, fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF314936), modifier = Modifier.size(15.dp))
            }
        }

        // Bento Grid of quick cards
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
            ) {
                VitalBentoCard(
                    icon = Icons.Filled.Bloodtype,
                    iconColor = Color(0xFFBA1A1A),
                    title = "Fasting Sugar",
                    value = if (state.fastingSugarValue > 0) "${state.fastingSugarValue}" else "—",
                    unit = if (state.fastingSugarValue > 0) "mg/dL" else "",
                    annotation = if (state.fastingSugarValue > 0) "Today" else "Tap to log",
                    onClick = { state.isQuickLogFastingOpen = true }
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp)
            ) {
                VitalBentoCard(
                    icon = Icons.Filled.Favorite,
                    iconColor = Color(0xFF426820),
                    title = "Blood Pressure",
                    value = state.latestBpReading ?: "—",
                    unit = "",
                    annotation = if (state.latestBpReading != null) "Latest" else "No data yet",
                    onClick = { state.currentScreen = "bp_overview" }
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
            ) {
                VitalBentoCard(
                    icon = Icons.Filled.Bedtime,
                    iconColor = Color(0xFF4B6450),
                    title = "Sleep Duration",
                    value = if (state.sleepHours > 0 || state.sleepMinutes > 0) "${state.sleepHours}h ${state.sleepMinutes}m" else "—",
                    unit = "",
                    annotation = if (state.sleepHours > 0 || state.sleepMinutes > 0) "Last night" else "No data yet",
                    onClick = { state.currentScreen = "sleep_overview" }
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp)
            ) {
                VitalBentoCard(
                    icon = Icons.Filled.DirectionsWalk,
                    iconColor = Color(0xFF426820),
                    title = "Steps Done",
                    value = if (state.stepsLogged > 0) String.format("%,d", state.stepsLogged) else "—",
                    unit = if (state.stepsLogged > 0) "steps" else "",
                    annotation = if (state.stepsLogged > 0) "Today" else "No data yet",
                    onClick = { state.currentScreen = "walking_overview" }
                )
            }
        }

        // Weekly preview card - opens the real Rhythm screen (ring + 30-day grid),
        // not the old generic "weekly_report" catalog template.
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { state.currentScreen = "rhythm" }
                .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.4f), shape = RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Weekly Rhythm",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B3221),
                        fontSize = 15.sp
                    )
                    IconButton(onClick = { state.activeTab = "Insights" }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowForward,
                            contentDescription = "Insights Page",
                            tint = Color(0xFF314936)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (state.sugarLogs.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.Insights, contentDescription = null, tint = Color(0xFF9CB79F), modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Start logging today to see your weekly rhythm here",
                            fontSize = 12.sp,
                            color = Color(0xFF737972),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Recent readings sparkline built from real logged sugar values
                    val recent = state.sugarLogs.take(7).reversed()
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    ) {
                        val spacing = size.width / recent.size
                        val barWidth = 14.dp.toPx()
                        val maxValue = (recent.maxOfOrNull { it.value } ?: 1).coerceAtLeast(1)

                        recent.forEachIndexed { index, log ->
                            val x = index * spacing + (spacing / 2) - (barWidth / 2)
                            val totalHeight = size.height - 30.dp.toPx()
                            val barHeight = totalHeight * (log.value.toFloat() / maxValue)
                            val y = totalHeight - barHeight
                            val isLast = index == recent.size - 1

                            drawRoundRect(
                                color = Color(0xFFE5F1E2),
                                topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                                size = androidx.compose.ui.geometry.Size(barWidth, totalHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )

                            val barColor = if (isLast) Color(0xFF1B3221) else Color(0xFF426820).copy(alpha = 0.5f)
                            drawRoundRect(
                                color = barColor,
                                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        recent.forEachIndexed { i, log ->
                            Text(
                                text = log.type.take(4),
                                fontSize = 10.sp,
                                fontWeight = if (i == recent.size - 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (i == recent.size - 1) Color(0xFF1B3221) else Color(0xFF737972),
                                modifier = Modifier.width(36.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { state.currentScreen = "health_file" }
                .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.3f), shape = RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEBF7E8)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Description, "Health File", tint = Color(0xFF1B3221), modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Health File", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B2219))
                    Text("Always ready to show any doctor", fontSize = 11.5.sp, color = Color(0xFF8B9285))
                }
                Text("Open", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
            }
        }

        // The one thing Today has that Track doesn't: a program-aware view of
        // what's coming up in Care+. Only rendered for enrolled members, and
        // only when there's something upcoming to show - never an empty card.
        if (state.isProgramActive) {
            TodayProgramPreview(state)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Today's program-aware utility: the next upcoming Care+ calendar event, read
 * live from the same programEvents the admin console manages. Nothing is
 * shown until there's a real, real upcoming event - no placeholder card.
 */
@Composable
private fun TodayProgramPreview(state: NirogState) {
    var events by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    DisposableEffect(state.activeProgramId) {
        if (state.activeProgramId.isBlank()) return@DisposableEffect onDispose {}
        val sub = state.repository.listenProgramEvents(state.activeProgramId) { result ->
            if (result is com.nirogbhumi.app.data.CloudResult.Success) events = result.value.map { it.values }
        }
        onDispose { sub.cancel() }
    }
    val next = events
        .mapNotNull { e -> (e["startsAt"] as? com.google.firebase.Timestamp)?.toDate()?.let { it to e } }
        .filter { (date, _) -> date.time >= System.currentTimeMillis() }
        .minByOrNull { (date, _) -> date.time }
        ?.second
        ?: return

    val title = next["title"] as? String ?: "Program event"
    val type = next["type"] as? String
    val startsAt = (next["startsAt"] as? com.google.firebase.Timestamp)?.toDate()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { state.currentScreen = "program_calendar" }
            .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.3f), shape = RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (startsAt != null) {
                Column(
                    modifier = Modifier
                        .width(46.dp)
                        .background(Color(0xFFF1EDE3), RoundedCornerShape(12.dp))
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        java.text.SimpleDateFormat("d", java.util.Locale.getDefault()).format(startsAt),
                        fontFamily = FontFamily.Serif, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221)
                    )
                    Text(
                        java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault()).format(startsAt).uppercase(),
                        fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B9285)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Next in your program", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFC7902F))
                }
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B2219))
                if (startsAt != null) {
                    Text(
                        java.text.SimpleDateFormat("EEE, d MMM · h:mm a", java.util.Locale.getDefault()).format(startsAt) + (type?.let { " · $it" } ?: ""),
                        fontSize = 11.5.sp, color = Color(0xFF8B9285)
                    )
                }
            }
        }
    }
}

@Composable
fun VitalBentoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    value: String,
    unit: String,
    annotation: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.4f), shape = RoundedCornerShape(16.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFF1FDEE), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = title, tint = iconColor, modifier = Modifier.size(18.dp))
                }

                if (annotation.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(iconColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = annotation,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = iconColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF434842)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B3221)
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        fontSize = 11.sp,
                        color = Color(0xFF737972),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}

// TAB 2: Track Hub Grid
@Composable
fun TrackTab(state: NirogState) {
    var showActivityLog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column {
            Text(
                text = "Track",
                fontSize = 32.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B3221)
            )
            Text(
                text = "Your whole day's logging in one calm flow.",
                fontSize = 15.sp,
                color = Color(0xFF434842),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Primary path: the guided daily check-in. This is the low-friction default -
        // one tap walks the user through sugar/BP/weight without hunting around the grid.
        // Once something real has been logged today, this becomes a calm "done"
        // state instead of a blank restart prompt - re-opening the wizard after
        // completion used to just reset every field, which read as broken.
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { state.checkinStartStep = 0; state.currentScreen = "daily_checkin" }
                .border(width = 0.5.dp, color = Color(0xFF9CB79F).copy(alpha = 0.4f), shape = RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = if (state.checkedInToday) Color(0xFFEBF7E8) else Color(0xFF314936)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).background(
                        if (state.checkedInToday) Color(0xFF314936).copy(alpha = 0.12f) else Color.White.copy(alpha = 0.15f),
                        CircleShape
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (state.checkedInToday) Icons.Filled.CheckCircle else Icons.Filled.PlaylistAddCheck,
                        contentDescription = null,
                        tint = if (state.checkedInToday) Color(0xFF3F7D58) else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (state.checkedInToday) "Checked in for today" else "Daily Check-in",
                        fontSize = 18.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                        color = if (state.checkedInToday) Color(0xFF1B3221) else Color.White
                    )
                    Text(
                        if (state.checkedInToday) "Nicely done — tap to add another reading" else "Sugar, BP & weight — under 2 minutes",
                        fontSize = 13.sp,
                        color = if (state.checkedInToday) Color(0xFF4B6450) else Color(0xFFB2CEB4)
                    )
                }
                Icon(
                    Icons.Filled.ArrowForward,
                    contentDescription = "Start",
                    tint = if (state.checkedInToday) Color(0xFF314936) else Color.White
                )
            }
        }

        // Quick Log - single-tap entry for logging just one thing without the full flow
        Column {
            Text("Or log just one thing", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickLogChip("Sugar", Icons.Filled.Bloodtype, Color(0xFFBA1A1A)) { state.checkinStartStep = 0; state.currentScreen = "daily_checkin" }
                QuickLogChip("BP", Icons.Filled.Favorite, Color(0xFF1B3221)) { state.checkinStartStep = 1; state.currentScreen = "daily_checkin" }
                QuickLogChip("Weight", Icons.Filled.MonitorWeight, Color(0xFF4B6450)) { state.checkinStartStep = 2; state.currentScreen = "daily_checkin" }
                QuickLogChip("Activity", Icons.Filled.DirectionsWalk, Color(0xFF426820)) { showActivityLog = true }
            }
        }

        Text("Today's Summary", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))

        // Bento Grid Modules of Tracks
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                TrackModuleBox(
                    icon = Icons.Filled.Bloodtype,
                    iconBg = Color(0xFFFFDAD6),
                    iconTint = Color(0xFFBA1A1A),
                    title = "Blood Sugar",
                    measuredValue = if (state.fastingSugarValue > 0) "${state.fastingSugarValue}" else "No data",
                    labelSuffix = if (state.fastingSugarValue > 0) "mg/dL" else "",
                    onClick = { state.currentScreen = "sugar_detail" }
                )
            }
            Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                TrackModuleBox(
                    icon = Icons.Filled.Favorite,
                    iconBg = Color(0xFFCDEAD0),
                    iconTint = Color(0xFF1B3221),
                    title = "BP",
                    measuredValue = state.latestBpReading ?: "No data",
                    labelSuffix = "",
                    onClick = { state.currentScreen = "bp_overview" }
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                TrackModuleBox(
                    icon = Icons.Filled.Bedtime,
                    iconBg = Color(0xFFE5F1E2),
                    iconTint = Color(0xFF4B6450),
                    title = "Sleep",
                    measuredValue = if (state.sleepHours > 0 || state.sleepMinutes > 0) "${state.sleepHours}h ${state.sleepMinutes}m" else "No data",
                    labelSuffix = "",
                    onClick = { state.currentScreen = "sleep_overview" }
                )
            }
            Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                TrackModuleBox(
                    icon = Icons.Filled.DirectionsWalk,
                    iconBg = Color(0xFFBFEE95).copy(alpha = 0.5f),
                    iconTint = Color(0xFF426820),
                    title = "Walking & Activity",
                    measuredValue = if (state.stepsLogged > 0) String.format("%,d", state.stepsLogged) else "No data",
                    labelSuffix = "",
                    onClick = { state.currentScreen = "walking_overview" }
                )
            }
        }

        TrackModuleBox(
            icon = Icons.Filled.Science,
            iconBg = Color(0xFFE5F1E2),
            iconTint = Color(0xFF1B3221),
            title = "Lab Reports",
            measuredValue = "Upload",
            labelSuffix = "",
            fullWidth = true,
            onClick = { state.currentScreen = "lab_reports" }
        )
        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showActivityLog) {
        ActivityLogDialog(state) { showActivityLog = false }
    }
}

@Composable
fun QuickLogChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(tint.copy(alpha = 0.12f), CircleShape)
                .border(1.dp, tint.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = "Log $label", tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
    }
}

// Fixed compile extension function for modifier border to prevent error
private fun Modifier.border(width: androidx.compose.ui.unit.Dp, color: Color, roundedCorner: Int): Modifier =
    this.border(width, color, RoundedCornerShape(roundedCorner.dp))


@Composable
fun TrackModuleBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    measuredValue: String,
    labelSuffix: String,
    fullWidth: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (fullWidth) Modifier else Modifier.aspectRatio(1.0f))
            .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.4f), roundedCorner = 24)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = if (fullWidth) Arrangement.spacedBy(10.dp) else Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(18.dp))
            }

            Column {
                Text(
                    text = title.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF434842),
                    letterSpacing = 0.75.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = measuredValue,
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B3221)
                    )
                    if (labelSuffix.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = labelSuffix,
                            fontSize = 11.sp,
                            color = Color(0xFF737972),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// TAB 3: Insights Page
@Composable
fun InsightsTab(state: NirogState) {
    val weekRange = remember {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        val start = cal.time
        cal.add(java.util.Calendar.DAY_OF_YEAR, 6)
        val end = cal.time
        val fmt = java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())
        "${fmt.format(start)} - ${fmt.format(end)}"
    }
    val hasEnoughData = state.sugarLogs.size >= 3
    val avgFastingSugar = state.sugarLogs.filter { it.type == "Fasting" }.map { it.value }.let { if (it.isEmpty()) null else it.average().toInt() }
    val sugarTrend = remember(state.sugarLogs.size) {
        val values = state.sugarLogs.map { it.value }
        if (values.size < 4) null else {
            val half = values.size / 2
            val recentAvg = values.take(half).average()
            val olderAvg = values.drop(half).average()
            when {
                recentAvg < olderAvg - 3 -> "Improving" to Icons.Filled.TrendingDown
                recentAvg > olderAvg + 3 -> "Rising" to Icons.Filled.TrendingUp
                else -> "Stable" to Icons.Filled.TrendingFlat
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Weekly Report",
                fontSize = 32.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B3221)
            )
            Text(
                text = weekRange,
                fontSize = 13.sp,
                color = Color(0xFF737972)
            )
        }

        Text(
            text = if (hasEnoughData) "Your pattern is becoming clearer." else "Log a few readings this week to unlock your first insight.",
            fontSize = 16.sp,
            color = Color(0xFF4B6450),
            fontWeight = FontWeight.Medium
        )

        if (!hasEnoughData) {
            Card(
                modifier = Modifier.fillMaxWidth().border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.4f), shape = RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.Insights, contentDescription = null, tint = Color(0xFF9CB79F), modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No insights yet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF1B3221)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Log at least 3 sugar readings this week and we'll show you real trends, not guesses.",
                        fontSize = 13.sp,
                        color = Color(0xFF737972),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { state.activeTab = "Track" },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Log a Reading", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        } else {
            // Avg Fasting Sugar - computed from real logged readings
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    // Opens the real Rhythm screen, not the old generic "trends_30"
                    // catalog template.
                    .clickable { state.currentScreen = "rhythm" }
                    .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.4f), shape = RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WaterDrop, "Sugar", tint = Color(0xFF43242A), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Avg Fasting Sugar", fontWeight = FontWeight.SemiBold, color = Color(0xFF434842))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(avgFastingSugar?.toString() ?: "—", fontSize = 28.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("mg/dL", fontSize = 12.sp, color = Color(0xFF737972))
                        }

                        sugarTrend?.let { (label, icon) ->
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFE5F1E2), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(icon, label, tint = Color(0xFF426820), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                                }
                            }
                        }
                    }
                }
            }

            // BP - latest real reading
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { state.currentScreen = "bp_overview" }
                    .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.4f), shape = RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Favorite, "BP", tint = Color(0xFFBA1A1A), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Latest Blood Pressure", fontWeight = FontWeight.SemiBold, color = Color(0xFF434842))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(state.latestBpReading ?: "—", fontSize = 28.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                        if (state.latestBpReading != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("mmHg", fontSize = 12.sp, color = Color(0xFF737972))
                        }
                    }
                }
            }

            // Row for Sleep and Walk - real state values
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { state.currentScreen = "sleep_overview" }
                            .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.4f), shape = RoundedCornerShape(24.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Bedtime, "Sleep", tint = Color(0xFF4B6450), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sleep", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF434842))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                if (state.sleepHours > 0 || state.sleepMinutes > 0) "${state.sleepHours}h ${state.sleepMinutes}m" else "—",
                                fontSize = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221)
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { state.currentScreen = "walking_overview" }
                            .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.4f), shape = RoundedCornerShape(24.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.DirectionsWalk, "Steps", tint = Color(0xFF426820), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Daily Walk", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF434842))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                if (state.stepsLogged > 0) String.format("%,d", state.stepsLogged) else "—",
                                fontSize = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221)
                            )
                        }
                    }
                }
            }

            // Plain-language guidance grounded in the real average, not a fabricated correlation
            avgFastingSugar?.let { avg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.2f), shape = RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE0ECDD)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Spa, "Insight", tint = Color(0xFF1B3221), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("THIS WEEK'S GUIDANCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when {
                                avg > 130 -> "Your average fasting sugar this week is $avg mg/dL, above the typical target range. A short walk after meals and a consistent dinner time can help. Please discuss any persistent high readings with your doctor."
                                avg < 80 -> "Your average fasting sugar this week is $avg mg/dL, on the lower side. If you feel dizzy or shaky, eat something and tell your doctor about these readings."
                                else -> "Your average fasting sugar this week is $avg mg/dL, within a typical range. Keep up your current routine, and keep logging so trends stay accurate."
                            },
                            fontSize = 13.sp,
                            color = Color(0xFF141E15),
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Share CTA
            OutlinedButton(
                onClick = { state.currentScreen = "share_report" },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFFC3C8C0)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1B3221))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Share, "Share", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Report with Doctor", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

// TAB 4: Care Team & Consultations
@Composable
fun CareTab(state: NirogState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column {
            Text(
                text = "Care+",
                fontSize = 32.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B3221)
            )
            Text(
                text = if (state.isProgramActive) "Your program, your batch, your coach." else "A guided program with a real coach and a community on the same path.",
                fontSize = 15.sp,
                color = Color(0xFF434842),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (!state.isProgramActive) {
            // Care+'s calendar/community layer is a second tier only for enrolled
            // program members. Rather than a single thin locked card, show what's
            // actually inside so the upsell isn't just an empty-feeling wall.
            Card(
                modifier = Modifier.fillMaxWidth().border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.3f), shape = RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF314936)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Box(
                        modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Lock, "Locked", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("You're not doing this alone", fontSize = 20.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Join a Nirog Bhumi program to unlock a coach, a batch of people on the same journey, and a program calendar.",
                        fontSize = 13.sp, color = Color(0xFFB2CEB4), lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            state.enteringProgramCodeFromCarePlus = true
                            state.currentScreen = "program_code_optional"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC7902F)),
                        shape = RoundedCornerShape(20.dp)
                    ) { Text("Enter program code", fontWeight = FontWeight.Bold, color = Color(0xFF241706)) }
                }
            }

            CareRow(Icons.Outlined.Groups, "A coach, not a chatbot", "A named program coach who checks in on your batch and answers questions.") {}
            CareRow(Icons.Outlined.Forum, "A batch on the same path", "Group chat and a coach announcements channel with people doing this with you.") {}
            CareRow(Icons.Outlined.CalendarMonth, "A real program calendar", "Live sessions, group walks, and lab-review weeks - never a silent schedule change.") {}
        } else {
            val dayNumber = if (state.programStartedAtMillis > 0) {
                (((System.currentTimeMillis() - state.programStartedAtMillis) / (1000L * 60 * 60 * 24)) + 1).coerceAtLeast(1)
            } else 1
            Card(
                modifier = Modifier.fillMaxWidth().border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.3f), shape = RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF314936)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(state.activeProgramName.ifBlank { "Your Program" }, fontSize = 18.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (state.programDurationDays > 0) "Day $dayNumber of ${state.programDurationDays}" else "Day $dayNumber",
                        fontSize = 13.sp, color = Color(0xFFB2CEB4)
                    )
                }
            }

            BatchPulseCard(state)
            PinnedAnnouncementCard(state)

            CareRow(Icons.Outlined.Checklist, "Today's Checklist", "Your daily program actions") { state.currentScreen = "active_journey" }
            CareRow(Icons.Outlined.CalendarMonth, "Program Calendar", "Live sessions, group walks, and lab weeks") { state.currentScreen = "program_calendar" }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * Latest announcement, pinned on the Care+ home so a coach's most recent update
 * is never missed behind a nav tap. Full history lives in the Chat Hub's
 * Announcements room (top-right chat icon) - this is a preview, not a duplicate
 * surface, so tapping it opens that same room rather than a third screen.
 */
@Composable
private fun PinnedAnnouncementCard(state: NirogState) {
    var latest by remember { mutableStateOf<Map<String, Any?>?>(null) }
    DisposableEffect(state.activeProgramId) {
        if (state.activeProgramId.isBlank()) return@DisposableEffect onDispose {}
        val sub = state.repository.listenAnnouncements(state.activeProgramId) { result ->
            if (result is CloudResult.Success) latest = result.value.firstOrNull()?.values
        }
        onDispose { sub.cancel() }
    }
    val announcement = latest ?: return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { state.currentScreen = "announcements" }
            .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.3f), shape = RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF4E9D3)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).background(Color(0xFFB9832B).copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Campaign, contentDescription = null, tint = Color(0xFFB9832B), modifier = Modifier.size(18.dp)) }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    announcement["title"]?.toString() ?: "From your coach",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B2219),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    announcement["body"]?.toString().orEmpty(),
                    fontSize = 12.sp, color = Color(0xFF4B6450),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Batch Pulse (PRD v2, Care+): cooperative presence + a collective goal -
 * deliberately never a per-member ranking. Reads a Function-aggregated,
 * PII-free document; shows an honest "be the first" state at zero rather
 * than fabricating activity.
 */
@Composable
private fun BatchPulseCard(state: NirogState) {
    var pulse by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var loaded by remember { mutableStateOf(false) }

    DisposableEffect(state.activeProgramId) {
        if (state.activeProgramId.isBlank()) {
            loaded = true
            return@DisposableEffect onDispose {}
        }
        val sub = state.repository.listenBatchPulse(state.activeProgramId) { result ->
            if (result is CloudResult.Success) pulse = result.value?.values
            loaded = true
        }
        onDispose { sub.cancel() }
    }

    if (!loaded) return
    val checkedIn = (pulse?.get("checkedInCount") as? Number)?.toInt() ?: 0
    val memberCount = (pulse?.get("memberCount") as? Number)?.toInt() ?: 0
    val collectiveMinutes = (pulse?.get("collectiveMinutes") as? Number)?.toInt() ?: 0

    Card(
        modifier = Modifier.fillMaxWidth().border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.3f), shape = RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(Modifier.padding(NirogSpace.xl)) {
            SectionLabel("Batch pulse")
            Spacer(Modifier.height(NirogSpace.sm))
            if (memberCount == 0) {
                Text(
                    "Be the first in your batch to check in today.",
                    style = NirogType.body,
                    color = NirogColor.inkSecondary,
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$checkedIn", style = NirogType.display, color = NirogColor.forest)
                    Text("/$memberCount", style = NirogType.cardTitle, color = NirogColor.inkMuted)
                }
                Text(
                    "batchmates checked in today" + if (checkedIn > 0) " - you're one of them" else "",
                    style = NirogType.caption,
                    color = NirogColor.inkMuted,
                )
            }
            if (collectiveMinutes > 0) {
                Spacer(Modifier.height(NirogSpace.md))
                Text(
                    "Batch goal: walk together this month",
                    style = NirogType.secondary,
                    color = NirogColor.inkSecondary,
                )
                Text(
                    "$collectiveMinutes minutes walked as a batch so far - no rankings, just the team total.",
                    style = NirogType.caption,
                    color = NirogColor.inkMuted,
                )
            }
        }
    }
}

@Composable
private fun CareRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.3f), shape = RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).background(Color(0xFFEBF7E8), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, title, tint = Color(0xFF1B3221), modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF141E15), fontSize = 15.sp)
                Text(subtitle, fontSize = 12.sp, color = Color(0xFF737972))
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFF737972))
        }
    }
}

// TAB 5: Learn Section & Store
@Composable
fun LearnTab(state: NirogState) {
    val context = LocalContext.current
    var featuredArticle by remember { mutableStateOf<Result<List<com.nirogbhumi.app.content.NirogBhumiArticle>>?>(null) }
    LaunchedEffect(Unit) { featuredArticle = com.nirogbhumi.app.content.NirogBhumiContentApi.fetchArticles(perPage = 1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column {
            Text(
                text = "Learn & Explore",
                fontSize = 32.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B3221)
            )
            Text(
                text = "Discover Ayurvedic wisdom & modern metabolic sciences.",
                fontSize = 15.sp,
                color = Color(0xFF434842),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Search bar
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { state.searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Filled.Search, "Search", tint = Color(0xFF737972)) },
            placeholder = { Text("Search articles, guides, or products...", color = Color(0xFFC3C8C0)) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF1B3221),
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color(0xFFEEE8DC).copy(alpha = 0.7f),
                unfocusedContainerColor = Color(0xFFEEE8DC).copy(alpha = 0.7f)
            )
        )

        // Categories Bento Grid
        Text(
            text = "Categories",
            fontSize = 20.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B3221)
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                LearnCategoryCard("Diabetes", Icons.Filled.Spa, Color(0xFFBFEE95)) { state.currentScreen = "articles" }
            }
            Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                LearnCategoryCard("Food", Icons.Filled.Restaurant, Color(0xFFFFD9DE)) { state.currentScreen = "articles" }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                LearnCategoryCard("Movement", Icons.Filled.DirectionsWalk, Color(0xFFCDEAD0)) { state.currentScreen = "articles" }
            }
            Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                LearnCategoryCard("Mindfulness", Icons.Filled.SelfImprovement, Color(0xFFE5F1E2)) { state.currentScreen = "articles" }
            }
        }

        // Featured read article card
        Text(
            text = "Featured Read",
            fontSize = 20.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B3221)
        )

        val featured = featuredArticle?.getOrNull()?.firstOrNull()
        when {
            featuredArticle == null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF9CB79F))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Loading from nirogbhumi.com...", fontSize = 13.sp, color = Color(0xFF697169))
            }
            featured == null -> Text(
                "Couldn't load the latest article from nirogbhumi.com right now.",
                fontSize = 13.sp, color = Color(0xFF697169)
            )
            else -> Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.35f), shape = RoundedCornerShape(24.dp))
                    .clickable {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(featured.link))) }
                    },
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column {
                    featured.imageUrl?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = featured.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = featured.title,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B3221)
                        )
                        if (featured.excerpt.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = featured.excerpt,
                                fontSize = 13.sp,
                                color = Color(0xFF434842),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(featured.dateLabel, fontSize = 11.sp, color = Color(0xFF737972))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFF314936), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.ChevronRight, "Read", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Store is not launched yet - a single clear teaser instead of a shop
        // front with nothing real to sell.
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { state.currentScreen = "coming_soon" }
                .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.35f), shape = RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEEE8DC)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Nirog Bhumi Store",
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B3221)
                    )
                    Text("Wellness tools and kits - coming soon", fontSize = 12.sp, color = Color(0xFF737972))
                }
                Icon(Icons.Filled.ChevronRight, "Coming soon", tint = Color(0xFF1B3221))
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun LearnCategoryCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, iconBg: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 110.dp)
            .clickable(onClick = onClick)
            .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.35f), shape = RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconBg.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = Color(0xFF1B3221), modifier = Modifier.size(18.dp))
            }
            Text(text = title, fontWeight = FontWeight.SemiBold, color = Color(0xFF1B3221), fontSize = 14.sp)
        }
    }
}

