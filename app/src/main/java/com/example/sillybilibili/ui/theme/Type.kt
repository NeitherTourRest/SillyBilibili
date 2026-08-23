package com.example.sillybilibili.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val AppSansSerif = FontFamily.SansSerif
private val CompactTextPlatformStyle = PlatformTextStyle(includeFontPadding = false)

/**
 * Explicit sans-serif text keeps the application independent from OEM theme fonts. The compact
 * platform style removes the legacy top/bottom font padding that made the app-bar appear clipped
 * or vertically misaligned on devices with large/custom system fonts.
 */
val Typography = Typography(
    displayLarge = TextStyle(fontFamily = AppSansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp, platformStyle = CompactTextPlatformStyle),
    headlineLarge = TextStyle(fontFamily = AppSansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, platformStyle = CompactTextPlatformStyle),
    headlineMedium = TextStyle(fontFamily = AppSansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp, platformStyle = CompactTextPlatformStyle),
    titleLarge = TextStyle(fontFamily = AppSansSerif, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp, platformStyle = CompactTextPlatformStyle),
    titleMedium = TextStyle(fontFamily = AppSansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.15.sp, platformStyle = CompactTextPlatformStyle),
    titleSmall = TextStyle(fontFamily = AppSansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, platformStyle = CompactTextPlatformStyle),
    bodyLarge = TextStyle(fontFamily = AppSansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, platformStyle = CompactTextPlatformStyle),
    bodyMedium = TextStyle(fontFamily = AppSansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, platformStyle = CompactTextPlatformStyle),
    bodySmall = TextStyle(fontFamily = AppSansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp, platformStyle = CompactTextPlatformStyle),
    labelLarge = TextStyle(fontFamily = AppSansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp, platformStyle = CompactTextPlatformStyle),
    labelMedium = TextStyle(fontFamily = AppSansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp, platformStyle = CompactTextPlatformStyle),
    labelSmall = TextStyle(fontFamily = AppSansSerif, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.2.sp, platformStyle = CompactTextPlatformStyle)
)

