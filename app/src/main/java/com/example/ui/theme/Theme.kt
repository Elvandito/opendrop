package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme =
  lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight
  )

private val DarkColorScheme = darkColorScheme(
  primary = androidx.compose.ui.graphics.Color(0xFFBAC3FF),
  onPrimary = androidx.compose.ui.graphics.Color(0xFF001062),
  primaryContainer = androidx.compose.ui.graphics.Color(0xFF24389C),
  onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFE0E0FF),
  secondary = androidx.compose.ui.graphics.Color(0xFF5CD6C6),
  onSecondary = androidx.compose.ui.graphics.Color(0xFF003731),
  secondaryContainer = androidx.compose.ui.graphics.Color(0xFF005047),
  onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF85F6E5),
  tertiary = androidx.compose.ui.graphics.Color(0xFFFFB77C),
  onTertiary = androidx.compose.ui.graphics.Color(0xFF4C2200),
  tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF6C3400),
  onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDBC6),
  error = androidx.compose.ui.graphics.Color(0xFFFFB4AB),
  onError = androidx.compose.ui.graphics.Color(0xFF690005),
  errorContainer = androidx.compose.ui.graphics.Color(0xFF93000A),
  onErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6),
  background = androidx.compose.ui.graphics.Color(0xFF111318),
  onBackground = androidx.compose.ui.graphics.Color(0xFFE2E2E9),
  surface = androidx.compose.ui.graphics.Color(0xFF111318),
  onSurface = androidx.compose.ui.graphics.Color(0xFFE2E2E9),
  surfaceVariant = androidx.compose.ui.graphics.Color(0xFF454652),
  onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFC5C5D4),
  outline = androidx.compose.ui.graphics.Color(0xFF8F909F),
  outlineVariant = androidx.compose.ui.graphics.Color(0xFF454652)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false, // disabled to force professional polish colors
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
