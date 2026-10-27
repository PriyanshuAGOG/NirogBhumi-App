package com.nirogbhumi.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirogbhumi.app.data.CloudResult
import com.nirogbhumi.app.health.HealthConnectManager
import com.nirogbhumi.app.health.SyncPreview
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.theme.NirogColor
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private sealed interface SyncUi {
    data object Loading : SyncUi
    data class Review(val preview: SyncPreview) : SyncUi
    data object Saving : SyncUi
    data class Done(val message: String) : SyncUi
    data class Failed(val message: String) : SyncUi
}

private val dayLabel = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/**
 * "Sync now" for Health Connect, in two honest steps: first show what was found (nothing is saved), then add it only after the
 * member agrees. Steps are Health Connect's own daily totals with overlap between apps already removed, and values that look
 * impossible are left out and counted. A member can also remove everything a phone ever imported.
 */
@Composable
fun HealthConnectSyncDialog(state: NirogState, manager: HealthConnectManager, onClose: () -> Unit) {
    var ui by remember { mutableStateOf<SyncUi>(SyncUi.Loading) }
    var confirmingRemove by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        ui = runCatching { manager.preview() }.fold({ SyncUi.Review(it) }, { SyncUi.Failed(it.message ?: "Health Connect could not be read.") })
    }

    if (confirmingRemove) {
        AlertDialog(
            onDismissRequest = { confirmingRemove = false },
            title = { Text("Remove imported data?") },
            text = { Text("This deletes the steps, sleep, blood sugar, blood pressure and weight that were copied from Health Connect. Anything you typed in yourself stays. You can sync again later.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingRemove = false
                    ui = SyncUi.Saving
                    state.repository.deleteImportedHealthData { result ->
                        ui = when (result) {
                            is CloudResult.Success -> SyncUi.Done(if (result.value == 0) "There was nothing imported to remove." else "Removed ${result.value} imported records.")
                            is CloudResult.Failure -> SyncUi.Failed(result.message)
                        }
                    }
                }) { Text("Remove", color = NirogColor.errorColor) }
            },
            dismissButton = { TextButton(onClick = { confirmingRemove = false }) { Text("Keep") } },
        )
    }

    AlertDialog(
        onDismissRequest = { if (ui !is SyncUi.Saving) onClose() },
        title = {
            Text(
                when (val s = ui) {
                    is SyncUi.Loading -> "Checking Health Connect"
                    is SyncUi.Review -> if (s.preview.isEmpty) "Nothing to add" else "Review before adding"
                    is SyncUi.Saving -> "Working"
                    is SyncUi.Done -> "Done"
                    is SyncUi.Failed -> "Couldn't sync"
                },
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val s = ui) {
                    is SyncUi.Loading, is SyncUi.Saving -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                    is SyncUi.Done -> Text(s.message)
                    is SyncUi.Failed -> Text(s.message)
                    is SyncUi.Review -> ReviewBody(s.preview)
                }
                if (ui !is SyncUi.Loading && ui !is SyncUi.Saving) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { confirmingRemove = true }) { Text("Remove data imported earlier", fontSize = 13.sp, color = NirogColor.inkSecondary) }
                }
            }
        },
        confirmButton = {
            val s = ui
            if (s is SyncUi.Review && !s.preview.isEmpty) {
                TextButton(onClick = {
                    ui = SyncUi.Saving
                    scope.launch {
                        ui = runCatching { manager.commit(s.preview) }.fold(
                            { r -> SyncUi.Done("Added ${r.steps} days of steps, ${r.sleep} sleep, ${r.glucose} blood sugar, ${r.bloodPressure} blood pressure and ${r.weight} weight records.") },
                            { SyncUi.Failed(it.message ?: "Nothing was saved. Please try again.") },
                        )
                    }
                }) { Text("Add to my record", color = NirogColor.forest) }
            } else if (s !is SyncUi.Loading && s !is SyncUi.Saving) {
                TextButton(onClick = onClose) { Text("Close") }
            }
        },
        dismissButton = {
            val s = ui
            if (s is SyncUi.Review && !s.preview.isEmpty) TextButton(onClick = onClose) { Text("Cancel") }
        },
    )
}

@Composable
private fun ReviewBody(p: SyncPreview) {
    if (p.isEmpty) {
        Text("Health Connect has no new steps, sleep, blood sugar, blood pressure or weight for the last 30 days.")
    } else {
        Text("Nothing is saved until you tap Add. This is what was found for the last 30 days:", fontSize = 13.sp, color = NirogColor.inkSecondary)
        if (p.stepDays.isNotEmpty()) {
            Text("Steps: ${"%,d".format(Locale.US, p.totalSteps)} across ${p.stepDays.size} days", fontSize = 15.sp)
            p.stepDays.takeLast(7).reversed().forEach { day ->
                Text("${day.date.format(dayLabel)}: ${"%,d".format(Locale.US, day.steps)} steps", fontSize = 13.sp, color = NirogColor.inkSecondary)
            }
            if (p.stepDays.size > 7) Text("…and ${p.stepDays.size - 7} earlier days", fontSize = 13.sp, color = NirogColor.inkSecondary)
        }
        if (p.sleep.isNotEmpty()) Text("Sleep: ${p.sleep.size} nights", fontSize = 15.sp)
        if (p.glucose.isNotEmpty()) Text("Blood sugar: ${p.glucose.size} readings", fontSize = 15.sp)
        if (p.bloodPressure.isNotEmpty()) Text("Blood pressure: ${p.bloodPressure.size} readings", fontSize = 15.sp)
        if (p.weight.isNotEmpty()) Text("Weight: ${p.weight.size} entries", fontSize = 15.sp)
        if (p.sources.isNotEmpty()) Text("From: ${p.sources.joinToString(", ")}", fontSize = 13.sp, color = NirogColor.inkSecondary)
        Text("Step totals are the figures Health Connect reports after removing overlap between your apps.", fontSize = 12.sp, color = NirogColor.inkSecondary)
    }
    if (p.skippedTotal > 0) Text("Left out ${p.skippedTotal} values that looked unrealistic.", fontSize = 13.sp, color = NirogColor.inkSecondary)
}
