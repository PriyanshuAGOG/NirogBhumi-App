package com.nirogbhumi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.components.PrimaryButton
import com.nirogbhumi.app.ui.components.RowCard
import com.nirogbhumi.app.ui.components.SectionLabel
import com.nirogbhumi.app.ui.theme.NirogColor
import com.nirogbhumi.app.ui.theme.NirogRadius
import com.nirogbhumi.app.ui.theme.NirogSpace
import com.nirogbhumi.app.ui.theme.NirogType

/**
 * Chat Hub (PRD v2, Pillar E) - the dedicated Care+ chat section reached from the
 * chat icon in the Care tab's top-right. Lists exactly two rooms: Announcements
 * (read-only for members, coach/admin posts) and General (the whole batch).
 *
 * Non-members see a calm unlock instead of empty rooms.
 */
@Composable
fun ChatHubScreen(state: NirogState) {
  Column(
    Modifier
      .fillMaxSize()
      .background(NirogColor.surface),
  ) {
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
        Text("Batch messages", style = NirogType.sectionHeading, color = NirogColor.forest)
        Text(
          if (state.isProgramActive && state.activeProgramName.isNotBlank()) state.activeProgramName
          else "Care+ community",
          style = NirogType.caption,
          color = NirogColor.inkMuted,
        )
      }
    }

    Column(Modifier.padding(horizontal = NirogSpace.lg)) {
      if (!state.isProgramActive) {
        Spacer(Modifier.size(NirogSpace.md))
        Box(
          Modifier
            .fillMaxWidth()
            .clip(NirogRadius.cardShape)
            .background(NirogColor.surfaceSunken)
            .padding(NirogSpace.xl),
        ) {
          Column {
            SectionLabel("Care+ community")
            Spacer(Modifier.size(NirogSpace.sm))
            Text(
              "Your batch chat unlocks when you join a program",
              style = NirogType.cardTitle,
              color = NirogColor.inkPrimary,
            )
            Spacer(Modifier.size(NirogSpace.sm))
            Text(
              "Members get a coach-led announcements channel and a supportive group chat with everyone on the same journey.",
              style = NirogType.body,
              color = NirogColor.inkSecondary,
            )
            Spacer(Modifier.size(NirogSpace.lg))
            PrimaryButton("I have a program code", onClick = {
              state.enteringProgramCodeFromCarePlus = true
              state.currentScreen = "program_code_optional"
            })
          }
        }
        return@Column
      }

      Spacer(Modifier.size(NirogSpace.md))
      RoomRow(
        icon = Icons.Filled.Campaign,
        tint = NirogColor.statusAttention,
        tintBg = NirogColor.statusAttentionBg,
        title = "Announcements",
        subtitle = "Updates from your coach · read-only",
        onClick = { state.currentScreen = "announcements" },
      )
      Spacer(Modifier.size(NirogSpace.md))
      RoomRow(
        icon = Icons.Filled.Forum,
        tint = NirogColor.statusInRange,
        tintBg = NirogColor.statusInRangeBg,
        title = "General",
        subtitle = "Chat with everyone in your batch",
        onClick = { state.currentScreen = "program_chat" },
      )

      Spacer(Modifier.size(NirogSpace.lg))
      Text(
        "Long-press a message to report it. Your coach reviews reports within 24 hours.",
        style = NirogType.caption,
        color = NirogColor.inkMuted,
      )
    }
  }
}

@Composable
private fun RoomRow(
  icon: ImageVector,
  tint: Color,
  tintBg: Color,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
) {
  RowCard(
    title = title,
    subtitle = subtitle,
    onClick = onClick,
    leading = {
      Box(
        Modifier
          .size(44.dp)
          .clip(RoundedCornerShape(NirogSpace.md))
          .background(tintBg),
        contentAlignment = Alignment.Center,
      ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
      }
    },
  )
}
