package com.example.sillybilibili.ui.pages.player

import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.sillybilibili.service.PlaybackService
import com.example.sillybilibili.service.SettingsService
import com.example.sillybilibili.ui.theme.CyberVermilion
import com.example.sillybilibili.ui.theme.CyberVermilionLight
import com.example.sillybilibili.ui.theme.DarkCard
import com.example.sillybilibili.ui.theme.DarkSurfaceVariant
import com.example.sillybilibili.ui.theme.DarkTextPrimary
import com.example.sillybilibili.ui.theme.DarkTextSecondary
import com.example.sillybilibili.ui.theme.GlassBorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class BackgroundPlaybackViewModel @Inject constructor(settingsService: SettingsService) : ViewModel() {
    val isEnabled = settingsService.backgroundPlaybackEnabledFlow
}

/** Persistent, Bilibili-style mini player that appears after leaving the player with audio enabled. */
@Composable
fun BackgroundPlaybackEntry(
    visible: Boolean,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackgroundPlaybackViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val enabled by viewModel.isEnabled.collectAsState()
    val controllerFuture = remember(context) {
        MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
    }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var title by remember { mutableStateOf("") }
    var mediaCount by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }

    DisposableEffect(controllerFuture) {
        controllerFuture.addListener(
            { runCatching { controllerFuture.get() }.onSuccess { controller = it } },
            ContextCompat.getMainExecutor(context)
        )
        onDispose { MediaController.releaseFuture(controllerFuture) }
    }

    LaunchedEffect(controller, enabled) {
        val activeController = controller ?: return@LaunchedEffect
        while (enabled) {
            mediaCount = activeController.mediaItemCount
            title = activeController.mediaMetadata.title?.toString().orEmpty()
            isPlaying = activeController.isPlaying
            delay(500)
        }
    }

    if (visible && enabled && mediaCount > 0) {
        Surface(
            modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            color = DarkCard,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPlayer).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = DarkSurfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Default.Headset, contentDescription = null, tint = CyberVermilionLight, modifier = Modifier.padding(10.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title.ifBlank { "正在后台播放" }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = DarkTextPrimary, style = MaterialTheme.typography.labelLarge)
                    Text(if (isPlaying) "正在后台播放 · 点此回到播放页" else "播放已暂停 · 点此回到播放页", color = DarkTextSecondary, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { controller?.let { if (it.isPlaying) it.pause() else it.play() } }) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isPlaying) "暂停" else "播放", tint = CyberVermilion)
                }
                IconButton(onClick = { PlaybackService.stopPlayback(context) }) {
                    Icon(Icons.Default.Close, contentDescription = "Close background playback", tint = DarkTextSecondary)
                }
            }
        }
    }
}
