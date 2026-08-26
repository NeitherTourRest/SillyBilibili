package com.example.sillybilibili.service

import android.content.Context
import com.example.sillybilibili.domain.model.ConversionProgress
import com.example.sillybilibili.domain.model.ConversionStatus
import com.example.sillybilibili.domain.model.Video
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class BatchEnqueueResult(
    val queued: Int,
    val skippedAlreadyRunning: Int
)

/**
 * Process-wide conversion queue shared by every library screen. It starts one foreground-service
 * conversion at a time and advances only after that item's terminal progress is reported.
 */
@Singleton
class ConversionBatchCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jobRegistry: ConversionJobRegistry,
    private val settingsService: SettingsService,
    private val converter: VideoConverterService
) {
    private val lock = Any()
    private val queue = SerialConversionBatchQueue()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(queue.snapshot)
    val state: StateFlow<ConversionBatchSnapshot> = _state.asStateFlow()

    init {
        scope.launch {
            jobRegistry.jobs.collect { jobs ->
                synchronized(lock) {
                    // The queue itself confirms the id, so an unrelated terminal job is ignored.
                    queue.snapshot.currentVideoId?.let { id ->
                        jobs[id]?.status?.let { queue.finish(id, it) }
                    }
                    drainLocked(jobs.values)
                    publishLocked()
                }
            }
        }
    }

    fun enqueue(videos: List<Video>): BatchEnqueueResult = synchronized(lock) {
        val distinct = videos.distinctBy { it.id }
        val (running, candidates) = distinct.partition { jobRegistry.isRunning(it.id) }
        val added = queue.enqueue(candidates.map { QueuedConversion(it.id, it.title) })
        queuedVideos = (queuedVideos + candidates.associateBy { it.id })
        drainLocked(jobRegistry.jobs.value.values)
        publishLocked()
        BatchEnqueueResult(queued = added, skippedAlreadyRunning = running.size)
    }

    /** Source details are retained only for the in-memory batch and released when the batch ends. */
    private var queuedVideos: Map<Long, Video> = emptyMap()

    private fun drainLocked(existingJobs: Collection<ConversionProgress>) {
        if (queue.snapshot.currentVideoId != null) return
        if (existingJobs.any { it.status == ConversionStatus.PENDING || it.status == ConversionStatus.CONVERTING }) return
        val next = queue.takeNext() ?: run {
            if (!queue.snapshot.isRunning) queuedVideos = emptyMap()
            return
        }
        val video = queuedVideos[next.videoId]
        if (video == null) {
            queue.finish(next.videoId, ConversionStatus.FAILED)
            drainLocked(existingJobs)
            return
        }
        val pending = ConversionProgress(
            videoId = video.id,
            videoName = video.title,
            progress = 0f,
            status = ConversionStatus.PENDING,
            statusMessage = "正在等待后台转换…"
        )
        if (!jobRegistry.begin(pending)) {
            queue.finish(video.id, ConversionStatus.FAILED)
            drainLocked(existingJobs)
            return
        }
        try {
            ConversionForegroundService.start(
                context,
                ConversionForegroundService.ConversionRequest(
                    videoId = video.id,
                    videoPath = video.path,
                    audioPath = video.audioPath,
                    outputDir = settingsService.outputPath ?: converter.getDefaultOutputPath(),
                    outputFileName = video.title,
                    isPreRegistered = true
                )
            )
        } catch (error: Exception) {
            jobRegistry.update(
                pending.copy(
                    status = ConversionStatus.FAILED,
                    errorMessage = error.message ?: "无法启动后台转换",
                    statusMessage = "无法启动后台转换"
                )
            )
        }
    }

    private fun publishLocked() {
        _state.value = queue.snapshot
    }
}
