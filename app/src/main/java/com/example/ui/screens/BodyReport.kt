package com.nirogbhumi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.components.InsightCard
import com.nirogbhumi.app.ui.components.NirogCard
import com.nirogbhumi.app.ui.components.PrimaryButton
import com.nirogbhumi.app.ui.components.RowCard
import com.nirogbhumi.app.ui.components.StatusChip
import com.nirogbhumi.app.ui.components.StatusKind
import com.nirogbhumi.app.ui.theme.NirogColor
import com.nirogbhumi.app.ui.theme.NirogSpace
import com.nirogbhumi.app.ui.theme.NirogType

/**
 * Body Report (PRD v2 USP) - the instant payoff shown right after a check-in.
 *
 * It reads the member's most-recent logged values from [NirogState] (never
 * fabricated: a metric that hasn't been logged shows "Not logged today"), maps
 * each to a status via plain clinical thresholds, offers one warm plain-language
 * takeaway, and points at the cumulative Health File. No scores, no grades.
 */
@Composable
fun BodyReportScreen(state: NirogState) {
  val rows = buildBodyReportRows(state)

  Column(
    Modifier
      .fillMaxSize()
      .background(NirogColor.surface)
      .verticalScroll(rememberScrollState())
      .padding(NirogSpace.lg),
  ) {
    Spacer(Modifier.size(NirogSpace.xxl))

    // Hero
    Box(
      Modifier
        .size(56.dp)
        .clip(CircleShape)
        .background(NirogColor.statusInRangeBg),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Filled.Check, contentDescription = null, tint = NirogColor.statusInRange, modifier = Modifier.size(28.dp))
    }
    Spacer(Modifier.size(NirogSpace.md))
    Text("Your Body Report", style = NirogType.heroTitle, color = NirogColor.forest)
    Text("Today's check-in, in plain language", style = NirogType.caption, color = NirogColor.inkMuted)

    Spacer(Modifier.size(NirogSpace.xl))

    // Metric rows
    NirogCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = NirogSpace.xl, vertical = NirogSpace.sm)) {
      rows.forEachIndexed { index, row ->
        Row(
          Modifier
            .fillMaxWidth()
            .padding(vertical = NirogSpace.md),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(row.name, style = NirogType.bodyStrong, color = NirogColor.inkPrimary, modifier = Modifier.weight(1f))
          if (row.value != null) {
            Text(row.value, style = NirogType.cardTitle, color = NirogColor.inkPrimary)
            Spacer(Modifier.width(NirogSpace.md))
            StatusChip(row.statusLabel, row.statusKind)
          } else {
            Text("Not logged today", style = NirogType.caption, color = NirogColor.inkMuted)
          }
        }
        if (index != rows.lastIndex) {
          Box(Modifier.fillMaxWidth().height(1.dp).background(NirogColor.surfaceSunken))
        }
      }
    }

    Spacer(Modifier.size(NirogSpace.lg))
    InsightCard(text = buildTakeaway(rows), emphasis = takeawayHeadline(rows))

    Spacer(Modifier.size(NirogSpace.lg))
    RowCard(
      title = "Health File updated",
      subtitle = "Always ready to show any doctor",
      trailing = "Open",
      onClick = { state.currentScreen = "dashboard" },
    )

    Spacer(Modifier.size(NirogSpace.md))
    RowCard(
      title = "See your rhythm",
      subtitle = "Your check-in pattern over the last 30 days",
      trailing = "View",
      onClick = { state.currentScreen = "rhythm" },
    )

    Spacer(Modifier.size(NirogSpace.xl))
    PrimaryButton("Done", onClick = { state.currentScreen = "dashboard" })
    Spacer(Modifier.size(NirogSpace.xxl))
  }
}

private data class ReportRow(
  val name: String,
  val value: String?,
  val statusLabel: String,
  val statusKind: StatusKind,
)

private fun buildBodyReportRows(state: NirogState): List<ReportRow> {
  val rows = mutableListOf<ReportRow>()

  // Fasting sugar
  val sugar = state.fastingSugarValue
  if (sugar > 0) {
    val (label, kind) = when {
      sugar < 70 -> "Low" to StatusKind.Attention
      sugar in 70..99 -> "In range" to StatusKind.InRange
      sugar in 100..125 -> "Watch" to StatusKind.Attention
      else -> "High" to StatusKind.Critical
    }
    rows += ReportRow("Fasting sugar", "$sugar", label, kind)
  } else {
    rows += ReportRow("Fasting sugar", null, "", StatusKind.Neutral)
  }

  // Blood pressure
  val bp = state.latestBpReading
  if (!bp.isNullOrBlank() && bp.contains('/')) {
    val parts = bp.split('/')
    val sys = parts.getOrNull(0)?.trim()?.toIntOrNull()
    val dia = parts.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull()
    val (label, kind) = when {
      sys == null || dia == null -> "Recorded" to StatusKind.Neutral
      sys >= 180 || dia >= 120 -> "High" to StatusKind.Critical
      sys >= 140 || dia >= 90 -> "Watch" to StatusKind.Attention
      else -> "Steady" to StatusKind.InRange
    }
    rows += ReportRow("Blood pressure", bp, label, kind)
  } else {
    rows += ReportRow("Blood pressure", null, "", StatusKind.Neutral)
  }

  // Sleep (informational)
  val sleepTotal = state.sleepHours * 60 + state.sleepMinutes
  if (sleepTotal > 0) {
    rows += ReportRow("Sleep", "${state.sleepHours}h ${state.sleepMinutes}m", "Logged", StatusKind.Synced)
  }

  // Steps (informational)
  if (state.stepsLogged > 0) {
    rows += ReportRow("Steps so far", "${state.stepsLogged}", "Synced", StatusKind.Synced)
  }

  return rows
}

private fun takeawayHeadline(rows: List<ReportRow>): String {
  val logged = rows.filter { it.value != null }
  return when {
    logged.isEmpty() -> "Nothing logged yet"
    logged.any { it.statusKind == StatusKind.Critical } -> "Worth a closer look"
    logged.all { it.statusKind == StatusKind.InRange || it.statusKind == StatusKind.Synced } -> "A steady day"
    else -> "Mostly on track"
  }
}

private fun buildTakeaway(rows: List<ReportRow>): String {
  val logged = rows.filter { it.value != null }
  return when {
    logged.isEmpty() ->
      "You didn't log anything this time - that's okay. Even one reading a day helps your care team see the full picture."
    logged.any { it.statusKind == StatusKind.Critical } ->
      "One of today's readings is higher than usual. Repeat it when you can, and mention it to your doctor - especially if you feel unwell."
    logged.all { it.statusKind == StatusKind.InRange || it.statusKind == StatusKind.Synced } ->
      "Your readings look balanced today. A good day for a 15-minute walk after lunch."
    else ->
      "A mostly steady day. Keep the rhythm going - small, consistent check-ins are what move the needle."
  }
}
