package com.nirogbhumi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nirogbhumi.app.ui.theme.NirogColor
import com.nirogbhumi.app.ui.theme.NirogElevation
import com.nirogbhumi.app.ui.theme.NirogRadius
import com.nirogbhumi.app.ui.theme.NirogSpace
import com.nirogbhumi.app.ui.theme.NirogType

/**
 * Shared Compose component kit (PRD v2, "Pillar A / component kit").
 *
 * Every screen composes from these so the product reads as one designed system.
 * All visual constants come from the design-system tokens - no ad-hoc hex/dp here.
 */

/** The gold, letter-spaced, uppercase section label used throughout the app. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = NirogColor.gold) {
  Text(text.uppercase(), style = NirogType.overline, color = color, modifier = modifier)
}

/** Clinical / sync state is only ever shown through this chip. */
enum class StatusKind { InRange, Attention, Critical, Neutral, Synced }

@Composable
fun StatusChip(label: String, kind: StatusKind, modifier: Modifier = Modifier) {
  val (bg, fg) = when (kind) {
    StatusKind.InRange -> NirogColor.statusInRangeBg to NirogColor.statusInRange
    StatusKind.Attention -> NirogColor.statusAttentionBg to NirogColor.statusAttention
    StatusKind.Critical -> NirogColor.statusCriticalBg to NirogColor.statusCritical
    StatusKind.Neutral -> NirogColor.statusNeutralBg to NirogColor.inkMuted
    StatusKind.Synced -> NirogColor.surfaceSunken to NirogColor.inkMuted
  }
  Box(
    modifier
      .clip(NirogRadius.pillShape)
      .background(bg)
      .padding(horizontal = 10.dp, vertical = 4.dp),
  ) {
    Text(label.uppercase(), style = NirogType.overline, color = fg)
  }
}

/** Standard white card: 24dp radius, soft lift, no hard border. */
@Composable
fun NirogCard(
  modifier: Modifier = Modifier,
  padding: PaddingValues = PaddingValues(NirogSpace.xl),
  onClick: (() -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  val base = modifier
    .fillMaxWidth()
    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
  Surface(
    modifier = base,
    shape = NirogRadius.cardShape,
    color = NirogColor.surfaceCard,
    shadowElevation = NirogElevation.card,
  ) {
    Column(Modifier.padding(padding)) { content() }
  }
}

/** Primary forest CTA. */
@Composable
fun PrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  container: Color = NirogColor.forest,
  contentColor: Color = NirogColor.onAccent,
) {
  val bg = if (enabled) container else NirogColor.statusNeutral
  Box(
    modifier
      .fillMaxWidth()
      .clip(NirogRadius.pillShape)
      .background(bg)
      .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(vertical = 14.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, style = NirogType.button, color = contentColor)
  }
}

/** Gold emphasis CTA, for use on dark (forest) surfaces. */
@Composable
fun GoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
  PrimaryButton(text, onClick, modifier, container = NirogColor.gold, contentColor = Color(0xFF241706))

/**
 * The forest-gradient "focus" card that anchors Today and Care+ headers:
 * an overline, a display title, an optional subtitle, an optional leading slot,
 * and an optional gold CTA.
 */
@Composable
fun FocusCard(
  overline: String,
  title: String,
  subtitle: String? = null,
  modifier: Modifier = Modifier,
  leading: (@Composable () -> Unit)? = null,
  ctaText: String? = null,
  onCta: (() -> Unit)? = null,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = NirogRadius.cardShape,
    shadowElevation = NirogElevation.card,
    color = Color.Transparent,
  ) {
    Column(
      Modifier
        .background(Brush.linearGradient(listOf(NirogColor.forest, NirogColor.forestSoft)))
        .padding(NirogSpace.xl),
    ) {
      SectionLabel(overline, color = NirogColor.goldSoft)
      Spacer(Modifier.size(NirogSpace.md))
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
          leading()
          Spacer(Modifier.width(NirogSpace.lg))
        }
        Column {
          Text(title, style = NirogType.cardTitle, color = NirogColor.onAccent)
          if (subtitle != null) {
            Text(subtitle, style = NirogType.caption, color = NirogColor.onAccent.copy(alpha = 0.75f))
          }
        }
      }
      if (ctaText != null && onCta != null) {
        Spacer(Modifier.size(NirogSpace.lg))
        GoldButton(ctaText, onCta)
      }
    }
  }
}

/** A tappable list row: leading slot, title + subtitle, optional trailing text. */
@Composable
fun RowCard(
  title: String,
  subtitle: String? = null,
  modifier: Modifier = Modifier,
  leading: (@Composable () -> Unit)? = null,
  trailing: String? = null,
  onClick: (() -> Unit)? = null,
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    shape = NirogRadius.cardShape,
    color = NirogColor.surfaceCard,
    shadowElevation = NirogElevation.card,
  ) {
    Row(
      Modifier.padding(horizontal = NirogSpace.lg, vertical = NirogSpace.md),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (leading != null) {
        leading()
        Spacer(Modifier.width(NirogSpace.md))
      }
      Column(Modifier.weight(1f)) {
        Text(title, style = NirogType.bodyStrong, color = NirogColor.inkPrimary)
        if (subtitle != null) {
          Text(subtitle, style = NirogType.caption, color = NirogColor.inkMuted)
        }
      }
      if (trailing != null) {
        Text(trailing, style = NirogType.bodyStrong, color = NirogColor.forest)
      }
    }
  }
}

/** Overlapping circular avatars from initials. */
@Composable
fun AvatarStack(initials: List<String>, modifier: Modifier = Modifier) {
  Row(modifier) {
    initials.forEachIndexed { index, label ->
      Box(
        Modifier
          .offset(x = if (index == 0) 0.dp else (-8 * index).dp)
          .size(28.dp)
          .clip(CircleShape)
          .background(NirogColor.goldSoft),
        contentAlignment = Alignment.Center,
      ) {
        Text(label.take(1).uppercase(), style = NirogType.overline, color = NirogColor.forest)
      }
    }
  }
}

/** The sunken "insight/reward" card that pays back a check-in with one observation. */
@Composable
fun InsightCard(text: String, emphasis: String? = null, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = NirogRadius.cardShape,
    color = NirogColor.surfaceSunken,
  ) {
    Column(Modifier.padding(horizontal = NirogSpace.xl, vertical = NirogSpace.lg)) {
      if (emphasis != null) {
        Text(emphasis, style = NirogType.bodyStrong, color = NirogColor.inkPrimary)
        Spacer(Modifier.size(NirogSpace.xs))
      }
      Text(text, style = NirogType.body, color = NirogColor.inkSecondary, textAlign = TextAlign.Start)
    }
  }
}
