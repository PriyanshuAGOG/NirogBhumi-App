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

/** Canonical inventory from the approved UI brief. Tests assert that all 86 product screens remain present. */
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
    s(13,"today_empty","Today Dashboard, New User Empty State","Today","Start with one reading today.","Log fasting sugar|Sugar|BP|Sleep|Walk|Water|Food","Log now"),
    s(14,"dashboard","Today Dashboard, Active User","Today","Your calm daily health cockpit.","3 of 6 check-ins done|One daily action|Weekly report building","Complete check-in"),
    s(15,"today_program","Today Dashboard, Program User","Today","6-Month Program · Week 2","Fasting sugar logged|Post-meal walk pending|Yoga pending|Expert note","Open Program Plan"),
    s(16,"quick_sugar","Quick Log, Fasting Sugar","Today","Record a fasting value in seconds.","Reading in mg/dL|Time|Optional note|Recent values","Save"),
    s(17,"quick_water","Quick Log, Water","Today","Small sips add up.","5 of 8 glasses|Today’s target|Undo last glass","+1 glass"),
    s(18,"quick_walk","Quick Log, Walk","Today","Add a short walk to today.","5 min|10 min|15 min|30 min|After meal","Save walk"),
    s(19,"track_hub","Track Hub","Track","Log the few things that shape your health.","Blood Sugar|Blood Pressure|Sleep|Walking|Water|Food Impact|Medication|Lab Reports","Choose metric"),
    s(20,"sugar_detail","Blood Sugar Overview","Track","See readings and the pattern behind them.","Latest fasting sugar|7, 30 and 90 day trend|Walking-day insight|History","Add reading"),
    s(21,"add_sugar","Add Blood Sugar Reading","Track","Add a reading with useful context.","Reading value and type|Date and time|Linked meal|Notes and source","Save reading"),
    s(22,"sugar_reading_detail","Blood Sugar Reading Detail","Track","Review the reading and its context.","Value and status|Source|Linked meal|Notes|Related insight","Share"),
    s(23,"bp_overview","BP Overview","Track","Measure at a consistent time for clearer trends.","124/82 mmHg|Pulse 76|7-day trend|History","Add BP"),
    s(24,"add_bp","Add BP Reading","Track","Record blood pressure and pulse.","Systolic|Diastolic|Pulse|Context|Date and notes","Save BP"),
    s(25,"sleep_overview","Sleep Overview","Track","See how rest may shape your health rhythm.","6h 20m|Quality: Average|Weekly duration|Sugar relationship","Add sleep"),
    s(26,"add_sleep","Add Sleep Log","Track","Capture last night in a few taps.","Sleep and wake time|Quality|Late dinner|Screen before sleep|Notes","Save sleep"),
    s(27,"walking_overview","Walking Overview","Track","Build movement gently across the day.","12 of 30 minutes|Post-meal walks|Weekly minutes","Add walk"),
    s(28,"walk_timer","Walk Timer","Track","Post-dinner walk in progress.","Elapsed time|Pause|Finish|Cancel","Finish walk"),
    s(29,"water_overview","Water Overview","Track","Stay consistent without overthinking it.","5 of 8 glasses|Morning|Afternoon|Evening","+1 glass"),
    s(30,"food_journal","Food Impact Journal","Track","Understand what meals do to your sugar.","Today’s meals|Meals that worked|Meals that caused spikes|Expert swaps","Add meal"),
    s(31,"add_meal","Add Meal","Track","Log the meal, portion and timing.","Meal type and time|Photo|Indian food tags|Portion|Linked sugar","Save meal"),
    s(32,"meal_detail","Meal Detail","Track","Review the meal’s measured impact.","Food tags|Linked reading|Impact|Expert note","Save as usual meal"),
    s(33,"medication_overview","Medication Overview","Track","Do not change medication without consulting your doctor.","Morning medicine pending|Evening scheduled|Taken and missed history","Add medicine"),
    s(34,"add_medicine","Add Medicine","Track","Set a safe, quiet reminder.","Medicine name|Dose|Frequency|Times|Reminder","Save medicine"),
    s(35,"lab_reports","Lab Reports Overview","Track","Keep important reports together and private.","HbA1c|Lipid profile|Kidney function|Recent reports","Upload report"),
    s(36,"upload_lab","Upload Lab Report","Track","Upload a PDF or photo for your records.","Report type and date|Lab name|Private file|Manual values|Notes","Save report"),
    s(37,"insights_hub","Insights Hub","Insights","This week: sugar is stable, walking improved, sleep needs attention.","Weekly Report|Sugar Story|30-Day Trends|Share with Expert","Open weekly report"),
    s(38,"weekly_report","Weekly Report","Insights","A calm summary of the last seven days.","Sugar averages|BP average|Sleep|Walking|Water|Medication adherence","Download PDF"),
    s(39,"sugar_story","Sugar Story","Insights","Simple patterns from your own data.","Sleep and fasting sugar|Dinner walk impact|Meals to review","View details"),
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
    s(59,"learn_hub","Learn Hub","Learn","Short, useful health education.","Diabetes|Food|Sleep|Yoga|Naturopathy|BP","Explore"),
    s(60,"articles","Education Article List","Learn","Practical reading for your goals.","2-minute reads|Saved articles|Recommended for you","Open article"),
    s(61,"article_detail","Article Detail","Learn","Education only; consult your clinician for treatment decisions.","Key idea|Why it matters|Try this today|Sources","Save article"),
    s(62,"store_home","Store Home","Store","Relevant products, selected with care.","Naturopathy|Yoga|Wellness kits|Orders","Browse products"),
    s(63,"products","Product List","Store","Find products by routine and need.","Category|Price|Availability|Safety note","View product"),
    s(64,"product_detail","Product Detail","Store","Clear instructions and safety information.","Images|Description|Usage|Safety|Delivery","Add to cart"),
    s(65,"cart","Cart","Store","Review your selected items.","Items|Quantity|Subtotal|Delivery|Total","Checkout"),
    s(66,"checkout","Checkout","Store","Confirm delivery and payment.","Address|Order summary|UPI or Razorpay|Policies","Place order"),
    s(67,"order_success","Order Success","Store","Your order has been received.","Order number|Payment status|Delivery estimate","Track order"),
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
            val folder = if (spec.route == "add_meal") "meal-photos" else "lab-reports"
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
            readCollection == null || !state.repository.isCloudConfigured -> null
            readCollection in setOf("contentItems", "products", "programs") -> state.repository.listenPublicCollection(readCollection) { result ->
                when (result) {
                    is CloudResult.Success -> state.cloudRecords[spec.route] = result.value
                    is CloudResult.Failure -> message = result.message
                }
            }
            state.repository.userId != null -> state.repository.listenUserCollection(readCollection) { result ->
                when (result) {
                    is CloudResult.Success -> state.cloudRecords[spec.route] = result.value
                    is CloudResult.Failure -> message = result.message
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
                if (spec.route == "water_overview") {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFDDF3F3)), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${state.waterGlasses} of 8 glasses", fontFamily = FontFamily.Serif, fontSize = 22.sp, color = Color(0xFF182219))
                            LinearProgressIndicator(progress = { (state.waterGlasses / 8f).coerceIn(0f, 1f) }, Modifier.fillMaxWidth(), color = Color(0xFF77D0D4), trackColor = Color.White)
                        }
                    }
                }
                if (spec.route == "medication_overview") {
                    Surface(color = Color(0xFFF5E7D1), shape = RoundedCornerShape(14.dp)) { Text("Do not change or stop medication without consulting your doctor.", Modifier.padding(14.dp), color = Color(0xFF624B20), fontWeight = FontWeight.Medium) }
                }

                state.cloudRecords[spec.route]?.take(5)?.takeIf { it.isNotEmpty() }?.let { records ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Latest", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF182219))
                        records.forEach { record ->
                            val recordDestination = when (readCollection) {
                                "products" -> "product_detail"
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

                if (spec.route == "product_detail" && state.selectedDocumentValues.isNotEmpty()) {
                    ProductSummary(state.selectedDocumentValues)
                }
                if (spec.route == "article_detail" && state.selectedDocumentValues.isNotEmpty()) {
                    ArticleSummary(state.selectedDocumentValues)
                }

                if (spec.route == "cart") {
                    CartEditor(state)
                }

                experience?.fields?.forEach { field ->
                    val storageKey = "${spec.route}.${field.key}"
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

                if (spec.route == "upload_lab" || spec.route == "add_meal") {
                    OutlinedButton(onClick = { uploadLauncher.launch(if (spec.route == "add_meal") "image/*" else "*/*") }, Modifier.fillMaxWidth(), enabled = !saving, shape = RoundedCornerShape(24.dp)) {
                        Text(if (state.formValues["${spec.route}.fileUrl"].isNullOrBlank()) { if (spec.route == "add_meal") "Add meal photo (optional)" else "Choose PDF or photo" } else "File attached securely")
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
                        if (spec.route == "checkout" && state.cartItems.isEmpty()) { message = "Please add a product before checkout"; return@Button }

                        if (experience?.writeCollection != null) {
                            saving = true
                            val payload = buildPayload(spec.route, experience, state)
                            state.repository.addHealthLog(experience.writeCollection, payload) { result ->
                                when (result) {
                                    is CloudResult.Failure -> { saving = false; message = result.message }
                                    is CloudResult.Success -> when (spec.route) {
                                        "pre_consultation" -> { saving = false; state.pendingConsultationId = result.value; state.currentScreen = "payment_confirmation" }
                                        "checkout" -> {
                                            state.pendingOrderId = result.value; state.pendingPaymentKind = "order"
                                            state.repository.createPaymentOrder("order", result.value) { paymentResult ->
                                                saving = false
                                                when (paymentResult) {
                                                    is CloudResult.Success -> runCatching { RazorpayPaymentLauncher.open(requireNotNull(activity), paymentResult.value, state.profileName, state.userEmail, state.userMobile) }.onFailure { message = it.message ?: "Payment could not open" }
                                                    is CloudResult.Failure -> message = paymentResult.message
                                                }
                                            }
                                        }
                                        else -> {
                                            saving = false; message = "Saved and synced securely"
                                            if (spec.route == "quick_water") state.waterGlasses = (state.waterGlasses + 1).coerceAtMost(20)
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
                            } else if (spec.route == "product_detail") {
                                if (state.selectedDocumentId.isBlank()) { message = "Please choose a product first"; return@Button }
                                state.cartItems[state.selectedDocumentId] = (state.cartItems[state.selectedDocumentId] ?: 0) + 1
                                state.currentScreen = "cart"
                            } else if (spec.route == "family_dashboard") {
                                if (state.selectedDocumentId.isBlank()) { message = "Choose a family profile first"; return@Button }
                                state.activeProfileId = state.selectedDocumentId
                                state.selectedDocumentValues["name"]?.toString()?.let { state.profileName = it }
                                state.currentScreen = "dashboard"
                            } else if (spec.route == "payment_confirmation") {
                                if (state.pendingConsultationId.isBlank()) { message = "Please complete the consultation form first"; return@Button }
                                saving = true; state.pendingPaymentKind = "consultation"
                                state.repository.createPaymentOrder("consultation", state.pendingConsultationId) { result ->
                                    saving = false
                                    when (result) {
                                        is CloudResult.Success -> runCatching { RazorpayPaymentLauncher.open(requireNotNull(activity), result.value, state.profileName, state.userEmail, state.userMobile) }.onFailure { message = it.message ?: "Payment could not open" }
                                        is CloudResult.Failure -> message = result.message
                                    }
                                }
                            } else if (spec.route == "notification_settings" || spec.route == "privacy_consent") {
                                val selected = experience?.checklist.orEmpty().filter { "${spec.route}:$it" in state.checkedItems }
                                val preferences = if (spec.route == "notification_settings") mapOf(
                                    "notificationPreferences" to mapOf(
                                        "enabledTypes" to selected,
                                        "quietHoursStart" to state.formValues["notification_settings.quietHoursStart"],
                                        "quietHoursEnd" to state.formValues["notification_settings.quietHoursEnd"],
                                        "maxHealthReminders" to 3
                                    )
                                ) else mapOf("privacyConsentSelections" to selected)
                                state.repository.saveProfile(preferences + mapOf("preferencesUpdatedAt" to FieldValue.serverTimestamp())) { result ->
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
                if (spec.route == "medication_overview") {
                    OutlinedButton(onClick = {
                        state.repository.addHealthLog("medicationLogs", mapOf("medicineName" to "Scheduled medicine", "status" to "taken", "takenAt" to FieldValue.serverTimestamp())) { result ->
                            message = if (result is CloudResult.Success) "Marked taken" else (result as CloudResult.Failure).message
                        }
                    }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp)) { Text("Mark morning medicine taken") }
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
                    state.cloudRecords[spec.route]?.firstOrNull { it.values["status"] == "completed" && it.values["storagePath"] != null }?.let { export ->
                        OutlinedButton(onClick = {
                            state.repository.getPrivateDownloadUrl(export.values["storagePath"].toString()) { result ->
                                when (result) {
                                    is CloudResult.Success -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.value))) }.onFailure { message = "No app could open the export" }
                                    is CloudResult.Failure -> message = result.message
                                }
                            }
                        }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp)) { Text("Open latest export") }
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
        "add_meal" -> "mealType"
        "checkout" -> "paymentProvider"
        else -> "selection"
    }] = selected.lowercase().replace(' ', '_') }
    values["selectedItems"] = experience.checklist.filter { "$route:$it" in state.checkedItems }
    state.formValues["$route.fileUrl"]?.let { values["fileUrl"] = it }
    when (route) {
        "quick_water" -> values.putAll(mapOf("glasses" to 1, "millilitres" to 250))
        "quick_walk" -> values["minutes"] = state.routeSelections[route]?.filter(Char::isDigit)?.toIntOrNull() ?: 15
        "pre_consultation" -> values.putAll(mapOf("status" to "payment_pending", "paymentStatus" to "pending"))
        "checkout" -> values.putAll(mapOf("orderStatus" to "pending", "paymentStatus" to "pending"))
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
    if (route == "checkout") values["items"] = state.cartItems.map { (productId, quantity) -> mapOf("productId" to productId, "quantity" to quantity) }
    return values
}

@Composable
private fun ProductSummary(values: Map<String, Any?>) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFEEE8DC)), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(values["name"]?.toString() ?: "Selected product", fontFamily = FontFamily.Serif, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color(0xFF182219))
            Text("₹${values["price"] ?: "—"}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF314936))
            values["description"]?.toString()?.let { Text(it, color = Color(0xFF455148), lineHeight = 20.sp) }
            values["usageInstructions"]?.toString()?.let { Text("Use: $it", fontSize = 13.sp, color = Color(0xFF455148)) }
            values["safetyNote"]?.toString()?.let { Text("Safety: $it", fontSize = 13.sp, color = Color(0xFF7B4D20)) }
        }
    }
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

@Composable
private fun CartEditor(state: NirogState) {
    if (state.cartItems.isEmpty()) {
        Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFD8D0C0))) {
            Text("Your cart is empty. Browse products to add something useful.", Modifier.padding(18.dp), color = Color(0xFF526057))
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.cartItems.toMap().forEach { (productId, quantity) ->
            Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFD8D0C0))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Product …${productId.takeLast(6)}", fontWeight = FontWeight.SemiBold); Text("Quantity $quantity", fontSize = 12.sp, color = Color(0xFF697169)) }
                    IconButton(onClick = { if (quantity <= 1) state.cartItems.remove(productId) else state.cartItems[productId] = quantity - 1 }) { Text("−", fontSize = 22.sp) }
                    IconButton(onClick = { if (quantity < 20) state.cartItems[productId] = quantity + 1 }) { Text("+", fontSize = 20.sp) }
                }
            }
        }
    }
}

