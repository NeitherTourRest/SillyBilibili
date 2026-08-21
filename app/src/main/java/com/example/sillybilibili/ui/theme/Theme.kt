// ============================================================
// Theme.kt — Material3 主题配置
// ============================================================

package com.example.sillybilibili.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = CyberVermilion,
    onPrimary = Color.White,
    primaryContainer = CyberVermilion.copy(alpha = 0.16f),
    onPrimaryContainer = CyberVermilionLight,
    secondary = CyberGold,
    onSecondary = Color(0xFF382600),
    secondaryContainer = CyberGold.copy(alpha = 0.12f),
    onSecondaryContainer = CyberGold,
    tertiary = NeonPurple,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkDivider,
    outlineVariant = Color(0xFF40495C),
    error = NeonRed,
    onError = Color.Black,
    errorContainer = NeonRed.copy(alpha = 0.12f),
    onErrorContainer = NeonRed
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun SillyBilibiliTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        shapes = AppShapes,
    ) {
        Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
            GlassBackground()
            content()
        }
    }
}
