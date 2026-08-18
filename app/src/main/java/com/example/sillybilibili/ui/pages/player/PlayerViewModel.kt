package com.example.sillybilibili.ui.pages.player

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sillybilibili.domain.model.ConversionProgress
import com.example.sillybilibili.domain.model.ConversionStatus
import com.example.sillybilibili.service.ConversionForegroundService
import com.example.sillybilibili.service.ConversionJobRegistry
import com.example.sillybilibili.service.PlaybackMediaResolver
import com.example.sillybilibili.service.PlaybackReadAheadPreloader
import com.example.sillybilibili.service.PlaybackFirstFrameExtractor
import com.example.sillybilibili.service.PreparedPlaybackItem
import com.example.sillybilibili.service.SettingsService
import com.example.sillybilibili.service.OnlineVideoStatusService
import com.example.sillybilibili.service.VideoConverterService
import com.example.sillybilibili.domain.model.OnlineVideoStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerPreparationState(
    val isPreparing: Boolean = false,
    val items: List<PreparedPlaybackItem> = emptyList(),
    val errorMessage: String? = null,
    val usesShizukuDataSource: Boolean = false
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackMediaResolver: PlaybackMediaResolver,
    private val playbackReadAheadPreloader: PlaybackReadAheadPreloader,
    private val playbackFirstFrameExtractor: PlaybackFirstFrameExtractor,
    private val settingsService: SettingsService,
    private val onlineVideoStatusService: OnlineVideoStatusService,
    private val videoConverterService: VideoConverterService,
    private val conversionJobRegistry: ConversionJobRegistry
) : ViewModel() {
    private val _state = MutableStateFlow(PlayerPreparationState())
    val state: StateFlow<PlayerPreparationState> = _state.asStateFlow()
    val backgroundPlaybackEnabled = settingsService.backgroundPlaybackEnabledFlow
    private val _onlineStatus = MutableStateFlow(OnlineVideoStatus.UNCHECKED)
    val onlineStatus: StateFlow<OnlineVideoStatus> = _onlineStatus.asStateFlow()
    private val _conversionProgress = MutableStateFlow<ConversionProgress?>(null)
    val conversionProgress: StateFlow<ConversionProgress?> = _conversionProgress.asStateFlow()
    private val _swipePreviewFrames = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    /** First decoded frame for queue neighbours, keyed by their queue index. */
    val swipePreviewFrames: StateFlow<Map<Int, Bitmap>> = _swipePreviewFrames.asStateFlow()
    private var queue: PlaybackQueue? = null
    private var conversionObservation: Job? = null
    private var readAheadJob: Job? = null
    private var swipeWarmupJob: Job? = null
    private var swipeWarmupTargetIndex: Int? = null
    private val firstFrameJobs = mutableMapOf<Int, Job>()

    fun prepare(playbackQueue: PlaybackQueue) {
        firstFrameJobs.values.forEach(Job::cancel)
        firstFrameJobs.clear()
        _swipePreviewFrames.value = emptyMap()
        queue = playbackQueue
        resolve(forceTemporaryCopies = false)
    }

    fun useTemporaryCopyFallback() = resolve(forceTemporaryCopies = true)

    /** Warms nearby isolated-cache media without creating another decoder or audio session. */
    fun preloadAdjacent(activeIndex: Int) {
        val adjacentItems = adjacentPlaybackPreloadItems(queue?.items.orEmpty(), activeIndex)
        readAheadJob?.cancel()
        if (adjacentItems.isEmpty()) return
        readAheadJob = viewModelScope.launch {
            playbackReadAheadPreloader.preload(adjacentItems)
        }
        preloadSwipePreview(activeIndex - 1)
        preloadSwipePreview(activeIndex + 1)
    }

    /** Starts a larger read-ahead as soon as a full-screen drag exposes a neighbouring item. */
    fun preloadSwipeTarget(targetIndex: Int) {
        val item = queue?.items?.getOrNull(targetIndex) ?: return
        if (swipeWarmupTargetIndex == targetIndex && swipeWarmupJob?.isActive == true) return
        swipeWarmupTargetIndex = targetIndex
        swipeWarmupJob?.cancel()
        swipeWarmupJob = viewModelScope.launch {
            playbackReadAheadPreloader.preloadTransitionTarget(item)
        }
        preloadSwipePreview(targetIndex)
    }

    /** Starts decoding the neighbouring page at t=0; it is silent and does not alter playback. */
    fun preloadSwipePreview(targetIndex: Int) {
        if (targetIndex !in queue?.items.orEmpty().indices) return
        if (_swipePreviewFrames.value.containsKey(targetIndex) || firstFrameJobs[targetIndex]?.isActive == true) return
        val preparedItem = _state.value.items.getOrNull(targetIndex) ?: return
        firstFrameJobs[targetIndex] = viewModelScope.launch {
            playbackFirstFrameExtractor.extract(preparedItem)?.let { frame ->
                _swipePreviewFrames.update { it + (targetIndex to frame) }
            }
        }
    }

    fun shouldKeepPlayingAfterLeaving(): Boolean = settingsService.backgroundPlaybackEnabled

    fun requestOnlineStatus(videoId: Long) {
        if (videoId <= 0L) return
        viewModelScope.launch {
            onlineVideoStatusService.checkIfNeeded(videoId)?.let { _onlineStatus.value = it }
        }
    }

    fun observeConversion(videoId: Long) {
        conversionObservation?.cancel()
        _conversionProgress.value = null
        if (videoId <= 0L) return
        conversionObservation = viewModelScope.launch {
            conversionJobRegistry.progressFor(videoId).collect { _conversionProgress.value = it }
        }
    }

    fun convertToMp4(item: PlaybackQueueItem) {
        if (!item.canConvertToMp4 || conversionJobRegistry.isRunning(item.id)) return
        _conversionProgress.value = ConversionProgress(
            videoId = item.id,
            videoName = item.title,
            progress = 0f,
            status = ConversionStatus.PENDING,
            statusMessage = "正在启动后台转换…"
        )
        try {
            ConversionForegroundService.start(
                context,
                ConversionForegroundService.ConversionRequest(
                    videoId = item.id,
                    videoPath = item.videoPath,
                    audioPath = item.audioPath.orEmpty(),
                    outputDir = settingsService.outputPath ?: videoConverterService.getDefaultOutputPath(),
                    outputFileName = item.title
                )
            )
        } catch (error: Exception) {
            _conversionProgress.value = ConversionProgress(
                videoId = item.id,
                videoName = item.title,
                progress = 0f,
                status = ConversionStatus.FAILED,
                errorMessage = error.message,
                statusMessage = "无法启动后台转换"
            )
        }
    }

    private fun resolve(forceTemporaryCopies: Boolean) {
        val currentQueue = queue ?: return
        viewModelScope.launch {
            _state.update { it.copy(isPreparing = true, errorMessage = null) }
            playbackMediaResolver.prepare(currentQueue.items, forceTemporaryCopies)
                .onSuccess { items ->
                    _state.value = PlayerPreparationState(
                        items = items,
                        usesShizukuDataSource = items.any { it.usesShizukuDataSource }
                    )
                }
                .onFailure { error ->
                    _state.value = PlayerPreparationState(errorMessage = error.message ?: "无法准备播放文件")
                }
        }
    }
}
