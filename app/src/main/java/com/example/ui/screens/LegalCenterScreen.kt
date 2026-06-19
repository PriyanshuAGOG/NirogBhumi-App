package com.nirogbhumi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirogbhumi.app.ui.NirogState

private val legalSections = listOf(
    "Medical Disclaimer" to "Nirog Bhumi is for education, lifestyle tracking, and wellness support only. It is not a substitute for medical advice, diagnosis, or treatment. Consult a doctor or qualified healthcare professional before changing medication, diet, exercise, or treatment. Seek emergency care when symptoms or readings indicate urgent risk.",
    "Privacy Policy" to "We process identity, contact, health logs, uploaded reports, connected-device data, consultations, program activity, orders, and technical diagnostics to provide requested features. Health records are not public and are shared with assigned experts only when you request care and consent. We do not sell health data. You may request access, correction, export, or deletion subject to lawful retention requirements.",
    "Terms of Use" to "Provide accurate information, protect your account, and use the service lawfully. Insights are educational and do not authorize medication changes. Fraud, harassment, unauthorized access, or attempts to compromise the service may result in suspension. Final operating-entity and jurisdiction terms are published on the Nirog Bhumi website.",
    "Consent Notice" to "Required consent covers app use, protected health-data storage, and the medical disclaimer. Expert review applies when you book care or join a program. Device import, anonymized research, and marketing are separately controllable. Optional consent can be withdrawn in Privacy and Consent settings.",
    "Data Deletion Policy" to "Submit an in-app deletion request from Data Export and Delete. After identity verification and approval, access is disabled and data is deleted or irreversibly anonymized, except limited records retained for legal, payment, fraud-prevention, or dispute obligations. Completion is recorded and communicated.",
    "Refund Policy" to "Eligibility depends on cancellation timing, service delivery, payment settlement, product condition, and applicable consumer law. The exact consultation, program, or product terms shown before payment control the request.",
    "Shipping Policy" to "Availability, dispatch estimates, shipping fees, serviceable PIN codes, tracking, failed delivery, damage, and return eligibility are shown during checkout and in order details. Health and hygiene products may have lawful return restrictions.",
    "Program Terms" to "Programs support lifestyle consistency and do not promise a cure, guaranteed reversal, medicine discontinuation, or a fixed outcome. Outcomes vary. Continue appropriate medical supervision and promptly report concerning symptoms or readings to a qualified clinician."
)

@Composable
fun LegalCenterScreen(state: NirogState) {
    var expanded by remember { mutableStateOf("Medical Disclaimer") }
    Column(Modifier.fillMaxSize().background(Color(0xFFF8F6EF)).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.currentScreen = state.legalReturnRoute }) { Icon(Icons.Outlined.ArrowBack, "Back") }
            Text("Legal and trust", fontFamily = FontFamily.Serif, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF182219))
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Clear terms for your health data, care, and purchases.", color = Color(0xFF526057))
            legalSections.forEach { (title, body) ->
                Card(Modifier.fillMaxWidth().clickable { expanded = if (expanded == title) "" else title }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = Color(0xFF182219)); Icon(Icons.Outlined.ExpandMore, null) }
                        if (expanded == title) { Spacer(Modifier.height(10.dp)); Text(body, fontSize = 13.sp, lineHeight = 20.sp, color = Color(0xFF455148)) }
                    }
                }
            }
            Text("These in-app summaries must match the final counsel-reviewed policies published by the operating entity.", fontSize = 11.sp, color = Color(0xFF6B736C), modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}
