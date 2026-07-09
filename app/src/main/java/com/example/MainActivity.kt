package com.nirogbhumi.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.screens.*
import com.nirogbhumi.app.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

private const val IS_PRODUCTION_APK = true

// MainActivity is exported (it's the launcher), so any other app on the
// device can start it with an arbitrary "route" extra. The only legitimate
// producers of this extra are internal (NirogMessagingService's fixed
// type->route map, ReminderWorker, EventReminderWorker) and only ever emit
// one of these values - validating against this allowlist stops a
// crafted external intent from forcing navigation to an arbitrary internal
// screen key (e.g. skipping past the consent screen).
private val DEEP_LINK_ROUTES = setOf(
  "dashboard", "weekly_report", "consultation_detail", "active_journey",
  "order_detail", "expert_notes", "program_calendar", "announcements",
)
private fun sanitizedRoute(raw: String?): String = raw?.takeIf { it in DEEP_LINK_ROUTES } ?: ""

class MainActivity : ComponentActivity() {
  private val nirogState by lazy { NirogState() }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Lets testers get prompted to install new builds from inside the app itself,
    // instead of depending on the separate "Firebase App Tester" companion app or
    // an easily-missed email - this is what the debug distribution channel is for.
    if (BuildConfig.DEBUG) {
      com.google.firebase.appdistribution.FirebaseAppDistribution.getInstance()
        .updateIfNewReleaseAvailable()
        .addOnFailureListener { /* not signed in as a tester yet, or no newer release - nothing to show */ }
    }
    nirogState.pendingDeepLink = sanitizedRoute(intent.getStringExtra("route"))
    applyQuickLogWidgetAction(intent)
    val tourSeen = getSharedPreferences("nirog_prefs", MODE_PRIVATE).getBoolean("onboarding_tour_seen", false)
    nirogState.shouldShowTour = !tourSeen
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val state = nirogState
        val snackbarHostState = remember { SnackbarHostState() }

        // Every cloud read/write failure across the app (chat send, reactions,
        // photo/voice upload, profile edits, ...) sets state.cloudMessage - this
        // is the single global place that surfaces it as a Snackbar. Error
        // reporting to the admin console happens at the repository layer now
        // (every CloudResult.Failure reports itself at its own source, tagged
        // with the exact operation that failed) rather than here, since this
        // choke point also fires for non-error status messages (e.g. "Synced
        // securely") that would otherwise get logged as if they were failures.
        LaunchedEffect(state.cloudMessage) {
          if (state.cloudMessage.isNotBlank()) {
            val message = state.cloudMessage
            state.cloudMessage = ""
            snackbarHostState.showSnackbar(message)
          }
        }

