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
import com.nirogbhumi.app.health.domain.GlucoseStatus
import com.nirogbhumi.app.health.domain.HealthLabels
import com.nirogbhumi.app.health.domain.HealthStateBuilder
import com.nirogbhumi.app.health.domain.HealthUiState
import com.nirogbhumi.app.health.domain.status
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.collectHealth
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
 * It reads today's entries from the shared health state (never
 * fabricated: a metric that hasn't been logged shows "Not logged today"), maps
 * each to a status via plain clinical thresholds, offers one warm plain-language
 * takeaway, and points at the cumulative Health File. No scores, no grades.
 */
@Composable
fun BodyReportScreen(state: NirogState) {
  val health = state.collectHealth()
  val rows = buildBodyReportRows(health)

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
      onClick = { state.currentScreen = "health_file" },
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

private fun buildBodyReportRows(health: HealthUiState): List<ReportRow> {
  val rows = mutableListOf<ReportRow>()
  val today = health.today.date
  fun isToday(millis: Long) = HealthStateBuilder.dateOf(millis, health.zone) == today

  // Blood sugar: today's latest reading, labelled with the same ranges as every other screen.
  val sugar = health.latestGlucose?.takeIf { isToday(it.measuredAtMillis) }
  if (sugar != null) {
    val (label, kind) = when (sugar.status()) {
      GlucoseStatus.LOW -> "Low" to StatusKind.Attention
      GlucoseStatus.NORMAL -> "In range" to StatusKind.InRange
      GlucoseStatus.HIGH -> "High" to StatusKind.Critical
    }
    rows += ReportRow("Blood sugar (${HealthLabels.glucoseKind(sugar.kind).lowercase()})", "${sugar.value.toInt()}", label, kind)
  } else {
    rows += ReportRow("Blood sugar", null, "", StatusKind.Neutral)
  }

  // Blood pressure
  val bp = health.latestBp?.takeIf { isToday(it.measuredAtMillis) }
  if (bp != null) {
    val (label, kind) = when {
      bp.systolic >= 180 || bp.diastolic >= 120 -> "High" to StatusKind.Critical
      bp.systolic >= 140 || bp.diastolic >= 90 -> "Watch" to StatusKind.Attention
      else -> "Steady" to StatusKind.InRange
    }
    rows += ReportRow("Blood pressure", HealthLabels.bp(bp), label, kind)
  } else {
    rows += ReportRow("Blood pressure", null, "", StatusKind.Neutral)
  }

  health.latestWeight?.takeIf { isToday(it.measuredAtMillis) }?.let {
    rows += ReportRow("Weight", HealthLabels.weight(it.valueKg), "Logged", StatusKind.Synced)
  }

  // Sleep and steps (informational)
  health.today.sleepMinutesToday?.takeIf { it > 0 }?.let { minutes ->
    rows += ReportRow("Sleep", "${minutes / 60}h ${minutes % 60}m", "Logged", StatusKind.Synced)
  }
  if (health.today.stepsToday > 0) {
    rows += ReportRow("Steps so far", "${health.today.stepsToday}", "Synced", StatusKind.Synced)
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
