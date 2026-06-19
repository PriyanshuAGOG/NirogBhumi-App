package in.nirogbhumi.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import in.nirogbhumi.app.ui.NirogState
import in.nirogbhumi.app.ui.StoreProduct
import in.nirogbhumi.app.ui.SugarLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainHub(state: NirogState) {
    Scaffold(
        topBar = {
            NirogTopAppBar(
                cartCount = state.cartProducts.size,
                onProfileClick = {
                    state.currentScreen = "profile"
                },
                onNotificationClick = {
                    state.currentScreen = "notification_settings"
                },
                onCartClick = { state.currentScreen = "cart" }
            )
        },
        bottomBar = {
            NirogBottomNavigationBar(
                activeTab = state.activeTab,
                onTabSelect = { state.activeTab = it }
            )
        },
        containerColor = Color(0xFFF8F6EF)
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

@Composable
fun OnboardingTourOverlay(state: NirogState) {
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
fun NirogTopAppBar(cartCount: Int, onProfileClick: () -> Unit, onNotificationClick: () -> Unit, onCartClick: () -> Unit) {
    Surface(
        color = Color(0xFFF1FDEE),
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Editorial user profile picture
                AsyncImage(
                    model = "https://lh3.googleusercontent.com/aida-public/AB6AXuD3SJTqwkd-IfhxbEBzxNHWNxnVmDGPPUV_eBVRPcKL488OzQWI0EArbUTY34o9XZElJKSuu28NGvHB2gvbbD82l0RqBijwjuHs0SeTSRdEjfaOCtnuJVObWMcA3_HSeD9iU91Zlxi_5I329o4W_HuwE1Ia2E479hy2lMzsJM-0unvdqMva4fmRRqWxb7qxTmNgRowhNMjxHpz12M6X9YPNgruOUp_NxMAnxaOfRmm4sBwgv3XbRq5E3zXM-ueLutxa-OmXwW8EI8w",
                    contentDescription = "User profile photo",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color(0xFFC3C8C0), CircleShape)
                        .clickable { onProfileClick() },
                    contentScale = ContentScale.Crop
                )
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
                if (cartCount > 0) {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clickable(onClick = onCartClick)
                            .background(Color(0xFFBFEE95), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$cartCount items",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B3221)
                        )
                    }
                }

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
                .padding(vertical = 12.dp, horizontal = 8.dp),
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Welcome and Pitta Chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Good morning, Priyanshu",
                    fontSize = 24.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B3221)
                )
                Text(
                    text = "Thursday, 18 June",
                    fontSize = 14.sp,
                    color = Color(0xFF434842),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Pitta cycle indicator
            Box(
                modifier = Modifier
                    .background(Color(0xFFE0ECDD), RoundedCornerShape(16.dp))
                    .border(width = 0.5.dp, color = Color(0xFFC3C8C0), shape = RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.WbSunny,
                        contentDescription = "Pitta Cycle",
                        tint = Color(0xFF426820),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pitta Cycle",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF141E15)
                    )
                }
            }
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

                Button(
                    onClick = {
                        if (isWalkTaskDone) {
                            state.dailyRitualsCompleted.remove("Walk")
                            state.stepsLogged -= 1500
                        } else {
                            state.dailyRitualsCompleted.add("Walk")
                            state.stepsLogged += 1500
                        }
                        state.repository.addHealthLog("checklistLogs", mapOf(
                            "taskId" to "daily_post_dinner_walk",
                            "title" to "Walk 15 minutes after dinner",
                            "status" to if (isWalkTaskDone) "pending" else "done",
                            "date" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                            "completedAt" to if (isWalkTaskDone) null else com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )) { result -> if (result is in.nirogbhumi.app.data.CloudResult.Failure) state.cloudMessage = result.message }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = if (isWalkTaskDone) "Log Completed" else "Mark Complete",
                        color = Color(0xFF314936),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Quick Vitals Header
        Text(
            text = "Today's Vitals",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B3221)
        )

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
                    value = "${state.fastingSugarValue}",
                    unit = "mg/dL",
                    annotation = "+2 yesterday",
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
                    value = "124/82",
                    unit = "",
                    annotation = "Optimal",
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
                    title = "Sleep Quality",
                    value = "${state.sleepHours}h ${state.sleepMinutes}m",
                    unit = "",
                    annotation = "Avg duration",
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
                    value = String.format("%,d", state.stepsLogged),
                    unit = "steps",
                    annotation = "Daily total",
                    onClick = { state.currentScreen = "walking_overview" }
                )
            }
        }

        // Weekly preview card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { state.currentScreen = "weekly_report" }
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

                // Custom Canvas Drawing of Weekly Rhythm Barchart
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                ) {
                    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    // simulated step rates out of 100% capacity
                    val percentageValues = listOf(0.6f, 0.8f, 0.4f, 0.7f, 0.1f, 0.1f, 0.1f)
                    val spacing = size.width / 7
                    val barWidth = 14.dp.toPx()

                    days.forEachIndexed { index, name ->
                        val x = index * spacing + (spacing / 2) - (barWidth / 2)
                        val totalHeight = size.height - 30.dp.toPx()
                        val barHeight = totalHeight * percentageValues[index]
                        val y = totalHeight - barHeight

                        // Background pillar track
                        drawRoundRect(
                            color = Color(0xFFE5F1E2),
                            topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                            size = androidx.compose.ui.geometry.Size(barWidth, totalHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )

                        // If it is Thursday (today), highlight it in deep green, others in average green
                        val isToday = index == 3
                        val barColor = if (isToday) Color(0xFF1B3221) else Color(0xFF426820).copy(alpha = 0.5f)

                        drawRoundRect(
                            color = barColor,
                            topLeft = androidx.compose.ui.geometry.Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }

                // X-Axis text names
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    days.forEachIndexed { i, d ->
                        Text(
                            text = d,
                            fontSize = 11.sp,
                            fontWeight = if (i == 3) FontWeight.Bold else FontWeight.Medium,
                            color = if (i == 3) Color(0xFF1B3221) else Color(0xFF737972),
                            modifier = Modifier.width(36.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
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
                text = "Monitor your daily vitals and lifestyle patterns.",
                fontSize = 15.sp,
                color = Color(0xFF434842),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Bento Grid Modules of Tracks
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                TrackModuleBox(
                    icon = Icons.Filled.Bloodtype,
                    iconBg = Color(0xFFFFDAD6),
                    iconTint = Color(0xFFBA1A1A),
                    title = "Blood Sugar",
                    measuredValue = "105",
                    labelSuffix = "mg/dL",
                    onClick = { state.currentScreen = "sugar_detail" }
                )
            }
            Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                TrackModuleBox(
                    icon = Icons.Filled.Favorite,
                    iconBg = Color(0xFFCDEAD0),
                    iconTint = Color(0xFF1B3221),
                    title = "BP",
                    measuredValue = "120/80",
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
                    measuredValue = "7h 20m",
                    labelSuffix = "",
                    onClick = { state.currentScreen = "sleep_overview" }
                )
            }
            Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                TrackModuleBox(
                    icon = Icons.Filled.DirectionsWalk,
                    iconBg = Color(0xFFBFEE95).copy(alpha = 0.5f),
                    iconTint = Color(0xFF426820),
                    title = "Walking",
                    measuredValue = String.format("%,d", state.stepsLogged),
                    labelSuffix = "",
                    onClick = { state.currentScreen = "walking_overview" }
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                // Interactive modular water tracking directly inside Hub!
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.0f)
                        .clickable { state.currentScreen = "water_overview" }
                        .border(width = 0.5.dp, color = Color(0xFFC3C8C0), roundedCorner = 24),
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp).background(Color(0xFFCDEAD0), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.WaterDrop, "Water", tint = Color(0xFF1B3221), modifier = Modifier.size(18.dp))
                            }
                            // Quick Increment button
                            IconButton(
                                onClick = { state.currentScreen = "quick_water" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.AddCircle, contentDescription = "Add Water", tint = Color(0xFF1B3221))
                            }
                        }

                        Column {
                            Text("Water", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF434842))
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("${1.5 + (0.25 * state.waterGlasses)}", fontSize = 24.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("L", fontSize = 12.sp, color = Color(0xFF737972))
                            }
                        }
                    }
                }
            }
            Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                TrackModuleBox(
                    icon = Icons.Filled.Restaurant,
                    iconBg = Color(0xFFFFD9DE),
                    iconTint = Color(0xFF43242A),
                    title = "Food Impact",
                    measuredValue = "Stable",
                    labelSuffix = "",
                    onClick = { state.currentScreen = "food_journal" }
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                TrackModuleBox(
                    icon = Icons.Filled.Medication,
                    iconBg = Color(0xFFFFD9DE),
                    iconTint = Color(0xFF43242A),
                    title = "Medication",
                    measuredValue = "Taken",
                    labelSuffix = "",
                    onClick = { state.currentScreen = "medication_overview" }
                )
            }
            Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                TrackModuleBox(
                    icon = Icons.Filled.Science,
                    iconBg = Color(0xFFE5F1E2),
                    iconTint = Color(0xFF1B3221),
                    title = "Lab Reports",
                    measuredValue = "New",
                    labelSuffix = "",
                    onClick = { state.currentScreen = "lab_reports" }
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
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
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.0f)
            .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.4f), roundedCorner = 24)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
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
    var showSugarStory by remember { mutableStateOf(false) }

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
                text = "12-18 Jun",
                fontSize = 13.sp,
                color = Color(0xFF737972)
            )
        }

        Text(
            text = "Your pattern is becoming clearer.",
            fontSize = 16.sp,
            color = Color(0xFF4B6450),
            fontWeight = FontWeight.Medium
        )

        // Bento Grid Metrics items
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { state.currentScreen = "weekly_report" }
                .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.4f), shape = RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WaterDrop, "Sugar", tint = Color(0xFF43242A), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Avg Fasting Sugar", fontWeight = FontWeight.SemiBold, color = Color(0xFF434842))
                    }
                    IconButton(onClick = { state.currentScreen = "trends_30" }) { Icon(Icons.Filled.MoreHoriz, "Options") }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("108", fontSize = 28.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("mg/dL", fontSize = 12.sp, color = Color(0xFF737972))
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xFFE5F1E2), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.TrendingDown, "Improving", tint = Color(0xFF426820), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Improving", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                        }
                    }
                }
            }
        }

        // Avg BP
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
                    Text("Avg Blood Pressure", fontWeight = FontWeight.SemiBold, color = Color(0xFF434842))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("118/78", fontSize = 28.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("mmHg", fontSize = 12.sp, color = Color(0xFF737972))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                        Icon(Icons.Filled.TrendingFlat, "Stable", tint = Color(0xFF737972), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stable", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF737972))
                    }
                }
            }
        }

        // Row for Sleep and Walk averages
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
                        Text("6h 15m", fontSize = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, "Needs attention", tint = Color(0xFFBA1A1A), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Needs attention", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFBA1A1A))
                        }
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
                        Text("7,240", fontSize = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.TrendingUp, "Improving", tint = Color(0xFF1B3221), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Improving", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                        }
                    }
                }
            }
        }

        // Ayurvedic recommendation box
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
                    Text("AYURVEDIC INSIGHT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Your sleep duration was lower on several days. Try a consistent wind-down time this week. Ask your doctor before adding supplements or changing treatment.",
                    fontSize = 13.sp,
                    color = Color(0xFF141E15),
                    lineHeight = 18.sp
                )
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

        // Expanded clickable Insight Sugar Story Block
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE5F1E2), RoundedCornerShape(16.dp))
                .clickable { state.currentScreen = "insight_detail" }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sleep correlates to Sugar peaks",
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B3221)
                    )
                    Text(
                        text = "We noticed patterns connecting your rest quality to morning sugar level ranges. Select to read details.",
                        fontSize = 12.sp,
                        color = Color(0xFF434842),
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Icon(Icons.Filled.ChevronRight, "View Insight", tint = Color(0xFF1B3221))
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
                text = "Care",
                fontSize = 32.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B3221)
            )
            Text(
                text = "Connect with your expert team and care programs.",
                fontSize = 15.sp,
                color = Color(0xFF434842),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Welcome / Consultation CTA
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { state.currentScreen = "consultation_types" }
                .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.3f), shape = RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEEE8DC)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Your Care Team",
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B3221)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Schedule video consultations or follow-ups with qualified experts and metabolic coaches.",
                    fontSize = 14.sp,
                    color = Color(0xFF434842),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        state.consultStep = 1
                        state.currentScreen = "consult_stepper"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarMonth, "Book Class", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Book Consultation", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Bento care options
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { state.currentScreen = "active_journey" }
                        .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.35f), shape = RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Box(
                            modifier = Modifier.size(36.dp).background(Color(0xFFEBF7E8), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Assignment, "Program", tint = Color(0xFF1B3221), modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("My Program", fontSize = 16.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF141E15))
                        Text("Lifestyle care plan", fontSize = 12.sp, color = Color(0xFF737972))
                    }
                }
            }
            Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { state.currentScreen = "expert_notes" }
                        .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.35f), shape = RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Box(
                            modifier = Modifier.size(36.dp).background(Color(0xFFEBF7E8), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Description, "Notes", tint = Color(0xFF1B3221), modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Expert Notes", fontSize = 16.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF141E15))
                        Text("Summaries & reports", fontSize = 12.sp, color = Color(0xFF737972))
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { state.currentScreen = "yoga_plan" }
                        .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.35f), shape = RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Outlined.SelfImprovement, "Yoga", tint = Color(0xFF426820), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Yoga Plan", fontSize = 16.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF141E15))
                        Text("Metabolic posture loops", fontSize = 12.sp, color = Color(0xFF737972))
                    }
                }
            }
            Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { state.currentScreen = "naturopathy_plan" }
                        .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.35f), shape = RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Outlined.Eco, "Naturopathy", tint = Color(0xFF426820), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Naturopathy", fontSize = 16.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF141E15))
                        Text("Supportive routines", fontSize = 12.sp, color = Color(0xFF737972))
                    }
                }
            }
        }

        // Scheduled check detail
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { state.currentScreen = "consultation_detail" }
                .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.3f), shape = RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(44.dp).background(Color(0xFFEBF7E8), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.EventAvailable, "Upcoming", tint = Color(0xFF1B3221))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Upcoming Follow-ups", fontWeight = FontWeight.Bold, color = Color(0xFF141E15), fontSize = 16.sp)
                        Text(
                            text = if (state.userConcernText.isNotEmpty()) "Next: Dr. Sharma, ${state.selectedConsultDate} Nov" else "Next: Dr. Sharma, Oct 12",
                            fontSize = 12.sp,
                            color = Color(0xFF737972)
                        )
                    }
                }
                Icon(Icons.Filled.ChevronRight, "Go", tint = Color(0xFF737972))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// TAB 5: Learn Section & Store
