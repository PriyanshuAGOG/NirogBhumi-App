package com.nirogbhumi.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type system (PRD v2, "Pillar A").
 *
 * Two families: a display face for headings and a body face for everything else.
 * The premium target is Fraunces (display) + Manrope (body); those ship via
 * downloadable Google Fonts in a dedicated slice. Until then these alias to the
 * platform serif / sans-serif so the SCALE and hierarchy are already correct and
 * swapping in the real faces is a one-line change here.
 */
val DisplayFontFamily = FontFamily.Serif // -> Fraunces
val BodyFontFamily = FontFamily.SansSerif // -> Manrope

/**
 * Named styles the design references directly. `overline` is the gold,
 * letter-spaced, uppercase section label seen throughout the prototype.
 */
object NirogType {
  val display = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp)
  val heroTitle = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp)
  val cardTitle = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp)
  val sectionHeading = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp)
  val body = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp)
  val bodyStrong = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 21.sp)
  val secondary = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)
  val caption = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
  val overline = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.0.sp)
  val button = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 18.sp)
}

/** Material3 Typography mapped onto the Nirog scale so stock components inherit it. */
val Typography =
  Typography(
    displayLarge = NirogType.display,
    displayMedium = NirogType.display.copy(fontSize = 28.sp, lineHeight = 34.sp),
    headlineLarge = NirogType.heroTitle,
    headlineMedium = NirogType.cardTitle,
    headlineSmall = NirogType.sectionHeading,
    titleLarge = NirogType.sectionHeading,
    titleMedium = NirogType.bodyStrong.copy(fontSize = 15.sp, lineHeight = 22.sp),
    titleSmall = NirogType.secondary,
    bodyLarge = NirogType.body.copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = NirogType.body,
    bodySmall = NirogType.caption,
    labelLarge = NirogType.button,
    labelMedium = NirogType.secondary,
    labelSmall = NirogType.overline,
  )
