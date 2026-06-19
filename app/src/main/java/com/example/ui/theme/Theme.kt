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

private val DarkColorScheme =
  darkColorScheme(
    primary = PrimaryDeepGreen,
    onPrimary = OnPrimaryWhite,
    primaryContainer = PrimaryContainerSoftGreen,
    onPrimaryContainer = OnPrimaryContainerLight,
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
    outline = OutlineGrey,
    outlineVariant = OutlineVariantLight
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PrimaryDeepGreen,
    onPrimary = OnPrimaryWhite,
    primaryContainer = PrimaryContainerSoftGreen,
    onPrimaryContainer = OnPrimaryContainerLight,
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
    outline = OutlineGrey,
    outlineVariant = OutlineVariantLight
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