@Composable
fun LearnTab(state: NirogState) {
    val storeProducts = listOf(
        StoreProduct(1, "Metabolic Health Kit", "Practical program support tools", "₹2,499", "https://lh3.googleusercontent.com/aida-public/AB6AXuAoO2CNikwTlslSE5tcf8ZZH0nlWC50VF5es0zN87-MW1SmCiM8KfOffKi_5IGARyQozjibn5VMupjhsi4f8A7bCsL80qJFP61a2xfmfFf9rWanenexPvohOBo0mjTEtrsp0xyHcgjBXP3DQzFeV6PoIUmCNMh9EFlPzhmecxaZ_PILJQJXEBYlOlQ3v7lOyQURl5JYoJBPPCRWRp38g1zXXdbNBUy4hpR6IChVg2kmjFpr3iMGkk6dJnfJEFo8rURhQZVc6iAceBw"),
        StoreProduct(2, "Jal Neti Pot", "Ceramic sinus cleansing tool", "₹450", "https://lh3.googleusercontent.com/aida-public/AB6AXuDvaGRrpKr1PZz5fErBo90b5adVv18mhAphPhJSxqUeVkICr8EtPy7ihi1jvmIAvQzxpovja0bZGharwGHm7qOogAid_rgqT1VgbZPWM1K_8vnqC6JRO9KuSbm1ug27dD5vvj7F8e2fUlLRVCUYKko7-vEt7oJg3lbfOs_d14K7DDcKj2hwTIZ2OmRs954u4Jpj4pn_qsDRRQgsrSYJ_cYv07YXfVmiplTUnxGD91TmG1QJ28kLXS8YhsU3cOPgUBnsJr_IKshREGU"),
        StoreProduct(3, "Acupressure Tool Set", "Handcrafted neem wood piece", "₹650", "https://lh3.googleusercontent.com/aida-public/AB6AXuAFMUfqnLWtrp62-4Gz77MCAHTycvtuLRku6m72kTEyRec7efonrRPvyDcCJwLRah2bJa16O_nYJsoehgNGn7Xy7CGRRjc1OM6eilWwWIgaZBNaXcsRMh16smFSfm_oYvZho3cSR9rhSXYPDJl9sBKKpdds2A9PD0f6oiQhMSzdHewy_He8HeWVaOQHKG5Lo_zZOxoBrXsfS5v-QbtnSf7S1KBtfPsvbLVDtX6A4KfaC2sTOOAwAhCvIV5QqoseKw1TWx4tEm6zc14"),
        StoreProduct(4, "Vijaysar Tumbler", "Overnight metabolic water infusion", "₹890", "https://lh3.googleusercontent.com/aida-public/AB6AXuAJ5Crvk37pXEnB7GoEifO0dMTADM8sOK91xYVP5MahOfkUONjvVTOu993_XstoqUgM-4qKe3c3WjT_VIvw-sGnK6wkod1CIRD25Ko4B3G1PKaNOiMmLi5BV5We4j482Ims5_0mJykYTD2td9DTkRlm3MxPXxVQo4V_vCwq4ZXic93q5arYVGzNkxtAcYp2CtU2psvumplf3P97fzbPbrxguDC5FOgGplwVzVdUYgRNWxNSRl_tR7PKiSwycGPYABXIl59w5VHO6PA")
    )

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

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.35f), shape = RoundedCornerShape(24.dp))
                .clickable { state.selectedArticleTitle = "Why walking after meals helps blood sugar"; state.currentScreen = "article_detail" },
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column {
                AsyncImage(
                    model = "https://lh3.googleusercontent.com/aida-public/AB6AXuAiKstrL5Z2X50MVjICfXPYiCenL_XiAhjJ6m_rtONb9ESjHiQGFBNYN9JCxsLd-OcRQZ3DCjpkQoEg1NC7vuhkTzmgNLvb0eqimy5C--zUoclsNLq7jYsbYg-IZHevbRt2G0KAFh1X257Yx9v4knCQF8BdqheXPg4i7VuMw1gLXRVJkFh00V5Fe1tVJ_0NGqhtJRVf6SZDiQip52J0XeNwOglN_aJLfSODjG6oNmBKABF8jkZK3uQJaOj74CMZHiTa6S4ksNxqPWQ",
                    contentDescription = "Walking path",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentScale = ContentScale.Crop
                )

                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Why walking after meals helps blood sugar",
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B3221)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Discover the simple practice of Shatapavali (100 steps) and how it profoundly impacts your post-prandial glucose levels.",
                        fontSize = 13.sp,
                        color = Color(0xFF434842),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("4 min read", fontSize = 11.sp, color = Color(0xFF737972))
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

        // Store Products Showcase
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Nirog Bhumi Store",
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B3221)
                )
                Text("Tools for your wellness journey", fontSize = 12.sp, color = Color(0xFF737972))
            }

            TextButton(onClick = { state.currentScreen = "products" }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("View All", color = Color(0xFF1B3221), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Filled.ChevronRight, "More", modifier = Modifier.size(16.dp), tint = Color(0xFF1B3221))
                }
            }
        }

        // Horizontal scrolling products
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            storeProducts.forEach { product ->
                StoreItemCard(
                    product = product,
                    isAdded = state.cartProducts.contains(product.id),
                    onToggleCart = {
                        if (state.cartProducts.contains(product.id)) {
                            state.cartProducts.remove(product.id)
                        } else {
                            state.cartProducts.add(product.id)
                        }
                    },
                    onOpen = { state.currentScreen = "product_detail" }
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }

    // Article reading modal
    if (state.selectedArticleTitle != null) {
        AlertDialog(
            onDismissRequest = { state.selectedArticleTitle = null },
            confirmButton = {
                Button(
                    onClick = { state.selectedArticleTitle = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936))
                ) {
                    Text("Close", color = Color.White)
                }
            },
            title = {
                Text(
                    text = state.selectedArticleTitle!!,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF1B3221)
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "The ancient texts of Ayurveda advocate Shatapavali—literally 'a hundred steps' taken immediately after taking a meal. In modern endocrinological fields, post-meal muscular activity directs glucose transporters (GLUT4) to migrate to the cell surface independent of insulin. Taking a simple, slow, 10–15 minute walk within an hour of taking dinner significantly blunts glycemic excursions by up to 15-22%. Walking helps draw circulating glucose into primary skeletal muscle matrices, thereby sustaining metabolic equilibrium.",
                        fontSize = 14.sp,
                        color = Color(0xFF434842),
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Tip: Refrain from brisk running, which diverts circulation away from digestive networks. Maintain a gentle, meditative, natural walking speed.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF426820),
                        lineHeight = 18.sp
                    )
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun LearnCategoryCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, iconBg: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
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

@Composable
fun StoreItemCard(product: StoreProduct, isAdded: Boolean, onToggleCart: () -> Unit, onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .width(180.dp)
            .clickable(onClick = onOpen)
            .border(width = 0.5.dp, color = Color(0xFFC3C8C0).copy(alpha = 0.35f), shape = RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            AsyncImage(
                model = product.imageDescription,
                contentDescription = product.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = product.name,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B3221),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = product.subtitle,
                fontSize = 11.sp,
                color = Color(0xFF737972),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = product.price,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1B3221),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )

                Button(
                    onClick = onToggleCart,
                    modifier = Modifier.height(30.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAdded) Color(0xFFBFEE95) else Color(0xFF314936)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Text(
                        text = if (isAdded) "Added" else "Add",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAdded) Color(0xFF1B3221) else Color.White
                    )
                }
            }
        }
    }
}
