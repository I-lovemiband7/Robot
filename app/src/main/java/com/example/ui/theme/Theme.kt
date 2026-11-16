package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LooiColorScheme =
  darkColorScheme(
    primary = NeonCyan,
    onPrimary = OledBlack,
    primaryContainer = DeepCyanContainer,
    onPrimaryContainer = NeonCyanSoft,
    secondary = NeonCyanDim,
    onSecondary = OledBlack,
    background = OledBlack,
    onBackground = TextPrimary,
    surface = DeepCyanSurface,
    onSurface = TextPrimary,
    surfaceVariant = DeepCyanContainer,
    onSurfaceVariant = TextSecondary,
    error = AlertRose,
    onError = OledBlack,
  )

@Composable
fun LooiBotTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = LooiColorScheme,
    typography = Typography,
    content = content,
  )
}
