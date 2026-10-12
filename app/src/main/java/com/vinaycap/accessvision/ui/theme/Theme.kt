package com.vinaycap.accessvision.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * AccessVision theme.
 *
 * Uses a dark color scheme by default since the app is primarily
 * a camera viewfinder and dark UI reduces visual distraction.
 * The theme is intentionally minimal since the target users
 * rely on audio/haptic feedback rather than visual UI.
 */

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4FC3F7),       // Light blue
    secondary = Color(0xFF81C784),     // Green
    tertiary = Color(0xFFFFB74D),      // Orange (for warnings)
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    error = Color(0xFFEF5350),         // Red for errors
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0288D1),
    secondary = Color(0xFF388E3C),
    tertiary = Color(0xFFF57C00),
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    error = Color(0xFFD32F2F),
    onError = Color.White
)

@Composable
fun AccessVisionTheme(
    darkTheme: Boolean = true, // Default to dark for camera app
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
