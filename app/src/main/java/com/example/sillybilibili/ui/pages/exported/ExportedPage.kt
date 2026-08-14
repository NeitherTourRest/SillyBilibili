package com.example.sillybilibili.ui.pages.exported

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.sillybilibili.ui.theme.*
import com.example.sillybilibili.ui.components.AppTopBar
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportedPage(
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (String, String) -> Unit,
    viewModel: ExportedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            AppTopBar(title = "已导出视频", subtitle = "转换完成的 MP4 文件", onNavigateBack = onNavigateBack)
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
            // Header stats
            if (uiState.videos.isNotEmpty()) {
                Surface(
                    color = CyberVermilion.copy(alpha = 0.08f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${uiState.videos.size} 个视频",
                            style = MaterialTheme.typography.labelLarge,
                            color = CyberVermilion,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            formatSize(uiState.totalSize),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFA0A0B8)
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CyberVermilion, trackColor = CyberVermilion.copy(alpha = 0.1f))
                }
            } else if (uiState.videos.isEmpty()) {
                Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.VideoLibrary, null, Modifier.size(64.dp), tint = Color(0xFF404060))
                    Text("还没有导出的视频", color = DarkTextSecondary, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.videos, key = { it.id }) { video ->
                        LaunchedEffect(video.id, video.coverPath, video.exportedPath) {
                            viewModel.requestCover(video)
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            shape = MaterialTheme.shapes.medium,
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(if (video.isVertical) 54.dp else 96.dp)
                                        .aspectRatio(video.previewAspectRatio)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(DarkSurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (video.displayCoverPath != null) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current).data(video.displayCoverPath).crossfade(true).build(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Default.PlayArrow, null, tint = CyberVermilion)
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(video.title, fontWeight = FontWeight.Bold, color = DarkTextPrimary, maxLines = 2)
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(video.quality, color = NeonCyan, style = MaterialTheme.typography.labelSmall)
                                        Text(if (video.exportedSize > 0L) formatExportedSize(video.exportedSize) else video.formattedSize, color = Color(0xFF8080A0), style = MaterialTheme.typography.labelSmall)
                                        Text(video.formattedDuration, color = Color(0xFF8080A0), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                    IconButton(onClick = {
                                        video.exportedPath?.let { onNavigateToPlayer(it, video.title) }
                                    }) {
                                        Icon(Icons.Default.PlayArrow, "播放", tint = CyberVermilion)
                                    }
                                    IconButton(onClick = { viewModel.showDeleteConfirm(video) }) {
                                        Icon(Icons.Default.Delete, "删除", tint = NeonRed.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom gradient bar
            Box(Modifier.fillMaxWidth().height(4.dp).background(Brush.horizontalGradient(listOf(CyberVermilion, CyberGold, NeonPurple))))
        }
    }

    // Delete confirmation dialog
    uiState.deleteConfirmVideo?.let { video ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirm() },
            containerColor = DarkSurface,
            shape = MaterialTheme.shapes.large,
            title = { Text("删除 MP4", fontWeight = FontWeight.Bold) },
            text = { Text("将删除列表记录和磁盘中的 MP4 文件：\n${video.title}", color = DarkTextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteExported(video) }) {
                    Text("删除", color = NeonRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirm() }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun formatExportedSize(size: Long): String = when {
    size >= 1024L * 1024 * 1024 -> "%.2f GB".format(size / (1024.0 * 1024 * 1024))
    size >= 1024L * 1024 -> "%.2f MB".format(size / (1024.0 * 1024))
    size >= 1024L -> "%.2f KB".format(size / 1024.0)
    else -> "$size B"
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
