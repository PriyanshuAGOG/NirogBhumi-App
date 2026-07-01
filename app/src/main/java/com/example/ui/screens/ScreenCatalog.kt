package com.nirogbhumi.app.ui.screens

import android.Manifest
import android.app.Activity
import android.os.Build
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.health.connect.client.PermissionController
import com.nirogbhumi.app.health.HealthConnectManager
import kotlinx.coroutines.launch
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.data.CloudResult
import com.google.firebase.firestore.FieldValue
import com.nirogbhumi.app.payments.RazorpayPaymentLauncher
import com.nirogbhumi.app.reports.ReportShare

data class ScreenSpec(
    val id: Int,
    val route: String,
    val title: String,
    val section: String,
    val subtitle: String,
    val items: List<String>,
    val primaryAction: String
)

private fun s(id: Int, route: String, title: String, section: String, subtitle: String, items: String, action: String) =
    ScreenSpec(id, route, title, section, subtitle, items.split('|'), action)

/** Canonical inventory of in-app screens. Water, Medicine and Food Journal tracking, and the
 *  in-app Store, were removed by product decision - see ComingSoonScreen for the store's
 *  replacement and ScreenCatalogTest for the structural checks that replaced the old fixed count. */
val NirogScreens = listOf(
    s(1,"splash","Splash Screen","Onboarding","Your daily rhythm for better metabolic health.","Nirog Bhumi|Secure health companion","Continue"),
    s(2,"welcome","Welcome Intro","Onboarding","Track your health in 2 minutes a day.","Daily check-in|Weekly health report|Nirog Bhumi care","Continue"),
    s(3,"value_slides","Value Slides","Onboarding","Know your pattern. One useful action at a time.","Know your pattern|One action, not ten|Share better data with experts","Continue"),
    s(4,"login_mobile","Phone Login","Onboarding","Enter your mobile number to continue securely.","+91 mobile number|Terms and Privacy|Medical Disclaimer","Send OTP"),
    s(5,"login_otp","OTP Verification","Onboarding","Verify the six-digit code sent to your phone.","6-digit OTP|Resend timer|Change number","Verify"),
    s(6,"setup_profile","Basic Profile Setup","Onboarding","Tell us the basics.","Full name|Age and gender|Height and weight|City and language","Continue"),
    s(7,"selection_caregiver","Self or Caregiver Selection","Onboarding","Who are you tracking health for?","Myself|A family member","Continue"),
    s(8,"health_profile_setup","Health Profile Setup","Onboarding","A simple profile helps personalize your tracking.","Diabetes status|BP status|Medication|Doctor supervision","Continue"),
    s(9,"goal_selection","Main Goal Selection","Onboarding","What should Nirog Bhumi help with?","Control sugar|Improve lifestyle|Improve BP|Sleep better|Walk more|Join a program","Continue"),
    s(10,"consent","Consent Screen","Onboarding","Your health data stays protected.","Health data tracking|Expert review|Medical disclaimer|Optional research consent","Agree and continue"),
    s(11,"program_code_optional","Program Code Optional","Onboarding","Already part of a Nirog Bhumi program?","Program code|Diet plan|Yoga routines|Expert reviews","Unlock Program Mode"),
    s(12,"onboarding_complete","Onboarding Complete","Onboarding","You’re ready. Start with today’s two-minute check-in.","Profile complete|Consent saved|Reminders ready","Go to Today"),
    s(13,"today_empty","Today Dashboard, New User Empty State","Today","Start with one reading today.","Log fasting sugar|Sugar|BP|Sleep|Walk","Log now"),
    s(14,"dashboard","Today Dashboard, Active User","Today","Your calm daily health cockpit.","3 of 6 check-ins done|One daily action|Weekly report building","Complete check-in"),
    s(15,"today_program","Today Dashboard, Program User","Today","6-Month Program · Week 2","Fasting sugar logged|Post-meal walk pending|Yoga pending|Expert note","Open Program Plan"),
    s(16,"quick_sugar","Quick Log, Fasting Sugar","Today","Record a fasting value in seconds.","Reading in mg/dL|Time|Optional note|Recent values","Save"),
    s(18,"quick_walk","Quick Log, Walk","Today","Add a short walk to today.","5 min|10 min|15 min|30 min|After meal","Save walk"),
    s(19,"track_hub","Track Hub","Track","Log the few things that shape your health.","Blood Sugar|Blood Pressure|Sleep|Walking and Activity|Lab Reports","Choose metric"),
    s(20,"sugar_detail","Blood Sugar Overview","Track","See readings and the pattern behind them.","History","Add reading"),
    s(21,"add_sugar","Add Blood Sugar Reading","Track","Add a reading with useful context.","Reading value and type|Date and time|Linked meal|Notes and source","Save reading"),
    s(22,"sugar_reading_detail","Blood Sugar Reading Detail","Track","Review the reading and its context.","Value and status|Source|Linked meal|Notes|Related insight","Share"),
    s(23,"bp_overview","BP Overview","Track","Measure at a consistent time for clearer trends.","History","Add BP"),
    s(24,"add_bp","Add BP Reading","Track","Record blood pressure and pulse.","Systolic|Diastolic|Pulse|Context|Date and notes","Save BP"),
    s(25,"sleep_overview","Sleep Overview","Track","See how rest may shape your health rhythm.","History","Add sleep"),
    s(26,"add_sleep","Add Sleep Log","Track","Capture last night in a few taps.","Sleep and wake time|Duration is calculated for you|Late dinner|Screen before sleep|Notes","Save sleep"),
    s(27,"walking_overview","Walking and Activity Overview","Track","Steps sync automatically; log exercise or yoga too.","History","Add activity"),
    s(28,"walk_timer","Walk Timer","Track","Post-dinner walk in progress.","Elapsed time|Pause|Finish|Cancel","Finish walk"),
    s(35,"lab_reports","Lab Reports Overview","Track","Keep important reports together and private.","Recent reports","Upload report"),
    s(36,"upload_lab","Upload Lab Report","Track","Upload a PDF or photo for your records.","Report type and date|Lab name|Private file|Manual values|Notes","Save report"),
    s(37,"insights_hub","Insights Hub","Insights","This week: sugar is stable, walking improved, sleep needs attention.","Weekly Report|Sugar Story|30-Day Trends|Share with Expert","Open weekly report"),
    s(38,"weekly_report","Weekly Report","Insights","A calm summary of the last seven days.","Sugar averages|BP average|Sleep|Walking","Download PDF"),
    s(39,"sugar_story","Sugar Story","Insights","Simple patterns from your own data.","Sleep and fasting sugar|Dinner walk impact","View details"),
    s(40,"insight_detail","Sugar Story Detail","Insights","Post-dinner walks were linked with lower readings.","Comparison graph|Data used|Suggested action|Confidence","Try 15-minute walk"),
    s(41,"trends_30","30-Day Trends","Insights","Zoom out to see what is changing.","Sugar|BP|Sleep|Walking|Weight|Consistency","Choose metric"),
    s(42,"share_report","Share Report","Insights","Share only what you choose.","Expert|Family member|PDF|Date range|Privacy reminder","Share securely"),
    s(43,"care_hub","Care Hub","Care","Expert help, programs and follow-ups in one place.","Consultations|Program Mode|Expert notes|Upcoming care","Book consultation"),
    s(44,"consultation_types","Consultation Types","Care","Choose the kind of support you need.","Diabetes lifestyle|Diet review|Yoga|Naturopathy|Follow-up","Choose consultation"),
    s(45,"consult_stepper","Consultation Booking, Slot Selection","Care","Pick a suitable expert and time.","Expert|Date|Available slot|Price","Continue"),
    s(46,"pre_consultation","Pre-Consultation Form","Care","Help the expert prepare.","Primary concern|Recent readings|Medication|Reports|Consent","Continue to payment"),
    s(47,"payment_confirmation","Payment Confirmation","Care","Review before secure payment.","Consultation|Date and time|Fee|Refund terms","Pay securely"),
    s(48,"consultation_confirmed","Consultation Confirmed","Care","Your consultation is booked.","Booking reference|Calendar reminder|Preparation checklist","View booking"),
    s(49,"consultation_detail","Consultation Detail","Care","Everything for this appointment.","Expert and slot|Status|Attachments|Notes|Reschedule policy","Join consultation"),
    s(50,"program_locked","Program Mode Locked","Program","Unlock structured Nirog Bhumi care.","Diet plan|Yoga|Naturopathy|Weekly expert review","Enter program code"),
    s(51,"active_journey","Program Dashboard","Program","Your program rhythm for this week.","Week progress|Today’s checklist|Plans|Expert note","Open checklist"),
    s(52,"program_checklist","Program Checklist","Program","Small actions, checked off calmly.","Fasting sugar|Post-meal walk|Yoga|Dinner timing","Save progress"),
    s(53,"diet_plan","Diet Plan","Program","A practical Indian food rhythm.","Morning|Breakfast|Lunch|Snack|Dinner|Swaps","View today’s plan"),
    s(54,"yoga_plan","Yoga Plan","Program","Gentle routines assigned for you.","Today’s routine|Weekly schedule|Safety note","Start routine"),
    s(55,"yoga_detail","Yoga Routine Detail","Program","Move within comfort and stop if unwell.","Warm-up|Poses|Breathing|Duration|Contraindications","Begin"),
    s(56,"naturopathy_plan","Naturopathy Routine","Program","Simple supportive practices.","Morning routine|Hydrotherapy|Relaxation|Safety","View routine"),
    s(57,"naturopathy_detail","Naturopathy Routine Detail","Program","Follow the assigned steps and safety note.","Materials|Steps|Duration|When to avoid","Mark complete"),
    s(58,"expert_notes","Expert Notes","Program","Guidance from your care team.","Latest note|Diet|Sugar|Sleep|Program follow-up","Acknowledge note"),
    s(59,"learn_hub","Learn Hub","Learn","Short, useful health education.","Diabetes|Sleep|Yoga|Naturopathy|BP","Explore"),
    s(60,"articles","Education Article List","Learn","Practical reading for your goals.","2-minute reads|Saved articles|Recommended for you","Open article"),
    s(61,"article_detail","Article Detail","Learn","Education only; consult your clinician for treatment decisions.","Key idea|Why it matters|Try this today|Sources","Save article"),
    s(68,"orders","Orders List","Store","Your current and past orders.","Confirmed|Shipped|Delivered|Cancelled","View order"),
    s(69,"order_detail","Order Detail","Store","Follow this order’s progress.","Items|Payment|Shipping address|Timeline|Support","Get help"),
    s(70,"family_profiles","Family Profiles","Family","Care for family without mixing health records.","My profile|Parent|Spouse|Permissions","Add family member"),
    s(71,"add_family","Add Family Member","Family","Create a separate, consent-aware profile.","Name|Relationship|Age|Health status|Consent","Create profile"),
    s(72,"family_dashboard","Family Member Dashboard","Family","A focused view of this family member’s day.","Today’s status|Pending logs|Recent report|Care access","Switch profile"),
    s(73,"device_hub","Device Connect Hub","Devices","Bring in supported data with permission.","Health Connect|HealthKit|Samsung Health|Fitbit|Manual device","Connect device"),
    s(74,"health_connect_permissions","Health Connect Permissions","Devices","Choose exactly which data can sync.","Steps|Sleep|Heart rate|Weight|Blood glucose|Blood pressure","Allow selected"),
    s(75,"device_sync","Device Sync Status","Devices","Your latest sync status.","Connected provider|Permissions|Last sync|Imported records|Errors","Sync now"),
    s(76,"profile","Profile","Settings","Your account, preferences and care access.","Personal details|Family profiles|Devices|Orders|Legal","Edit profile"),
    s(77,"profile_edit","Edit Profile","Settings","Keep your profile accurate.","Name and city|Height and weight|Language|Health status","Save changes"),
    s(78,"notification_settings","Notification Settings","Settings","Keep reminders useful and quiet.","Health reminders|Consultations|Programs|Orders|Quiet hours","Save preferences"),
    s(79,"privacy_consent","Privacy and Consent","Settings","Control permissions and understand data use.","Health data|Expert review|Device import|Research|Marketing","Update consent"),
    s(80,"data_controls","Data Export and Delete","Settings","Your data remains under your control.","Download data|Export status|Request deletion|Retention policy","Request export"),
    s(81,"support","Support","Settings","We’re here to help with the app and your account.","FAQs|Chat support|Email|Call|Report a problem","Contact support"),
    s(82,"empty_state","Empty State, No Data","States","There is nothing here yet.","Start with one small log|Your patterns build over time","Add first log"),
    s(83,"loading_state","Loading State","States","Bringing your health rhythm up to date.","Secure sync|Please wait","Continue"),
    s(84,"error_state","Error State","States","Something didn’t load. Your saved data is safe.","Try again|Contact support if this continues","Retry"),
    s(85,"offline_state","Offline State","States","You’re offline. New logs will sync later.","Previously saved data available|Sync pending","Continue offline"),
    s(86,"critical_reading_caution","Critical Reading Caution","States","This reading needs attention.","If you feel unwell, contact emergency care|Repeat the measurement|Consult your doctor","I understand")
)

