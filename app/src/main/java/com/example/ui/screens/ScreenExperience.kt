package com.nirogbhumi.app.ui.screens

enum class FieldType { TEXT, NUMBER, DECIMAL, TIME, DATE, MULTILINE }

data class FormFieldSpec(
    val key: String,
    val label: String,
    val type: FieldType = FieldType.TEXT,
    val required: Boolean = true,
    val suffix: String? = null
)

data class ScreenExperience(
    val fields: List<FormFieldSpec> = emptyList(),
    val choices: List<String> = emptyList(),
    val checklist: List<String> = emptyList(),
    val writeCollection: String? = null,
    val successRoute: String? = null
)

val ScreenExperiences = mapOf(
    "quick_sugar" to ScreenExperience(
        fields = listOf(FormFieldSpec("value", "Fasting sugar", FieldType.NUMBER, suffix = "mg/dL"), FormFieldSpec("time", "Time", FieldType.TIME), FormFieldSpec("note", "Optional note", FieldType.MULTILINE, false)),
        writeCollection = "glucoseReadings", successRoute = "dashboard"
    ),
    "quick_water" to ScreenExperience(choices = listOf("+1 glass", "+250 ml", "Undo last"), writeCollection = "waterLogs", successRoute = "water_overview"),
    "quick_walk" to ScreenExperience(choices = listOf("5 min", "10 min", "15 min", "30 min", "Custom"), checklist = listOf("After breakfast", "After lunch", "After dinner", "Normal walk"), writeCollection = "walkLogs", successRoute = "walking_overview"),
    "add_sugar" to ScreenExperience(
        fields = listOf(FormFieldSpec("value", "Reading", FieldType.NUMBER, suffix = "mg/dL"), FormFieldSpec("date", "Date", FieldType.DATE), FormFieldSpec("time", "Time", FieldType.TIME), FormFieldSpec("meal", "Linked meal", required = false), FormFieldSpec("notes", "Notes", FieldType.MULTILINE, false)),
        choices = listOf("Fasting", "Post breakfast", "Post lunch", "Post dinner", "Random", "HbA1c"), writeCollection = "glucoseReadings", successRoute = "sugar_detail"
    ),
    "add_bp" to ScreenExperience(
        fields = listOf(FormFieldSpec("systolic", "Systolic", FieldType.NUMBER, suffix = "mmHg"), FormFieldSpec("diastolic", "Diastolic", FieldType.NUMBER, suffix = "mmHg"), FormFieldSpec("pulse", "Pulse", FieldType.NUMBER, suffix = "bpm"), FormFieldSpec("notes", "Notes", FieldType.MULTILINE, false)),
        choices = listOf("Morning", "Evening", "After walk", "After yoga", "Feeling unwell"), writeCollection = "bpReadings", successRoute = "bp_overview"
    ),
    "add_sleep" to ScreenExperience(
        fields = listOf(FormFieldSpec("sleepTime", "Sleep time", FieldType.TIME), FormFieldSpec("wakeTime", "Wake time", FieldType.TIME), FormFieldSpec("duration", "Duration", FieldType.DECIMAL, suffix = "hours"), FormFieldSpec("notes", "Notes", FieldType.MULTILINE, false)),
        choices = listOf("Good", "Average", "Poor"), checklist = listOf("Late dinner", "Screen before sleep"), writeCollection = "sleepLogs", successRoute = "sleep_overview"
    ),
    "add_meal" to ScreenExperience(
        fields = listOf(FormFieldSpec("mealTime", "Meal time", FieldType.TIME), FormFieldSpec("foods", "Foods eaten", FieldType.MULTILINE), FormFieldSpec("linkedSugar", "Linked sugar reading", required = false), FormFieldSpec("notes", "Notes", FieldType.MULTILINE, false)),
        choices = listOf("Breakfast", "Lunch", "Dinner", "Tea / snack"), checklist = listOf("Roti", "Rice", "Dal", "Sabzi", "Poha", "Upma", "Paratha", "Chai", "Fruit", "Salad", "Outside food"), writeCollection = "mealLogs", successRoute = "food_journal"
    ),
    "add_medicine" to ScreenExperience(
        fields = listOf(FormFieldSpec("name", "Medicine name"), FormFieldSpec("dose", "Dose", required = false), FormFieldSpec("frequency", "Frequency"), FormFieldSpec("times", "Reminder times"), FormFieldSpec("notes", "Notes", FieldType.MULTILINE, false)),
        checklist = listOf("Enable reminders"), writeCollection = "medications", successRoute = "medication_overview"
    ),
    "upload_lab" to ScreenExperience(
        fields = listOf(FormFieldSpec("reportType", "Report type"), FormFieldSpec("reportDate", "Report date", FieldType.DATE), FormFieldSpec("labName", "Lab name"), FormFieldSpec("manualValues", "Important values", FieldType.MULTILINE, false), FormFieldSpec("notes", "Notes", FieldType.MULTILINE, false)),
        choices = listOf("HbA1c", "Lipid profile", "Kidney function", "Other"), writeCollection = "labReports", successRoute = "lab_reports"
    ),
    "pre_consultation" to ScreenExperience(
        fields = listOf(FormFieldSpec("concern", "Primary concern", FieldType.MULTILINE), FormFieldSpec("recentMetrics", "Recent readings", FieldType.MULTILINE, false), FormFieldSpec("medications", "Current medication", FieldType.MULTILINE, false)),
        checklist = listOf("Share my recent health logs", "I understand this is not emergency care"), writeCollection = "consultations", successRoute = "payment_confirmation"
    ),
    "add_family" to ScreenExperience(
        fields = listOf(FormFieldSpec("name", "Full name"), FormFieldSpec("relationship", "Relationship"), FormFieldSpec("age", "Age", FieldType.NUMBER), FormFieldSpec("city", "City", required = false)),
        choices = listOf("No diabetes", "Prediabetes", "Type 2 diabetes", "Type 1 diabetes", "Not sure"), checklist = listOf("I have permission to manage this profile"), writeCollection = "profiles", successRoute = "family_profiles"
    ),
    "checkout" to ScreenExperience(
        fields = listOf(FormFieldSpec("name", "Recipient name"), FormFieldSpec("phone", "Mobile number", FieldType.NUMBER), FormFieldSpec("address", "Delivery address", FieldType.MULTILINE), FormFieldSpec("pincode", "PIN code", FieldType.NUMBER), FormFieldSpec("city", "City"), FormFieldSpec("state", "State")),
        choices = listOf("UPI / Razorpay", "Pay on delivery (when available)"), checklist = listOf("I agree to shipping and refund terms"), writeCollection = "orders", successRoute = "order_success"
    ),
    "notification_settings" to ScreenExperience(
        fields = listOf(FormFieldSpec("quietHoursStart", "Quiet hours start", FieldType.TIME), FormFieldSpec("quietHoursEnd", "Quiet hours end", FieldType.TIME)),
        checklist = listOf("Fasting sugar reminder", "BP reminder", "Post-meal walk reminder", "Water reminder", "Medication reminder", "Sleep reminder", "Consultation updates", "Program reminders", "Order updates")
    ),
    "privacy_consent" to ScreenExperience(checklist = listOf("Health data storage", "Expert review when care is requested", "Consultation data", "Device data import", "Anonymized research (optional)", "Marketing notifications (optional)")),
    "health_connect_permissions" to ScreenExperience(checklist = listOf("Steps", "Sleep", "Heart rate", "Weight", "Blood glucose", "Blood pressure")),
    "program_checklist" to ScreenExperience(checklist = listOf("Log fasting sugar", "Walk after a meal", "Complete yoga", "Dinner before 8:30 PM", "Follow today’s food plan"), writeCollection = "checklistLogs", successRoute = "active_journey")
    ,"support" to ScreenExperience(
        fields = listOf(FormFieldSpec("subject", "How can we help?"), FormFieldSpec("message", "Describe the issue", FieldType.MULTILINE)),
        choices = listOf("App help", "Account and privacy", "Consultation", "Program", "Order and delivery", "Report a safety concern"),
        writeCollection = "supportRequests", successRoute = "profile"
    )
)

