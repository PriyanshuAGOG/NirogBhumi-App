package com.nirogbhumi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirogbhumi.app.health.domain.ActivityEntry
import com.nirogbhumi.app.health.domain.BpEntry
import com.nirogbhumi.app.health.domain.ClockText
import com.nirogbhumi.app.health.domain.GlucoseEntry
import com.nirogbhumi.app.health.domain.GlucoseKind
import com.nirogbhumi.app.health.domain.SleepComposition
import com.nirogbhumi.app.health.domain.SleepEntry
import com.nirogbhumi.app.health.domain.SleepTimes
import com.nirogbhumi.app.health.domain.WeightEntry
import com.nirogbhumi.app.ui.components.ClockPickerDialog
import com.nirogbhumi.app.ui.components.CorrectionDialog
import com.nirogbhumi.app.ui.theme.NirogColor
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Validation limits shared by the check-in wizard and the correction dialogs. */
object HealthLimits {
    const val SUGAR_MIN = 20; const val SUGAR_MAX = 800
    const val HBA1C_MIN = 3.0; const val HBA1C_MAX = 20.0
    const val SYSTOLIC_MIN = 60; const val SYSTOLIC_MAX = 260
    const val DIASTOLIC_MIN = 30; const val DIASTOLIC_MAX = 180
    const val WEIGHT_MIN = 20.0; const val WEIGHT_MAX = 300.0
    const val ACTIVITY_MAX_MINUTES = 600
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, helper: String? = null, decimal: Boolean = false, error: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it.isDigit() || (decimal && it == '.') }.take(6)) },
        label = { Text(label) },
        supportingText = { Text(error ?: helper ?: "", color = if (error != null) NirogColor.statusCritical else NirogColor.inkTertiary) },
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(if (selected) NirogColor.forestSoft else NirogColor.surfaceNeutral)
            .clickable(onClick = onClick)
            .semantics { role = Role.RadioButton; this.selected = selected }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else NirogColor.inkSecondary) }
}

// ---- blood sugar ----
@Composable
fun GlucoseEditDialog(entry: GlucoseEntry, onDismiss: () -> Unit, onSave: (edits: Map<String, Any?>, report: (String?) -> Unit) -> Unit) {
    val isA1c = entry.isHbA1c
    var text by remember { mutableStateOf(if (isA1c) "%.1f".format(java.util.Locale.US, entry.value) else entry.value.toInt().toString()) }
    var kind by remember { mutableStateOf(if (entry.kind == GlucoseKind.POST_MEAL) GlucoseKind.POST_MEAL else GlucoseKind.FASTING) }
    val intValue = text.toIntOrNull()
    val a1cValue = text.toDoubleOrNull()
    val error = when {
        text.isBlank() -> null
        isA1c && (a1cValue == null || a1cValue !in HealthLimits.HBA1C_MIN..HealthLimits.HBA1C_MAX) -> "Enter a value between 3 and 20%."
        !isA1c && (intValue == null || intValue !in HealthLimits.SUGAR_MIN..HealthLimits.SUGAR_MAX) -> "Enter a value between 20 and 800 mg/dL."
        else -> null
    }
    val canSave = text.isNotBlank() && error == null
    CorrectionDialog("Edit blood sugar", canSave, onDismiss, onSave = { report ->
        val edits = if (isA1c) mapOf("value" to a1cValue) else buildMap<String, Any?> {
            put("value", intValue)
            if (entry.kind == GlucoseKind.FASTING || entry.kind == GlucoseKind.POST_MEAL) put("readingType", if (kind == GlucoseKind.POST_MEAL) "post_meal" else "fasting")
        }
        onSave(edits, report)
    }) {
        NumberField(if (isA1c) "HbA1c (%)" else "Blood sugar (mg/dL)", text, { text = it }, decimal = isA1c, error = error)
        if (!isA1c && (entry.kind == GlucoseKind.FASTING || entry.kind == GlucoseKind.POST_MEAL)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Fasting", kind == GlucoseKind.FASTING) { kind = GlucoseKind.FASTING }
                ChoiceChip("After a meal", kind == GlucoseKind.POST_MEAL) { kind = GlucoseKind.POST_MEAL }
            }
        }
    }
}

// ---- blood pressure ----
@Composable
fun BpEditDialog(entry: BpEntry, onDismiss: () -> Unit, onSave: (edits: Map<String, Any?>, report: (String?) -> Unit) -> Unit) {
    var upper by remember { mutableStateOf(entry.systolic.toString()) }
    var lower by remember { mutableStateOf(entry.diastolic.toString()) }
    val sys = upper.toIntOrNull(); val dia = lower.toIntOrNull()
    val upperError = if (upper.isNotBlank() && (sys == null || sys !in HealthLimits.SYSTOLIC_MIN..HealthLimits.SYSTOLIC_MAX)) "Enter a number between 60 and 260." else null
    val lowerError = if (lower.isNotBlank() && (dia == null || dia !in HealthLimits.DIASTOLIC_MIN..HealthLimits.DIASTOLIC_MAX)) "Enter a number between 30 and 180." else null
    val orderError = if (sys != null && dia != null && upperError == null && lowerError == null && dia >= sys) "Upper BP should be higher than Lower BP." else null
    CorrectionDialog("Edit blood pressure", sys != null && dia != null && upperError == null && lowerError == null && orderError == null, onDismiss, onSave = { report ->
        onSave(mapOf("systolic" to sys, "diastolic" to dia), report)
    }) {
        NumberField("Upper BP (mmHg)", upper, { upper = it }, helper = "Systolic, the top number", error = upperError)
        NumberField("Lower BP (mmHg)", lower, { lower = it }, helper = "Diastolic, the bottom number", error = lowerError ?: orderError)
    }
}