@Composable
fun CatalogScreen(state: NirogState, route: String) {
    val spec = NirogScreens.firstOrNull { it.route == route } ?: NirogScreens.first { it.route == "dashboard" }
    val experience = ScreenExperiences[spec.route]
    var message by remember(spec.route) { mutableStateOf<String?>(null) }
    var saving by remember(spec.route) { mutableStateOf(false) }
    LaunchedEffect(spec.route) {
        if (spec.route == "notification_settings") {
            state.formValues.putIfAbsent("notification_settings.quietHoursStart", "21:00")
            state.formValues.putIfAbsent("notification_settings.quietHoursEnd", "07:00")
        }
    }
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val healthConnect = remember { HealthConnectManager(context.applicationContext, state.repository) }
    val healthPermissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.isNotEmpty()) state.currentScreen = "device_sync"
        else message = "No category was approved. You can change access later."
    }
    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            saving = true
            val folder = "lab-reports"
            state.repository.uploadPrivateFile(folder, uri) { result ->
                saving = false
                when (result) {
                    is CloudResult.Success -> { state.formValues["${spec.route}.fileUrl"] = result.value; message = "Private file uploaded securely" }
                    is CloudResult.Failure -> message = result.message
                }
            }
        }
    }
    val readCollection = readCollectionFor(spec.route)
    DisposableEffect(spec.route, state.repository.userId) {
        val subscription = when {
            readCollection == null -> null
            !state.repository.isCloudConfigured -> { state.cloudRecords[spec.route] = emptyList(); null }
            readCollection in setOf("contentItems", "products", "programs") -> state.repository.listenPublicCollection(readCollection) { result ->
                when (result) {
                    is CloudResult.Success -> state.cloudRecords[spec.route] = result.value
                    is CloudResult.Failure -> { message = result.message; state.cloudRecords[spec.route] = emptyList() }
                }
            }
            state.repository.userId != null -> state.repository.listenUserCollection(readCollection) { result ->
                when (result) {
                    is CloudResult.Success -> state.cloudRecords[spec.route] = result.value
                    is CloudResult.Failure -> { message = result.message; state.cloudRecords[spec.route] = emptyList() }
                }
            }
            else -> null
        }
        onDispose { subscription?.cancel() }
    }
    Scaffold(containerColor = Color(0xFFF8F6EF)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { state.currentScreen = backDestinationFor(spec) }) {
                    Icon(Icons.Outlined.ArrowBack, "Back", tint = Color(0xFF314936))
                }
                Text(spec.section.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF58705E))
                Spacer(Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text("Secure") }, leadingIcon = { Icon(Icons.Outlined.CheckCircle, null, Modifier.size(14.dp)) })
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(spec.title, fontFamily = FontFamily.Serif, fontSize = 30.sp, lineHeight = 36.sp, color = Color(0xFF182219))
                Text(spec.subtitle, fontSize = 15.sp, lineHeight = 22.sp, color = Color(0xFF455148))

                if (spec.route in setOf("bp_overview", "sleep_overview", "walking_overview", "trends_30", "weekly_report")) {
                    TrendPanel(spec.route, state.cloudRecords[spec.route].orEmpty())
                }

                if (readCollection != null) {
                    val records = state.cloudRecords[spec.route]
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Latest", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF182219))
                        when {
                            records == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF9CB79F))
                                Spacer(Modifier.width(8.dp))
                                Text("Loading...", fontSize = 13.sp, color = Color(0xFF697169))
                            }
                            records.isEmpty() -> Surface(
                                Modifier.fillMaxWidth(), color = Color(0xFFF1EDE6), shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    "No entries yet. What you log here will show up in this list.",
                                    Modifier.padding(14.dp), fontSize = 13.sp, color = Color(0xFF697169)
                                )
                            }
                            else -> records.take(5).forEach { record ->
                                val recordDestination = when (readCollection) {
                                    "contentItems" -> "article_detail"
                                    "orders" -> "order_detail"
                                    "profiles" -> "family_dashboard"
                                    "consultations" -> "consultation_detail"
                                    else -> null
                                }
                                Surface(Modifier.fillMaxWidth().then(if (recordDestination != null) Modifier.clickable {
                                    state.selectedDocumentId = record.id
                                    state.selectedDocumentValues = record.values
                                    state.currentScreen = recordDestination
                                } else Modifier), color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFD8D0C0))) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(recordTitle(readCollection.orEmpty(), record.values), fontWeight = FontWeight.SemiBold, color = Color(0xFF182219))
                                            Text(recordSubtitle(record.values), fontSize = 12.sp, color = Color(0xFF697169), maxLines = 2)
                                        }
                                        StatusBadge(record.values["status"]?.toString())
                                    }
                                }
                            }
                        }
                    }
                }

                if (spec.route == "article_detail" && state.selectedDocumentValues.isNotEmpty()) {
                    ArticleSummary(state.selectedDocumentValues)
                }

                var openTimePickerFor by remember(spec.route) { mutableStateOf<String?>(null) }
                experience?.fields?.forEach { field ->
                    val storageKey = "${spec.route}.${field.key}"
                    // Sleep duration is derived from sleep/wake time rather than typed in,
                    // so the user never has to do that arithmetic themselves.
                    if (spec.route == "add_sleep" && field.key == "duration") {
                        val minutes = sleepDurationMinutes(state.formValues["add_sleep.sleepTime"], state.formValues["add_sleep.wakeTime"])
                        LaunchedEffect(minutes) {
                            state.formValues[storageKey] = minutes?.let { String.format(java.util.Locale.US, "%.2f", it / 60.0) }.orEmpty()
                        }
                        val computed = sleepDurationLabel(state.formValues["add_sleep.sleepTime"], state.formValues["add_sleep.wakeTime"])
                        OutlinedTextField(
                            value = computed ?: "",
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Duration (auto-calculated)") },
                            placeholder = { Text("Set both times above") },
                            suffix = field.suffix?.let { suffix -> { Text(suffix) } },
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(disabledBorderColor = Color(0xFFD8D0C0), disabledContainerColor = Color(0xFFF1EDE6), disabledTextColor = Color(0xFF1B3221))
                        )
                        return@forEach
                    }
                    if (field.type == FieldType.TIME) {
                        OutlinedTextField(
                            value = state.formValues[storageKey].orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().clickable { openTimePickerFor = field.key },
                            label = { Text(field.label + if (field.required) " *" else "") },
                            placeholder = { Text("Tap to choose a time") },
                            trailingIcon = { Icon(Icons.Outlined.ChevronRight, null) },
                            enabled = false,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(disabledBorderColor = Color(0xFFD8D0C0), disabledContainerColor = Color.White, disabledTextColor = Color(0xFF1B3221), disabledLabelColor = Color(0xFF697169))
                        )
                        return@forEach
                    }
                    OutlinedTextField(
                        value = state.formValues[storageKey].orEmpty(),
                        onValueChange = { value ->
                            val accepted = when (field.type) {
                                FieldType.NUMBER -> value.filter(Char::isDigit)
                                FieldType.DECIMAL -> value.filter { it.isDigit() || it == '.' }.let { candidate -> if (candidate.count { it == '.' } <= 1) candidate else state.formValues[storageKey].orEmpty() }
                                else -> value
                            }
                            state.formValues[storageKey] = accepted
                            message = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(field.label + if (field.required) " *" else "") },
                        suffix = field.suffix?.let { suffix -> { Text(suffix) } },
                        minLines = if (field.type == FieldType.MULTILINE) 3 else 1,
                        singleLine = field.type != FieldType.MULTILINE,
                        keyboardOptions = KeyboardOptions(keyboardType = when (field.type) {
                            FieldType.NUMBER -> KeyboardType.Number
                            FieldType.DECIMAL -> KeyboardType.Decimal
                            else -> KeyboardType.Text
                        }),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF314936), unfocusedBorderColor = Color(0xFFD8D0C0), focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
                    )
                }
                openTimePickerFor?.let { fieldKey ->
                    val storageKey = "${spec.route}.$fieldKey"
                    TimePickerAlertDialog(
                        initial = state.formValues[storageKey]?.takeIf { it.contains(":") } ?: "07:00",
                        onDismiss = { openTimePickerFor = null },
                        onConfirm = { value -> state.formValues[storageKey] = value; openTimePickerFor = null }
                    )
                }

                if (spec.route == "upload_lab") {
                    OutlinedButton(onClick = { uploadLauncher.launch("*/*") }, Modifier.fillMaxWidth(), enabled = !saving, shape = RoundedCornerShape(24.dp)) {
                        Text(if (state.formValues["${spec.route}.fileUrl"].isNullOrBlank()) "Choose PDF or photo" else "File attached securely")
                    }
                }

                if (!experience?.choices.isNullOrEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Choose one", fontWeight = FontWeight.SemiBold, color = Color(0xFF182219))
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            experience!!.choices.forEach { choice ->
                                val selected = state.routeSelections[spec.route] == choice
                                FilterChip(
                                    selected = selected,
                                    onClick = { state.routeSelections[spec.route] = choice; message = null },
                                    label = { Text(choice) },
                                    leadingIcon = if (selected) ({ Icon(Icons.Outlined.CheckCircle, null, Modifier.size(16.dp)) }) else null,
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFE4EFDB), selectedLabelColor = Color(0xFF314936))
                                )
                            }
                        }
                    }
                }

                if (!experience?.checklist.isNullOrEmpty()) {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFD8D0C0))) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            experience!!.checklist.forEach { item ->
                                val key = "${spec.route}:$item"
                                Row(Modifier.fillMaxWidth().clickable { if (key in state.checkedItems) state.checkedItems.remove(key) else state.checkedItems.add(key) }.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = key in state.checkedItems, onCheckedChange = { checked -> if (checked) { if (key !in state.checkedItems) state.checkedItems.add(key) } else state.checkedItems.remove(key) }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF314936)))
                                    Text(item, Modifier.weight(1f), color = Color(0xFF182219))
                                }
                            }
                        }
                    }
                }

                if (experience == null || (experience.fields.isEmpty() && experience.choices.isEmpty() && experience.checklist.isEmpty())) {
                    spec.items.forEach { item ->
                        val destination = destinationFor(spec.route, item)
                        Card(
                            Modifier.fillMaxWidth().clickable {
                                if (destination != null) {
                                    if (destination == "legal_center") state.legalReturnRoute = "profile"
                                    state.currentScreen = destination
                                } else message = item
                            },
                            shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFD8D0C0))
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.HealthAndSafety, null, tint = Color(0xFF82AD5C))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item, color = Color(0xFF182219), fontWeight = FontWeight.Medium)
                                    Text(itemSupportingCopy(spec.route, item), fontSize = 12.sp, color = Color(0xFF697169), lineHeight = 16.sp)
                                }
                                if (destination != null) Icon(Icons.Outlined.ChevronRight, null, tint = Color(0xFF7A827B))
                            }
                        }
                    }
                }

                message?.let {
                    val isError = it.startsWith("Please") || it.contains("required", true) || it.contains("failed", true) || it.contains("not configured", true) || it.contains("Sign in", true)
                    Surface(color = if (isError) Color(0xFFF7E7E3) else Color(0xFFE7F1DF), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = if (isError) Color(0xFF8B3E36) else Color(0xFF314936))
                            Spacer(Modifier.width(8.dp)); Text(it, color = if (isError) Color(0xFF7B332E) else Color(0xFF314936))
                        }
                    }
                }

                Button(
                    onClick = {
                        val missing = experience?.fields?.firstOrNull { field -> field.required && state.formValues["${spec.route}.${field.key}"].isNullOrBlank() }
                        val requiredChecks = experience?.checklist?.filter { it.contains("understand", true) || it.contains("agree", true) || it.contains("permission", true) }.orEmpty()
                        val uncheckedRequired = requiredChecks.firstOrNull { "${spec.route}:$it" !in state.checkedItems }
                        if (missing != null) { message = "Please complete ${missing.label.lowercase()}"; return@Button }
                        if (experience != null && experience.choices.isNotEmpty() && state.routeSelections[spec.route].isNullOrBlank()) { message = "Please choose one option"; return@Button }
                        if (uncheckedRequired != null) { message = "Please confirm: $uncheckedRequired"; return@Button }

                        if (experience?.writeCollection != null) {
                            saving = true
                            val payload = buildPayload(spec.route, experience, state)
                            state.repository.addHealthLog(experience.writeCollection, payload) { result ->
                                when (result) {
                                    is CloudResult.Failure -> { saving = false; message = result.message }
                                    is CloudResult.Success -> when (spec.route) {
                                        "pre_consultation" -> { saving = false; state.pendingConsultationId = result.value; state.currentScreen = "payment_confirmation" }
                                        else -> {
                                            saving = false; message = "Saved and synced securely"
                                            if (spec.route == "add_sugar" || spec.route == "quick_sugar") state.formValues["${spec.route}.value"]?.toIntOrNull()?.let { state.fastingSugarValue = it }
                                            experience.successRoute?.let { state.currentScreen = it }
                                        }
                                    }
                                }
                            }
                        } else {
                            if (spec.route == "data_controls") {
                                state.repository.requestDataExport { result -> message = when (result) {
                                    is CloudResult.Success -> "Your secure export has been requested"
                                    is CloudResult.Failure -> result.message
                                } }
                                return@Button
                            }
                            if (spec.route == "notification_settings" && Build.VERSION.SDK_INT >= 33 && activity != null) {
                                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4201)
                            }
                            if (spec.route == "share_report" || spec.route == "weekly_report") {
                                runCatching { ReportShare.shareWeeklyReport(context, state) }.onFailure { message = it.message ?: "Report could not be shared" }
                            } else if (spec.route == "health_connect_permissions") {
                                if (!healthConnect.isAvailable) message = "Health Connect is unavailable or needs an update" else healthPermissionLauncher.launch(HealthConnectManager.permissions)
                            } else if (spec.route == "device_sync") {
                                saving = true
                                coroutineScope.launch {
                                    runCatching { healthConnect.syncLastThirtyDays() }
                                        .onSuccess { summary -> message = "Synced ${summary.steps} step, ${summary.sleep} sleep, ${summary.glucose} glucose and ${summary.bloodPressure} BP records" }
                                        .onFailure { message = it.message ?: "Device sync failed" }
                                    saving = false
                                }
                            } else if (spec.route == "family_dashboard") {
                                if (state.selectedDocumentId.isBlank()) { message = "Choose a family profile first"; return@Button }
                                state.activeProfileId = state.selectedDocumentId
                                state.selectedDocumentValues["name"]?.toString()?.let { state.profileName = it }
                                state.currentScreen = "dashboard"
                            } else if (spec.route == "payment_confirmation") {
                                if (state.pendingConsultationId.isBlank()) { message = "Please complete the consultation form first"; return@Button }
                                saving = true
                                state.repository.createPaymentOrder("consultation", state.pendingConsultationId) { result ->
                                    saving = false
                                    when (result) {
                                        is CloudResult.Success -> runCatching { RazorpayPaymentLauncher.open(requireNotNull(activity), result.value, state.profileName, state.userEmail, state.userMobile) }.onFailure { message = it.message ?: "Payment could not open" }
                                        is CloudResult.Failure -> message = result.message
                                    }
                                }
                            } else if (spec.route == "privacy_consent") {
                                val selected = experience?.checklist.orEmpty().filter { "${spec.route}:$it" in state.checkedItems }
                                state.repository.saveProfile(mapOf("privacyConsentSelections" to selected, "preferencesUpdatedAt" to FieldValue.serverTimestamp())) { result ->
                                    message = if (result is CloudResult.Success) "Preferences saved securely" else (result as CloudResult.Failure).message
                                }
                            } else {
                                val destination = PrimaryDestinations[spec.route]
                                if (destination != null) state.currentScreen = destination else message = "${spec.primaryAction} completed"
                            }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936))
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text(spec.primaryAction, fontWeight = FontWeight.Bold)
                }
                if (spec.route == "walking_overview") {
                    OutlinedButton(onClick = { state.currentScreen = "walk_timer" }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp)) { Text("Start walk timer") }
                }
                if (spec.route == "weekly_report") {
                    OutlinedButton(onClick = { state.currentScreen = "share_report" }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp)) { Text("Share with expert or family") }
                }
                if (spec.route == "data_controls") {
                    OutlinedButton(
                        onClick = { state.repository.requestAccountDeletion { result -> message = when (result) {
                            is CloudResult.Success -> "Deletion request submitted for verification"
                            is CloudResult.Failure -> result.message
                        } } },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(25.dp), border = BorderStroke(1.dp, Color(0xFF9B5A54))
                    ) { Text("Request account deletion", color = Color(0xFF7B332E)) }
                    val exportRequests = state.cloudRecords[spec.route]
                    val completedExport = exportRequests?.firstOrNull { it.values["status"] == "completed" && it.values["storagePath"] != null }
                    when {
                        completedExport != null -> OutlinedButton(onClick = {
                            state.repository.getPrivateDownloadUrl(completedExport.values["storagePath"].toString()) { result ->
                                when (result) {
                                    is CloudResult.Success -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.value))) }.onFailure { message = "No app could open the export" }
                                    is CloudResult.Failure -> message = result.message
                                }
                            }
                        }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp)) { Text("Open latest export") }
                        exportRequests?.isNotEmpty() == true -> Text(
                            "Your export request is being prepared. This can take a little while - check back soon.",
                            fontSize = 13.sp, color = Color(0xFF697169)
                        )
                        else -> Text(
                            "No export requested yet. Tap \"Download data\" above to request one.",
                            fontSize = 13.sp, color = Color(0xFF697169)
                        )
                    }
                }
                Text("Education and lifestyle support only. Consult your doctor before changing medication or treatment.", fontSize = 11.sp, lineHeight = 16.sp, color = Color(0xFF6B736C))
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun buildPayload(route: String, experience: ScreenExperience, state: NirogState): Map<String, Any?> {
    val values = experience.fields.associate { field ->
        val raw = state.formValues["$route.${field.key}"].orEmpty()
        field.key to when (field.type) {
            FieldType.NUMBER -> raw.toLongOrNull()
            FieldType.DECIMAL -> raw.toDoubleOrNull()
            else -> raw.ifBlank { null }
        }
    }.toMutableMap<String, Any?>()
    values["source"] = "manual"
    if (state.activeProfileId.isNotBlank()) values["profileId"] = state.activeProfileId
    values["measuredAt"] = FieldValue.serverTimestamp()
    state.routeSelections[route]?.let { selected -> values[when (route) {
        "add_sugar" -> "readingType"
        "add_bp" -> "context"
        "add_sleep" -> "quality"
        else -> "selection"
    }] = selected.lowercase().replace(' ', '_') }
    values["selectedItems"] = experience.checklist.filter { "$route:$it" in state.checkedItems }
    state.formValues["$route.fileUrl"]?.let { values["fileUrl"] = it }
    when (route) {
        "quick_walk" -> values["minutes"] = state.routeSelections[route]?.filter(Char::isDigit)?.toIntOrNull() ?: 15
        "pre_consultation" -> values.putAll(mapOf("status" to "payment_pending", "paymentStatus" to "pending"))
        "program_checklist" -> values["status"] = "done"
        "support" -> values["status"] = "open"
    }
    if (route == "pre_consultation") values.putAll(mapOf(
        "consultationType" to state.selectedConsultType,
        "selectedDate" to state.selectedConsultDate,
        "selectedTime" to state.selectedConsultTime,
        "slotId" to state.selectedConsultSlotId,
        "price" to if (state.selectedConsultType == "naturopathy") 699 else 999
    ))
    return values
}

