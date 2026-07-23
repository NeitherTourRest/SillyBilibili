// ============================================================
// Theme.kt — Material3 主题配置
// ============================================================

package com.example.sillybilibili.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyberVermilion,
    onPrimary = Color.Black,
    primaryContainer = CyberVermilion.copy(alpha = 0.15f),
    onPrimaryContainer = CyberVermilion,
    secondary = CyberGold,
    onSecondary = Color.Black,
    secondaryContainer = CyberGold.copy(alpha = 0.12f),
    onSecondaryContainer = CyberGold,
    tertiary = NeonPurple,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = Color(0xFFE8E8F0),
    surface = DarkSurface,
    onSurface = Color(0xFFE8E8F0),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFA0A0B8),
    outline = DarkDivider,
    outlineVariant = Color(0xFF3A3A54),
    error = NeonRed,
    onError = Color.Black,
    errorContainer = NeonRed.copy(alpha = 0.12f),
    onErrorContainer = NeonRed
)

@Composable
fun SillyBilibiliTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

