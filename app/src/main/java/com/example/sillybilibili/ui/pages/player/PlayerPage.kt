package com.example.sillybilibili.ui.pages.player

import android.content.ComponentName
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.sillybilibili.service.PlaybackService
import com.example.sillybilibili.service.CachePlaybackMetadata
import com.example.sillybilibili.domain.model.ConversionStatus
import com.example.sillybilibili.ui.components.ConversionStatusView
import com.example.sillybilibili.util.BvConverter
import com.example.sillybilibili.ui.components.OnlineStatusBadge
import com.example.sillybilibili.ui.theme.CyberVermilion
import com.example.sillybilibili.ui.theme.CyberVermilionGlow
import com.example.sillybilibili.ui.theme.CyberVermilionLight
import com.example.sillybilibili.ui.theme.DarkBackground
import com.example.sillybilibili.ui.theme.DarkCard
import com.example.sillybilibili.ui.theme.DarkDivider
import com.example.sillybilibili.ui.theme.DarkSurface
import com.example.sillybilibili.ui.theme.DarkSurfaceVariant
import com.example.sillybilibili.ui.theme.DarkTextPrimary
import com.example.sillybilibili.ui.theme.DarkTextSecondary
import com.example.sillybilibili.ui.theme.GlassBorder
import com.example.sillybilibili.ui.theme.NeonRed
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerPage(
    queueId: String,
    initialIndex: Int,
    onNavigateBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val scope = rememberCoroutineScope()
    val queue = remember(queueId) { PlaybackQueueStore.queueFor(queueId) }
    val preparation by viewModel.state.collectAsState()
    val backgroundPlaybackEnabled by viewModel.backgroundPlaybackEnabled.collectAsState()
    val onlineStatus by viewModel.onlineStatus.collectAsState()
    val currentVideo by viewModel.currentVideo.collectAsState()
    val currentCategory by viewModel.currentCategory.collectAsState()
    val integrityStatus by viewModel.integrityStatus.collectAsState()
    val conversionProgress by viewModel.conversionProgress.collectAsState()
    val swipePreviewFrames by viewModel.swipePreviewFrames.collectAsState()
    val controllerFuture = remember(context) {
        MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, PlaybackService::class.java))
        ).buildAsync()
    }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var activeIndex by remember { mutableIntStateOf(initialIndex) }
    var shizukuFallbackAttempted by remember(queueId) { mutableStateOf(false) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var videoAspectRatio by remember { mutableStateOf(DEFAULT_PLAYER_VIEWPORT_ASPECT_RATIO) }
    var fullscreenSwipeOffsetPx by remember { mutableStateOf(0f) }
    var fullscreenViewportHeightPx by remember { mutableIntStateOf(0) }
    var isSettlingFullscreenSwipe by remember { mutableStateOf(false) }
    var swipeWarmupIndex by remember { mutableIntStateOf(-1) }
    var settledSwipePreviewIndex by remember { mutableIntStateOf(-1) }

    DisposableEffect(controllerFuture) {
        controllerFuture.addListener(
            {
                try {
                    controller = controllerFuture.get()
                } catch (_: Exception) {
                    playerError = "无法连接后台播放服务"
                }
            },
            ContextCompat.getMainExecutor(context)
        )
        onDispose {
            controller = null
            MediaController.releaseFuture(controllerFuture)
        }
    }

    LaunchedEffect(queueId, queue) {
        queue?.let(viewModel::prepare)
    }

    LaunchedEffect(queueId, activeIndex) {
        videoAspectRatio = DEFAULT_PLAYER_VIEWPORT_ASPECT_RATIO
        queue?.items?.getOrNull(activeIndex)?.let { item ->
            viewModel.requestOnlineStatus(item.id)
            viewModel.observeConversion(item.id)
            viewModel.loadVideoDetail(item.id)
        }
    }

    LaunchedEffect(queueId, preparation.items, activeIndex) {
        if (preparation.items.isNotEmpty()) viewModel.preloadAdjacent(activeIndex)
    }

    LaunchedEffect(controller, preparation.items) {
        val activeController = controller ?: return@LaunchedEffect
        val currentQueue = queue
        if (currentQueue == null || currentQueue.items.isEmpty()) {
            playerError = "播放队列已失效，请从视频列表重新打开"
            return@LaunchedEffect
        }
        if (preparation.items.isEmpty()) {
            preparation.errorMessage?.let { playerError = it }
            return@LaunchedEffect
        }
        val startIndex = initialIndex.coerceIn(0, preparation.items.lastIndex)
        val mediaItems = preparation.items.map { item ->
            MediaItem.Builder()
                .setMediaId(item.id.toString())
                .setUri(item.videoUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist("Silly Bilibili")
                        .setExtras(CachePlaybackMetadata.extras(item.audioUri))
                        .build()
                )
                .build()
        }
        if (activeController.mediaItemCount == 0 || PlaybackQueueStore.activeQueueId() != queueId) {
            activeController.setMediaItems(mediaItems, startIndex, 0L)
            activeController.prepare()
            activeController.play()
            PlaybackQueueStore.markActive(queueId)
        }
    }

    DisposableEffect(controller) {
        val activeController = controller ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (preparation.usesShizukuDataSource && !shizukuFallbackAttempted) {
                    shizukuFallbackAttempted = true
                    playerError = "直读缓存失败，正在切换至兼容播放模式…"
                    viewModel.useTemporaryCopyFallback()
                } else {
                    playerError = playbackErrorHint(error)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) playerError = null
            }

            override fun onRenderedFirstFrame() {
                if (settledSwipePreviewIndex == activeController.currentMediaItemIndex) {
                    settledSwipePreviewIndex = -1
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoAspectRatio = videoContentAspectRatio(
                    width = videoSize.width,
                    height = videoSize.height,
                    pixelWidthHeightRatio = videoSize.pixelWidthHeightRatio
                ) ?: DEFAULT_PLAYER_VIEWPORT_ASPECT_RATIO
            }
        }
        activeController.addListener(listener)
        videoAspectRatio = videoContentAspectRatio(
            width = activeController.videoSize.width,
            height = activeController.videoSize.height,
            pixelWidthHeightRatio = activeController.videoSize.pixelWidthHeightRatio
        ) ?: DEFAULT_PLAYER_VIEWPORT_ASPECT_RATIO
        onDispose { activeController.removeListener(listener) }
    }

    LaunchedEffect(controller) {
        val activeController = controller ?: return@LaunchedEffect
        while (true) {
            activeIndex = activeController.currentMediaItemIndex.coerceAtLeast(0)
            positionMs = activeController.currentPosition.coerceAtLeast(0L)
            durationMs = activeController.duration.coerceAtLeast(0L)
            isPlaying = activeController.isPlaying
            playbackSpeed = activeController.playbackParameters.speed
            delay(500)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(4_000)
            controlsVisible = false
        }
    }

    val leavePlayer = {
        if (!viewModel.shouldKeepPlayingAfterLeaving()) {
            controller?.pause()
            PlaybackService.stopPlayback(context)
        }
        onNavigateBack()
    }
    androidx.activity.compose.BackHandler {
        if (isFullscreen) {
            isFullscreen = false
            activity?.let { targetActivity ->
                WindowCompat.getInsetsController(targetActivity.window, targetActivity.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        } else {
            leavePlayer()
        }
    }

    fun setFullscreen(fullscreen: Boolean) {
        isFullscreen = fullscreen
        controlsVisible = true
        if (!fullscreen) fullscreenSwipeOffsetPx = 0f
        if (!fullscreen) settledSwipePreviewIndex = -1
        val targetActivity = activity ?: return
        val insetsController = WindowCompat.getInsetsController(targetActivity.window, targetActivity.window.decorView)
        if (fullscreen) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun switchToQueueIndex(index: Int) {
        activeIndex = index
        viewModel.preloadAdjacent(index)
        controller?.seekToDefaultPosition(index)
        controller?.play()
    }

    fun settleFullscreenSwipe() {
        if (isSettlingFullscreenSwipe) return
        val targetIndex = fullscreenSwipeTargetIndex(
            activeIndex = activeIndex,
            itemCount = queue?.items?.size ?: 0,
            offsetPx = fullscreenSwipeOffsetPx,
            viewportHeightPx = fullscreenViewportHeightPx
        )
        scope.launch {
            if (targetIndex == null) {
                animate(
                    initialValue = fullscreenSwipeOffsetPx,
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 180)
                ) { value, _ -> fullscreenSwipeOffsetPx = value }
                return@launch
            }

            isSettlingFullscreenSwipe = true
            val outgoingOffset = if (fullscreenSwipeOffsetPx < 0f) {
                -fullscreenViewportHeightPx.toFloat()
            } else {
                fullscreenViewportHeightPx.toFloat()
            }
            // Keep the old player live for the complete outgoing animation. The preview has
            // already decoded the target at t=0 and covers the player while the new item starts.
            viewModel.preloadSwipeTarget(targetIndex)
            viewModel.preloadAdjacent(targetIndex)
            animate(
                initialValue = fullscreenSwipeOffsetPx,
                targetValue = outgoingOffset,
                animationSpec = tween(durationMillis = 120)
            ) { value, _ -> fullscreenSwipeOffsetPx = value }

            activeIndex = targetIndex
            settledSwipePreviewIndex = targetIndex
            fullscreenSwipeOffsetPx = 0f
            controller?.seekToDefaultPosition(targetIndex)
            controller?.play()
            isSettlingFullscreenSwipe = false
        }
    }

    val swipePreviewIndex = fullscreenSwipePreviewIndex(
        activeIndex = activeIndex,
        itemCount = queue?.items?.size ?: 0,
        offsetPx = fullscreenSwipeOffsetPx,
        settledTargetIndex = settledSwipePreviewIndex.takeIf { it >= 0 }
    )

    Column(Modifier.fillMaxSize().background(DarkBackground)) {
    Box(
        (if (isFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(nonFullscreenViewportAspectRatio(videoAspectRatio)))
            .onSizeChanged { size -> fullscreenViewportHeightPx = size.height }
            .background(Color.Black)
    ) {
        if (isFullscreen && swipePreviewIndex != null && fullscreenViewportHeightPx > 0) {
            val isSettledPreview = settledSwipePreviewIndex == swipePreviewIndex
            val previewOffset = if (isSettledPreview) {
                0
            } else if (fullscreenSwipeOffsetPx < 0f) {
                fullscreenViewportHeightPx + fullscreenSwipeOffsetPx.roundToInt()
            } else {
                -fullscreenViewportHeightPx + fullscreenSwipeOffsetPx.roundToInt()
            }
            FullscreenSwipePreview(
                item = queue?.items?.getOrNull(swipePreviewIndex),
                frame = swipePreviewFrames[swipePreviewIndex],
                modifier = Modifier.fillMaxSize().offset { IntOffset(0, previewOffset) }.zIndex(if (isSettledPreview) 3f else 1f)
            )
        }

        Box(
            Modifier.fillMaxSize().offset { IntOffset(0, fullscreenSwipeOffsetPx.roundToInt()) }
        ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    player = controller
                    // Media3 remains responsible for decoding and MediaSession integration.
                    // Compose provides a control hierarchy suited to this app's layout.
                    useController = false
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { playerView ->
                playerView.player = controller
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            Modifier.fillMaxSize()
                .pointerInput(isFullscreen, activeIndex, fullscreenViewportHeightPx, isSettlingFullscreenSwipe) {
                    if (isFullscreen && fullscreenViewportHeightPx > 0) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                controlsVisible = false
                                swipeWarmupIndex = -1
                            },
                            onVerticalDrag = { _, dragAmount ->
                                if (!isSettlingFullscreenSwipe) {
                                    val proposedOffset = fullscreenSwipeOffsetPx + dragAmount
                                    val adjacentIndex = fullscreenSwipeAdjacentIndex(
                                        activeIndex = activeIndex,
                                        itemCount = queue?.items?.size ?: 0,
                                        offsetPx = proposedOffset
                                    )
                                    if (
                                        adjacentIndex != null &&
                                        swipeWarmupIndex != adjacentIndex &&
                                        abs(proposedOffset) >= fullscreenViewportHeightPx * 0.02f
                                    ) {
                                        swipeWarmupIndex = adjacentIndex
                                        viewModel.preloadSwipeTarget(adjacentIndex)
                                    }
                                    fullscreenSwipeOffsetPx = if (adjacentIndex != null) {
                                        proposedOffset.coerceIn(
                                            -fullscreenViewportHeightPx.toFloat(),
                                            fullscreenViewportHeightPx.toFloat()
                                        )
                                    } else {
                                        (proposedOffset * 0.18f).coerceIn(
                                            -fullscreenViewportHeightPx * 0.18f,
                                            fullscreenViewportHeightPx * 0.18f
                                        )
                                    }
                                }
                            },
                            onDragEnd = ::settleFullscreenSwipe,
                            onDragCancel = {
                                scope.launch {
                                    animate(
                                        initialValue = fullscreenSwipeOffsetPx,
                                        targetValue = 0f,
                                        animationSpec = tween(durationMillis = 160)
                                    ) { value, _ -> fullscreenSwipeOffsetPx = value }
                                }
                            }
                        )
                    }
                }
                .clickable { controlsVisible = !controlsVisible }
        )

        if (controlsVisible) {
            PlaybackControls(
                title = controller?.mediaMetadata?.title?.toString().orEmpty()
                    .ifBlank { queue?.items?.getOrNull(activeIndex)?.title.orEmpty() },
                isFullscreen = isFullscreen,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                playbackSpeed = playbackSpeed,
                episodeLabel = "第 ${activeIndex + 1} / ${queue?.items?.size ?: 0} 集",
                canGoPrevious = activeIndex > 0,
                canGoNext = activeIndex < (queue?.items?.lastIndex ?: -1),
                onBack = { if (isFullscreen) setFullscreen(false) else leavePlayer() },
                onTogglePlayback = {
                    controller?.let { if (it.isPlaying) it.pause() else it.play() }
                },
                onPrevious = {
                    controller?.seekToDefaultPosition((activeIndex - 1).coerceAtLeast(0))
                    controller?.play()
                },
                onNext = {
                    controller?.seekToDefaultPosition((activeIndex + 1).coerceAtMost(queue?.items?.lastIndex ?: 0))
                    controller?.play()
                },
                onSeek = { fraction -> controller?.seekTo((durationMs * fraction).toLong()) },
                onShowEpisodes = { showQueueSheet = true },
                onChangeSpeed = {
                    val nextSpeed = when (playbackSpeed) {
                        1f -> 1.25f
                        1.25f -> 1.5f
                        1.5f -> 2f
                        else -> 1f
                    }
                    controller?.setPlaybackSpeed(nextSpeed)
                    playbackSpeed = nextSpeed
                },
                onToggleFullscreen = { setFullscreen(!isFullscreen) }
            )
        }

        if ((controller == null || preparation.isPreparing) && playerError == null) {
            Surface(modifier = Modifier.align(Alignment.Center), color = DarkSurface.copy(alpha = 0.92f), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)) {
                Text(if (preparation.isPreparing) "正在准备原始缓存播放…" else "正在连接后台播放服务…", modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp), color = DarkTextPrimary)
            }
        }
        playerError?.let { message ->
            Surface(modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp), color = NeonRed.copy(alpha = 0.18f), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, NeonRed.copy(alpha = 0.44f))) {
                Text(message, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp), color = DarkTextPrimary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    }
        if (!isFullscreen) LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            item {
                PlayerInfoPanel(
                    title = controller?.mediaMetadata?.title?.toString().orEmpty().ifBlank { queue?.items?.getOrNull(activeIndex)?.title.orEmpty() },
                    activeIndex = activeIndex,
                    queueSize = queue?.items?.size ?: 0,
                    onlineStatus = onlineStatus,
                    activeItem = queue?.items?.getOrNull(activeIndex),
                    currentVideo = currentVideo,
                    currentCategory = currentCategory,
                    integrityStatus = integrityStatus,
                    conversionProgress = conversionProgress,
                    onConvertToMp4 = viewModel::convertToMp4,
                    backgroundPlaybackEnabled = backgroundPlaybackEnabled,
                    durationMs = durationMs,
                    playbackSpeed = playbackSpeed,
                    videoAspectRatio = videoAspectRatio,
                    usesShizukuDataSource = preparation.usesShizukuDataSource,
                    onShowEpisodes = { showQueueSheet = true },
                    onSleepTimer = { showSleepTimerSheet = true },
                    onRefreshStatus = viewModel::refreshOnlineStatus,
                    onCheckIntegrity = viewModel::checkMediaIntegrity
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("播放列表", color = DarkTextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${queue?.items?.size ?: 0} 个视频 · 当前列表", color = DarkTextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
            itemsIndexed(queue?.items.orEmpty(), key = { _, item -> item.id }) { index, item ->
                InlinePlaylistRow(
                    item = item,
                    index = index,
                    selected = index == activeIndex,
                    onClick = {
                        controller?.seekToDefaultPosition(index)
                        controller?.play()
                    }
                )
            }
        }
    }

    if (showQueueSheet) {
        ModalBottomSheet(onDismissRequest = { showQueueSheet = false }, containerColor = DarkSurface) {
            Text("选集 · 当前搜索/筛选结果", modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = DarkTextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${queue?.items?.size ?: 0} 个可播放视频", modifier = Modifier.padding(horizontal = 24.dp), color = DarkTextSecondary, style = MaterialTheme.typography.bodySmall)
            LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp).padding(top = 12.dp)) {
                itemsIndexed(queue?.items.orEmpty(), key = { _, item -> item.id }) { index, item ->
                    val selected = index == activeIndex
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            controller?.seekToDefaultPosition(index)
                            controller?.play()
                            showQueueSheet = false
                        }.background(if (selected) CyberVermilionGlow else Color.Transparent).padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${index + 1}", color = if (selected) CyberVermilion else DarkTextSecondary, modifier = Modifier.width(32.dp), fontWeight = FontWeight.Bold)
                        Text(item.title, color = DarkTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        if (selected) Icon(Icons.Default.PlayArrow, null, tint = CyberVermilion)
                    }
                }
            }
        }
    }

    if (showSleepTimerSheet) {
        ModalBottomSheet(onDismissRequest = { showSleepTimerSheet = false }, containerColor = DarkSurface) {
            Text("定时停止播放", modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = DarkTextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("时间到后会暂停播放，后台播放时同样有效。", modifier = Modifier.padding(horizontal = 24.dp), color = DarkTextSecondary, style = MaterialTheme.typography.bodySmall)
            listOf(15, 30, 45, 60).forEach { minutes ->
                Row(modifier = Modifier.fillMaxWidth().clickable {
                    PlaybackService.setSleepTimer(context, minutes * 60_000L)
                    showSleepTimerSheet = false
                }.padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, null, tint = CyberVermilion)
                    Spacer(Modifier.width(16.dp))
                    Text("${minutes} 分钟后停止", color = DarkTextPrimary, fontWeight = FontWeight.Medium)
                }
            }
            if (PlaybackService.timerEndTimeMillis > System.currentTimeMillis()) {
                TextButton(onClick = { PlaybackService.cancelSleepTimer(context); showSleepTimerSheet = false }, modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                    Text("取消当前定时", color = CyberVermilionLight)
                }
            }
        }
    }
}