@Composable
private fun ArticleSummary(values: Map<String, Any?>) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color(0xFFD8D0C0))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(values["title"]?.toString() ?: "Health article", fontFamily = FontFamily.Serif, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color(0xFF182219))
            values["summary"]?.toString()?.let { Text(it, fontWeight = FontWeight.Medium, color = Color(0xFF455148), lineHeight = 21.sp) }
            values["body"]?.toString()?.let { Text(it, color = Color(0xFF303B33), lineHeight = 22.sp) }
            Text("Education only. Ask a qualified healthcare professional before changing treatment.", fontSize = 11.sp, color = Color(0xFF6B736C))
        }
    }
}

private fun sleepDurationMinutes(sleepTime: String?, wakeTime: String?): Long? {
    if (sleepTime.isNullOrBlank() || wakeTime.isNullOrBlank()) return null
    val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    val start = runCatching { java.time.LocalTime.parse(sleepTime, formatter) }.getOrNull() ?: return null
    val end = runCatching { java.time.LocalTime.parse(wakeTime, formatter) }.getOrNull() ?: return null
    return java.time.Duration.between(start, end).toMinutes().let { if (it <= 0) it + 24 * 60 else it }
}

private fun sleepDurationLabel(sleepTime: String?, wakeTime: String?): String? =
    sleepDurationMinutes(sleepTime, wakeTime)?.let { "${it / 60}h ${it % 60}m" }