// ---- weight ----
@Composable
fun WeightEditDialog(entry: WeightEntry, onDismiss: () -> Unit, onSave: (edits: Map<String, Any?>, report: (String?) -> Unit) -> Unit) {
    var text by remember { mutableStateOf("%.1f".format(java.util.Locale.US, entry.valueKg)) }
    val kg = text.toDoubleOrNull()
    val error = if (text.isNotBlank() && (kg == null || kg !in HealthLimits.WEIGHT_MIN..HealthLimits.WEIGHT_MAX)) "Enter a weight between 20 and 300 kg." else null
    CorrectionDialog("Edit weight", kg != null && error == null, onDismiss, onSave = { report -> onSave(mapOf("valueKg" to kg), report) }) {
        NumberField("Weight (kg)", text, { text = it }, decimal = true, error = error)
    }
}

// ---- activity ----
@Composable
fun ActivityEditDialog(entry: ActivityEntry, onDismiss: () -> Unit, onSave: (edits: Map<String, Any?>, report: (String?) -> Unit) -> Unit) {
    var text by remember { mutableStateOf((entry.minutes ?: 0).toString()) }
    val minutes = text.toIntOrNull()
    val error = if (text.isNotBlank() && (minutes == null || minutes !in 1..HealthLimits.ACTIVITY_MAX_MINUTES)) "Enter minutes between 1 and 600." else null
    CorrectionDialog("Edit activity", minutes != null && error == null, onDismiss, onSave = { report -> onSave(mapOf("minutes" to minutes), report) }) {
        NumberField("Minutes", text, { text = it }, error = error)
    }
}

// ---- sleep: add or edit ----
/**
 * Add or correct a sleep. Times show as "10:30 PM" / "6:30 AM" whatever the phone's clock setting is;
 * the dialog builds real timestamps and refuses impossible ones with a plain explanation, instead of
 * quietly turning identical times into a 24-hour night.
 */
@Composable
fun SleepEditorDialog(
    initial: SleepEntry?,
    zone: ZoneId,
    title: String,
    onDismiss: () -> Unit,
    onSave: (start: Instant, end: Instant, minutes: Int, report: (String?) -> Unit) -> Unit,
) {
    val now = remember { Instant.now() }
    val initialStart = initial?.startAtMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: LocalTime.of(22, 30)
    val initialEnd = initial?.endAtMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: LocalTime.of(6, 30)
    var bedtime by remember { mutableStateOf(initialStart) }
    var wake by remember { mutableStateOf(initialEnd) }
    var pickedDay by remember { mutableStateOf<LocalDate?>(initial?.endAtMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }) }
    var pickBed by remember { mutableStateOf(false) }
    var pickWake by remember { mutableStateOf(false) }
    val today = remember { now.atZone(zone).toLocalDate() }
    val wakeDate = pickedDay ?: SleepTimes.defaultWakeDate(wake, zone, now)
    val result = SleepTimes.compose(bedtime, wake, wakeDate, zone, now)

    CorrectionDialog(
        title = title,
        canSave = result is SleepComposition.Valid,
        onDismiss = onDismiss,
        onSave = { report -> (result as? SleepComposition.Valid)?.let { onSave(it.startAt, it.endAt, it.minutes, report) } },
    ) {
        TimeRow("Went to sleep", ClockText.format12(bedtime)) { pickBed = true }
        TimeRow("Woke up", ClockText.format12(wake)) { pickWake = true }
        Text("Woke up on", fontSize = 13.sp, color = NirogColor.inkTertiary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Today", wakeDate == today) { pickedDay = today }
            ChoiceChip("Yesterday", wakeDate == today.minusDays(1)) { pickedDay = today.minusDays(1) }
        }
        when (result) {
            is SleepComposition.Valid -> Text("You slept for ${ClockText.duration(result.minutes)}.", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NirogColor.forest)
            is SleepComposition.Invalid -> Text(SleepTimes.message(result.problem), fontSize = 13.sp, color = NirogColor.statusCritical)
        }
    }
    if (pickBed) ClockPickerDialog("Went to sleep", bedtime.hour, bedtime.minute, { pickBed = false }) { h, m -> bedtime = LocalTime.of(h, m); pickBed = false }
    if (pickWake) ClockPickerDialog("Woke up", wake.hour, wake.minute, { pickWake = false }) { h, m -> wake = LocalTime.of(h, m); pickWake = false }
}

@Composable
private fun TimeRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = 4.dp)
            .semantics { role = Role.Button },
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = NirogColor.inkPrimary)
        Text(value, fontWeight = FontWeight.Bold, color = NirogColor.forestSoft)
    }
}
