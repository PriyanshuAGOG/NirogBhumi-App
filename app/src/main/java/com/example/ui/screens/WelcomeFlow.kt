package com.nirogbhumi.app.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.data.FirebaseAuthGateway
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// Brand Palette Tokens
val DeepGreen = Color(0xFF314936)
val Ink = Color(0xFF182219)
val Paper = Color(0xFFF8F6EF)
val Line = Color(0xFFD8D0C0)
val Earth = Color(0xFF82AD5C)
val Water = Color(0xFF77D0D4)
val SoftClay = Color(0xFFEEE8DC)
val White = Color(0xFFFFFFFF)

@Composable
fun NirogLogo(modifier: Modifier = Modifier) {
    // Renders the actual brand mark asset (assets/branding/nirog-bhumi-logo-mark.png),
    // not a hand-drawn approximation, so it's pixel-identical to the real logo everywhere it appears.
    Image(
        painter = painterResource(id = com.nirogbhumi.app.R.drawable.logo_nirog_mark),
        contentDescription = "Nirog Bhumi logo",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

// Existing accounts signing back in (any method) must land on their dashboard,
// not repeat onboarding - only a genuinely new account has no completed profile yet.
private fun routeAfterAuthSuccess(state: NirogState, deepLinkFallback: String = "dashboard") {
    val uid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
    if (uid == null) { state.currentScreen = "consent"; return }
    FirebaseFirestore.getInstance().collection("users").document(uid).get()
        .addOnSuccessListener { document ->
            state.currentScreen = if (document.getBoolean("onboardingComplete") == true) deepLinkFallback else "consent"
        }
        .addOnFailureListener { state.currentScreen = "consent" }
}

// SCREEN 1: SPLASH SCREEN
@Composable
fun SplashScreen(state: NirogState) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(900)
        val user = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
        if (user == null) state.currentScreen = "welcome"
        else routeAfterAuthSuccess(state, state.pendingDeepLink.ifBlank { "dashboard" })
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Draw real custom premium high-fidelity NirogLogo
            NirogLogo(
                modifier = Modifier
                    .size(110.dp)
                    .border(1.dp, Line.copy(alpha = 0.5f), CircleShape)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Nirog Bhumi",
                fontSize = 40.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Ink,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Your daily rhythm for better metabolic health.",
                fontSize = 15.sp,
                color = Ink.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Gentle visual loading indicator
            CircularProgressIndicator(
                color = DeepGreen,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

        }
    }
}

// SCREEN 2: WELCOME INTRO SCREEN
@Composable
fun WelcomeScreen(state: NirogState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(32.dp))

            NirogLogo(
                modifier = Modifier
                    .size(72.dp)
                    .border(1.dp, Line.copy(alpha = 0.5f), CircleShape)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Track your health in 2 minutes a day.",
                fontSize = 32.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                color = Ink,
                textAlign = TextAlign.Center,
                lineHeight = 38.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Sugar, BP, sleep, walking, water, food rhythm, and expert guidance in one calm place.",
                fontSize = 15.sp,
                color = Ink.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Three soft cards highlighting key features
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IntroFeatureCard("Daily check-in", "Understand patterns and complete today's vitals simply.", Icons.Outlined.CheckCircle, Earth)
                IntroFeatureCard("Weekly health report", "Structured reports summarizing consistency and progress trends.", Icons.Outlined.Assignment, Water)
                IntroFeatureCard("Nirog Bhumi care", "Direct consultation loops with Ayurvedic experts.", Icons.Outlined.MedicalServices, DeepGreen)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
        ) {
            Button(
                onClick = { state.currentScreen = "value_slides" },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DeepGreen),
                shape = RoundedCornerShape(27.dp)
            ) {
                Text(
                    text = "Continue",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { state.currentScreen = "login_mobile" },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DeepGreen),
                border = BorderStroke(1.dp, Line),
                shape = RoundedCornerShape(27.dp)
            ) {
                Text(
                    text = "I already have an account",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun IntroFeatureCard(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accentColor: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Line.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accentColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = accentColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = desc, fontSize = 12.sp, color = Ink.copy(alpha = 0.7f), lineHeight = 16.sp)
            }
        }
    }
}

