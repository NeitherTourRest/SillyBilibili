package com.example.sillybilibili.ui.pages.player

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerPage(
    filePath: String,
    videoTitle: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var isLocked by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1.0f) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(filePath))))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(player) { onDispose { player.release() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // ExoPlayer PlayerView — full built-in controls + gestures
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    controllerAutoShow = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Persistent top bar — back, title, speed, lock
        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Text(
                    videoTitle, maxLines = 1, fontWeight = FontWeight.Bold,
                    color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                )
                TextButton(onClick = {
                    speed = when (speed) { 0.5f -> 1.0f; 1.0f -> 1.5f; 1.5f -> 2.0f; else -> 0.5f }
                    player.setPlaybackParameters(PlaybackParameters(speed))
                }) {
                    Text("${speed}x", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { isLocked = !isLocked }) {
                    Icon(
                        if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen, "Lock",
                        tint = if (isLocked) Color(0xFFE53935) else Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Lock overlay
        if (isLocked) {
            Icon(
                Icons.Default.Lock, "Locked",
                tint = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.align(Alignment.Center).size(72.dp)
            )
        }
    }
}