@Composable
private fun PlaybackControls(
    title: String,
    isFullscreen: Boolean,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    episodeLabel: String,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onBack: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onShowEpisodes: () -> Unit,
    onChangeSpeed: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Column(
        modifier = modifier.fillMaxSize().zIndex(2f),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    text = title.ifBlank { "正在播放" },
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(episodeLabel, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall)
            }
        }

        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(formatPlaybackTime(positionMs), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = progress,
                    onValueChange = onSeek,
                    modifier = Modifier.weight(1f).height(12.dp).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = CyberVermilionLight,
                        activeTrackColor = CyberVermilion,
                        inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                    )
                )
                Text(formatPlaybackTime(durationMs), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelSmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(38.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious, enabled = canGoPrevious, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "上一集", tint = Color.White.copy(alpha = if (canGoPrevious) 1f else 0.35f), modifier = Modifier.size(23.dp))
                }
                IconButton(onClick = onTogglePlayback, modifier = Modifier.size(38.dp)) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isPlaying) "暂停" else "播放", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                IconButton(onClick = onNext, enabled = canGoNext, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "下一集", tint = Color.White.copy(alpha = if (canGoNext) 1f else 0.35f), modifier = Modifier.size(23.dp))
                }
                Spacer(Modifier.weight(1f))
                CompactControlPill(text = "选集", onClick = onShowEpisodes)
                Spacer(Modifier.width(12.dp))
                CompactControlPill(text = "${playbackSpeed}×", onClick = onChangeSpeed)
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(34.dp)) {
                    Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, contentDescription = if (isFullscreen) "退出全屏" else "全屏", tint = Color.White, modifier = Modifier.size(23.dp))
                }
            }
        }
    }
}

