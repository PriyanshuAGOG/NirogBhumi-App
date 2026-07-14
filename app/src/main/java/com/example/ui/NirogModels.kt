package com.nirogbhumi.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nirogbhumi.app.data.FirebaseHealthRepository
import com.nirogbhumi.app.data.HealthRepository
import com.nirogbhumi.app.data.CloudDocument

// Version of the consent notice/policies the user agrees to. Stored on every
// consent receipt and on users/{uid}.consent so we can prove what was agreed
// to and, when this bumps after a material policy change, ask for fresh
// consent (DPDP Act 2023). Bump this string when the notice materially changes.
const val CONSENT_VERSION = "2025-07"

// Data Models
data class SugarLog(
    val id: Int,
    val value: Int,
    val type: String, // "Fasting" or "Post-meal"
    val time: String,
    val status: String, // "High", "Normal" or "Low"
    // Defaults to "now" so the existing optimistic quick-log call sites (which
    // log the instant a reading is entered) don't need updating - only the
    // Firestore-sync call sites pass the reading's real measuredAt, which the
    // 7/30/90-day trend chart needs for real day-bucketing instead of relying
    // on the pre-formatted display string in `time`.
    val measuredAtMillis: Long = System.currentTimeMillis()
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
    var selectedDocumentId by mutableStateOf("")
    var selectedDocumentValues by mutableStateOf<Map<String, Any?>>(emptyMap())
    var activeProfileId by mutableStateOf("")
    var pendingConsultationId by mutableStateOf("")
    var pendingDeepLink by mutableStateOf("")
    var legalReturnRoute by mutableStateOf("profile")
    // Which Legal Center accordion section to open with, e.g. jumping
    // straight to "Privacy Policy" from a specific link instead of always
    // landing on the default "Medical Disclaimer" section.
    var legalInitialSection by mutableStateOf<String?>(null)
    // True only once the member has actually gone through
    // HealthProfileSetupScreen's "Continue" (not "Skip") - the four
    // selectedDiabetesStatus/selectedBpStatus/selectedOnMedication/
    // selectedDoctorSupervision fields can't tell this apart on their own,
    // since their skip-time defaults ("None"/"Normal"/"No"/"Yes") are also
    // legitimate real answers someone could deliberately choose.
    var healthProfileCompleted by mutableStateOf(false)
    // Where HealthProfileSetupScreen's back/Continue/Skip should land when
    // it's re-entered later from Profile's "Complete your health profile"
    // nudge, instead of always continuing into goal_selection - blank means
    // "still in the original onboarding chain."
    var healthProfileReturnRoute by mutableStateOf("")
    var currentScreen by mutableStateOf("splash") // "splash", "welcome", "value_slides", "consent", "login_mobile", "login_otp", "email_auth", "password_reset", "setup_profile", "selection_caregiver", "health_profile_setup", "goal_selection", "program_code_optional", "onboarding_complete", "dashboard", "sugar_detail", "consult_stepper", "active_journey"
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
    // Optional, separate, off-by-default consents (DPDP Act: optional consent
    // must be distinct from required consent and independently withdrawable).
    // Toggled in Privacy & consent; hydrated from users/{uid}.consent on load.
    var consentResearch by mutableStateOf(false)
    var consentMarketing by mutableStateOf(false)
    var isTrackingForSelf by mutableStateOf(true) // true for Myself, false for Family Member

    // Profile Details
    var profileName by mutableStateOf("")
    var profileAge by mutableStateOf("28")
    var profileGender by mutableStateOf("Male")
    var profileHeight by mutableStateOf("174")
    var profileWeight by mutableStateOf("72")
    var profileCity by mutableStateOf("Jaipur")
    var profileLanguage by mutableStateOf("English")
    // Private (users/{uid}/profile-photo/... - readable only by the owner or
    // an admin, same as any other private upload). Not yet surfaced to other
    // members (e.g. as a chat sender avatar) - that would need a public-read
    // Storage path, which is a deliberate separate decision from "let me set
    // my own profile picture."
    var photoUrl by mutableStateOf("")

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
    // ProgramCodeOptionalScreen is shared between first-time onboarding and the
    // Care+ "Enter program code" unlock reached mid-session - this tells it which
    // context it's in so back/success routing doesn't dump an existing user back
    // into the middle of onboarding.
    var enteringProgramCodeFromCarePlus by mutableStateOf(false)
    var activeProgramId by mutableStateOf("")
    var activeProgramName by mutableStateOf("")
    var programDurationDays by mutableStateOf(0L)
    var programStartedAtMillis by mutableStateOf(0L)

    // Care+ (Announcements/Chat) admin capability - resolved from the signed-in user's
    // Firebase custom claims, not a client-trusted flag, so it can only ever reflect
    // what the server actually granted. True for admin and super_admin.
    var isAdmin by mutableStateOf(false)
    // Raw role claim ("", "user", "coach", "admin", "super_admin").
    var staffRole by mutableStateOf("")
    // Program IDs this coach is the assigned coachId for (per the `programs`
    // read rule: coach() && coachId == uid) - empty for non-coaches. Lets
    // announcement/pin actions recognize the actual per-batch program
    // manager, not just the platform-wide admin role.
    var coachProgramIds by mutableStateOf(setOf<String>())

    // Active Tab under Dashboard
    var activeTab by mutableStateOf("Today") // "Today", "Track", "Insights", "Care", "Learn"

    // User Metrics State - starts at nil/zero until the user logs a real reading
    var fastingSugarValue by mutableStateOf(0)
    var sleepHours by mutableStateOf(0)
    var sleepMinutes by mutableStateOf(0)
    var stepsLogged by mutableStateOf(0)
    var latestBpReading by mutableStateOf<String?>(null)

    // True once ANY real reading (sugar/BP/weight) has been logged today - the one
    // shared signal the Today/Track prompts and Rhythm agree on, so a completed
    // check-in never keeps re-prompting with an empty-feeling "do this now" card.
    var checkedInToday by mutableStateOf(false)
    // Lets a quick-log entry point (a chip, a tile's "+" ) jump the Daily Check-in
    // wizard straight to the relevant step instead of starting over at sugar.
    var checkinStartStep by mutableStateOf(0)
    // One-time "N walks logged" milestone moment - set right after the timed
    // walk that crosses a threshold, consumed (and cleared) by the first
    // screen that shows it, same one-shot pattern as the check-in streak
    // milestone in CheckInFlow.kt.
    var walkMilestoneCount by mutableStateOf<Long?>(null)

    // Sugar History & Tracking State - populated only from real Firestore reads
    val sugarLogs = mutableStateListOf<SugarLog>()

    // Log FASTING sugar bottom sheet state
    var isQuickLogFastingOpen by mutableStateOf(false)
    var quickLogFastingValue by mutableStateOf(100)

    // Daily Checklist State
    var dailyRitualsCompleted = mutableStateListOf<String>()

    // Active Experiment State - startedAtMillis anchors real progress
    // (nights of 7+ hours actually logged since start), not just a manually
    // incremented day counter disconnected from real sleep data.
    var isExperimentActive by mutableStateOf(false)
    var experimentStartedAtMillis by mutableStateOf(0L)

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

    // Introduction Tour State
    var shouldShowTour by mutableStateOf(true)
    var currentTourStep by mutableStateOf(0)

    // Self-Update System State
    var availableUpdate by mutableStateOf<com.nirogbhumi.app.update.UpdateInfo?>(null)
    var updateDownloadState by mutableStateOf<com.nirogbhumi.app.update.DownloadState>(com.nirogbhumi.app.update.DownloadState.Idle)
    var activeDownloadId by mutableStateOf<Long?>(null)
    var updateCheckBusy by mutableStateOf(false)
    var updateCheckError by mutableStateOf("")
}

/** Mirrors the programStaff() Firestore rule (admin() || assignedCoach(programId)). */
fun NirogState.canManageProgram(programId: String): Boolean =
    isAdmin || (staffRole == "coach" && programId.isNotBlank() && coachProgramIds.contains(programId))
