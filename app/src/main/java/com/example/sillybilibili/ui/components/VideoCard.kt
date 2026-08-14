package com.example.sillybilibili.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.domain.model.OnlineVideoStatus
import com.example.sillybilibili.ui.theme.CyberVermilion
import com.example.sillybilibili.ui.theme.DarkCard
import com.example.sillybilibili.ui.theme.DarkDivider
import com.example.sillybilibili.ui.theme.DarkSurfaceVariant
import com.example.sillybilibili.ui.theme.DarkTextPrimary
import com.example.sillybilibili.ui.theme.DarkTextSecondary
import com.example.sillybilibili.ui.theme.DarkTextTertiary
import com.example.sillybilibili.ui.theme.GlassHighlight
import com.example.sillybilibili.ui.theme.NeonPurple

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(
    video: Video,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onCoverRequested: (Video) -> Unit = {},
    onOnlineStatusRequested: (Video) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accent = if (video.isVertical) NeonPurple else CyberVermilion
    LaunchedEffect(video.id, video.coverPath, video.coverSourcePath, video.exportedPath) {
        // Also re-check a stale cached path. CoverCacheService removes invalid cache files
        // and restores the original cover or a video-frame fallback.
        onCoverRequested(video)
    }
    LaunchedEffect(video.id, video.onlineStatus, video.onlineCheckedAt) {
        onOnlineStatusRequested(video)
    }

    Card(
        modifier = modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 8.dp),
        border = CardDefaults.outlinedCardBorder(enabled = true).copy(brush = Brush.linearGradient(listOf(GlassHighlight, accent.copy(alpha = 0.52f), DarkDivider)))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VideoThumbnail(video = video, accent = accent)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (video.ownerName.isNotBlank()) {
                        Text(video.ownerName, style = MaterialTheme.typography.labelMedium, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (video.resolutionLabel.isNotBlank()) {
                        Text("·", color = DarkTextTertiary)
                        Text(video.resolutionLabel, style = MaterialTheme.typography.labelMedium, color = DarkTextSecondary)
                    }
                }
                Text(video.title, style = MaterialTheme.typography.titleSmall, color = DarkTextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    VideoMeta(video.formattedDuration)
                    VideoMeta(video.formattedSize)
                    OnlineStatusBadge(video.onlineStatus)
                }
            }
        }
    }
}

@Composable
private fun VideoThumbnail(video: Video, accent: Color) {
    var imageFailed by remember(video.id, video.displayCoverPath) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(if (video.isVertical) 72.dp else 120.dp)
            .aspectRatio(video.previewAspectRatio)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        video.displayCoverPath?.takeUnless { imageFailed }?.let { displayCoverPath ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(displayCoverPath).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { imageFailed = true }
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)))))
        } ?: run {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(32.dp), tint = accent)
        }
        if (video.quality.isNotBlank()) {
            Surface(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp), shape = RoundedCornerShape(6.dp), color = Color.Black.copy(alpha = 0.62f)) {
                Text(video.quality, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun VideoMeta(label: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = DarkSurfaceVariant) {
        Text(label, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = DarkTextSecondary)
    }
}

@Composable
fun OnlineStatusBadge(status: OnlineVideoStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        OnlineVideoStatus.ONLINE -> "在线" to Color(0xFF5BCB9A)
        OnlineVideoStatus.UNAVAILABLE -> "已下架/不可访问" to CyberVermilion
        OnlineVideoStatus.UNVERIFIABLE -> "暂无法核验" to DarkTextSecondary
        OnlineVideoStatus.UNCHECKED -> "待核验" to DarkTextTertiary
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.14f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.36f))
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun CategoryChip(name: String, color: Color, count: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        label = { Text(if (count > 0) "$name  $count" else name, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium) },
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.2f), selectedLabelColor = color,
            containerColor = DarkSurfaceVariant, labelColor = DarkTextSecondary
        ),
        border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
            borderColor = DarkDivider, selectedBorderColor = color.copy(alpha = 0.7f), enabled = true, selected = selected
        ),
        modifier = modifier
    )
}