// SCREEN 3: VALUE ONBOARDING SLIDES
@Composable
fun ValueSlidesScreen(state: NirogState) {
    var slideIndex by remember { mutableStateOf(0) }

    val slideContents = listOf(
        Triple(
            "Know your pattern.",
            "Visual glucose trends and circadian cycles mapped automatically to understand your system's unique metabolic responses.",
            "PATTERN"
        ),
        Triple(
            "One action, not ten.",
            "We focus on small changes that create enormous results—like a simple 15-minute walk after lunch or warm herbal water.",
            "RITUAL"
        ),
        Triple(
            "Share better data with professionals.",
            "Download clinical-grade weekly report summaries to empower your general physician, coach, or Ayurvedic expert panel.",
            "COOPERATE"
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Skip Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${slideIndex + 1} of 3",
                fontSize = 13.sp,
                color = Ink.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = { state.currentScreen = "login_mobile" }) {
                Text("Skip", color = DeepGreen, fontWeight = FontWeight.SemiBold)
            }
        }

        // Graphical Visual Stage depending on slide index
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .heightIn(min = 200.dp)
                    .background(SoftClay.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                    .border(1.dp, Line, RoundedCornerShape(24.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                when (slideIndex) {
                    0 -> {
                        // Pattern Graph simulation
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Fasting Sugar (Mon-Sun)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DeepGreen)
                            Spacer(modifier = Modifier.height(16.dp))
                            Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                                val steps = size.width / 5
                                drawLine(color = Line, start = androidx.compose.ui.geometry.Offset(0f, size.height/2), end = androidx.compose.ui.geometry.Offset(size.width, size.height/2), strokeWidth = 1.dp.toPx())
                                // draw a sine wave
                                var lastX = 0f
                                var lastY = size.height/2
                                for (i in 0..5) {
                                    val x = i * steps
                                    val y = size.height/2 + (if (i % 2 == 0) -24.dp.toPx() else 16.dp.toPx())
                                    drawCircle(color = DeepGreen, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
                                    if (i > 0) {
                                        drawLine(color = DeepGreen, start = androidx.compose.ui.geometry.Offset(lastX, lastY), end = androidx.compose.ui.geometry.Offset(x, y), strokeWidth = 2.dp.toPx())
                                    }
                                    lastX = x
                                    lastY = y
                                }
                            }
                            Text("Circadian baseline stable", fontSize = 11.sp, color = Earth, fontWeight = FontWeight.Bold)
                        }
                    }
                    1 -> {
                        // Daily action card simulation
                        Card(
                            colors = CardDefaults.cardColors(containerColor = White),
                            modifier = Modifier.fillMaxWidth(0.85f).border(1.dp, Line, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("TODAY'S PRACTICAL PROTOCOL", fontSize = 10.sp, color = Earth, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Drink Vijaysar water", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = Ink)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Fosters diabetic pancreatic cell health.", fontSize = 12.sp, color = Ink.copy(alpha = 0.7f))
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier.background(DeepGreen, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Mark Complete", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    2 -> {
                        // Report preview simulation
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(imageVector = Icons.Outlined.Summarize, contentDescription = "Report Info", tint = Water, modifier = Modifier.size(48.dp))
                            Text("Weekly Progress File Summary", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text("PDF contains meal graphs, steps averages, glucose metrics standardizations.", fontSize = 11.sp, color = Ink.copy(alpha = 0.62f), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
                            Box(
                                modifier = Modifier.border(1.dp, DeepGreen, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Download clinical summary", color = DeepGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = slideContents[slideIndex].first,
                fontSize = 24.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Ink,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = slideContents[slideIndex].second,
                fontSize = 14.sp,
                color = Ink.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
                lineHeight = 20.sp
            )
        }

        // Controls indicators and action Button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Slide Dot Indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                for (i in 0..2) {
                    val isActive = i == slideIndex
                    Box(
                        modifier = Modifier
                            .size(if (isActive) 10.dp else 8.dp)
                            .background(if (isActive) DeepGreen else Line, CircleShape)
                    )
                }
            }

            Button(
                onClick = {
                    if (slideIndex < 2) {
                        slideIndex++
                    } else {
                        state.currentScreen = "login_mobile"
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DeepGreen),
                shape = RoundedCornerShape(27.dp)
            ) {
                Text(
                    text = if (slideIndex < 2) "Continue" else "Get Started",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// SCREEN 4: INTUITIVE PHONE LOGIN SCREEN
@Composable
fun LoginMobileScreen(state: NirogState) {
    var phoneInput by remember { mutableStateOf("") }
    var isAgreedToTerms by remember { mutableStateOf(true) }
    val activity = LocalActivity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Back navigation arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = { state.currentScreen = "welcome" }) {
                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Enter your mobile number",
                fontSize = 28.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Ink,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "We will send a secure one-time password to authorize.",
                fontSize = 14.sp,
                color = Ink.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Phone inputs inside modern White card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Line.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 10) phoneInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 12.dp)
                            ) {
                                Icon(imageVector = Icons.Filled.Phone, contentDescription = "Phone Prefix", tint = Ink.copy(alpha = 0.6f))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("+91", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 15.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Divider(modifier = Modifier.width(1.dp).height(20.dp), color = Line)
                            }
                        },
                        placeholder = { Text("10-digit number", color = Ink.copy(alpha = 0.35f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepGreen,
                            unfocusedBorderColor = Line,
                            focusedContainerColor = Paper.copy(alpha = 0.5f),
                            unfocusedContainerColor = Paper.copy(alpha = 0.5f)
                        )
                    )

                    // HIPAA & Med disclaimer check
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAgreedToTerms = !isAgreedToTerms },
                        verticalAlignment = Alignment.Top
                    ) {
                        Checkbox(
                            checked = isAgreedToTerms,
                            onCheckedChange = { isAgreedToTerms = it },
                            colors = CheckboxDefaults.colors(checkedColor = DeepGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "By continuing, you agree to the Terms, Privacy Policy, and Medical Disclaimer. This app does not replace medical advice.",
                            fontSize = 11.sp,
                            color = Ink.copy(alpha = 0.62f),
                            lineHeight = 15.sp
                        )
                    }

                    Button(
                        onClick = {
                            val host = activity ?: return@Button
                            state.authBusy = true
                            state.authError = ""
                            state.userMobile = "+91$phoneInput"
                            FirebaseAuthGateway.sendOtp(host, state.userMobile,
                                onSent = { id ->
                                    state.authBusy = false
                                    state.otpVerificationId = id
                                    state.currentScreen = if (id == "AUTO_VERIFIED") "consent" else "login_otp"
                                },
                                onError = { error -> state.authBusy = false; state.authError = error }
                            )
                        },
                        enabled = phoneInput.length == 10 && isAgreedToTerms && !state.authBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DeepGreen,
                            disabledContainerColor = Line.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(27.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(text = "Send OTP", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(imageVector = Icons.Filled.ArrowForward, contentDescription = "Proceed", tint = Color.White)
                        }
                    }
                    if (state.authError.isNotBlank()) Text(state.authError, color = Color(0xFF8B2E2E), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Dynamic secure option switch: E-mail Login & Registration (Asked for sign-up, email login, and password resets!)
            Text(
                text = "Alternative Authentication",
                fontSize = 12.sp,
                color = Ink.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedButton(
                onClick = { state.currentScreen = "email_auth" },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DeepGreen),
                border = BorderStroke(1.dp, Line),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.Email, contentDescription = "Email Access", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Use Email Sign-in / Sign-up", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

// SCREEN 5: OTP VERIFICATION
@Composable
fun LoginOtpScreen(state: NirogState) {
    val otpDigits = remember { mutableStateListOf("", "", "", "", "", "") }
    var isValidState by remember { mutableStateOf(true) }
    var resendTimer by remember { mutableStateOf(28) }
    val activity = LocalActivity.current

    LaunchedEffect(Unit) {
        while (resendTimer > 0) {
            kotlinx.coroutines.delay(1000)
            resendTimer--
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = { state.currentScreen = "login_mobile" }) {
                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
                }
            }

            Text(
                text = "Verify your number",
                fontSize = 28.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Ink,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Sent 6-digit code to ${state.userMobile.ifEmpty { "+91 98765 43210" }}",
                fontSize = 14.sp,
                color = Ink.copy(alpha = 0.65f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Line.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = White)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Multi-box inputs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (i in 0..5) {
                            OutlinedTextField(
                                value = otpDigits[i],
                                onValueChange = { newVal ->
                                    if (newVal.all { it.isDigit() } && newVal.length <= 1) {
                                        otpDigits[i] = newVal
                                    }
                                },
                                modifier = Modifier
                                    .width(42.dp)
                                    .height(58.dp),
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = Ink
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (isValidState) DeepGreen else Color.Red,
                                    unfocusedBorderColor = Line,
                                    focusedContainerColor = Paper,
                                    unfocusedContainerColor = Paper.copy(alpha = 0.4f)
                                )
                            )
                        }
                    }

                    if (!isValidState) {
                        Text(
                            text = "Invalid OTP code entered. Please retry.",
                            color = Color.Red,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (resendTimer > 0) "Resend OTP in ${resendTimer}s" else "Resend available!",
                            fontSize = 13.sp,
                            color = Ink.copy(alpha = 0.5f)
                        )

                        if (resendTimer == 0) {
                            TextButton(onClick = {
                                val host = activity ?: return@TextButton
                                resendTimer = 30; isValidState = true; state.authError = ""
                                FirebaseAuthGateway.sendOtp(host, state.userMobile,
                                    onSent = { state.otpVerificationId = it },
                                    onError = { state.authError = it; isValidState = false })
                            }) {
                                Text("Resend code", color = DeepGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val pinVal = otpDigits.joinToString("")
                            if (pinVal.length < 6) {
                                isValidState = false
                            } else {
                                state.authBusy = true
                                FirebaseAuthGateway.verifyOtp(state.otpVerificationId, pinVal,
                                    onSuccess = { state.authBusy = false; routeAfterAuthSuccess(state) },
                                    onError = { state.authBusy = false; state.authError = it; isValidState = false })
                            }
                        },
                        enabled = !state.authBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepGreen),
                        shape = RoundedCornerShape(27.dp)
                    ) {
                        Text(text = "Verify & Proceed", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    if (state.authError.isNotBlank()) Text(state.authError, color = Color(0xFF8B2E2E), fontSize = 12.sp)
                }
            }
        }
    }
}

// SCREEN 4B: EMAIL SIGN-UP & LOGIN FLOW
@Composable
fun EmailAuthScreen(state: NirogState) {
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    val activity = LocalActivity.current
    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        state.authBusy = true
        state.authError = ""
        FirebaseAuthGateway.google(
            result.data,
            onSuccess = {
                state.authBusy = false
                routeAfterAuthSuccess(state)
            },
            onError = {
                state.authBusy = false
                state.authError = it
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = { state.currentScreen = "login_mobile" }) {
                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sign Up vs Sign In tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .background(SoftClay, RoundedCornerShape(20.dp)),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp)
                        .background(if (!state.isSignUpMode) White else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable { state.isSignUpMode = false }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Sign In", fontWeight = FontWeight.Bold, color = Ink, fontSize = 14.sp)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp)
                        .background(if (state.isSignUpMode) White else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable { state.isSignUpMode = true }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Sign Up", fontWeight = FontWeight.Bold, color = Ink, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = if (state.isSignUpMode) "Create your account" else "Welcome back, log in",
                fontSize = 24.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Ink,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Metabolic details require private protected authentication.",
                fontSize = 13.sp,
                color = Ink.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Line.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val host = activity ?: return@OutlinedButton
                            state.authBusy = true
                            state.authError = ""
                            val intent = FirebaseAuthGateway.googleSignInIntent(
                                host,
                                onError = {
                                    state.authBusy = false
                                    state.authError = it
                                }
                            )
                            if (intent != null) googleLauncher.launch(intent)
                        },
                        enabled = !state.authBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DeepGreen)
                    ) {
                        Icon(imageVector = Icons.Outlined.AccountCircle, contentDescription = "Google Sign-in", tint = DeepGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Continue with Google", fontWeight = FontWeight.Bold)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Divider(modifier = Modifier.weight(1f), color = Line)
                        Text("or", modifier = Modifier.padding(horizontal = 12.dp), color = Ink.copy(alpha = 0.55f), fontSize = 12.sp)
                        Divider(modifier = Modifier.weight(1f), color = Line)
                    }

                    // Email
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(imageVector = Icons.Outlined.Email, contentDescription = "EmailIcon", tint = Ink.copy(alpha = 0.6f)) },
                        placeholder = { Text("Enter your email", color = Ink.copy(alpha = 0.35f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepGreen,
                            unfocusedBorderColor = Line,
                            focusedContainerColor = Paper.copy(alpha = 0.5f),
                            unfocusedContainerColor = Paper.copy(alpha = 0.5f)
                        )
                    )

                    // Password
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(imageVector = Icons.Outlined.Lock, contentDescription = "LockIcon", tint = Ink.copy(alpha = 0.6f)) },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = "Toggle Visibility",
                                    tint = Ink.copy(alpha = 0.5f)
                                )
                            }
                        },
                        placeholder = { Text("Enter password", color = Ink.copy(alpha = 0.35f)) },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepGreen,
                            unfocusedBorderColor = Line,
                            focusedContainerColor = Paper.copy(alpha = 0.5f),
                            unfocusedContainerColor = Paper.copy(alpha = 0.5f)
                        )
                    )

                    // Real-time security password strength checker under Sign Up mode
                    if (state.isSignUpMode && passwordInput.isNotEmpty()) {
                        val isStrong = passwordInput.length >= 8 && passwordInput.any { it.isDigit() }
                        Text(
                            text = if (isStrong) "● Strong password" else "▲ Use 8+ characters including a number",
                            color = if (isStrong) Earth else Color(0xFFBA1A1A),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Forgot Password? Clickable Link
                    if (!state.isSignUpMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "Forgot Password?",
                                fontSize = 13.sp,
                                color = DeepGreen,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { state.currentScreen = "password_reset" }
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            state.userEmail = emailInput
                            state.authBusy = true; state.authError = ""
                            FirebaseAuthGateway.email(emailInput, passwordInput, state.isSignUpMode,
                                onSuccess = { state.authBusy = false; routeAfterAuthSuccess(state) },
                                onError = { state.authBusy = false; state.authError = it })
                        },
                        enabled = emailInput.contains("@") && passwordInput.length >= 6 && !state.authBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepGreen),
                        shape = RoundedCornerShape(27.dp)
                    ) {
                        Text(
                            text = if (state.isSignUpMode) "Register Securely" else "Log In to Dashboard",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    if (state.authError.isNotBlank()) Text(state.authError, color = Color(0xFF8B2E2E), fontSize = 12.sp)
                }
            }
        }
    }
}

// SCREEN 4C: PASSWORD RESET MECHANISM
@Composable
fun PasswordResetScreen(state: NirogState) {
    var emailResetInput by remember { mutableStateOf("") }
    var emailDispatchedCompleted by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = { state.currentScreen = "email_auth" }) {
                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Reset your password",
                fontSize = 28.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Ink,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Type your registered metabolic profile email below and we will transmit direct instructions.",
                fontSize = 14.sp,
                color = Ink.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Line.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = White)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!emailDispatchedCompleted) {
                        OutlinedTextField(
                            value = emailResetInput,
                            onValueChange = { emailResetInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(imageVector = Icons.Outlined.Email, contentDescription = "Email", tint = Ink.copy(alpha = 0.6f)) },
                            placeholder = { Text("E-mail address", color = Ink.copy(alpha = 0.35f)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DeepGreen,
                                unfocusedBorderColor = Line,
                                focusedContainerColor = Paper,
                                unfocusedContainerColor = Paper
                            )
                        )

                        Button(
                            onClick = {
                                state.authBusy = true; state.authError = ""
                                FirebaseAuthGateway.resetPassword(emailResetInput,
                                    onSuccess = { state.authBusy = false; emailDispatchedCompleted = true; state.resetEmailSent = true },
                                    onError = { state.authBusy = false; state.authError = it })
                            },
                            enabled = emailResetInput.contains("@") && !state.authBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepGreen),
                            shape = RoundedCornerShape(26.dp)
                        ) {
                            Text("Transmit Reset Link", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        if (state.authError.isNotBlank()) Text(state.authError, color = Color(0xFF8B2E2E), fontSize = 12.sp)
                    } else {
                        // Success Feedback
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Earth.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Filled.Done, contentDescription = "Dispatched", tint = DeepGreen, modifier = Modifier.size(32.dp))
                        }

                        Text(
                            text = "Instructions Dispatched!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Ink
                        )

                        Text(
                            text = "We've dispatched password renewal instructions to $emailResetInput. Please examine your junk folders if unseen within 2 minutes.",
                            fontSize = 13.sp,
                            color = Ink.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Divider(color = Line.copy(alpha = 0.3f))

                        OutlinedButton(
                            onClick = { state.currentScreen = "email_auth" },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DeepGreen),
                            border = BorderStroke(1.dp, Line),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text("Back to Sign In", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// SCREEN 10: USER DATA PROTECTION & TRIPLE CONSENT SCREEN
@Composable
fun ConsentScreen(state: NirogState) {
    var check1 by remember { mutableStateOf(false) }
    var check2 by remember { mutableStateOf(false) }
    var check3 by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
    ) {
        // Simple custom bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { state.currentScreen = "login_mobile" }) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = Ink
                )
            }
            Icon(
                imageVector = Icons.Filled.VerifiedUser,
                contentDescription = "Trust Badge",
                tint = Earth,
                modifier = Modifier.size(24.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Your health data stays protected",
                fontSize = 32.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Ink,
                lineHeight = 38.sp
            )

            Text(
                text = "We treat your sensitive health signals with premium privacy, strictly keeping control in your hands.",
                color = Ink.copy(alpha = 0.7f),
                fontSize = 15.sp,
                lineHeight = 22.sp
            )

            Divider(color = Line.copy(alpha = 0.4f))

            // Information details
            ConsentInformationBullet("What we collect", "Circadian resting statistics, glucose values, steps progress and dietary patterns manually documented.", Icons.Filled.Storage, DeepGreen)
            ConsentInformationBullet("Why we collect it", "To produce comprehensive weekly charts and guide metabolic experts about patterns.", Icons.Filled.Insights, Earth)
            ConsentInformationBullet("Who reviews it", "Exclusively verified wellness consultants you engage. Safe role-locked clinical isolation.", Icons.Filled.Lock, Water)
            ConsentInformationBullet("What this app does not do", "Nirog Bhumi does not diagnose conditions or change medication, diet, exercise, or treatment for you.", Icons.Filled.DoNotDisturbOn, Color(0xFFBA1A1A))

            Spacer(modifier = Modifier.height(12.dp))

            // Triple critical checkboxes
            ConsentCheckRow(checked = check1, onCheckedChange = { check1 = it }, label = "I agree to health data tracking & secure cloud database persistence.")
            ConsentCheckRow(checked = check2, onCheckedChange = { check2 = it }, label = "I authorize certified Nirog Bhumi experts to review metabolic charts when consults are booked.")
            ConsentCheckRow(checked = check3, onCheckedChange = { check3 = it }, label = "I understand this application is for health tracking and lifestyle support, not medicine prescription.")

            TextButton(onClick = { state.legalReturnRoute = "consent"; state.currentScreen = "legal_center" }) {
                Text("Read full policies and medical disclaimer", color = DeepGreen, fontWeight = FontWeight.SemiBold)
            }

            if (!(check1 && check2 && check3)) {
                Text(
                    "Check all three items above to continue.",
                    color = Ink.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
            }
            if (state.authError.isNotBlank()) {
                Text(state.authError, color = Color(0xFF8B2E2E), fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Sticky consent continue bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Paper)
                .padding(16.dp)
        ) {
            Button(
                onClick = {
                    state.authError = ""
                    isSaving = true
                    state.consentHealthData = check1
                    state.consentExpertReview = check2
                    state.consentMedicalDisclaimer = check3
                    state.repository.saveProfile(mapOf("consent" to mapOf("healthData" to check1, "expertReview" to check2, "medicalDisclaimer" to check3, "version" to "1.0"))) { result ->
                        isSaving = false
                        if (result is com.nirogbhumi.app.data.CloudResult.Success) state.currentScreen = "setup_profile"
                        else state.authError = (result as com.nirogbhumi.app.data.CloudResult.Failure).message
                    }
                },
                enabled = check1 && check2 && check3 && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepGreen,
                    disabledContainerColor = Line.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(27.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Agree & Continue", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(imageVector = Icons.Filled.ArrowForward, contentDescription = "Proceed", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ConsentInformationBullet(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(color.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = color, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
            Spacer(modifier = Modifier.height(2.dp))
            Text(desc, fontSize = 12.sp, color = Ink.copy(alpha = 0.65f), lineHeight = 16.sp)
        }
    }
}

@Composable
fun ConsentCheckRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = DeepGreen)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, fontSize = 12.sp, color = Ink, lineHeight = 16.sp)
    }
}

// SCREEN 6: BASIC BIOMETRIC SETUP
@Composable
fun SetupProfileScreen(state: NirogState) {
    var nameTemp by remember { mutableStateOf(state.profileName) }
    var ageTemp by remember { mutableStateOf(state.profileAge) }
    var weightTemp by remember { mutableStateOf(state.profileWeight) }
    var heightTemp by remember { mutableStateOf(state.profileHeight) }
    var cityTemp by remember { mutableStateOf(state.profileCity) }
    var languageTemp by remember { mutableStateOf(state.profileLanguage) }
    var isSaving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
    ) {
        // App top step navigation bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { state.currentScreen = "consent" }) {
                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Text(
                "Step 1 of 3",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Ink.copy(alpha = 0.5f)
            )
            TextButton(onClick = { state.currentScreen = "selection_caregiver" }) {
                Text("Skip", color = DeepGreen, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Tell us the basics",
                fontSize = 32.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Ink
            )

            Text(
                text = "These basics help personalize tracking, reminders, and everyday guidance.",
                fontSize = 15.sp,
                color = Ink.copy(alpha = 0.65f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Fields Questionnaire
            OutlinedProfileField("Full Name", nameTemp, "e.g. Priyanshu") { nameTemp = it }
            OutlinedProfileField("Age", ageTemp, "e.g. 28") { ageTemp = it }
            OutlinedProfileField("Weight (kg)", weightTemp, "e.g. 72") { weightTemp = it }
            OutlinedProfileField("Height (cm)", heightTemp, "e.g. 174") { heightTemp = it }
            OutlinedProfileField("City", cityTemp, "e.g. Jaipur") { cityTemp = it }

            // Language Selector
            Text("Preferred Coaching Language", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("English", "Hindi", "Bengali").forEach { lang ->
                    val isS = languageTemp == lang
                    Box(
                        modifier = Modifier
                            .background(if (isS) DeepGreen else SoftClay, RoundedCornerShape(12.dp))
                            .clickable { languageTemp = lang }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(lang, color = if (isS) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        Box(modifier = Modifier.padding(16.dp)) {
            Column {
                if (state.authError.isNotBlank()) {
                    Text(state.authError, color = Color(0xFF8B2E2E), fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
                Button(
                    onClick = {
                        state.authError = ""
                        isSaving = true
                        state.profileName = nameTemp
                        state.profileAge = ageTemp
                        state.profileWeight = weightTemp
                        state.profileHeight = heightTemp
                        state.profileCity = cityTemp
                        state.profileLanguage = languageTemp
                        state.repository.saveProfile(mapOf("fullName" to nameTemp, "age" to ageTemp.toIntOrNull(), "weightKg" to weightTemp.toDoubleOrNull(), "heightCm" to heightTemp.toDoubleOrNull(), "city" to cityTemp, "preferredLanguage" to languageTemp)) { result ->
                            isSaving = false
                            if (result is com.nirogbhumi.app.data.CloudResult.Success) state.currentScreen = "selection_caregiver"
                            else state.authError = (result as com.nirogbhumi.app.data.CloudResult.Failure).message
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepGreen),
                    shape = RoundedCornerShape(27.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Text("Continue", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun OutlinedProfileField(label: String, value: String, placeholder: String, onValueChange: (String) -> Unit) {
    Column {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ink)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = Ink.copy(alpha = 0.3f)) },
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DeepGreen,
                unfocusedBorderColor = Line,
                focusedContainerColor = White,
                unfocusedContainerColor = White
            )
        )
    }
}

// SCREEN 7: SELF OR CAREGIVER TRACKER SELECTION
@Composable
fun SelfOrCaregiverScreen(state: NirogState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = { state.currentScreen = "setup_profile" }) {
                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Who are you tracking health for?",
                fontSize = 32.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Ink
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Nirog Bhumi customizes vitals logs depending on whether these represent your own levels or parents you look after.",
                fontSize = 15.sp,
                color = Ink.copy(alpha = 0.65f),
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Option 1: Myself
                CaregiverOptionCard(
                    title = "Myself",
                    desc = "My own metabolic health, physical walk goals, and water cycles tracking.",
                    isSelected = state.isTrackingForSelf,
                    onClick = { state.isTrackingForSelf = true }
                )

                // Option 2: Family member
                CaregiverOptionCard(
                    title = "A family member",
                    desc = "Parent, spouse, or another elder's HbA1c, BP charts, and medicine reminder sequences.",
                    isSelected = !state.isTrackingForSelf,
                    onClick = { state.isTrackingForSelf = false }
                )
            }
        }

        Button(
            onClick = { state.currentScreen = "health_profile_setup" },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DeepGreen),
            shape = RoundedCornerShape(27.dp)
        ) {
            Text("Continue", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
        }
    }
}

@Composable
fun CaregiverOptionCard(title: String, desc: String, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.5.dp,
                if (isSelected) DeepGreen else Line.copy(alpha = 0.4f),
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = if (isSelected) SoftClay else White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = Ink, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(desc, fontSize = 13.sp, color = Ink.copy(alpha = 0.7f), lineHeight = 18.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = DeepGreen)
            )
        }
    }
}

// SCREEN 8: HEALTH PROFILE METABOLIC QUESTIONS
@Composable
fun HealthProfileSetupScreen(state: NirogState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { state.currentScreen = "selection_caregiver" }) {
                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Text(
                "Step 2 of 3",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Ink.copy(alpha = 0.5f)
            )
            TextButton(onClick = { state.currentScreen = "goal_selection" }) {
                Text("Skip", color = DeepGreen, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Your health profile",
                fontSize = 32.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Ink
            )

            Text(
                text = "Select states that characterize the metabolic profile. Safe baseline personalization parameters apply.",
                fontSize = 15.sp,
                color = Ink.copy(alpha = 0.65f)
            )

            // Q1: Diabetes Status
            HealthProfileSelectorRow(
                question = "Diabetes Status",
                options = listOf("None", "Pre-diabetic", "Type 2", "Type 1", "Gestational"),
                selectedValue = state.selectedDiabetesStatus,
                onSelect = { state.selectedDiabetesStatus = it }
            )

            // Q2: BP Status
            HealthProfileSelectorRow(
                question = "Blood Pressure status",
                options = listOf("Normal", "High BP", "Low BP", "Not sure"),
                selectedValue = state.selectedBpStatus,
                onSelect = { state.selectedBpStatus = it }
            )

            // Q3: Under Medication
            HealthProfileSelectorRow(
                question = "Currently on Medication?",
                options = listOf("Yes", "No", "Not sure"),
                selectedValue = state.selectedOnMedication,
                onSelect = { state.selectedOnMedication = it }
            )

            // Q4: Supervised under Doctor
            HealthProfileSelectorRow(
                question = "Active Physician supervision?",
                options = listOf("Yes", "No"),
                selectedValue = state.selectedDoctorSupervision,
                onSelect = { state.selectedDoctorSupervision = it }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }

        Box(modifier = Modifier.padding(16.dp)) {
            Button(
                onClick = { state.currentScreen = "goal_selection" },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DeepGreen),
                shape = RoundedCornerShape(27.dp)
            ) {
                Text("Continue", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun HealthProfileSelectorRow(question: String, options: List<String>, selectedValue: String, onSelect: (String) -> Unit) {
    Column {
        Text(question, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { opt ->
                val active = opt == selectedValue
                Box(
                    modifier = Modifier
                        .background(if (active) DeepGreen else White, RoundedCornerShape(12.dp))
                        .border(1.dp, if (active) DeepGreen else Line, RoundedCornerShape(12.dp))
                        .clickable { onSelect(opt) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        opt,
                        color = if (active) Color.White else Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// SCREEN 9: MAIN GOAL SELECTION (MULTISELECT CHIPS)
@Composable
fun GoalSelectionScreen(state: NirogState) {
    val goalOptionsList = listOf(
        "Control sugar",
        "Improve lifestyle",
        "Reverse diabetes journey",
        "Track parent’s health",
        "Improve BP",
        "Sleep better",
        "Walk more",
        "Join a program"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { state.currentScreen = "health_profile_setup" }) {
                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
                }
                Text(
                    "Step 3 of 3",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Ink.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "What should Nirog Bhumi help with?",
                fontSize = 32.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Ink
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select all purposes that apply to you. Multiple target focus metrics allowed.",
                fontSize = 15.sp,
                color = Ink.copy(alpha = 0.65f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Multiselect grid items flow simulation
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                goalOptionsList.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { goalItem ->
                            val contains = state.selectedGoals.contains(goalItem)
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 60.dp)
                                    .border(
                                        1.5.dp,
                                        if (contains) DeepGreen else Line.copy(alpha = 0.4f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        if (contains) {
                                            state.selectedGoals.remove(goalItem)
                                        } else {
                                            state.selectedGoals.add(goalItem)
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = if (contains) SoftClay else White),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (contains) Icons.Filled.CheckCircle else Icons.Outlined.AddCircle,
                                        contentDescription = null,
                                        tint = if (contains) DeepGreen else Ink.copy(alpha = 0.4f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = goalItem,
                                        fontWeight = FontWeight.Bold,
                                        color = Ink,
                                        fontSize = 13.sp,
                                        lineHeight = 16.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { state.currentScreen = "program_code_optional" },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DeepGreen),
            shape = RoundedCornerShape(27.dp)
        ) {
            Text("Complete Setup", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
        }
    }
}

// SCREEN 11: PROGRAM CODE OPTIONAL (UNLOCK ACCREDITED PATHWAY)
@Composable
fun ProgramCodeOptionalScreen(state: NirogState) {
    var codeText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = { state.currentScreen = "goal_selection" }) {
                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
                }
            }

            Text(
                text = "Are you part of a Nirog Bhumi program?",
                fontSize = 28.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Ink,
                lineHeight = 34.sp
            )

            Text(
                text = "Program members receive assigned food plans, post-meal routines, yoga guidance, and expert reviews.",
                fontSize = 14.sp,
                color = Ink.copy(alpha = 0.7f),
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = codeText,
                onValueChange = {
                    codeText = it.uppercase()
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Code e.g. NIROG6M", color = Ink.copy(alpha = 0.3f)) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DeepGreen,
                    unfocusedBorderColor = Line,
                    focusedContainerColor = White,
                    unfocusedContainerColor = White
                )
            )

            errorMessage?.let { error ->
                Text(
                    text = error,
                    color = Color.Red,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = SoftClay),
                border = BorderStroke(1.dp, Line),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.Star, contentDescription = "Star features", tint = DeepGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Program Mode Perks", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ink)
                    }
                    Text(
                        text = "Unlocking Program Mode grants you active daily timelines, custom naturopathy routines, direct expert feedback circles, and weekly reports auditing.",
                        fontSize = 12.sp,
                        color = Ink.copy(alpha = 0.8f),
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    if (codeText.length < 4) { errorMessage = "Enter the program code shared by your care team"; return@Button }
                    verifying = true
                    state.repository.redeemProgramCode(codeText) { result ->
                        verifying = false
                        when (result) {
                            is com.nirogbhumi.app.data.CloudResult.Success -> { state.isProgramActive = true; state.programCodeInput = codeText; state.currentScreen = "onboarding_complete" }
                            is com.nirogbhumi.app.data.CloudResult.Failure -> errorMessage = result.message
                        }
                    }
                },
                enabled = !verifying,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DeepGreen),
                shape = RoundedCornerShape(27.dp)
            ) {
                if (verifying) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("Unlock Program Mode", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
            }

            OutlinedButton(
                onClick = {
                    state.currentScreen = "onboarding_complete"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                border = BorderStroke(1.dp, Line),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
                shape = RoundedCornerShape(27.dp)
            ) {
                Text("I will do this later", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// SCREEN 12: ONBOARDING COMPLETE GREETING CARD SCREEN
@Composable
fun OnboardingCompleteScreen(state: NirogState) {
    var isSaving by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            NirogLogo(
                modifier = Modifier
                    .size(100.dp)
                    .border(1.5.dp, Line, CircleShape)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "You're ready.",
                    fontSize = 32.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Start with today's check-in metrics. It takes less than 2 minutes of screen time daily.",
                    fontSize = 15.sp,
                    color = Ink.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    lineHeight = 22.sp
                )
            }

            // Small mock health stats visual overview card
            Card(
                colors = CardDefaults.cardColors(containerColor = White),
                border = BorderStroke(1.dp, Line.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sanctuary initialized for ${state.profileName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Earth)
                    Divider(color = Line.copy(alpha = 0.3f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Diabetes status:", fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
                        Text(state.selectedDiabetesStatus, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Target goals:", fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
                        Text("${state.selectedGoals.size} targets active", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
                    }
                    if (state.isProgramActive) {
                        Box(
                            modifier = Modifier
                                .background(DeepGreen, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .align(Alignment.CenterHorizontally)
                        ) {
                            Text("Active Lifestyle Program Enabled", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (state.authError.isNotBlank()) {
                Text(state.authError, color = Color(0xFF8B2E2E), fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            }

            Button(
                onClick = {
                    state.authError = ""
                    isSaving = true
                    state.repository.saveProfile(mapOf(
                        "onboardingComplete" to true,
                        "trackingFor" to if (state.isTrackingForSelf) "self" else "family",
                        "diabetesStatus" to state.selectedDiabetesStatus,
                        "bpStatus" to state.selectedBpStatus,
                        "onMedication" to state.selectedOnMedication,
                        "doctorSupervision" to state.selectedDoctorSupervision,
                        "goals" to state.selectedGoals.toList(),
                        "programActive" to state.isProgramActive
                    )) { result ->
                        isSaving = false
                        if (result is com.nirogbhumi.app.data.CloudResult.Success) state.currentScreen = "dashboard"
                        else state.authError = (result as com.nirogbhumi.app.data.CloudResult.Failure).message
                    }
                },
                enabled = !isSaving,
                modifier = Modifier
                    .fillModifierOnboarding()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DeepGreen),
                shape = RoundedCornerShape(27.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Text("Go to Today", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

// Helper utility spacing constraints
private fun Modifier.fillModifierOnboarding() = this.fillMaxWidth()
