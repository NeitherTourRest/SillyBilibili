package com.example.sillybilibili.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.example.sillybilibili.domain.model.Category
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
import kotlinx.coroutines.delay

/** 封面显示失败后的自动重试上限与基础间隔（文件可能正被 requestCover 重建）。 */
internal const val MAX_COVER_DISPLAY_RETRIES = 6
internal const val COVER_DISPLAY_RETRY_BASE_MS = 1200L

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(
    video: Video,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onCoverRequested: (Video) -> Unit = {},
    onOnlineStatusRequested: (Video) -> Unit = {},
    /** 多选模式：显示复选框，点击切换选中而不是播放。 */
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelect: () -> Unit = {},
    /** 视频所属分类（彩色小标签显示）。 */
    category: Category? = null,
    modifier: Modifier = Modifier
) {
    val accent = if (video.isVertical) NeonPurple else CyberVermilion
    // 封面显示失败状态与重试计数提升到卡片级，便于上面的 LaunchedEffect 控制自动重试
    var imageFailed by remember(video.id, video.displayCoverPath) { mutableStateOf(false) }
    var coverRetryCount by remember(video.id, video.displayCoverPath) { mutableIntStateOf(0) }
    val animatedContainer by animateColorAsState(
        targetValue = if (selected) CyberVermilion.copy(alpha = 0.14f) else DarkCard,
        animationSpec = tween(durationMillis = 180),
        label = "videoCardContainer"
    )
    LaunchedEffect(video.id, video.coverPath, video.coverSourcePath, video.exportedPath, imageFailed) {
        // Also re-check a stale cached path. CoverCacheService removes invalid cache files
        // and restores the original cover or a video-frame fallback. When the display fails
        // (e.g. the cached file was wiped by the system), keep retrying with growing delays
        // so a freshly rebuilt cover shows up without restarting the app.
        onCoverRequested(video)
        if (imageFailed && coverRetryCount < MAX_COVER_DISPLAY_RETRIES) {
            delay(COVER_DISPLAY_RETRY_BASE_MS * (coverRetryCount + 1))
            coverRetryCount++
            imageFailed = false
        }
    }
    LaunchedEffect(video.id, video.onlineStatus, video.onlineCheckedAt) {
        onOnlineStatusRequested(video)
    }

    Card(
        modifier = modifier.fillMaxWidth().combinedClickable(
            onClick = if (selectionMode) onSelect else onClick,
            onLongClick = if (selectionMode) onSelect else onLongClick
        ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = animatedContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) accent.copy(alpha = 0.52f) else DarkDivider.copy(alpha = 0.58f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(108.dp).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onSelect() })
            }
            VideoThumbnail(video = video, accent = accent, imageFailed = imageFailed, onImageFailed = { imageFailed = true })
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (video.ownerName.isNotBlank()) {
                        Text(video.ownerName, style = MaterialTheme.typography.labelSmall, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (video.resolutionLabel.isNotBlank()) {
                        Text("·", color = DarkTextTertiary)
                        Text(video.resolutionLabel, style = MaterialTheme.typography.labelMedium, color = DarkTextSecondary)
                    }
                }
                Text(video.title, style = MaterialTheme.typography.titleSmall, color = DarkTextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // 时长已移到缩略图角标；分类与线上状态保留为标签，文件大小降级为文本，避免一排“按钮感”。
                    category?.let { CategoryBadge(it) }
                    if (video.duration <= 0L) VideoMeta(video.formattedDuration)
                    Text(video.formattedSize, style = MaterialTheme.typography.labelSmall, color = DarkTextTertiary, maxLines = 1)
                    OnlineStatusBadge(video.onlineStatus)
                }
            }
        }
    }
}

@Composable
private fun VideoThumbnail(
    video: Video,
    accent: Color,
    imageFailed: Boolean,
    onImageFailed: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(130.dp)
            .height(90.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        video.displayCoverPath?.takeUnless { imageFailed }?.let { displayCoverPath ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(displayCoverPath).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { onImageFailed() }
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)))))
        } ?: run {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(32.dp), tint = accent)
        }
        // 画质角标：右上角
        if (video.quality.isNotBlank()) {
            Surface(modifier = Modifier.align(Alignment.TopEnd).padding(5.dp), shape = RoundedCornerShape(6.dp), color = Color.Black.copy(alpha = 0.62f)) {
                Text(video.quality, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        // 时长角标：右下角，现代视频列表的通用做法
        if (video.duration > 0L) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.72f)
            ) {
                Text(
                    video.formattedDuration,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CategoryBadge(category: Category) {
    val color = Color(category.color)
    Surface(shape = RoundedCornerShape(7.dp), color = color.copy(alpha = 0.14f)) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
            Text(
                category.name,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun VideoMeta(label: String) {
    Surface(shape = RoundedCornerShape(7.dp), color = DarkSurfaceVariant) {
        Text(label, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = DarkTextSecondary)
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
        shape = RoundedCornerShape(7.dp),
        color = color.copy(alpha = 0.14f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.36f))
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = color)
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