@Composable
private fun CompactControlPill(
    text: String,
    onClick: () -> Unit,
    icon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.height(30.dp).clickable(onClick = onClick).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        icon?.invoke()
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

private fun formatPlaybackTime(timeMs: Long): String {
    val totalSeconds = (timeMs.coerceAtLeast(0L) / 1_000L).toInt()
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/** The standard non-fullscreen frame used for landscape and unknown sources. */
internal const val DEFAULT_PLAYER_VIEWPORT_ASPECT_RATIO = 16f / 9f

/**
 * Builds a display aspect ratio from Media3's decoded video metadata. Pixel aspect ratio is
 * included so older non-square-pixel sources do not get treated as portrait by mistake.
 */
internal fun videoContentAspectRatio(
    width: Int,
    height: Int,
    pixelWidthHeightRatio: Float
): Float? {
    if (width <= 0 || height <= 0 || pixelWidthHeightRatio <= 0f) return null
    val ratio = width.toFloat() * pixelWidthHeightRatio / height.toFloat()
    return ratio.takeIf { it in 0.1f..10f }
}

/**
 * Mirrors the compact Bilibili-style viewing area: widescreen sources use a 16:9 frame, while
 * portrait sources receive a taller theatre frame with a small black margin on both sides.
 * This keeps a 9:16 source nearly full-height instead of shrinking it into a 16:9 box.
 */
internal fun nonFullscreenViewportAspectRatio(videoAspectRatio: Float): Float = when {
    videoAspectRatio !in 0.1f..10f -> DEFAULT_PLAYER_VIEWPORT_ASPECT_RATIO
    videoAspectRatio < 1f -> (videoAspectRatio / 0.82f).coerceIn(0.66f, 0.96f)
    videoAspectRatio <= 1.08f -> 1f
    else -> DEFAULT_PLAYER_VIEWPORT_ASPECT_RATIO
}

/** Returns the adjacent queue entry represented by a live full-screen drag. */
internal fun fullscreenSwipeAdjacentIndex(
    activeIndex: Int,
    itemCount: Int,
    offsetPx: Float
): Int? {
    if (offsetPx == 0f || itemCount <= 0) return null
    val candidate = if (offsetPx < 0f) activeIndex + 1 else activeIndex - 1
    return candidate.takeIf { it in 0 until itemCount }
}

/** A short, intentional drag is enough to move through a short-video style queue. */
internal const val FULLSCREEN_SWIPE_SWITCH_FRACTION = 0.07f

internal fun fullscreenSwipeTargetIndex(
    activeIndex: Int,
    itemCount: Int,
    offsetPx: Float,
    viewportHeightPx: Int
): Int? {
    if (viewportHeightPx <= 0 || abs(offsetPx) < viewportHeightPx * FULLSCREEN_SWIPE_SWITCH_FRACTION) return null
    return fullscreenSwipeAdjacentIndex(activeIndex, itemCount, offsetPx)
}

/** Keeps the revealed first-frame page on top until the target player has rendered a frame. */
internal fun fullscreenSwipePreviewIndex(
    activeIndex: Int,
    itemCount: Int,
    offsetPx: Float,
    settledTargetIndex: Int?
): Int? = settledTargetIndex?.takeIf { it in 0 until itemCount }
    ?: fullscreenSwipeAdjacentIndex(activeIndex, itemCount, offsetPx)

@Composable
private fun FullscreenSwipePreview(item: PlaybackQueueItem?, frame: Bitmap?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (frame != null) {
            androidx.compose.foundation.Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else if (!item?.coverPath.isNullOrBlank()) {
            AsyncImage(
                model = item?.coverPath,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = item?.title.orEmpty(),
                modifier = Modifier.padding(horizontal = 32.dp),
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlayerInfoPanel(
    title: String,
    activeIndex: Int,
    queueSize: Int,
    onlineStatus: com.example.sillybilibili.domain.model.OnlineVideoStatus,
    activeItem: PlaybackQueueItem?,
    currentVideo: com.example.sillybilibili.domain.model.Video?,
    currentCategory: com.example.sillybilibili.domain.model.Category?,
    integrityStatus: com.example.sillybilibili.service.MediaIntegrityStatus?,
    conversionProgress: com.example.sillybilibili.domain.model.ConversionProgress?,
    onConvertToMp4: (PlaybackQueueItem) -> Unit,
    backgroundPlaybackEnabled: Boolean,
    durationMs: Long,
    playbackSpeed: Float,
    videoAspectRatio: Float,
    usesShizukuDataSource: Boolean,
    onShowEpisodes: () -> Unit,
    onSleepTimer: () -> Unit,
    onRefreshStatus: () -> Unit,
    onCheckIntegrity: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title.ifBlank { "正在播放" }, color = DarkTextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("本地缓存 · 第 ${activeIndex + 1} / $queueSize 集", color = DarkTextSecondary, style = MaterialTheme.typography.bodySmall)
            currentCategory?.let { cat ->
                val catColor = Color(cat.color)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = catColor.copy(alpha = 0.14f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, catColor.copy(alpha = 0.36f))
                ) {
                    Row(
                        Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(catColor))
                        Text(cat.name, style = MaterialTheme.typography.labelSmall, color = catColor, maxLines = 1)
                    }
                }
            }
            OnlineStatusBadge(onlineStatus)
        }
        val sourceType = when {
            activeItem == null -> "正在读取播放来源"
            activeItem.isMuxedFile -> "已导出 MP4 / 单文件播放"
            else -> "B 站分离缓存 / 视频 + 音频轨"
        }
        val videoName = activeItem?.videoPath?.substringAfterLast('/')?.ifBlank { "未知文件" } ?: "未知文件"
        val durationLabel = if (durationMs > 0L) formatPlaybackTime(durationMs) else "读取中"
        val ratioLabel = if (videoAspectRatio > 0f) "%.2f:1".format(videoAspectRatio) else "读取中"
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = DarkCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("播放详情", color = DarkTextPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                currentVideo?.let { video ->
                    val videoIdLabel = buildString {
                        append("av${video.avid}")
                        BvConverter.avidToBv(video.avid)?.let { append(" · $it") }
                    }
                    PlayerDetailLine("视频编号", videoIdLabel)
                    if (video.pubdate > 0L) PlayerDetailLine("发布时间", formatPublishDate(video.pubdate))
                    PlayerDetailLine("缓存时间", formatCachedAt(video.addedAt))
                }
                integrityStatus?.let { status ->
                    PlayerDetailLine("文件完整性", integrityStatusLabel(status))
                }
                PlayerDetailLine("播放来源", sourceType)
                PlayerDetailLine("媒体文件", videoName)
                PlayerDetailLine("时长 / 倍速", "$durationLabel · ${playbackSpeed}×")
                PlayerDetailLine("画面比例", ratioLabel)
                PlayerDetailLine("访问通道", if (usesShizukuDataSource) "Shizuku 直读缓存" else "本地文件或 SAF 访问")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onShowEpisodes, modifier = Modifier.weight(1f)) { Text("选集 $queueSize", color = CyberVermilion) }
            TextButton(onClick = onSleepTimer, modifier = Modifier.weight(1f)) { Text("定时停止", color = DarkTextSecondary) }
            TextButton(onClick = {}, modifier = Modifier.weight(1f)) { Text(if (backgroundPlaybackEnabled) "后台播放已开" else "后台播放已关", color = DarkTextSecondary, maxLines = 1) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRefreshStatus, modifier = Modifier.weight(1f)) { Text("刷新在线状态", color = DarkTextSecondary) }
            TextButton(onClick = onCheckIntegrity, modifier = Modifier.weight(1f)) { Text("检查文件完整性", color = DarkTextSecondary) }
        }
        activeItem?.takeIf { it.canConvertToMp4 }?.let { item ->
            val isConverting = conversionProgress?.status in setOf(ConversionStatus.PENDING, ConversionStatus.CONVERTING)
            Button(
                onClick = { onConvertToMp4(item) },
                enabled = !isConverting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CyberVermilion)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isConverting) "正在后台转换…" else "转换为 MP4")
            }
            conversionProgress?.let { progress ->
                ConversionStatusView(
                    status = progress.status,
                    progress = progress.progress,
                    message = progress.statusMessage ?: progress.outputPath ?: progress.errorMessage
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onShowEpisodes),
            shape = RoundedCornerShape(14.dp),
            color = DarkCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QueueMusic, contentDescription = null, tint = CyberVermilion)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("选集 · 当前列表", color = DarkTextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("正在播放第 ${activeIndex + 1} 集，点击切换", color = DarkTextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Text("${activeIndex + 1}/$queueSize", color = CyberVermilionLight, style = MaterialTheme.typography.labelLarge)
            }
        }
        Text("播放设置", color = DarkTextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            if (backgroundPlaybackEnabled) "退出此页面后将继续播放，底部迷你播放器可随时回到这里。"
            else "后台播放已关闭：退出此页面会停止播放，可在设置中开启。",
            color = DarkTextSecondary, style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PlayerDetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = DarkTextSecondary, style = MaterialTheme.typography.labelSmall)
        Text(
            value,
            modifier = Modifier.padding(start = 16.dp).weight(1f, fill = false),
            color = DarkTextPrimary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InlinePlaylistRow(
    item: PlaybackQueueItem,
    index: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(if (selected) CyberVermilionGlow else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.width(128.dp).height(72.dp),
            shape = RoundedCornerShape(10.dp),
            color = DarkSurfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) CyberVermilion.copy(alpha = 0.48f) else DarkDivider)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (!item.coverPath.isNullOrBlank()) {
                    AsyncImage(
                        model = item.coverPath,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Surface(color = Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(6.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)) {
                    Text(if (selected) "播放中" else "第 ${index + 1} 集", modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp), color = if (selected) CyberVermilionLight else DarkTextPrimary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(item.title, color = if (selected) CyberVermilionLight else DarkTextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (selected) "正在播放 · 点击重新开始" else "本地缓存视频 · 点击播放", color = DarkTextSecondary, style = MaterialTheme.typography.labelSmall)
        }
        if (selected) Icon(Icons.Default.PlayArrow, contentDescription = "当前播放", tint = CyberVermilion)
    }
}

/** 发布时间（Unix 秒）→ 日期。 */
internal fun formatPublishDate(pubdateSeconds: Long): String =
    java.time.Instant.ofEpochSecond(pubdateSeconds)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))

