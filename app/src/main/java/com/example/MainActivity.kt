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
import androidx.compose.material.icons.filled.Close
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
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.screens.*
import com.nirogbhumi.app.ui.theme.MyApplicationTheme

private const val IS_PRODUCTION_APK = true

class MainActivity : ComponentActivity(), com.razorpay.PaymentResultListener {
  private val nirogState by lazy { NirogState() }

  override fun onPaymentSuccess(paymentId: String?) {
    nirogState.cloudMessage = "Payment received securely"
    nirogState.currentScreen = if (nirogState.pendingPaymentKind == "order") "order_success" else "consultation_confirmed"
  }

  override fun onPaymentError(code: Int, response: String?) {
    nirogState.cloudMessage = response ?: "Payment was not completed. You can safely try again."
    nirogState.currentScreen = if (nirogState.pendingPaymentKind == "order") "checkout" else "payment_confirmation"
  }

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
    nirogState.pendingDeepLink = intent.getStringExtra("route").orEmpty()
    val tourSeen = getSharedPreferences("nirog_prefs", MODE_PRIVATE).getBoolean("onboarding_tour_seen", false)
    nirogState.shouldShowTour = !tourSeen
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val state = nirogState

        Scaffold(
          modifier = Modifier.fillMaxSize()
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
    intent.getStringExtra("route")?.takeIf { it.isNotBlank() }?.let {
      nirogState.pendingDeepLink = it
      nirogState.currentScreen = it
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
  Box(modifier = Modifier.fillMaxSize()) {
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
      "device_hub" -> DeviceSyncScreen(state)
      "notifications" -> NotificationInboxScreen(state)
      "notification_settings" -> NotificationSettingsScreen(state)
      "family_profiles" -> FamilyProfilesScreen(state)
      "orders" -> OrdersScreen(state)
      "articles" -> ArticlesScreen(state)

      // Metrics Detailed screens
      "sugar_detail" -> BloodSugarDetailScreen(state)
      "food_journal" -> FoodJournalScreen(state)
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
  }
}

@Composable
fun QuickLogFastingOverlay(state: NirogState) {
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

                Text(
                    text = "Slide to record the fasting value displayed on your metabolic monitor.",
                    fontSize = 13.sp,
                    color = Color(0xFF737972)
                )

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
                    onClick = {
                        state.fastingSugarValue = state.quickLogFastingValue
                        state.sugarLogs.add(
                            0,
                            com.nirogbhumi.app.ui.SugarLog(
                                state.sugarLogs.size + 1,
                                state.quickLogFastingValue,
                                "Fasting",
                                "Today, Just Now",
                                if (state.quickLogFastingValue > 125) "High" else if (state.quickLogFastingValue < 80) "Low" else "Normal"
                            )
                        )
                        state.repository.addHealthLog(
                            "glucoseReadings",
                            mapOf(
                                "value" to state.quickLogFastingValue,
                                "unit" to "mg/dL",
                                "readingType" to "fasting",
                                "measuredAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                "source" to "manual"
                            )
                        ) { result ->
                            state.cloudMessage = when (result) {
                                is com.nirogbhumi.app.data.CloudResult.Success -> "Synced securely"
                                is com.nirogbhumi.app.data.CloudResult.Failure -> result.message
                            }
                        }
                        state.isQuickLogFastingOpen = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(text = "Save Fasting Sugar", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