private fun itemSupportingCopy(route: String, item: String): String = when {
    route == "track_hub" -> "Latest status and history"
    route == "insights_hub" -> "Built from your securely stored logs"
    route == "care_hub" -> "Open care details and next steps"
    route == "profile" -> "Manage account and privacy settings"
    item.contains("Safety", true) -> "Read before use"
    item.contains("Status", true) -> "View the latest update"
    else -> "View details"
}

@Composable
private fun TrendPanel(route: String, records: List<com.nirogbhumi.app.data.CloudDocument>) {
    val defaults = when (route) {
        "bp_overview" -> listOf(126f, 122f, 125f, 121f, 120f, 124f, 118f)
        "sleep_overview" -> listOf(6.2f, 6.8f, 7.1f, 6.4f, 7.3f, 6.9f, 7.2f)
        "walking_overview" -> listOf(12f, 18f, 25f, 14f, 30f, 22f, 28f)
        else -> listOf(132f, 128f, 130f, 124f, 121f, 118f, 116f)
    }
    val key = when (route) { "bp_overview" -> "systolic"; "sleep_overview" -> "duration"; "walking_overview" -> "minutes"; else -> "glucoseAverage" }
    val live = records.mapNotNull { (it.values[key] as? Number)?.toFloat() }.takeLast(14)
    val points = if (live.size >= 2) live else defaults
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color(0xFFD8D0C0))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (live.size >= 2) "Your recent trend" else "Trend builds as you log", fontWeight = FontWeight.SemiBold, color = Color(0xFF182219))
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val min = points.minOrNull() ?: 0f; val range = ((points.maxOrNull() ?: 1f) - min).coerceAtLeast(1f)
                repeat(4) { row -> val y = size.height * row / 3f; drawLine(Color(0xFFD8D0C0), Offset(0f, y), Offset(size.width, y), 1f) }
                points.zipWithNext().forEachIndexed { index, pair ->
                    val x1 = size.width * index / (points.size - 1).coerceAtLeast(1); val x2 = size.width * (index + 1) / (points.size - 1).coerceAtLeast(1)
                    val y1 = size.height - ((pair.first - min) / range * size.height); val y2 = size.height - ((pair.second - min) / range * size.height)
                    drawLine(Color(0xFF314936), Offset(x1, y1), Offset(x2, y2), 4f)
                }
            }
            Text("Look for consistency over time; one reading does not define your health.", fontSize = 12.sp, color = Color(0xFF697169))
        }
    }
}

