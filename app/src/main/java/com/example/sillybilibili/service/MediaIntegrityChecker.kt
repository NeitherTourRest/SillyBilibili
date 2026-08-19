package com.example.sillybilibili.service

import android.net.Uri
import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.util.SafFileHelper
import com.example.sillybilibili.util.ShizukuFileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class MediaIntegrityStatus {
    /** 视频与音频（如有）文件都存在且非空。 */
    OK,
    VIDEO_MISSING,
    AUDIO_MISSING,
    BOTH_MISSING,
    /** 目录被隔离且无 Shizuku，无法确认文件是否存在。 */
    UNKNOWN
}

/**
 * 纯判定：根据两个文件的大小（-1 = 无法访问/无法判定）得出完整性结论。
 * 独立出来便于 JVM 单元测试。
 */
internal fun classifyIntegrity(
    videoSize: Long,
    audioSize: Long,
    hasAudioTrack: Boolean
): MediaIntegrityStatus = when {
    videoSize < 0L -> MediaIntegrityStatus.UNKNOWN
    videoSize <= 0L -> if (hasAudioTrack && audioSize < 0L) MediaIntegrityStatus.UNKNOWN
        else if (hasAudioTrack && audioSize <= 0L) MediaIntegrityStatus.BOTH_MISSING
        else MediaIntegrityStatus.VIDEO_MISSING
    !hasAudioTrack -> MediaIntegrityStatus.OK
    audioSize < 0L -> MediaIntegrityStatus.UNKNOWN
    audioSize <= 0L -> MediaIntegrityStatus.AUDIO_MISSING
    else -> MediaIntegrityStatus.OK
}

/** 检查视频的 m4s 媒体文件是否仍然存在且非空（直接/SAF/Shizuku 三种访问方式）。 */
@Singleton
class MediaIntegrityChecker @Inject constructor(
    private val shizukuHelper: ShizukuFileHelper,
    private val safFileHelper: SafFileHelper
) {
    data class CheckResult(val videoId: Long, val status: MediaIntegrityStatus)

    suspend fun check(video: Video): CheckResult = withContext(Dispatchers.IO) {
        checkInLine(video)
    }

    suspend fun checkAll(videos: List<Video>): List<CheckResult> = withContext(Dispatchers.IO) {
        videos.map(::checkInLine)
    }

    private fun checkInLine(video: Video): CheckResult = CheckResult(
        video.id,
        classifyIntegrity(
            videoSize = sizeOf(video.path),
            audioSize = if (video.audioPath.isBlank()) 0L else sizeOf(video.audioPath),
            hasAudioTrack = video.audioPath.isNotBlank()
        )
    )

    private fun sizeOf(path: String): Long {
        if (path.isBlank()) return 0L
        return if (path.startsWith("content://")) {
            // SAF：长度 0 即视为缺失
            safFileHelper.fileLength(Uri.parse(path))
        } else {
            val file = File(path)
            if (file.isFile) return file.length()
            // Android/data 隔离目录：只有 Shizuku 能确认存在性；否则无法判定
            if (!shizukuHelper.isShizukuAvailable()) return -1L
            if (!shizukuHelper.fileExists(path, useShizuku = true)) return 0L
            shizukuHelper.fileLength(path, useShizuku = true)
        }
    }
}