        Scaffold(
          modifier = Modifier.fillMaxSize(),
          // This shell has no topBar/bottomBar of its own - every real screen underneath
          // (MainHub's Scaffold + bottom nav bar, CatalogScreen's Scaffold, etc.) already
          // reserves status/navigation-bar insets itself. Letting this outer Scaffold also
          // default to WindowInsets.safeDrawing double-reserves the bottom inset, which is
          // what was pushing the bottom nav bar up and leaving an empty gap beneath it.
          contentWindowInsets = WindowInsets(0, 0, 0, 0),
          snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(innerPadding)
          ) {
            if (!IS_PRODUCTION_APK) {
              // DEVELOPER BAR OVERVIEW SWITCHER
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(8.dp)
                  .background(Color(0xFFF1EDE6)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEEE8DC).copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                      onClick = { state.viewMode = "mobile" },
                      colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.viewMode == "mobile") Color(0xFF314936) else Color.Transparent,
                        contentColor = if (state.viewMode == "mobile") Color.White else Color(0xFF1B3221)
                      ),
                      shape = RoundedCornerShape(8.dp),
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                      Text("Mobile Emulator", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                      onClick = { state.viewMode = "admin" },
                      colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.viewMode == "admin") Color(0xFF314936) else Color.Transparent,
                        contentColor = if (state.viewMode == "admin") Color.White else Color(0xFF1B3221)
                      ),
                      shape = RoundedCornerShape(8.dp),
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                      Text("Admin Dashboard Web (14 Screens)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                      onClick = { state.viewMode = "expert" },
                      colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.viewMode == "expert") Color(0xFF314936) else Color.Transparent,
                        contentColor = if (state.viewMode == "expert") Color.White else Color(0xFF1B3221)
                      ),
                      shape = RoundedCornerShape(8.dp),
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                      Text("Expert Consultation Port", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                  }

                  // Quick jump controls inside emulator views
                  if (state.viewMode == "mobile") {
                    Row(
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text("EMULATOR STATES:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3221).copy(alpha = 0.6f))

                      TextButton(
                        onClick = { state.currentScreen = "splash" },
                        colors = ButtonDefaults.textButtonColors(contentColor = if (state.currentScreen == "splash") Color(0xFF314936) else Color.Gray)
                      ) {
                        Text("Splash", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                      }

                      TextButton(
                        onClick = { state.currentScreen = "welcome" },
                        colors = ButtonDefaults.textButtonColors(contentColor = if (state.currentScreen == "welcome") Color(0xFF314936) else Color.Gray)
                      ) {
                        Text("Welcome", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                      }

                      TextButton(
                        onClick = { state.currentScreen = "email_auth" },
                        colors = ButtonDefaults.textButtonColors(contentColor = if (state.currentScreen == "email_auth") Color(0xFF314936) else Color.Gray)
                      ) {
                        Text("Email Auth", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                      }

                      TextButton(
                        onClick = { state.currentScreen = "empty_state" },
                        colors = ButtonDefaults.textButtonColors(contentColor = if (state.currentScreen == "empty_state") Color(0xFF314936) else Color.Gray)
                      ) {
                        Text("Empty State", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                      }

                      TextButton(
                        onClick = { state.currentScreen = "error_state" },
                        colors = ButtonDefaults.textButtonColors(contentColor = if (state.currentScreen == "error_state") Color(0xFF314936) else Color.Gray)
                      ) {
                        Text("Error State", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                      }

                      TextButton(
                        onClick = { state.currentScreen = "offline_state" },
                        colors = ButtonDefaults.textButtonColors(contentColor = if (state.currentScreen == "offline_state") Color(0xFF314936) else Color.Gray)
                      ) {
                        Text("Offline State", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                      }

                      TextButton(
                        onClick = {
                          state.currentScreen = "critical_reading_caution"
                          state.fastingSugarValue = 192
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = if (state.currentScreen == "critical_reading_caution") Color(0xFF314936) else Color.Gray)
                      ) {
                        Text("Critical Alert", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                      }
                    }
                  }
                }
              }
            }

            // INNER MASTER RENDERER AREA
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
            ) {
              if (IS_PRODUCTION_APK) {
                // Actual fullscreen presentation on real devices
                Box(modifier = Modifier.fillMaxSize()) {
                  ActiveScreenContent(state)
                }
              } else {
                when (state.viewMode) {
                  "admin" -> AdminWebDashboard(state)
                  "expert" -> ExpertWebDashboard(state)
                  else -> {
                    // Mobile Simulator Center Area
                    Box(
                      modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF1EDE6)),
                      contentAlignment = Alignment.Center
                    ) {
                      Card(
                        modifier = Modifier
                          .width(390.dp)
                          .height(820.dp)
                          .border(6.dp, Color(0xFF1B2219), RoundedCornerShape(32.dp)),
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                      ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                          ActiveScreenContent(state)
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
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    sanitizedRoute(intent.getStringExtra("route")).takeIf { it.isNotBlank() }?.let {
      nirogState.pendingDeepLink = it
      nirogState.currentScreen = it
    }
    applyQuickLogWidgetAction(intent)
  }

  // The home-screen widget's two buttons launch this same Activity with one
  // of these fixed action strings instead of an arbitrary extra - jumps
  // straight to an existing quick-log entry point rather than the widget
  // trying to capture a precise health value itself with no way to review
  // or correct it before saving.
  private fun applyQuickLogWidgetAction(intent: Intent) {
    when (intent.action) {
      com.nirogbhumi.app.widget.ACTION_OPEN_QUICK_LOG_SUGAR -> nirogState.isQuickLogFastingOpen = true
      com.nirogbhumi.app.widget.ACTION_OPEN_QUICK_LOG_BP -> {
        nirogState.checkinStartStep = 1
        nirogState.currentScreen = "daily_checkin"
      }
    }
  }
}

@Composable
fun ActiveScreenContent(state: NirogState) {
  val context = LocalContext.current
  LaunchedEffect(state.repository.userId) {
    if (state.repository.userId != null) {
      runCatching { com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> state.repository.saveProfile(mapOf("fcmToken" to token, "fcmTokenUpdatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())) {} } }
    }
  }
  LaunchedEffect(state.currentScreen) {
    runCatching {
      com.google.firebase.analytics.FirebaseAnalytics.getInstance(context).logEvent("screen_view", android.os.Bundle().apply {
        putString("screen_name", state.currentScreen)
        putString("screen_class", "MainActivity")
      })
    }
  }
  UpdateLifecycleEffects(state)
  // Single, high-leverage inset fix: every screen dispatched below used to
  // handle (or, in ~40 of 45 cases, simply not handle) its own status-bar/
  // nav-bar/keyboard insets individually, which is why headers looked "too
  // raised into the top" and bottom content/inputs sat flush against (or
  // under) the gesture nav bar or keyboard. "dashboard" (MainHub) is the one
  // exception - it already reserves its own insets correctly via its inner
  // Scaffold's topBar/bottomBar, so wrapping it again here would double-pad it.
  val selfManagesInsets = state.currentScreen == "dashboard"
  Box(
    modifier = Modifier
      .fillMaxSize()
      .then(
        if (selfManagesInsets) Modifier
        else Modifier.statusBarsPadding().navigationBarsPadding().imePadding()
      )
  ) {
    when (state.currentScreen) {
      "splash" -> SplashScreen(state)
      "welcome" -> WelcomeScreen(state)
      "value_slides" -> ValueSlidesScreen(state)
      "consent" -> ConsentScreen(state)
      "login_mobile" -> LoginMobileScreen(state)
      "login_otp" -> LoginOtpScreen(state)
      "email_auth" -> EmailAuthScreen(state)
      "password_reset" -> PasswordResetScreen(state)
      "setup_profile" -> SetupProfileScreen(state)
      "selection_caregiver" -> SelfOrCaregiverScreen(state)
      "health_profile_setup" -> HealthProfileSetupScreen(state)
      "goal_selection" -> GoalSelectionScreen(state)
      "program_code_optional" -> ProgramCodeOptionalScreen(state)
      "onboarding_complete" -> OnboardingCompleteScreen(state)
      "dashboard" -> MainHub(state)
      "profile" -> ProfileScreen(state)
      "profile_edit" -> ProfileEditScreen(state)
      "developer_settings" -> DeveloperSettingsScreen(state)
      "device_hub" -> DeviceSyncScreen(state)
      "notifications" -> NotificationInboxScreen(state)
      "notification_settings" -> NotificationSettingsScreen(state)
      "family_profiles" -> FamilyProfilesScreen(state)
      "orders" -> OrdersScreen(state)
      "articles" -> ArticlesScreen(state)
      "daily_checkin" -> DailyCheckInScreen(state)
      "body_report" -> BodyReportScreen(state)
      "rhythm" -> RhythmScreen(state)
      "health_file" -> HealthFileScreen(state)
      "bp_overview" -> BpOverviewScreen(state)
      "sleep_overview" -> SleepOverviewScreen(state)
      "walking_overview" -> WalkingActivityScreen(state)
      "program_calendar" -> ProgramCalendarScreen(state)
      "announcements" -> AnnouncementsScreen(state)
      "program_chat" -> ProgramChatScreen(state)
      "chat_hub" -> ChatHubScreen(state)

      // Metrics Detailed screens
      "sugar_detail" -> BloodSugarDetailScreen(state)
      "coming_soon" -> ComingSoonScreen(state)
      "lab_reports" -> LabReportsScreen(state)
      "family_member_detail" -> FamilyMemberDetailScreen(state)
      "data_controls" -> DataControlsScreen(state)
      "privacy_consent" -> PrivacyConsentScreen(state)
      "support" -> SupportScreen(state)
      "consult_stepper" -> BookConsultationStepper(state)
      "active_journey" -> ActiveJourneyScreen(state)
      "insight_detail" -> InsightDetailScreen(state)
      "walk_timer" -> WalkTimerScreen(state)
      "legal_center" -> LegalCenterScreen(state)
      "empty_state" -> MobileEmptyStateView {
        state.currentScreen = "dashboard"
        state.fastingSugarValue = 94
      }
      "error_state" -> MobileErrorStateView { state.currentScreen = "dashboard" }
      "offline_state" -> MobileOfflineStateView { state.currentScreen = "dashboard" }
      "critical_reading_caution" -> MobileCriticalCautionStateView(state.fastingSugarValue) {
        state.currentScreen = "dashboard"
      }
      "screen_directory" -> ScreenDirectory(state)
      else -> CatalogScreen(state, state.currentScreen)
    }

    // Bottom popup sliding HUD for quick sugar logs
    if (state.isQuickLogFastingOpen) {
      QuickLogFastingOverlay(state)
    }

    state.availableUpdate?.let { info ->
      val currentVersionCode = remember { currentVersionCode(context) }
      com.nirogbhumi.app.ui.components.UpdateDialog(
        info = info,
        downloadState = state.updateDownloadState,
        mandatory = com.nirogbhumi.app.update.UpdateManager.isMandatory(context, info, currentVersionCode),
        onUpdateNow = { beginUpdateDownload(context, state, info) },
        onInstall = {
          val ready = state.updateDownloadState
          if (ready is com.nirogbhumi.app.update.DownloadState.ReadyToInstall) {
            com.nirogbhumi.app.update.UpdateInstaller.installApk(context, java.io.File(ready.filePath))
          }
        },
        onRetry = { beginUpdateDownload(context, state, info) },
        onDismiss = {
          com.nirogbhumi.app.update.UpdatePrefs.dismissVersion(context, info.latestVersionCode)
          state.availableUpdate = null
          state.updateDownloadState = com.nirogbhumi.app.update.DownloadState.Idle
        },
      )
    }
  }
}

/** BuildConfig.VERSION_CODE isn't stable across module boundaries in this project's Compose preview tooling, so read it straight from PackageManager like UpdateCheckWorker does. */
private fun currentVersionCode(context: android.content.Context): Int = runCatching {
  val info = context.packageManager.getPackageInfo(context.packageName, 0)
  if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) info.longVersionCode.toInt()
  else @Suppress("DEPRECATION") info.versionCode
}.getOrDefault(0)

private fun beginUpdateDownload(context: android.content.Context, state: NirogState, info: com.nirogbhumi.app.update.UpdateInfo) {
  val id = com.nirogbhumi.app.update.ApkDownloader.enqueue(context, info)
  if (id == null) {
    state.updateDownloadState = com.nirogbhumi.app.update.DownloadState.Failed("Couldn't start the download - the update link looks invalid")
    return
  }
  state.activeDownloadId = id
  state.updateDownloadState = com.nirogbhumi.app.update.DownloadState.InProgress(0, 0, 0)
}

/**
 * Drives the whole "on launch, on foreground, every 30 minutes while open"
 * requirement from one place: an initial check, a lifecycle-scoped loop that
 * only ticks while RESUMED (so it naturally pauses when backgrounded and
 * restarts on the next foreground), and download-progress polling once a
 * download is active. WorkManager's UpdateManager.schedulePeriodicCheck
 * (registered in NirogBhumiApplication) is the separate background-only
 * backstop for when the app isn't open at all.
 */
@Composable
private fun UpdateLifecycleEffects(state: NirogState) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  suspend fun runCheck() {
    if (state.updateDownloadState !is com.nirogbhumi.app.update.DownloadState.Idle) return
    state.updateCheckBusy = true
    val currentVersionCode = currentVersionCode(context)
    val result = com.nirogbhumi.app.update.UpdateManager.checkNow(context, currentVersionCode)
    state.updateCheckBusy = false
    result.onSuccess { info ->
      state.updateCheckError = ""
      if (info != null) state.availableUpdate = info
    }.onFailure {
      state.updateCheckError = it.message ?: "Couldn't check for updates"
    }
  }

  LaunchedEffect(lifecycleOwner) {
    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
      runCheck()
      while (true) {
        kotlinx.coroutines.delay(30 * 60 * 1000L)
        runCheck()
      }
    }
  }

  val downloadId = state.activeDownloadId
  LaunchedEffect(downloadId) {
    if (downloadId == null) return@LaunchedEffect
    while (true) {
      val progress = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        com.nirogbhumi.app.update.ApkDownloader.queryProgress(context, downloadId)
      }
      when (progress) {
        is com.nirogbhumi.app.update.DownloadState.ReadyToInstall -> {
          val info = state.availableUpdate
          val target = if (info != null) com.nirogbhumi.app.update.ApkDownloader.targetFile(context, info.latestVersionCode) else null
          if (info != null && target != null && target.exists()) {
            state.updateDownloadState = com.nirogbhumi.app.update.DownloadState.Verifying(target.absolutePath)
            val verified = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
              com.nirogbhumi.app.update.UpdateInstaller.verifyChecksum(target, info.checksum)
            }
            state.updateDownloadState = if (verified) {
              com.nirogbhumi.app.update.DownloadState.ReadyToInstall(target.absolutePath)
            } else {
              com.nirogbhumi.app.update.DownloadState.Failed("The downloaded file didn't match the expected checksum - try checking for updates again")
            }
          } else {
            state.updateDownloadState = com.nirogbhumi.app.update.DownloadState.Failed("The downloaded file is missing - try again")
          }
          state.activeDownloadId = null
          return@LaunchedEffect
        }
        is com.nirogbhumi.app.update.DownloadState.Failed -> {
          state.updateDownloadState = progress
          state.activeDownloadId = null
          return@LaunchedEffect
        }
        else -> {
          state.updateDownloadState = progress
          kotlinx.coroutines.delay(500L)
        }
      }
    }
  }
}

@Composable
fun QuickLogFastingOverlay(state: NirogState) {
    // Right after saving, stays open one more beat showing a confirmation
    // with an Edit action instead of dismissing immediately - fixing a
    // typo'd value previously meant finding it again in the reading
    // history screen. savedDocId tracks the real Firestore doc id so a
    // correction updates that same reading rather than creating a
    // duplicate one.
    var savedDocId by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val widgetScope = androidx.compose.runtime.rememberCoroutineScope()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { state.isQuickLogFastingOpen = false },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) { },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick Log Fasting Sugar",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        fontSize = 18.sp,
                        color = Color(0xFF1B3221)
                    )
                    IconButton(onClick = { state.isQuickLogFastingOpen = false }) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close Overlay")
                    }
                }

                if (confirming) {
                    // Just-saved confirmation, replacing the slider - the
                    // whole point of this state is giving a moment to catch
                    // a typo right here instead of after closing the sheet.
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF3F7D58))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Logged ${state.quickLogFastingValue} mg/dL",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF1B3221),
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { confirming = false },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                        ) { Text("Edit", fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = { state.isQuickLogFastingOpen = false },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936)),
                            shape = RoundedCornerShape(24.dp),
                        ) { Text("Done", fontWeight = FontWeight.Bold, color = Color.White) }
                    }
                } else {
                    val voiceLaunch = com.nirogbhumi.app.ui.components.rememberVoiceInputLauncher(
                        prompt = "Say your fasting sugar, e.g. \"110\"",
                        onResult = { heard ->
                            val value = com.nirogbhumi.app.ui.components.parseSpokenNumber(heard)?.toFloatOrNull()?.toInt()
                            if (value != null) state.quickLogFastingValue = value.coerceIn(50, 250)
                            else state.cloudMessage = "Didn't catch a number - try again or use the slider."
                        },
                        onUnavailable = { state.cloudMessage = "Voice entry isn't available on this device." },
                    )
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Slide to record the fasting value displayed on your metabolic monitor.",
                            fontSize = 13.sp,
                            color = Color(0xFF737972),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = voiceLaunch) {
                            Icon(imageVector = Icons.Filled.Mic, contentDescription = "Say the value instead", tint = Color(0xFF314936))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "${state.quickLogFastingValue}",
                            fontSize = 44.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1B3221)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "mg/dL",
                            fontSize = 14.sp,
                            color = Color(0xFF737972),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Slider(
                        value = state.quickLogFastingValue.toFloat(),
                        onValueChange = { state.quickLogFastingValue = it.toInt() },
                        valueRange = 50f..250f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF314936),
                            activeTrackColor = Color(0xFFBFEE95)
                        )
                    )

                    Button(
                        enabled = !saving,
                        onClick = {
                            saving = true
                            state.fastingSugarValue = state.quickLogFastingValue
                            val status = if (state.quickLogFastingValue > 125) "High" else if (state.quickLogFastingValue < 80) "Low" else "Normal"
                            val values = mapOf(
                                "value" to state.quickLogFastingValue,
                                "unit" to "mg/dL",
                                "readingType" to "fasting",
                                "measuredAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                "source" to "manual"
                            )
                            fun onDone(result: com.nirogbhumi.app.data.CloudResult<*>) {
                                saving = false
                                if (result is com.nirogbhumi.app.data.CloudResult.Success<*>) {
                                    state.cloudMessage = "Synced securely"
                                    confirming = true
                                    widgetScope.launch {
                                        com.nirogbhumi.app.widget.updateHealthQuickLogWidget(context, state.quickLogFastingValue)
                                    }
                                } else if (result is com.nirogbhumi.app.data.CloudResult.Failure) {
                                    state.cloudMessage = result.message
                                }
                            }
                            val existingDocId = savedDocId
                            if (existingDocId == null) {
                                state.sugarLogs.add(0, com.nirogbhumi.app.ui.SugarLog(state.sugarLogs.size + 1, state.quickLogFastingValue, "Fasting", "Today, Just Now", status))
                                state.repository.addHealthLog("glucoseReadings", values) { result ->
                                    if (result is com.nirogbhumi.app.data.CloudResult.Success) savedDocId = result.value
                                    onDone(result)
                                }
                            } else {
                                if (state.sugarLogs.isNotEmpty()) {
                                    state.sugarLogs[0] = state.sugarLogs[0].copy(value = state.quickLogFastingValue, status = status)
                                }
                                state.repository.updateHealthLog("glucoseReadings", existingDocId, values, ::onDone)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        if (saving) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        } else {
                            Text(text = if (savedDocId == null) "Save Fasting Sugar" else "Update Fasting Sugar", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