private fun readCollectionFor(route: String): String? = when (route) {
    "bp_overview" -> "bpReadings"
    "sleep_overview" -> "sleepLogs"
    "walking_overview" -> "walkLogs"
    "lab_reports" -> "labReports"
    "weekly_report" -> "weeklyReports"
    "sugar_story", "insight_detail" -> "sugarStories"
    "consultation_detail" -> "consultations"
    "program_checklist" -> "checklistLogs"
    "diet_plan", "yoga_plan", "yoga_detail", "naturopathy_plan", "naturopathy_detail" -> "programPlans"
    "expert_notes" -> "expertNotes"
    "articles", "article_detail" -> "contentItems"
    "family_profiles", "family_dashboard" -> "profiles"
    "device_sync" -> "deviceConnections"
    "orders", "order_detail", "order_success" -> "orders"
    "data_controls" -> "dataExportRequests"
    else -> null
}

private fun recordTitle(collection: String, values: Map<String, Any?>): String = when (collection) {
    "bpReadings" -> "${values["systolic"] ?: "—"}/${values["diastolic"] ?: "—"} mmHg"
    "sleepLogs" -> "${values["duration"] ?: values["durationHours"] ?: "—"} hours"
    "walkLogs" -> "${values["minutes"] ?: "—"} minute walk"
    "labReports" -> values["reportType"]?.toString() ?: "Lab report"
    "weeklyReports" -> "Weekly report"
    "sugarStories" -> values["title"]?.toString() ?: "Sugar pattern"
    "consultations" -> values["consultationType"]?.toString() ?: "Consultation"
    "programPlans" -> values["title"]?.toString() ?: "Care plan"
    "expertNotes" -> values["category"]?.toString()?.replaceFirstChar(Char::uppercase) ?: "Expert note"
    "contentItems" -> values["title"]?.toString() ?: "Health article"
    "profiles" -> values["name"]?.toString() ?: values["fullName"]?.toString() ?: "Family profile"
    "orders" -> "Order ${values["orderId"]?.toString()?.takeLast(6) ?: ""}"
    "dataExportRequests" -> "Data export · ${values["status"] ?: "requested"}"
    else -> values["title"]?.toString() ?: "Saved record"
}