/** 缓存入库时间（毫秒）→ 日期时间。 */
internal fun formatCachedAt(addedAtMs: Long): String =
    java.time.Instant.ofEpochMilli(addedAtMs)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

/** 完整性状态 → 中文说明。 */
internal fun integrityStatusLabel(status: com.example.sillybilibili.service.MediaIntegrityStatus): String = when (status) {
    com.example.sillybilibili.service.MediaIntegrityStatus.OK -> "视频与音频文件完好"
    com.example.sillybilibili.service.MediaIntegrityStatus.VIDEO_MISSING -> "视频文件缺失或为空"
    com.example.sillybilibili.service.MediaIntegrityStatus.AUDIO_MISSING -> "音频文件缺失或为空"
    com.example.sillybilibili.service.MediaIntegrityStatus.BOTH_MISSING -> "视频与音频文件均缺失"
    com.example.sillybilibili.service.MediaIntegrityStatus.UNKNOWN -> "无法确认（目录隔离且未授权 Shizuku）"
}

/** 把 Media3 错误码翻译成用户能理解的中文提示。 */
internal fun playbackErrorHint(error: PlaybackException): String = playbackErrorHint(error.errorCode)

internal fun playbackErrorHint(errorCode: Int): String = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
        "缓存文件读取失败：文件可能已被清除或损坏，请重新扫描后重试"
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
        "视频格式无法解析：缓存文件可能已损坏，请重新扫描或重新缓存"
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
        "设备不支持该视频的编码格式（如 HEVC），请尝试转换后播放"
    PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
    PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ->
        "音频输出初始化失败，请检查系统音量或蓝牙连接"
    else -> "无法播放此视频（${PlaybackException.getErrorCodeName(errorCode)}）"
}
