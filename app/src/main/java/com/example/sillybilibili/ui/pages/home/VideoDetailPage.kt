package com.example.sillybilibili.ui.pages.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sillybilibili.domain.model.ConversionStatus
import com.example.sillybilibili.ui.components.ConversionStatusView
import com.example.sillybilibili.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDetailPage(
    videoId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (String, String) -> Unit,
    viewModel: VideoDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showOutputDirDialog by remember { mutableStateOf(false) }
    var outputDir by remember { mutableStateOf(viewModel.getDefaultOutputPath()) }

    LaunchedEffect(Unit) {
        outputDir = viewModel.getDefaultOutputPath()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            uiState.video?.let { video ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) {
                        if (video.coverPath != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(video.coverPath)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(DarkSurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = CyberVermilion.copy(alpha = 0.5f)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            DarkBackground
                                        ),
                                        startY = 150f
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            if (video.ownerName.isNotEmpty()) {
                                Text(
                                    text = video.ownerName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CyberVermilion,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InfoChip(
                            icon = Icons.Default.Storage,
                            label = video.formattedSize
                        )
                        InfoChip(
                            icon = Icons.Default.Timer,
                            label = video.formattedDuration
                        )
                        if (video.resolutionLabel.isNotEmpty()) {
                            InfoChip(
                                icon = Icons.Default.AspectRatio,
                                label = video.resolutionLabel
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (video.quality.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "画质",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF606080)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                video.quality,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan,
                                fontFamily = FontFamily.Monospace
                            )
                            if (video.width > 0 && video.height > 0) {
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    "(${video.width}×${video.height})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF404060)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (video.exportedPath != null && uiState.conversionProgress == null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(0.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = NeonGreen, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Exported", style = MaterialTheme.typography.labelMedium, color = NeonGreen, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { onNavigateToPlayer(video.exportedPath!!, video.title) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberGold),
                                    shape = RoundedCornerShape(0.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Play", fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    uiState.conversionProgress?.let { progress ->
                        ConversionStatusView(
                            status = progress.status,
                            progress = progress.progress,
                            message = progress.outputPath ?: progress.errorMessage,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        if (progress.status == ConversionStatus.COMPLETED || progress.status == ConversionStatus.FAILED) {
                            Spacer(modifier = Modifier.height(12.dp))
                            if (progress.status == ConversionStatus.COMPLETED && progress.outputPath != null) {
                                Button(
                                    onClick = { onNavigateToPlayer(progress.outputPath!!, video.title) },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberGold),
                                    shape = RoundedCornerShape(0.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Play", fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            TextButton(
                                onClick = { viewModel.clearConversionStatus() },
                                modifier = Modifier.padding(horizontal = 16.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF606080))
                            ) { Text("Dismiss") }
                        }
                    } ?: run {
                        if (uiState.isConverting) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = CyberVermilion,
                                    trackColor = CyberVermilion.copy(alpha = 0.1f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Merging video and audio...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF606080)
                                )
                            }
                        } else {
                            Surface(
                                onClick = { showOutputDirDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .height(52.dp),
                                shape = RoundedCornerShape(0.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(CyberVermilion, CyberGold)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FileDownload,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Convert to MP4",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } ?: run {
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = CyberVermilion,
                            trackColor = CyberVermilion.copy(alpha = 0.1f)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Video not found",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF404060)
                        )
                    }
                }
            }
        }
    }

    if (showOutputDirDialog) {
        AlertDialog(
            onDismissRequest = { showOutputDirDialog = false },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(0.dp),
            title = {
                Text(
                    "Output Directory",
                    fontWeight = FontWeight.Bold,
                    color = CyberVermilion
                )
            },
            text = {
                OutlinedTextField(
                    value = outputDir,
                    onValueChange = { outputDir = it },
                    shape = RoundedCornerShape(0.dp),
                    label = {
                        Text(
                            "Output path",
                            color = Color(0xFF606080)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberVermilion,
                        unfocusedBorderColor = DarkDivider,
                        cursorColor = CyberVermilion,
                        focusedTextColor = Color(0xFFE8E8F0),
                        unfocusedTextColor = Color(0xFFE8E8F0),
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.convertToMp4(outputDir)
                        showOutputDirDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberVermilion
                    ),
                    shape = RoundedCornerShape(0.dp)
                ) {
                    Text(
                        "Convert",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showOutputDirDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF606080)
                    )
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun InfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(0.dp),
        color = DarkCard
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = CyberVermilion.copy(alpha = 0.7f)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFC0C0D0),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