private val RouteDestinations = mapOf(
    "track_hub" to mapOf("Blood Sugar" to "sugar_detail", "Blood Pressure" to "bp_overview", "Sleep" to "sleep_overview", "Walking" to "walking_overview", "Water" to "water_overview", "Food Impact" to "food_journal", "Medication" to "medication_overview", "Lab Reports" to "lab_reports"),
    "sugar_detail" to mapOf("Latest fasting sugar" to "sugar_reading_detail", "History" to "sugar_reading_detail"),
    "insights_hub" to mapOf("Weekly Report" to "weekly_report", "Sugar Story" to "sugar_story", "30-Day Trends" to "trends_30", "Share with Expert" to "share_report"),
    "care_hub" to mapOf("Consultations" to "consultation_types", "Program Mode" to "active_journey", "Expert notes" to "expert_notes", "Upcoming care" to "consultation_detail"),
    "consultation_types" to mapOf("Diabetes lifestyle" to "consult_stepper", "Diet review" to "consult_stepper", "Yoga" to "consult_stepper", "Naturopathy" to "consult_stepper", "Follow-up" to "consult_stepper"),
    "active_journey" to mapOf("Today’s checklist" to "program_checklist", "Plans" to "diet_plan", "Expert note" to "expert_notes"),
    "learn_hub" to mapOf("Diabetes" to "articles", "Food" to "articles", "Sleep" to "articles", "Yoga" to "articles", "Naturopathy" to "articles", "BP" to "articles"),
    "articles" to mapOf("2-minute reads" to "article_detail", "Saved articles" to "article_detail", "Recommended for you" to "article_detail"),
    "store_home" to mapOf("Naturopathy" to "products", "Yoga" to "products", "Wellness kits" to "products", "Orders" to "orders"),
    "products" to mapOf("Category" to "product_detail", "Price" to "product_detail", "Availability" to "product_detail", "Safety note" to "product_detail"),
    "cart" to mapOf("Items" to "product_detail", "Delivery" to "checkout", "Total" to "checkout"),
    "family_profiles" to mapOf("My profile" to "dashboard", "Parent" to "family_dashboard", "Spouse" to "family_dashboard", "Permissions" to "privacy_consent"),
    "device_hub" to mapOf("Health Connect" to "health_connect_permissions", "HealthKit" to "device_sync", "Samsung Health" to "device_sync", "Fitbit" to "device_sync", "Manual device" to "device_sync"),
    "profile" to mapOf("Personal details" to "profile_edit", "Family profiles" to "family_profiles", "Devices" to "device_hub", "Orders" to "orders", "Legal" to "legal_center")
)

