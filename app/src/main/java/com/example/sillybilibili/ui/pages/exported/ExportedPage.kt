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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.sillybilibili.ui.theme.*

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
            TopAppBar(
                title = {
                    Text("Converted MP4", fontWeight = FontWeight.Bold, color = Color.White)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White.copy(alpha = 0.8f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
            // Header stats
            if (uiState.videos.isNotEmpty()) {
                Surface(
                    color = CyberVermilion.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(0.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${uiState.videos.size} videos",
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
                        Text("No converted videos yet", color = Color(0xFF404060), fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.videos, key = { it.id }) { video ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            shape = RoundedCornerShape(0.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(video.title, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2)
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(video.quality, color = NeonCyan, style = MaterialTheme.typography.labelSmall)
                                        Text(video.formattedSize, color = Color(0xFF8080A0), style = MaterialTheme.typography.labelSmall)
                                        Text(video.formattedDuration, color = Color(0xFF8080A0), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                    IconButton(onClick = {
                                        video.exportedPath?.let { onNavigateToPlayer(it, video.title) }
                                    }) {
                                        Icon(Icons.Default.PlayArrow, "Play", tint = CyberGold)
                                    }
                                    IconButton(onClick = { viewModel.showDeleteConfirm(video) }) {
                                        Icon(Icons.Default.Delete, "Delete", tint = NeonRed.copy(alpha = 0.6f))
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
            shape = RoundedCornerShape(0.dp),
            title = { Text("Delete MP4", fontWeight = FontWeight.Bold, color = NeonRed) },
            text = { Text("Remove \"${video.title}\" from the list?\nThe MP4 file on disk will be deleted.", color = Color(0xFF8080A0)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteExported(video) }) {
                    Text("Delete", color = NeonRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirm() }) {
                    Text("Cancel", color = Color(0xFF606080))
                }
            }
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