private fun itemSupportingCopy(route: String, item: String): String = when {
    route == "track_hub" -> "Latest status and history"
    route == "insights_hub" -> "Built from your securely stored logs"
    route == "care_hub" -> "Open care details and next steps"
    route == "store_home" -> "Usage guidance and safety information"
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
    "water_overview" -> "waterLogs"
    "meal_detail" -> "mealLogs"
    "medication_overview" -> "medications"
    "lab_reports" -> "labReports"
    "weekly_report" -> "weeklyReports"
    "sugar_story", "insight_detail" -> "sugarStories"
    "consultation_detail" -> "consultations"
    "program_checklist" -> "checklistLogs"
    "diet_plan", "yoga_plan", "yoga_detail", "naturopathy_plan", "naturopathy_detail" -> "programPlans"
    "expert_notes" -> "expertNotes"
    "articles", "article_detail" -> "contentItems"
    "products", "product_detail", "store_home" -> "products"
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
    "waterLogs" -> "${values["glasses"] ?: 1} glass added"
    "medications" -> values["name"]?.toString() ?: "Medication"
    "labReports" -> values["reportType"]?.toString() ?: "Lab report"
    "weeklyReports" -> "Weekly report"
    "sugarStories" -> values["title"]?.toString() ?: "Sugar pattern"
    "consultations" -> values["consultationType"]?.toString() ?: "Consultation"
    "programPlans" -> values["title"]?.toString() ?: "Care plan"
    "expertNotes" -> values["category"]?.toString()?.replaceFirstChar(Char::uppercase) ?: "Expert note"
    "contentItems" -> values["title"]?.toString() ?: "Health article"
    "products" -> values["name"]?.toString() ?: "Product"
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
