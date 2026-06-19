package com.nirogbhumi.app.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirogbhumi.app.ui.NirogState

// Re-map tokens inside dashboards
private val DashDeepGreen = Color(0xFF314936)
val ActiveGreenText = Color(0xFF1B3221)
val BackgroundPaperLight = Color(0xFFF8F6EF)
val BorderLineAccent = Color(0xFFD8D0C0)
val SageAccent = Color(0xFF82AD5C)

// ADMIN DASHBOARD MAIN COMPOSABLE
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminWebDashboard(state: NirogState) {
    var adminActiveMenu by remember { mutableStateOf("Overview") }
    var selectedManageUser by remember { mutableStateOf<String?>(null) }
    var userSearchText by remember { mutableStateOf("") }

    // Admin login security simulation
    var isAdminLoggedIn by remember { mutableStateOf(false) }
    var adminEmail by remember { mutableStateOf("admin@nirogbhumi.org") }
    var adminPassword by remember { mutableStateOf("••••••••") }

    if (!isAdminLoggedIn) {
        // ADMIN LOGIN SCREEN
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundPaperLight),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .width(420.dp)
                    .border(1.dp, BorderLineAccent, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(56.dp).background(BackgroundPaperLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Filled.Lock, contentDescription = null, tint = DashDeepGreen)
                    }

                    Text(
                        text = "Nirog Bhumi Admin Port",
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = ActiveGreenText
                    )

                    Text(
                        text = "Restricted cloud database administration endpoint.",
                        fontSize = 12.sp,
                        color = ActiveGreenText.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )

                    OutlinedTextField(
                        value = adminEmail,
                        onValueChange = { adminEmail = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email identifier") },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DashDeepGreen)
                    )

                    OutlinedTextField(
                        value = adminPassword,
                        onValueChange = { adminPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Security key") },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DashDeepGreen)
                    )

                    Button(
                        onClick = { isAdminLoggedIn = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DashDeepGreen),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Verify & Authenticate Superuser", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    } else {
        // FULL SCREEN WEB ADMIN BOARD AT 1440x1024 simulation
        Row(modifier = Modifier.fillMaxSize().background(Color(0xFFFBFBF9))) {

            // SIDEBAR
            Column(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF1B2C1E))
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        modifier = Modifier.padding(bottom = 24.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Filled.Spa, contentDescription = null, tint = SageAccent, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Nirog Portal", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, fontFamily = FontFamily.Serif)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.background(SageAccent, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                            Text("ADMIN", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Divider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(bottom = 16.dp))

                    val adminMenuItems = listOf("Overview", "Users", "Queue", "Consults", "Checklists", "Store", "Content", "Exports", "Roles")
                    adminMenuItems.forEach { m ->
                        val isSel = adminActiveMenu == m
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(if (isSel) DashDeepGreen else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable {
                                    adminActiveMenu = m
                                    selectedManageUser = null
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val itemIcon = when (m) {
                                    "Overview" -> Icons.Filled.Dashboard
                                    "Users" -> Icons.Filled.People
                                    "Queue" -> Icons.Filled.NotificationImportant
                                    "Consults" -> Icons.Filled.CalendarMonth
                                    "Checklists" -> Icons.Filled.FactCheck
                                    "Store" -> Icons.Filled.Storefront
                                    "Content" -> Icons.Filled.MenuBook
                                    "Exports" -> Icons.Filled.CloudDownload
                                    else -> Icons.Filled.AdminPanelSettings
                                }
                                Icon(imageVector = itemIcon, contentDescription = m, tint = if (isSel) Color.White else Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(m, color = if (isSel) Color.White else Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }

                // Superuser info
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(36.dp).background(SageAccent, CircleShape))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Founder View", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Revoke access", color = SageAccent, fontSize = 11.sp, modifier = Modifier.clickable { isAdminLoggedIn = false })
                    }
                }
            }

            // INNER WEB BODY
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {

                // Web Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(Color.White)
                        .border(1.dp, Color(0xFFF1EDE6))
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "System Scope: ${adminActiveMenu.uppercase()}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ActiveGreenText,
                        fontFamily = FontFamily.Serif
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = userSearchText,
                            onValueChange = { userSearchText = it },
                            placeholder = { Text("Search system logs...", color = Color.Gray) },
                            modifier = Modifier.width(280.dp).height(44.dp),
                            shape = RoundedCornerShape(20.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DashDeepGreen, unfocusedContainerColor = BackgroundPaperLight)
                        )

                        Box(
                            modifier = Modifier
                                .background(Color(0xFFEBF7E8), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Db Status: 49 Nodes Synced", color = DashDeepGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Render respective tab contents
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    when (adminActiveMenu) {
                        "Overview" -> AdminOverviewGrid(state)
                        "Users" -> {
                            if (selectedManageUser == null) {
                                AdminUserManagementTable(search = userSearchText) { selectedManageUser = it }
                            } else {
                                AdminUserDetailPanel(userId = selectedManageUser!!, state = state) { selectedManageUser = null }
                            }
                        }
                        "Queue" -> AdminExpertReviewQueue(state)
                        "Consults" -> AdminConsultationManagement(state)
                        "Checklists" -> AdminProgramChecklistTemplateRunner(state)
                        "Store" -> AdminStoreProductInventory()
                        "Content" -> AdminContentArticleEditor()
                        "Exports" -> AdminReportsAuditExportLog()
                        "Roles" -> AdminSettingsRolePermissions()
                    }
                }
            }
        }
    }
}

// ADMIN SUB PARTS
@Composable
fun AdminOverviewGrid(state: NirogState) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {

        // Row 1: Web Metrics cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val stats = listOf(
                Quadruple("Total Enrolled Users", "14,842", "● 352 signed up today", Color(0xFF1B3221), Color(0xFFE5F1E2)),
                Quadruple("Active Reversal Plans", "4,210", "▲ 85% compliance rate", Color(0xFF426820), Color(0xFFF1FDEE)),
                Quadruple("Total Consultations", "843 booked", "● 12 pending slots", Color(0xFF4B6450), Color(0xFFEEE8DC)),
                Quadruple("System Flagged Alerts", "4 critical", "▼ Requires doctor review!", Color(0xFFBA1A1A), Color(0xFFFFDAD6))
            )
            stats.forEach { (lbl, num, note, color, bg) ->
                Card(
                    modifier = Modifier.weight(1f).border(1.dp, BorderLineAccent.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = bg),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(lbl, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(num, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = color)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(note, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Daily Message Publisher
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderLineAccent, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Daily Message & Push Notification Manager", fontWeight = FontWeight.Bold, color = ActiveGreenText, fontSize = 16.sp, fontFamily = FontFamily.Serif)
                Text("Compose a message to transmit directly to the general wellness user check-in dashboards today at 5:00 AM IST.", fontSize = 13.sp, color = Color.Gray)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = "Walk 15 minutes after dinner. Post-meal walking helps GLUT4 migration to skeleton matrices.",
                        onValueChange = {},
                        modifier = Modifier.weight(4f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DashDeepGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Broadcast NOW", color = Color.White)
                    }
                }
            }
        }

        // Simulating charts
        Text("Weekly Traffic Activity Logs", fontWeight = FontWeight.Bold, color = ActiveGreenText, fontSize = 18.sp, fontFamily = FontFamily.Serif)
        Card(
            modifier = Modifier.fillMaxWidth().height(260.dp).border(1.dp, BorderLineAccent, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Daily Fasting Checkin Entries (Total 32,842 checks tracked)", fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val chartBars = listOf(30, 45, 62, 85, 98, 120, 140, 110, 95)
                        chartBars.forEach { ht ->
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(ht.dp)
                                    .background(DashDeepGreen, RoundedCornerShape(4.dp))
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Systematic peak activity aligns during morning Vata baseline hours (6 AM - 8 AM)", fontSize = 12.sp, color = DashDeepGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun AdminUserManagementTable(search: String, onUserSelect: (String) -> Unit) {
    val usersListData = listOf(
        Quintuple("Priyanshu Agrawal", "98765 43210", "Type 2 Diabetic", "6-Month Reversal Prog", "Critical sugar alert!"),
        Quintuple("Archana Sharma", "91234 56789", "Pre-diabetic", "Yoga Reversal", "None"),
        Quintuple("Vijay Kumar", "88776 65544", "None", "General Vitals", "None"),
        Quintuple("Rakesh Gupta", "77665 54433", "Type 1 Diabetic", "6-Month Reversal Prog", "High BP alert!")
    ).filter { it.first.contains(search, ignoreCase = true) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Enrolled User Directory", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ActiveGreenText, fontFamily = FontFamily.Serif)

        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderLineAccent, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column {
                // Header row
                Row(
                    modifier = Modifier.background(BackgroundPaperLight).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("NAME ID", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, color = ActiveGreenText, fontSize = 13.sp)
                    Text("PHONE", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, color = ActiveGreenText, fontSize = 13.sp)
                    Text("METABOLIC STATUS", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, color = ActiveGreenText, fontSize = 13.sp)
                    Text("PROGRAM CHANNEL", modifier = Modifier.weight(2.5f), fontWeight = FontWeight.Bold, color = ActiveGreenText, fontSize = 13.sp)
                    Text("SYSTEM FLAG", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, color = ActiveGreenText, fontSize = 13.sp)
                }

                Divider(color = BorderLineAccent.copy(alpha = 0.5f))

                usersListData.forEach { (n, ph, db, plan, flag) ->
                    Row(
                        modifier = Modifier
                            .clickable { onUserSelect(n) }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(n, modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, color = ActiveGreenText, fontSize = 14.sp)
                        Text("+91 $ph", modifier = Modifier.weight(2f), color = Color.Gray, fontSize = 13.sp)
                        Text(db, modifier = Modifier.weight(2f), color = ActiveGreenText, fontSize = 13.sp)
                        Text(plan, modifier = Modifier.weight(2.5f), color = DashDeepGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        Box(modifier = Modifier.weight(2f)) {
                            if (flag != "None") {
                                Box(modifier = Modifier.background(Color(0xFFFFDAD6), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text(flag, color = Color(0xFFBA1A1A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Box(modifier = Modifier.background(Color(0xFFE5F1E2), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text("Consistent", color = DashDeepGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Divider(color = BorderLineAccent.copy(alpha = 0.2f))
                }
            }
        }
    }
}

@Composable
fun AdminUserDetailPanel(userId: String, state: NirogState, onBack: () -> Unit) {
    var doctorAssignedNoteInput by remember { mutableStateOf("") }
    val assignedNotes = remember { mutableStateListOf("Instructed user to prioritize walking after dinner to flatten evening post-meal glucose spikes.") }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = SoftClay),
                modifier = Modifier.height(36.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.ChevronLeft, contentDescription = "Back", tint = ActiveGreenText)
                    Text("Back to User Management directory", color = ActiveGreenText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("Admin profile auditor active", color = SageAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        Text("$userId Metabolic Profile", fontSize = 28.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = ActiveGreenText)

        // Cards layout
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Biometrics details left
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.weight(1.5f).border(1.dp, BorderLineAccent, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Biometric Details", fontWeight = FontWeight.Bold, color = ActiveGreenText, fontSize = 16.sp, fontFamily = FontFamily.Serif)
                    Divider(color = BorderLineAccent.copy(alpha = 0.3f))
                    RowDetailLabel("Assigned unique ID", "USR_JAIPUR_8204_A")
                    RowDetailLabel("Current tracking goal", "Manage blood sugar levels and reverse insulin resistance")
                    RowDetailLabel("Registered Age / Weight", "28 years old • 72 kg")
                    RowDetailLabel("Coaching Language preference", "English")
                    RowDetailLabel("Diabetes scale category", "Type 2 diabetes mellitus")
                    RowDetailLabel("Blood Pressure category", "Pre-hypertensive state / Normal range")
                }
            }

            // Expert annotations right
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.weight(1f).border(1.dp, BorderLineAccent, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Clinical Annotations Log (Clinical Handoff)", fontWeight = FontWeight.Bold, color = ActiveGreenText, fontSize = 16.sp, fontFamily = FontFamily.Serif)
                    Divider(color = BorderLineAccent.copy(alpha = 0.3f))

                    assignedNotes.forEach { nt ->
                        Box(modifier = Modifier.background(BackgroundPaperLight, RoundedCornerShape(8.dp)).padding(10.dp)) {
                            Text(nt, fontSize = 12.sp, color = ActiveGreenText)
                        }
                    }

                    OutlinedTextField(
                        value = doctorAssignedNoteInput,
                        onValueChange = { doctorAssignedNoteInput = it },
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                        placeholder = { Text("Append secure medical / lifestyle note...", color = Color.Gray) }
                    )

                    Button(
                        onClick = {
                            if (doctorAssignedNoteInput.isNotEmpty()) {
                                assignedNotes.add(doctorAssignedNoteInput)
                                doctorAssignedNoteInput = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = DashDeepGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add secured audit note", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminExpertReviewQueue(state: NirogState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Expert Review Queue (Live Tasks)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ActiveGreenText, fontFamily = FontFamily.Serif)
        Text("The following users have recorded critical spikes or uploaded certified lab reports that require expert signature and note logs.", fontSize = 13.sp, color = Color.Gray)

        val queueTasks = listOf(
            Triple("Priyanshu Agrawal", "Fasting Sugar spike: 136 mg/dL logged", "Spike recorded during Pitta Cycle"),
            Triple("Rakesh Gupta", "BP spike: 142/95 mmHg logged", "Action post walk recommended"),
            Triple("VJ Kumar", "Lab report uploaded (HbA1c: 7.2)", "Report audited by Metropolis Lab")
        )

        queueTasks.forEach { (name, flag, note) ->
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, BorderLineAccent, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(name, fontWeight = FontWeight.Bold, color = ActiveGreenText, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.background(Color(0xFFFFDAD6), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text(flag, color = Color(0xFFBA1A1A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(note, fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    Button(
                        onClick = {},
                        colors = ButtonDefaults.buttonColors(containerColor = DashDeepGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Resolve task", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminConsultationManagement(state: NirogState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Consultation Bookings scheduler", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ActiveGreenText, fontFamily = FontFamily.Serif)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(
                modifier = Modifier.weight(1f).border(1.dp, BorderLineAccent, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ACTIVE CLINIC SLOTS", fontWeight = FontWeight.Bold, color = ActiveGreenText)
                    Divider(color = BorderLineAccent.copy(alpha = 0.3f))
                    Text("Slot 1: Thu 18, 10:30 AM (Assigned to Dr. Sharma)", fontSize = 13.sp, color = Color.Gray)
                    Text("Slot 2: Thu 18, 2:15 PM (Assigned to Dr. Sharma)", fontSize = 13.sp, color = Color.Gray)
                    Text("Slot 3: Thu 18, 4:30 PM (Assigned to Coach R. Singh)", fontSize = 13.sp, color = Color.Gray)

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = DashDeepGreen)
                    ) {
                        Text("Establish new slot", color = Color.White)
                    }
                }
            }

            Card(
                modifier = Modifier.weight(1f).border(1.dp, BorderLineAccent, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("YOGA CLASSES SCHEDULER", fontWeight = FontWeight.Bold, color = ActiveGreenText)
                    Divider(color = BorderLineAccent.copy(alpha = 0.3f))
                    RowDetailLabel("Posture loop 1", "Mandukasana digestive activation series")
                    RowDetailLabel("Pranayama daily class", "Nadi shodhana stress blunter loop")
                }
            }
        }
    }
}

@Composable
fun AdminProgramChecklistTemplateRunner(state: NirogState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Checklist & Reversal Protocols Template Builder", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ActiveGreenText, fontFamily = FontFamily.Serif)

        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderLineAccent, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("6-Month Diabetes Reversal Checklist template", fontWeight = FontWeight.Bold, color = ActiveGreenText)
                Divider(color = BorderLineAccent.copy(alpha = 0.3f))
                Text("● Daily fasting blood sugar report logging (Required)", fontSize = 13.sp, color = Color.Gray)
                Text("● Mandukasana posture loop 15 minutes morning (Recommended)", fontSize = 13.sp, color = Color.Gray)
                Text("● Shatapavali walk (100 steps / 15 mins) post lunch (Strictly flat sugar peaks)", fontSize = 13.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun AdminStoreProductInventory() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Store Inventory & stock controller", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ActiveGreenText, fontFamily = FontFamily.Serif)

        val stockLevels = listOf(
            Triple("Diabetes Reversal Kit", "₹2,499", "10 units remaining (Low stock warning!)"),
            Triple("Jal Neti Pot", "₹450", "45 units ready"),
            Triple("Acupressure Neem Wood Set", "₹650", "32 units ready"),
            Triple("Vijaysar Tumbler", "₹890", "12 units remaining")
        )

        stockLevels.forEach { (n, pr, stk) ->
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, BorderLineAccent, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(n, fontWeight = FontWeight.Bold, color = ActiveGreenText)
                        Text(stk, fontSize = 12.sp, color = if (stk.contains("Low")) Color.Red else Color.Gray)
                    }
                    Text(pr, fontWeight = FontWeight.Bold, color = ActiveGreenText)
                }
            }
        }
    }
}

@Composable
fun AdminContentArticleEditor() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Editorial Content manager", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ActiveGreenText, fontFamily = FontFamily.Serif)

        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderLineAccent, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Drafting / Review Queue articles list", fontWeight = FontWeight.Bold, color = ActiveGreenText)
                Divider(color = BorderLineAccent.copy(alpha = 0.3f))
                Text("Draft 1: Ayurvedic element balances (Pitta/Kapha/Vata cycles) • Draft status", fontSize = 13.sp, color = Color.Gray)
                Text("Published: Why walking after meals blunts sugar peaks • Published: Yes", fontSize = 13.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun AdminReportsAuditExportLog() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Clinical database audit logs & exports", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ActiveGreenText, fontFamily = FontFamily.Serif)

        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderLineAccent, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Secure Audit Export log table", fontWeight = FontWeight.Bold, color = ActiveGreenText)
                Divider(color = BorderLineAccent.copy(alpha = 0.3f))
                Text("● Admin (FOUNDER) generated full CSV download of active type 2 diabetics list. Reason: Auditing seasonal Pitta charts compilation. Timestamp: June 18, 5:10 AM.", fontSize = 12.sp, color = Color.DarkGray)
                Text("● Admin (FOUNDER) generated secure JSON export of expert consult queue follow-ups list. Reason: Scheduling video slot links. Timestamp: June 18, 4:45 AM.", fontSize = 12.sp, color = Color.DarkGray)
            }
        }
    }
}

@Composable
fun AdminSettingsRolePermissions() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Permissions & administrative parameters roles", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ActiveGreenText, fontFamily = FontFamily.Serif)
        Text("Manage access tokens for doctors, coaches, inventory officers and content creators.", fontSize = 13.sp, color = Color.Gray)

        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderLineAccent, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RoleRowLabel("Founder Scope", "Absolute full authorization")
                RoleRowLabel("Certified Specialist Dr. Sharma", "Assigned patients medical profile view, review log editing")
                RoleRowLabel("Specialist Coach Singh", "Steps and lifestyle activity logs auditing, diet checklist template assignation")
            }
        }
    }
}

@Composable
fun RoleRowLabel(role: String, scope: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(role, fontWeight = FontWeight.Bold, color = ActiveGreenText, fontSize = 13.sp)
        Text(scope, color = Color.Gray, fontSize = 12.sp)
    }
}



// EXPERT DASHBOARD PORTAL
@Composable
fun ExpertWebDashboard(state: NirogState) {
    var selectedUserReviewName by remember { mutableStateOf<String?>("Priyanshu Agrawal") }
    var userInternalNote by remember { mutableStateOf("") }
    var userVisibleExpertNote by remember { mutableStateOf("") }

    val internalNotesList = remember { mutableStateListOf("Patient indicates morning waking feels heavy; might indicate late Pitta circle digestive congestion from dinner timing.") }
    val visibleNotesList = remember { mutableStateListOf("Great work on completing 12 checks this week. Make sure you walk post lunch too.") }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFFFAF9F6))) {

        // EXPERT COLUMN SIDEBAR
        Column(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight()
                .background(Color(0xFF27382B))
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    modifier = Modifier.padding(bottom = 24.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Filled.Spa, contentDescription = null, tint = SageAccent, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Nirog Care", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, fontFamily = FontFamily.Serif)
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.background(SageAccent, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Text("EXPERT", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(bottom = 16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DashDeepGreen, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.HealthAndSafety, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("My Assigned Users", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column {
                Text("Verified expert login active", color = SageAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Dr. Sharma (M.D. Ayurveda)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // EXPERT PANEL BODY
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(Color.White)
                    .border(1.dp, Color(0xFFF1EDE6))
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Clinical Dashboard Profile Auditor", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ActiveGreenText, fontFamily = FontFamily.Serif)
            }

            Row(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {

                // Assigned user lists table left
                Card(
                    modifier = Modifier.weight(1f).fillMaxHeight().border(1.dp, BorderLineAccent, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Assigned Users Queue", fontWeight = FontWeight.Bold, color = ActiveGreenText, fontSize = 15.sp, fontFamily = FontFamily.Serif)
                        Divider(color = BorderLineAccent.copy(alpha = 0.3f))

                        val myUsers = listOf("Priyanshu Agrawal", "Archana Sharma", "Vijay Kumar")
                        myUsers.forEach { usr ->
                            val activeVal = selectedUserReviewName == usr
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (activeVal) SoftClay else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { selectedUserReviewName = usr }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(usr, fontWeight = if (activeVal) FontWeight.Bold else FontWeight.Medium, color = ActiveGreenText)
                                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.Gray)
                            }
                        }
                    }
                }

                // Selected user clinical notes editor right
                if (selectedUserReviewName != null) {
                    Card(
                        modifier = Modifier.weight(2f).fillMaxHeight().border(1.dp, BorderLineAccent, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Text("Profile audit: ${selectedUserReviewName!!}", fontSize = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = ActiveGreenText)

                            // Visual parameters
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(modifier = Modifier.background(BackgroundPaperLight, RoundedCornerShape(8.dp)).padding(12.dp)) {
                                    Text("Avg Fasting sugar: ${state.fastingSugarValue} mg/dL", color = ActiveGreenText, fontSize = 13.sp)
                                }
                                Box(modifier = Modifier.background(BackgroundPaperLight, RoundedCornerShape(8.dp)).padding(12.dp)) {
                                    Text("Sleep log avg: ${state.sleepHours}h", color = ActiveGreenText, fontSize = 13.sp)
                                }
                            }

                            Divider(color = BorderLineAccent.copy(alpha = 0.3f))

                            // Tab 1: Internal notes (Invisible to patient)
                            Text("Internal annotations log (Invisible to patient)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Red)
                            internalNotesList.forEach { iNote ->
                                Box(modifier = Modifier.background(Color(0xFFFFF1F1), RoundedCornerShape(8.dp)).padding(12.dp)) {
                                    Text(iNote, fontSize = 12.sp, color = Color.Red)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = userInternalNote,
                                    onValueChange = { userInternalNote = it },
                                    modifier = Modifier.weight(3f),
                                    placeholder = { Text("Log clinical internal concern...", color = Color.Gray) }
                                )
                                Button(
                                    onClick = { if (userInternalNote.isNotEmpty()) { internalNotesList.add(userInternalNote); userInternalNote = "" } },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Save Internal", color = Color.White)
                                }
                            }

                            Divider(color = BorderLineAccent.copy(alpha = 0.3f))

                            // Tab 2: User visible expert message (Patient dashboard update!)
                            Text("User-visible dashboard message (Updates mobile Today console!)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DashDeepGreen)
                            visibleNotesList.forEach { vNote ->
                                Box(modifier = Modifier.background(Color(0xFFF1FDEE), RoundedCornerShape(8.dp)).padding(12.dp)) {
                                    Text(vNote, fontSize = 12.sp, color = DashDeepGreen)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = userVisibleExpertNote,
                                    onValueChange = { userVisibleExpertNote = it },
                                    modifier = Modifier.weight(3f),
                                    placeholder = { Text("Write dashboard guidance...", color = Color.Gray) }
                                )
                                Button(
                                    onClick = { if (userVisibleExpertNote.isNotEmpty()) { visibleNotesList.add(userVisibleExpertNote); userVisibleExpertNote = "" } },
                                    colors = ButtonDefaults.buttonColors(containerColor = DashDeepGreen),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Submit Guidance", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// STATE STATE SCREENS (Mobile simulation states)
@Composable
fun MobileEmptyStateView(onAction: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(BackgroundPaperLight).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(imageVector = Icons.Filled.Spa, contentDescription = "Lotus empty", tint = BorderLineAccent, modifier = Modifier.size(72.dp))
            Text("No readings documented yet.", fontSize = 20.sp, color = ActiveGreenText, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            Text("Document one fast glucose count today. We'll begin producing weekly trends immediately.", textAlign = TextAlign.Center, fontSize = 13.sp, color = Color.Gray)
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = DashDeepGreen)
            ) {
                Text("Log fast reading now (10s)", color = Color.White)
            }
        }
    }
}

@Composable
fun MobileErrorStateView(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(BackgroundPaperLight).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = Icons.Filled.ErrorOutline, contentDescription = "Error notification", tint = Color(0xFFBA1A1A), modifier = Modifier.size(54.dp))
            Text("Something did not load.", fontSize = 18.sp, color = ActiveGreenText, fontWeight = FontWeight.Bold)
            Text("Your documented vitals logs stay securely saved. Direct cloud reconnection retry advised.", textAlign = TextAlign.Center, fontSize = 12.sp, color = Color.Gray)
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = DashDeepGreen)
            ) {
                Text("Retry Database pull", color = Color.White)
            }
        }
    }
}

@Composable
fun MobileOfflineStateView(onProgress: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(BackgroundPaperLight).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = Icons.Filled.WifiOff, contentDescription = "Offline indicator", tint = Color.Gray, modifier = Modifier.size(48.dp))
            Text("You are currently offline.", fontSize = 18.sp, color = ActiveGreenText, fontWeight = FontWeight.Bold)
            Text("Manual vitals recorded while disconnected will automatically sync once your cellular signal restores.", textAlign = TextAlign.Center, fontSize = 12.sp, color = Color.Gray)
            Button(
                onClick = onProgress,
                colors = ButtonDefaults.buttonColors(containerColor = DashDeepGreen)
            ) {
                Text("Continue logging offline", color = Color.White)
            }
        }
    }
}

@Composable
fun MobileCriticalCautionStateView(value: Int, onConfirm: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(BackgroundPaperLight).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier.size(64.dp).background(Color(0xFFFFDAD6), CircleShape), contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFBA1A1A))
            }
            Text("Logged value needs attention (${value} mg/dL)", fontSize = 18.sp, color = ActiveGreenText, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            Text("If you currently feel unwell, experience dizziness, or readings sustain above 180, please initiate direct contact with your medical physician or dial emergency local clinics immediately.", textAlign = TextAlign.Center, fontSize = 13.sp, color = Color.Gray, lineHeight = 18.sp)

            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = DashDeepGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("I understand and acknowledge", color = Color.White)
            }

            OutlinedButton(
                onClick = {},
                border = BorderStroke(1.dp, Color(0xFFBA1A1A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Initiate call clinic emergency", color = Color(0xFFBA1A1A))
            }
        }
    }
}


// Helper labels
@Composable
fun RowDetailLabel(lbl: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(lbl, fontWeight = FontWeight.SemiBold, color = ActiveGreenText.copy(alpha = 0.5f), fontSize = 13.sp)
        Text(value, color = ActiveGreenText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

// Customized data containers
data class Quadruple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
