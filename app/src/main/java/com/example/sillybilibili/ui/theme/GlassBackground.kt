package com.example.sillybilibili.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** The soft colour underneath every translucent surface in the app. */
@Composable
fun GlassBackground() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF111827), Color(0xFF080B12))))
            .drawBehind {
                val maxDimension = maxOf(size.width, size.height)
                drawCircle(
                    color = CyberVermilion.copy(alpha = 0.12f),
                    radius = maxDimension * 0.70f,
                    center = Offset(size.width * 0.04f, size.height * 0.10f)
                )
                drawCircle(
                    color = NeonPurple.copy(alpha = 0.10f),
                    radius = maxDimension * 0.64f,
                    center = Offset(size.width * 0.98f, size.height * 0.66f)
                )
                drawCircle(
                    color = NeonCyan.copy(alpha = 0.055f),
                    radius = maxDimension * 0.52f,
                    center = Offset(size.width * 0.45f, size.height * 1.05f)
                )
            }
    )
}
