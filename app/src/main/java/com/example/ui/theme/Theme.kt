package com.nirogbhumi.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Nirog Bhumi is deliberately a single, always-light "warm paper" theme that
// ignores the system dark/light setting (see MyApplicationTheme below), so both
// schemes here use identical light tokens. Newer Material3 surface-container
// tokens (surfaceContainerHigh, etc.) must be overridden too, not just the
// legacy surface/background ones - components like AlertDialog read their
// container color from surfaceContainerHigh, which otherwise falls back to
// darkColorScheme()'s near-black default and renders invisible dark-on-dark
// against this app's hardcoded dark green/gray text.
private val DarkColorScheme =
  darkColorScheme(
    primary = PrimaryDeepGreen,
    onPrimary = OnPrimaryWhite,
    primaryContainer = PrimaryContainerSoftGreen,
    onPrimaryContainer = OnPrimaryContainerLight,
    inversePrimary = PrimaryFixed,
    secondary = SecondaryOliveGreen,
    onSecondary = OnSecondaryWhite,
    secondaryContainer = SecondaryContainerLime,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryBrownishRed,
    onTertiary = OnTertiaryWhite,
    tertiaryContainer = TertiaryContainerMutedRed,
    onTertiaryContainer = OnTertiaryContainerPink,
    error = ErrorRed,
    onError = OnErrorWhite,
    errorContainer = ErrorContainerPink,
    onErrorContainer = OnErrorContainerRed,
    background = BaseBackgroundPaper,
    onBackground = InkText,
    surface = SurfaceMintTint,
    onSurface = OnSurfaceInk,
    surfaceVariant = SurfaceVariantGrey,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceTint = PrimaryDeepGreen,
    inverseSurface = InkText,
    inverseOnSurface = SurfaceContainerLowestWhite,
    outline = OutlineGrey,
    outlineVariant = OutlineVariantLight,
    surfaceBright = SurfaceContainerLowestWhite,
    surfaceDim = SurfaceDimMuted,
    surfaceContainer = SurfaceContainerNeutral,
    surfaceContainerHigh = SurfaceContainerHighMedium,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainerLowest = SurfaceContainerLowestWhite
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PrimaryDeepGreen,
    onPrimary = OnPrimaryWhite,
    primaryContainer = PrimaryContainerSoftGreen,
    onPrimaryContainer = OnPrimaryContainerLight,
    inversePrimary = PrimaryFixed,
    secondary = SecondaryOliveGreen,
    onSecondary = OnSecondaryWhite,
    secondaryContainer = SecondaryContainerLime,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryBrownishRed,
    onTertiary = OnTertiaryWhite,
    tertiaryContainer = TertiaryContainerMutedRed,
    onTertiaryContainer = OnTertiaryContainerPink,
    error = ErrorRed,
    onError = OnErrorWhite,
    errorContainer = ErrorContainerPink,
    onErrorContainer = OnErrorContainerRed,
    background = BaseBackgroundPaper,
    onBackground = InkText,
    surface = SurfaceMintTint,
    onSurface = OnSurfaceInk,
    surfaceVariant = SurfaceVariantGrey,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceTint = PrimaryDeepGreen,
    inverseSurface = InkText,
    inverseOnSurface = SurfaceContainerLowestWhite,
    outline = OutlineGrey,
    outlineVariant = OutlineVariantLight,
    surfaceBright = SurfaceContainerLowestWhite,
    surfaceDim = SurfaceDimMuted,
    surfaceContainer = SurfaceContainerNeutral,
    surfaceContainerHigh = SurfaceContainerHighMedium,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainerLowest = SurfaceContainerLowestWhite
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Set default to false to prevent system-wide thematic tinting from replacing design tokens
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
