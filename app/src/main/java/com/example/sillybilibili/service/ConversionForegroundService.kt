package com.example.sillybilibili.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.sillybilibili.R
import com.example.sillybilibili.domain.model.ConversionProgress
import com.example.sillybilibili.domain.model.ConversionStatus
import com.example.sillybilibili.domain.repository.VideoRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Runs MP4 remuxing independently from the detail page and exposes a foreground notification. */
@AndroidEntryPoint
class ConversionForegroundService : Service() {
    @Inject lateinit var converter: VideoConverterService
    @Inject lateinit var jobRegistry: ConversionJobRegistry
    @Inject lateinit var videoRepository: VideoRepository
    @Inject lateinit var coverCacheService: CoverCacheService

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        val request = ConversionRequest.from(intent) ?: return START_NOT_STICKY
        val pending = ConversionProgress(
            videoId = request.videoId,
            videoName = request.outputFileName,
            progress = 0f,
            status = ConversionStatus.PENDING,
            statusMessage = "正在准备转换文件…"
        )
        if (request.isPreRegistered) {
            if (!jobRegistry.isRunning(request.videoId)) return START_NOT_STICKY
            jobRegistry.update(pending)
        } else if (!jobRegistry.begin(pending)) {
            return START_NOT_STICKY
        }

        showForegroundNotification(pending)
        serviceScope.launch {
            converter.convertToMp4(
                videoPath = request.videoPath,
                audioPath = request.audioPath,
                outputDir = request.outputDir,
                outputFileName = request.outputFileName,
                videoId = request.videoId
            ).collect { progress ->
                jobRegistry.update(progress)
                showForegroundNotification(progress)
                if (progress.status == ConversionStatus.COMPLETED && progress.outputPath != null) {
                    persistCompletedConversion(request.videoId, progress.outputPath)
                }
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private suspend fun persistCompletedConversion(videoId: Long, outputPath: String) {
        val video = videoRepository.getVideoById(videoId) ?: return
        val outputFile = java.io.File(outputPath)
        var updated = video.copy(
            exportedPath = outputPath,
            exportedSize = outputFile.length(),
            exportedLastModified = outputFile.lastModified()
        )
        if (updated.coverPath == null) {
            coverCacheService.cacheCover(updated)?.let { cachedPath ->
                updated = updated.copy(coverPath = cachedPath)
            }
        }
        videoRepository.updateVideo(updated)
    }

    private fun showForegroundNotification(progress: ConversionProgress) {
        ensureNotificationChannel()
        val percent = (progress.progress.coerceIn(0f, 1f) * 100).toInt()
        val message = progress.statusMessage ?: when (progress.status) {
            ConversionStatus.PENDING -> "正在准备…"
            ConversionStatus.CONVERTING -> "正在写入 MP4：$percent%"
            ConversionStatus.COMPLETED -> "转换完成"
            ConversionStatus.FAILED -> progress.errorMessage ?: "转换失败"
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("正在转换为 MP4")
            .setContentText(message)
            .setOnlyAlertOnce(true)
            .setOngoing(progress.status == ConversionStatus.PENDING || progress.status == ConversionStatus.CONVERTING)
            .setProgress(100, percent, progress.status == ConversionStatus.PENDING)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "视频转换", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "video_conversion"
        private const val NOTIFICATION_ID = 2101
        private const val ACTION_START = "com.example.sillybilibili.action.START_CONVERSION"
        private const val EXTRA_VIDEO_ID = "video_id"
        private const val EXTRA_VIDEO_PATH = "video_path"
        private const val EXTRA_AUDIO_PATH = "audio_path"
        private const val EXTRA_OUTPUT_DIR = "output_dir"
        private const val EXTRA_OUTPUT_NAME = "output_name"
        private const val EXTRA_PRE_REGISTERED = "pre_registered"

        fun start(context: Context, request: ConversionRequest) {
            val intent = Intent(context, ConversionForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_VIDEO_ID, request.videoId)
                .putExtra(EXTRA_VIDEO_PATH, request.videoPath)
                .putExtra(EXTRA_AUDIO_PATH, request.audioPath)
                .putExtra(EXTRA_OUTPUT_DIR, request.outputDir)
                .putExtra(EXTRA_OUTPUT_NAME, request.outputFileName)
                .putExtra(EXTRA_PRE_REGISTERED, request.isPreRegistered)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    data class ConversionRequest(
        val videoId: Long,
        val videoPath: String,
        val audioPath: String,
        val outputDir: String,
        val outputFileName: String,
        /** The app-wide batch coordinator has already reserved this video in [ConversionJobRegistry]. */
        val isPreRegistered: Boolean = false
    ) {
        companion object {
            fun from(intent: Intent): ConversionRequest? {
                val id = intent.getLongExtra(EXTRA_VIDEO_ID, 0L)
                val videoPath = intent.getStringExtra(EXTRA_VIDEO_PATH)
                val audioPath = intent.getStringExtra(EXTRA_AUDIO_PATH)
                val outputDir = intent.getStringExtra(EXTRA_OUTPUT_DIR)
                val outputName = intent.getStringExtra(EXTRA_OUTPUT_NAME)
                val isPreRegistered = intent.getBooleanExtra(EXTRA_PRE_REGISTERED, false)
                return if (id > 0 && videoPath != null && audioPath != null && outputDir != null && outputName != null) {
                    ConversionRequest(id, videoPath, audioPath, outputDir, outputName, isPreRegistered)
                } else null
            }
        }
    }
}
