package com.example.sillybilibili.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sillybilibili.ui.theme.CyberVermilion
import com.example.sillybilibili.ui.theme.DarkSurfaceVariant
import com.example.sillybilibili.ui.theme.DarkTextPrimary
import com.example.sillybilibili.ui.theme.DarkTextTertiary
import com.example.sillybilibili.ui.theme.GlassHighlight

/**
 * 插画式空状态：同心圆光晕 + 圆角图标牌 + 点缀圆点构成的轻量插图，
 * 配合标题、说明与可选操作按钮，替代干巴巴的居中文字。
 */
@Composable
fun EmptyStatePanel(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    accent: Color = CyberVermilion,
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(148.dp), contentAlignment = Alignment.Center) {
                // 同心圆光晕
                Box(Modifier.size(148.dp).clip(CircleShape).background(accent.copy(alpha = 0.05f)))
                Box(Modifier.size(112.dp).clip(CircleShape).background(accent.copy(alpha = 0.10f)))
                // 圆角图标牌
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = DarkSurfaceVariant,
                    border = BorderStroke(1.dp, GlassHighlight)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(34.dp), tint = accent)
                    }
                }
                // 点缀圆点
                Box(Modifier.align(Alignment.TopStart).offset(x = 10.dp, y = 14.dp).size(9.dp).clip(CircleShape).background(accent.copy(alpha = 0.40f)))
                Box(Modifier.align(Alignment.BottomEnd).offset(x = (-4).dp, y = (-8).dp).size(12.dp).clip(CircleShape).background(accent.copy(alpha = 0.28f)))
                Box(Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = 30.dp).size(6.dp).clip(CircleShape).background(accent.copy(alpha = 0.50f)))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = DarkTextPrimary,
                textAlign = TextAlign.Center
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkTextTertiary,
                    textAlign = TextAlign.Center
                )
            }
            action?.invoke()
        }
    }
}