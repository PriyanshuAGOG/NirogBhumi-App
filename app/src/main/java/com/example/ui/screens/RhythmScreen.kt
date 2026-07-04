package com.nirogbhumi.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.nirogbhumi.app.data.CloudResult
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.localDayKey
import com.nirogbhumi.app.ui.components.PrimaryButton
import com.nirogbhumi.app.ui.components.SectionLabel
import com.nirogbhumi.app.ui.theme.NirogColor
import com.nirogbhumi.app.ui.theme.NirogSpace
import com.nirogbhumi.app.ui.theme.NirogType
import com.google.firebase.Timestamp

/**
 * Rhythm (PRD v2, Pillar D) - the deliberately NON-PUNITIVE consistency view.
 *
 * A 7-day ring (a fill that never counts down to a shaming zero) and a 30-day
 * grid where logged days are green and missed days stay neutral - never red.
 * Copy is warm and forward-looking; after a quiet stretch it offers a gentle
 * way back in, never a broken-streak guilt message.
 *
 * Logged-days are derived from real Firestore data (the member's blood-sugar
 * readings) - nothing here is fabricated. Before the first reading it shows an
 * honest empty state.
 */
@Composable
fun RhythmScreen(state: NirogState) {
  var loggedDayKeys by remember { mutableStateOf<Set<Long>>(emptySet()) }
  var loaded by remember { mutableStateOf(false) }

  DisposableEffect(Unit) {
    val sub = state.repository.listenUserCollection("glucoseReadings", 90, orderByField = "measuredAt", descending = true) { result ->
      if (result is CloudResult.Success) {
        loggedDayKeys = result.value.mapNotNull { doc ->
          val ts = (doc.values["createdAt"] as? Timestamp) ?: (doc.values["measuredAt"] as? Timestamp)
          ts?.let { localDayKey(it.toDate().time) }
        }.toSet()
      }
      loaded = true
    }
    onDispose { sub.cancel() }
  }

  val todayKey = localDayKey(System.currentTimeMillis())
  val last7 = (0..6).count { (todayKey - it) in loggedDayKeys }
  val daysSinceLast = loggedDayKeys.maxOrNull()?.let { todayKey - it }

  Column(
    Modifier
      .fillMaxSize()
      .background(NirogColor.surface)
      .verticalScroll(rememberScrollState()),
  ) {
    // Header
    Row(
      Modifier
        .fillMaxWidth()
        .padding(horizontal = NirogSpace.sm, vertical = NirogSpace.sm),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = { state.currentScreen = "dashboard" }) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NirogColor.forest)
      }
      Column {
        Text("Your rhythm", style = NirogType.sectionHeading, color = NirogColor.forest)
        Text("Not a streak. A pattern.", style = NirogType.caption, color = NirogColor.inkMuted)
      }
    }

    Column(Modifier.padding(horizontal = NirogSpace.lg)) {
      androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NirogRadiusLg),
        color = NirogColor.surfaceCard,
        shadowElevation = 2.dp,
      ) {
        Column(
          Modifier.padding(NirogSpace.xl),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          SectionLabel("Last 7 days")
          Spacer(Modifier.size(NirogSpace.lg))
          RhythmRing(loggedCount = last7, total = 7)
          Spacer(Modifier.size(NirogSpace.xl))

          SectionLabel("Last 30 days", modifier = Modifier.align(Alignment.Start))
          Spacer(Modifier.size(NirogSpace.md))
          ThirtyDayGrid(todayKey = todayKey, loggedDayKeys = loggedDayKeys)

          Spacer(Modifier.size(NirogSpace.lg))
          Box(
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(NirogRadiusMd))
              .background(NirogColor.surfaceSunken)
              .padding(NirogSpace.lg),
          ) {
            Text(
              warmRecap(loaded, last7, loggedDayKeys.isEmpty()),
              style = NirogType.body,
              color = NirogColor.inkSecondary,
            )
          }
        }
      }

      if (daysSinceLast != null && daysSinceLast >= 2) {
        Spacer(Modifier.size(NirogSpace.lg))
        Box(
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NirogRadiusLg))
            .background(NirogColor.statusAttentionBg)
            .padding(NirogSpace.xl),
        ) {
          Column {
            SectionLabel("Gentle nudge", color = NirogColor.statusAttention)
            Spacer(Modifier.size(NirogSpace.xs))
            Text(
              "It's been $daysSinceLast days since your last check-in. No worries - let's pick it back up today.",
              style = NirogType.body,
              color = NirogColor.inkSecondary,
            )
            Spacer(Modifier.size(NirogSpace.md))
            PrimaryButton("Log now", onClick = { state.currentScreen = "daily_checkin" })
          }
        }
      }

      Spacer(Modifier.size(NirogSpace.xxl))
    }
  }
}

private const val NirogRadiusLg = 24
private const val NirogRadiusMd = 16

@Composable
private fun RhythmRing(loggedCount: Int, total: Int) {
  Box(contentAlignment = Alignment.Center) {
    Canvas(Modifier.size(168.dp)) {
      val strokeWidth = 14.dp.toPx()
      val diameter = size.minDimension - strokeWidth
      val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
      val arcSize = Size(diameter, diameter)
      drawArc(
        color = NirogColor.statusNeutralBg,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokeWidth),
      )
      val sweep = if (total == 0) 0f else 360f * loggedCount / total
      if (sweep > 0f) {
        drawArc(
          color = NirogColor.statusInRange,
          startAngle = -90f,
          sweepAngle = sweep,
          useCenter = false,
          topLeft = topLeft,
          size = arcSize,
          style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
      }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text("$loggedCount/$total", style = NirogType.display, color = NirogColor.forest)
      Text("DAYS LOGGED", style = NirogType.overline, color = NirogColor.inkMuted)
    }
  }
}

@Composable
private fun ThirtyDayGrid(todayKey: Long, loggedDayKeys: Set<Long>) {
  // 30 cells (5 rows x 6), oldest first, ending today. Logged = green,
  // missed = neutral (never red).
  Column(verticalArrangement = Arrangement.spacedBy(NirogSpace.xs)) {
    for (rowIndex in 0 until 5) {
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NirogSpace.xs),
      ) {
        for (colIndex in 0 until 6) {
          val cellIndex = rowIndex * 6 + colIndex // 0..29
          val dayKey = todayKey - (29 - cellIndex)
          val logged = dayKey in loggedDayKeys
          Box(
            Modifier
              .weight(1f)
              .aspectRatio(1f)
              .clip(RoundedCornerShape(6.dp))
              .background(if (logged) NirogColor.statusInRange else NirogColor.statusNeutralBg),
          )
        }
      }
    }
  }
}

private fun warmRecap(loaded: Boolean, last7: Int, empty: Boolean): String = when {
  !loaded -> "Gathering your recent check-ins…"
  empty -> "Your rhythm will appear here after your first check-in. One reading a day is all it takes."
  else -> "You checked in $last7 of the last 7 days - that's steady progress. Every entry helps your care team see the full picture, and there's no penalty for a quiet week."
}
