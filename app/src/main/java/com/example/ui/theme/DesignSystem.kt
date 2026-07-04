package com.nirogbhumi.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Nirog Bhumi design-system tokens (PRD v2, "Pillar A").
 *
 * This is the single source of truth every screen inherits from. It deliberately
 * builds ON TOP of the existing brand palette in [Color.kt] rather than replacing
 * it - the warm-paper surface, deep-forest primary, white cards and ink text are
 * exactly the current app's colors. The only NEW colors introduced here are:
 *   - semantic status roles (in-range / attention / critical / neutral), which the
 *     app previously expressed ad-hoc per screen, now unified; and
 *   - a restrained gold accent used sparingly for emphasis on dark surfaces.
 *
 * Screens should reference [NirogColor], [NirogSpace], [NirogRadius] and the
 * [NirogType] scale instead of hard-coding hex values or dp literals, so the whole
 * product reads as one designed system.
 */

// ---- NEW semantic status colors (clinical state is communicated ONLY through these) ----
val StatusInRange = Color(0xFF3F7D58)
val StatusInRangeBg = Color(0xFFE4EFE4)
val StatusAttention = Color(0xFFB9832B)
val StatusAttentionBg = Color(0xFFF4E9D3)
val StatusCritical = Color(0xFFB4472F)
val StatusCriticalBg = Color(0xFFF5DFD6)
val StatusNeutral = Color(0xFFC7C2B4)
val StatusNeutralBg = Color(0xFFEDEAE0)

// ---- NEW restrained accent (Ayurvedic warmth) - emphasis only, never a background ----
val AccentGold = Color(0xFFC7902F)
val AccentGoldSoft = Color(0xFFEAD6A9)
val AccentTerracotta = Color(0xFFB4592F)

// ---- NEW ink hierarchy (primary ink already exists as InkText) ----
val InkSecondary = Color(0xFF56604F)
val InkMuted = Color(0xFF8B9285)

// ---- NEW neutral surfaces layered between paper and white ----
val SurfaceSunken = Color(0xFFF1EDE3)
val SurfaceCardWhite = Color(0xFFFFFFFF)
val OnAccentCream = Color(0xFFFBF8F0)

/**
 * Semantic color roles. Existing brand colors are re-exported by role so callers
 * speak in intent ("forest", "surface") not in raw names, and so a future palette
 * tweak happens in one place.
 */
object NirogColor {
  // Surfaces
  val surface = BaseBackgroundPaper // warm paper - unchanged brand background
  val surfaceCard = SurfaceCardWhite
  val surfaceSunken = SurfaceSunken
  val surfaceAlt = SurfaceContainerHighMedium

  // Ink
  val inkPrimary = InkText // unchanged brand ink
  val inkSecondary = InkSecondary
  val inkMuted = InkMuted
  val onAccent = OnAccentCream

  // Brand accents (unchanged brand greens)
  val forest = PrimaryDeepGreen
  val forestSoft = PrimaryContainerSoftGreen
  val gold = AccentGold
  val goldSoft = AccentGoldSoft
  val terracotta = AccentTerracotta

  // Status (the only channel for clinical meaning)
  val statusInRange = StatusInRange
  val statusInRangeBg = StatusInRangeBg
  val statusAttention = StatusAttention
  val statusAttentionBg = StatusAttentionBg
  val statusCritical = StatusCritical
  val statusCriticalBg = StatusCriticalBg
  val statusNeutral = StatusNeutral
  val statusNeutralBg = StatusNeutralBg

  // Additional semantic aliases for existing brand colors (Color.kt) that
  // pre-date this token system but are used heavily across the dashboard -
  // exposed here so screens can read from NirogColor instead of reaching
  // for the raw Color.kt constants directly, without changing any hex value.
  val outline = OutlineGrey
  val outlineVariant = OutlineVariantLight
  val secondaryGreen = SecondaryOliveGreen
  val secondaryContainer = SecondaryContainerLime
  val forestSofter = OnPrimaryContainerLight
  val forestPale = PrimaryFixedDim
  val forestPaleLight = PrimaryFixed
  val errorColor = ErrorRed
  val inkTertiary = OnSurfaceVariantDark
  val surfaceNeutral = SurfaceContainerNeutral
  val surfaceLow = SurfaceContainerLowLight
  val surfaceMint = SurfaceMintTint
}

/** 4 / 8 / 12 / 16 / 24 / 32 / 48 spacing scale. */
object NirogSpace {
  val xs = 4.dp
  val sm = 8.dp
  val md = 12.dp
  val lg = 16.dp
  val xl = 24.dp
  val xxl = 32.dp
  val xxxl = 48.dp
}

/** Corner-radius scale: small controls, medium tiles, cards, and pills. */
object NirogRadius {
  val sm = 10.dp
  val md = 16.dp
  val lg = 24.dp
  val pill = 999.dp

  val smallShape = RoundedCornerShape(sm)
  val mediumShape = RoundedCornerShape(md)
  val cardShape = RoundedCornerShape(lg)
  val pillShape = RoundedCornerShape(pill)
}

/** Elevation tokens (dp). Cards get a soft lift; sheets/dialogs a stronger float. */
object NirogElevation {
  val card = 2.dp
  val float = 12.dp
}
