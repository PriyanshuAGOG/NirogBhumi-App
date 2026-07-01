package com.nirogbhumi.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nirogbhumi.app.data.FirebaseHealthRepository
import com.nirogbhumi.app.data.HealthRepository
import com.nirogbhumi.app.data.CloudDocument

// Data Models
data class SugarLog(
    val id: Int,
    val value: Int,
    val type: String, // "Fasting" or "Post-meal"
    val time: String,
    val status: String // "High", "Normal" or "Low"
)

data class StoreProduct(
    val id: Int,
    val name: String,
    val subtitle: String,
    val price: String,
    val imageDescription: String
)

data class ConsultationSlot(
    val date: String,
    val time: String
)

// Shared Memory State Manager
class NirogState {
    val repository: HealthRepository = FirebaseHealthRepository()
    var cloudMessage by mutableStateOf("")
    val formValues = mutableStateMapOf<String, String>()
    val routeSelections = mutableStateMapOf<String, String>()
    val checkedItems = mutableStateListOf<String>()
    val cloudRecords = mutableStateMapOf<String, List<CloudDocument>>()
    val cartItems = mutableStateMapOf<String, Int>()
    var selectedDocumentId by mutableStateOf("")
    var selectedDocumentValues by mutableStateOf<Map<String, Any?>>(emptyMap())
    var activeProfileId by mutableStateOf("")
    var pendingConsultationId by mutableStateOf("")
    var pendingOrderId by mutableStateOf("")
    var pendingPaymentKind by mutableStateOf("")
    var pendingDeepLink by mutableStateOf("")
    var legalReturnRoute by mutableStateOf("profile")
    var currentScreen by mutableStateOf("splash") // "splash", "welcome", "value_slides", "consent", "login_mobile", "login_otp", "email_auth", "password_reset", "setup_profile", "selection_caregiver", "health_profile_setup", "goal_selection", "program_code_optional", "onboarding_complete", "dashboard", "sugar_detail", "food_journal", "consult_stepper", "active_journey"
    var viewMode by mutableStateOf("mobile") // "mobile", "admin", "expert"

    // Auth & Intake State
    var userMobile by mutableStateOf("")
    var userEmail by mutableStateOf("")
    var userPassword by mutableStateOf("")
    var isSignUpMode by mutableStateOf(false)
    var resetEmailSent by mutableStateOf(false)
    var otpVerificationId by mutableStateOf("")
    var authError by mutableStateOf("")
    var authBusy by mutableStateOf(false)
    var consentHealthData by mutableStateOf(false)
    var consentExpertReview by mutableStateOf(false)
    var consentMedicalDisclaimer by mutableStateOf(false)
    var isTrackingForSelf by mutableStateOf(true) // true for Myself, false for Family Member

    // Profile Details
    var profileName by mutableStateOf("Priyanshu")
    var profileAge by mutableStateOf("28")
    var profileGender by mutableStateOf("Male")
    var profileHeight by mutableStateOf("174")
    var profileWeight by mutableStateOf("72")
    var profileCity by mutableStateOf("Jaipur")
    var profileLanguage by mutableStateOf("English")

    // Health Details Setup
    var selectedDiabetesStatus by mutableStateOf("None")
    var selectedBpStatus by mutableStateOf("Normal")
    var selectedOnMedication by mutableStateOf("No")
    var selectedDoctorSupervision by mutableStateOf("Yes")
    var selectedGoal by mutableStateOf("Manage blood sugar levels")
    val selectedGoals = mutableStateListOf<String>().apply {
        add("Control sugar")
        add("Improve lifestyle")
    }

    // Program Code Storing
    var programCodeInput by mutableStateOf("")
    var isProgramActive by mutableStateOf(false)

    // Active Tab under Dashboard
    var activeTab by mutableStateOf("Today") // "Today", "Track", "Insights", "Care", "Learn"

    // User Metrics State - starts at nil/zero until the user logs a real reading
    var fastingSugarValue by mutableStateOf(0)
    var sleepHours by mutableStateOf(0)
    var sleepMinutes by mutableStateOf(0)
    var stepsLogged by mutableStateOf(0)
    var waterGlasses by mutableStateOf(0)
    var latestBpReading by mutableStateOf<String?>(null)

    // Sugar History & Tracking State - populated only from real Firestore reads
    val sugarLogs = mutableStateListOf<SugarLog>()

    // Log FASTING sugar bottom sheet state
    var isQuickLogFastingOpen by mutableStateOf(false)
    var quickLogFastingValue by mutableStateOf(100)

    // Daily Checklist State
    var dailyRitualsCompleted = mutableStateListOf<String>()

    // Active Experiment State
    var isExperimentActive by mutableStateOf(false)
    var experimentDayCount by mutableStateOf(1)

    // Active Journey Protocol State
    var activeJourneyProgress by mutableStateOf(0)
    val completedProtocols = mutableStateListOf<String>()

    // Book Consultation State
    var consultStep by mutableStateOf(1) // 1: Service, 2: Slot & Form, 3: Success
    var selectedConsultType by mutableStateOf("diabetes_lifestyle")
    var selectedConsultDate by mutableStateOf("Thu 14")
    var selectedConsultTime by mutableStateOf("10:30 AM")
    var selectedConsultSlotId by mutableStateOf("")
    var userConcernText by mutableStateOf("")
    var userRecentMetricText by mutableStateOf("")

    // Learn Section State
    var searchQuery by mutableStateOf("")
    val cartProducts = mutableStateListOf<Int>() // Product IDs currently added to Cart
    var selectedArticleTitle: String? = null // For detailed reading preview modal

    // Introduction Tour State
    var shouldShowTour by mutableStateOf(true)
    var currentTourStep by mutableStateOf(0)
}
