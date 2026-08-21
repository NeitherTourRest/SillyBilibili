package com.example.sillybilibili.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sillybilibili.domain.model.Category
import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.ui.theme.CyberVermilion
import com.example.sillybilibili.ui.theme.DarkCard
import com.example.sillybilibili.ui.theme.DarkDivider
import com.example.sillybilibili.ui.theme.DarkSurfaceVariant
import com.example.sillybilibili.ui.theme.DarkTextPrimary
import com.example.sillybilibili.ui.theme.DarkTextSecondary
import com.example.sillybilibili.ui.theme.DarkTextTertiary
import kotlinx.coroutines.delay

/**
 * 宫格视图卡片：封面大图在上（16:9），标题与元信息在下，适合快速浏览封面。
 * 多选模式下左上角显示圆形勾选标记，边框高亮。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoGridCard(
    video: Video,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onCoverRequested: (Video) -> Unit = {},
    onOnlineStatusRequested: (Video) -> Unit = {},
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelect: () -> Unit = {},
    category: Category? = null,
    modifier: Modifier = Modifier
) {
    val accent = if (video.isVertical) androidx.compose.ui.graphics.Color(0xFFB388FF) else CyberVermilion
    // 封面显示失败状态与重试计数（文件可能正被 requestCover 重建，失败后自动重试）
    var imageFailed by remember(video.id, video.displayCoverPath) { mutableStateOf(false) }
    var coverRetryCount by remember(video.id, video.displayCoverPath) { mutableIntStateOf(0) }
    val animatedContainer by animateColorAsState(
        targetValue = if (selected) CyberVermilion.copy(alpha = 0.14f) else DarkCard,
        animationSpec = tween(durationMillis = 180),
        label = "gridCardContainer"
    )
    val animatedBorder by animateColorAsState(
        targetValue = if (selected) CyberVermilion.copy(alpha = 0.52f) else DarkDivider.copy(alpha = 0.58f),
        animationSpec = tween(durationMillis = 180),
        label = "gridCardBorder"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "gridCheckPop"
    )
    LaunchedEffect(video.id, video.coverPath, video.coverSourcePath, video.exportedPath, imageFailed) {
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
            width = if (selectionMode) 1.5.dp else 1.dp,
            color = if (selectionMode) animatedBorder else DarkDivider.copy(alpha = 0.58f)
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                video.displayCoverPath?.takeUnless { imageFailed }?.let { displayCoverPath ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(displayCoverPath).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onError = { imageFailed = true }
                    )
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)))))
                } ?: run {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(32.dp), tint = accent)
                }
                if (selectionMode) {
                    val circleColor by animateColorAsState(
                        targetValue = if (selected) CyberVermilion else Color.Black.copy(alpha = 0.55f),
                        animationSpec = tween(durationMillis = 160),
                        label = "gridCheckCircle"
                    )
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                        shape = CircleShape,
                        color = circleColor,
                        border = BorderStroke(1.5.dp, if (selected) CyberVermilion else DarkDivider)
                    ) {
                        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "已选择",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp).graphicsLayer {
                                        scaleX = checkScale
                                        scaleY = checkScale
                                    }
                                )
                            }
                        }
                    }
                }
                if (video.quality.isNotBlank()) {
                    Surface(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp), shape = RoundedCornerShape(6.dp), color = Color.Black.copy(alpha = 0.62f)) {
                        Text(video.quality, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                if (video.duration > 0L) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.72f)
                    ) {
                        Text(
                            video.formattedDuration,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    video.title.ifBlank { "未命名视频" },
                    style = MaterialTheme.typography.labelLarge,
                    color = DarkTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    category?.let { cat ->
                        val catColor = Color(cat.color)
                        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(catColor))
                    }
                    Text(video.formattedSize, style = MaterialTheme.typography.labelSmall, color = DarkTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (video.ownerName.isNotBlank()) {
                        Text("·", color = DarkTextTertiary)
                        Text(video.ownerName, style = MaterialTheme.typography.labelSmall, color = DarkTextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