fun destinationFor(route: String, item: String): String? = RouteDestinations[route]?.get(item)
fun declaredDestinationRoutes(): Set<String> = RouteDestinations.values.flatMap { it.values }.toSet() + PrimaryDestinations.values

val PrimaryDestinations = mapOf(
    "today_empty" to "quick_sugar", "today_program" to "active_journey", "track_hub" to "sugar_detail",
    "sugar_detail" to "add_sugar", "sugar_reading_detail" to "share_report", "bp_overview" to "add_bp",
    "sleep_overview" to "add_sleep", "walking_overview" to "quick_walk", "walk_timer" to "walking_overview",
    "water_overview" to "quick_water", "food_journal" to "add_meal", "meal_detail" to "food_journal",
    "medication_overview" to "add_medicine", "lab_reports" to "upload_lab", "insights_hub" to "weekly_report",
    "weekly_report" to "share_report", "sugar_story" to "insight_detail", "trends_30" to "share_report",
    "share_report" to "care_hub", "care_hub" to "consultation_types", "consultation_types" to "consult_stepper",
    "payment_confirmation" to "consultation_confirmed", "consultation_confirmed" to "consultation_detail",
    "program_locked" to "program_code_optional", "active_journey" to "program_checklist", "diet_plan" to "program_checklist",
    "yoga_plan" to "yoga_detail", "yoga_detail" to "program_checklist", "naturopathy_plan" to "naturopathy_detail",
    "naturopathy_detail" to "program_checklist", "expert_notes" to "active_journey", "learn_hub" to "articles",
    "articles" to "article_detail", "article_detail" to "learn_hub", "store_home" to "products", "products" to "product_detail",
    "product_detail" to "cart", "cart" to "checkout", "order_success" to "order_detail", "orders" to "order_detail",
    "order_detail" to "support", "family_profiles" to "add_family", "family_dashboard" to "family_profiles",
    "device_hub" to "health_connect_permissions", "health_connect_permissions" to "device_sync", "device_sync" to "profile",
    "profile" to "profile_edit", "notification_settings" to "profile", "privacy_consent" to "profile", "support" to "profile"
)

private val ExplicitBackDestinations = mapOf(
    "quick_sugar" to "dashboard", "quick_water" to "dashboard", "quick_walk" to "dashboard",
    "add_sugar" to "sugar_detail", "sugar_reading_detail" to "sugar_detail", "add_bp" to "bp_overview",
    "add_sleep" to "sleep_overview", "walk_timer" to "walking_overview", "add_meal" to "food_journal",
    "meal_detail" to "food_journal", "add_medicine" to "medication_overview", "upload_lab" to "lab_reports",
    "insight_detail" to "sugar_story", "share_report" to "weekly_report", "pre_consultation" to "consult_stepper",
    "payment_confirmation" to "consult_stepper", "consultation_confirmed" to "care_hub", "consultation_detail" to "care_hub",
    "program_checklist" to "active_journey", "yoga_detail" to "yoga_plan", "naturopathy_detail" to "naturopathy_plan",
    "article_detail" to "articles", "product_detail" to "products", "cart" to "store_home", "checkout" to "cart",
    "order_success" to "orders", "order_detail" to "orders", "add_family" to "family_profiles",
    "family_dashboard" to "family_profiles", "health_connect_permissions" to "device_hub", "device_sync" to "device_hub",
    "profile_edit" to "profile"
)

fun backDestinationFor(spec: ScreenSpec): String = ExplicitBackDestinations[spec.route] ?: when (spec.section) {
    "Today" -> "dashboard"
    "Track" -> "dashboard"
    "Insights" -> if (spec.route == "insights_hub") "dashboard" else "insights_hub"
    "Care" -> if (spec.route == "care_hub") "dashboard" else "care_hub"
    "Program" -> if (spec.route == "active_journey") "care_hub" else "active_journey"
    "Learn" -> if (spec.route == "learn_hub") "dashboard" else "learn_hub"
    "Store" -> if (spec.route == "store_home") "learn_hub" else "store_home"
    "Family", "Devices", "Settings" -> "profile"
    else -> "dashboard"
}
