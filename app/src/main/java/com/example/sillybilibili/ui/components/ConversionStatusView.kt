package com.example.sillybilibili.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pending
import com.example.sillybilibili.domain.model.ConversionStatus
import com.example.sillybilibili.ui.theme.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ConversionStatusView(status: ConversionStatus, progress: Float, message: String?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        border = BorderStroke(1.dp, DarkDivider.copy(alpha = 0.58f))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (status) {
                ConversionStatus.PENDING -> Icon(Icons.Default.Pending, null, modifier = Modifier.size(24.dp), tint = Color(0xFF8080A0))
                ConversionStatus.CONVERTING -> CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = CyberVermilion, trackColor = CyberVermilion.copy(alpha = 0.1f))
                ConversionStatus.COMPLETED -> Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(24.dp), tint = NeonGreen)
                ConversionStatus.FAILED -> Icon(Icons.Default.Error, null, modifier = Modifier.size(24.dp), tint = NeonRed)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when (status) {
                        ConversionStatus.PENDING -> "等待转换"; ConversionStatus.CONVERTING -> "转换中 ${(progress * 100).toInt()}%"
                        ConversionStatus.COMPLETED -> "转换完成"; ConversionStatus.FAILED -> "转换失败"
                    },
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    color = when (status) { ConversionStatus.FAILED -> NeonRed; ConversionStatus.COMPLETED -> NeonGreen; else -> Color(0xFFF0F0F8) }
                )
                if (message != null) Text(message, style = MaterialTheme.typography.bodySmall, color = if (status == ConversionStatus.FAILED) NeonRed.copy(alpha = 0.8f) else DarkTextSecondary)
            }
        }
    }
}
