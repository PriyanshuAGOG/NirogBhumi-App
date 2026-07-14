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
    "Privacy Policy" to "Nirog Bhumi is the Data Fiduciary for your personal data under India's Digital Personal Data Protection Act, 2023 (DPDP Act). We process identity, contact, health logs, uploaded reports, connected-device data, consultations, program activity, orders, and technical diagnostics only for the purposes you've been shown. Health records are not public and are shared with an assigned expert only when you book care or join a program - and only for members in that expert's own batch. We do not sell your data or use it for third-party advertising. Data Processors (cloud hosting, diagnostics, notifications, payments) process it on our instructions under contract. You may request access, correction, export, or deletion, subject to lawful retention. Full policy: /legal/privacy-policy.html on the Nirog Bhumi website.",
    "Your rights (DPDP Act)" to "You have the right to: access a summary of the data we hold about you; correct or complete inaccurate data; erase your data (subject to lawful retention); withdraw consent for optional processing at any time; a readily available grievance mechanism; and nominate another person to exercise your rights on your death or incapacity. Use Profile > Privacy and consent and Data Controls in the app, or contact our Grievance Officer. If unsatisfied, you may complain to the Data Protection Board of India.",
    "Grievance Officer & complaints" to "For any privacy question, request, or complaint about your personal data, contact our Grievance Officer at grievance@nirogbhumi.com. We respond within the period required by law. The Grievance Officer's name and postal address are published on the Nirog Bhumi legal website (/legal/grievance.html). If your grievance isn't resolved to your satisfaction, you may escalate to the Data Protection Board of India.",
    "Terms of Use" to "Provide accurate information, protect your account, and use the service lawfully. Insights are educational and do not authorize medication changes. Fraud, harassment, unauthorized access, or attempts to compromise the service may result in suspension. Final operating-entity and jurisdiction terms are published on the Nirog Bhumi website.",
    "Consent Notice" to "Required consent covers app use, protected health-data storage, and the medical disclaimer - without these the app cannot function, so they are withdrawn by anonymizing or deleting your account. Expert review applies when you book care or join a program. Anonymized research and product/marketing messages are separate, optional, off by default, and independently withdrawable in Privacy and consent. Each consent is recorded with its date and version; if this notice materially changes we ask you to consent again.",
    "Children & dependents" to "The app is for adults (18+). You may add a dependent/family profile to help manage someone's health; if that person is under 18 you confirm you are their parent or lawful guardian and consent on their behalf. Consistent with the DPDP Act, we do not knowingly use a child's data for tracking, behavioural monitoring, or targeted advertising. If a child's data was added without proper consent, contact our Grievance Officer and we will delete it.",
    "Data Deletion Policy" to "Submit an in-app deletion request from Data Controls, or email privacy@nirogbhumi.com from your registered contact (no app needed). After identity verification, access is disabled and data is deleted or irreversibly anonymized, except limited records retained for legal, payment, fraud-prevention, or dispute obligations. Completion is recorded and communicated. Details and retention timeframes: /legal/account-deletion.html.",
    "Refund Policy" to "Eligibility depends on cancellation timing, service delivery, payment settlement, product condition, and applicable consumer law. The exact consultation, program, or product terms shown before payment control the request.",
    "Shipping Policy" to "Availability, dispatch estimates, shipping fees, serviceable PIN codes, tracking, failed delivery, damage, and return eligibility are shown during checkout and in order details. Health and hygiene products may have lawful return restrictions.",
    "Program Terms" to "Programs support lifestyle consistency and do not promise a cure, guaranteed reversal, medicine discontinuation, or a fixed outcome. Outcomes vary. Continue appropriate medical supervision and promptly report concerning symptoms or readings to a qualified clinician."
)

@Composable
fun LegalCenterScreen(state: NirogState) {
    var expanded by remember { mutableStateOf(state.legalInitialSection ?: "Medical Disclaimer") }
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