private fun recordSubtitle(values: Map<String, Any?>): String = listOfNotNull(
    values["quality"]?.toString(), values["context"]?.toString(), values["summary"]?.toString(),
    values["note"]?.toString(), values["orderStatus"]?.toString(), values["paymentStatus"]?.toString()
).joinToString(" · ").ifBlank { "Synced securely" }

@Composable
private fun StatusBadge(status: String?) {
    if (status.isNullOrBlank()) return
    val caution = status.contains("critical", true) || status.contains("attention", true) || status.contains("failed", true)
    Surface(color = if (caution) Color(0xFFF5E7D1) else Color(0xFFE4EFDB), shape = RoundedCornerShape(12.dp)) {
        Text(status.replace('_', ' '), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (caution) Color(0xFF6E4D16) else Color(0xFF314936))
    }
}

@Composable
fun ScreenDirectory(state: NirogState) {
    Column(Modifier.fillMaxSize().background(Color(0xFFF8F6EF)).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("App screen directory", fontFamily = FontFamily.Serif, fontSize = 30.sp, color = Color(0xFF182219))
        Text("All approved user journeys", color = Color(0xFF526057))
        NirogScreens.groupBy { it.section }.forEach { (section, screens) ->
            Text(section, Modifier.padding(top = 12.dp), fontWeight = FontWeight.Bold, color = Color(0xFF314936))
            screens.forEach { spec ->
                ListItem(
                    headlineContent = { Text("${spec.id}. ${spec.title}") },
                    trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.White),
                    modifier = Modifier.clickable { state.currentScreen = spec.route }
                )
            }
        }
    }
}
