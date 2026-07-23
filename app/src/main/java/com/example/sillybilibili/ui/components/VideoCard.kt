package com.example.sillybilibili.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(video: Video, onClick: () -> Unit, onLongClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    val accentColor = if (video.isVertical) NeonPurple else CyberGold

    Card(
        modifier = modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(CyberVermilionGlow, Color.Transparent))
        )
    ) {
        Box {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp).padding(top = 12.dp, bottom = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.width(4.dp).height(80.dp).clip(RoundedCornerShape(0.dp))
                        .background(Brush.verticalGradient(listOf(accentColor, if (video.isVertical) CyberGold else CyberVermilion)))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(0.dp)).background(DarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (video.coverPath != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(video.coverPath).crossfade(true).build(),
                            contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                        )
                        Box(modifier = Modifier.matchParentSize().background(
                            Brush.linearGradient(listOf(Color.Transparent, DarkBackground.copy(alpha = 0.3f)))
                        ))
                        if (video.quality.isNotEmpty()) {
                            Box(
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                                    .background(Brush.linearGradient(listOf(CyberVermilion, NeonPurple)), RoundedCornerShape(0.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(video.quality, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    } else {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(32.dp), tint = CyberVermilion)
                    }
                }

                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (video.ownerName.isNotEmpty()) {
                            Text(video.ownerName, style = MaterialTheme.typography.labelSmall, color = CyberVermilion, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(modifier = Modifier.size(3.dp).clip(RoundedCornerShape(0.dp)).background(CyberVermilion.copy(alpha = 0.5f)))
                            Spacer(modifier = Modifier.width(6.dp))
                            if (video.resolutionLabel.isNotEmpty()) {
                                Text(video.resolutionLabel, style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(video.title, style = MaterialTheme.typography.titleSmall, color = Color(0xFFF0F0F8), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(video.formattedSize, style = MaterialTheme.typography.bodySmall, color = NeonGreen.copy(alpha = 0.8f), fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("\u25CF", fontSize = 6.sp, color = DarkDivider)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(video.formattedDuration, style = MaterialTheme.typography.bodySmall, color = NeonCyan.copy(alpha = 0.8f), fontFamily = FontFamily.Monospace)
                    }
                    Text(formatDate(video.addedAt), style = MaterialTheme.typography.bodySmall, color = Color(0xFF606080), fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun CategoryChip(name: String, color: Color, count: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected, onClick = onClick, shape = RoundedCornerShape(0.dp),
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) color else Color(0xFFA0A0B8))
                Spacer(modifier = Modifier.width(4.dp))
                Text("($count)", style = MaterialTheme.typography.bodySmall, color = if (selected) color.copy(alpha = 0.7f) else Color(0xFF606080), fontFamily = FontFamily.Monospace)
            }
        },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.15f), containerColor = DarkSurfaceVariant),
        border = FilterChipDefaults.filterChipBorder(borderColor = if (selected) color else DarkDivider, selectedBorderColor = color, enabled = true, selected = selected),
        modifier = modifier
    )
}
