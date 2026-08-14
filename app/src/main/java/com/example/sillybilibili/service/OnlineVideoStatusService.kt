package com.example.sillybilibili.service

import com.example.sillybilibili.domain.model.OnlineVideoStatus
import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.domain.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Performs a lightweight, conservative lookup of the original Bilibili video page. */
interface OnlineVideoStatusRemoteDataSource {
    suspend fun fetchStatus(avid: Long): OnlineVideoStatus
}

data class OnlineStatusRefreshResult(
    val videoCount: Int,
    val requestCount: Int,
    val onlineCount: Int,
    val unavailableCount: Int,
    val unverifiableCount: Int
)

@Singleton
class BilibiliOnlineVideoStatusRemoteDataSource @Inject constructor() : OnlineVideoStatusRemoteDataSource {
    override suspend fun fetchStatus(avid: Long): OnlineVideoStatus = withContext(Dispatchers.IO) {
        try {
            val connection = (URL("https://api.bilibili.com/x/web-interface/view?aid=$avid").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", "SillyBilibili/1.0")
                setRequestProperty("Accept", "application/json")
            }
            try {
                val responseCode = connection.responseCode
                val payload = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { reader -> reader.readText() }
                    .orEmpty()
                OnlineVideoStatusClassifier.fromHttpResponse(responseCode, payload)
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            OnlineVideoStatus.UNVERIFIABLE
        }
    }
}

/** Kept dependency-free so it is covered by local JVM tests. */
object OnlineVideoStatusClassifier {
    private val codePattern = Regex("\\\"code\\\"\\s*:\\s*(-?\\d+)")

    fun fromPayload(payload: String): OnlineVideoStatus {
        val code = codePattern.find(payload)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: return OnlineVideoStatus.UNVERIFIABLE
        return when (code) {
            0 -> OnlineVideoStatus.ONLINE
            -404 -> OnlineVideoStatus.UNAVAILABLE
            else -> OnlineVideoStatus.UNVERIFIABLE
        }
    }

    fun fromHttpResponse(httpCode: Int, payload: String): OnlineVideoStatus = when {
        payload.isNotBlank() -> fromPayload(payload)
        httpCode == HttpURLConnection.HTTP_NOT_FOUND -> OnlineVideoStatus.UNAVAILABLE
        else -> OnlineVideoStatus.UNVERIFIABLE
    }
}

@Singleton
class OnlineVideoStatusService @Inject constructor(
    private val videoRepository: VideoRepository,
    private val remoteDataSource: OnlineVideoStatusRemoteDataSource
) {
    private val videoLocks = ConcurrentHashMap<Long, Mutex>()

    suspend fun checkIfNeeded(videoId: Long): OnlineVideoStatus? {
        val video = videoRepository.getVideoById(videoId) ?: return null
        return checkIfNeeded(video)
    }

    suspend fun checkIfNeeded(video: Video): OnlineVideoStatus {
        if (video.id <= 0L) return video.onlineStatus
        val lock = videoLocks.getOrPut(video.id) { Mutex() }
        return lock.withLock {
            val current = videoRepository.getVideoById(video.id) ?: return@withLock video.onlineStatus
            if (!shouldCheck(current, System.currentTimeMillis())) return@withLock current.onlineStatus

            val result = remoteDataSource.fetchStatus(current.avid)
            videoRepository.updateVideo(
                current.copy(onlineStatus = result, onlineCheckedAt = System.currentTimeMillis())
            )
            result
        }
    }

    /**
     * Explicit user-triggered refresh. Unlike [checkIfNeeded], this deliberately ignores the
     * normal cache window and groups split episodes under the same AV into one request.
     */
    suspend fun forceRefreshAll(): OnlineStatusRefreshResult {
        val scannedVideos = videoRepository.getAllVideos().first().filter { it.sourceAvailable }
        if (scannedVideos.isEmpty()) return OnlineStatusRefreshResult(0, 0, 0, 0, 0)

        var onlineCount = 0
        var unavailableCount = 0
        var unverifiableCount = 0
        val checkedAt = System.currentTimeMillis()
        val groups = scannedVideos.groupBy { it.avid }

        groups.values.forEach { videosWithSameAv ->
            val result = remoteDataSource.fetchStatus(videosWithSameAv.first().avid)
            when (result) {
                OnlineVideoStatus.ONLINE -> onlineCount += videosWithSameAv.size
                OnlineVideoStatus.UNAVAILABLE -> unavailableCount += videosWithSameAv.size
                OnlineVideoStatus.UNVERIFIABLE, OnlineVideoStatus.UNCHECKED -> unverifiableCount += videosWithSameAv.size
            }
            videosWithSameAv.forEach { video ->
                videoRepository.updateVideo(video.copy(onlineStatus = result, onlineCheckedAt = checkedAt))
            }
        }
        return OnlineStatusRefreshResult(
            videoCount = scannedVideos.size,
            requestCount = groups.size,
            onlineCount = onlineCount,
            unavailableCount = unavailableCount,
            unverifiableCount = unverifiableCount
        )
    }

    internal fun shouldCheck(video: Video, nowMs: Long): Boolean = when (video.onlineStatus) {
        OnlineVideoStatus.UNCHECKED -> true
        OnlineVideoStatus.UNVERIFIABLE -> nowMs - video.onlineCheckedAt >= RETRY_AFTER_FAILURE_MS
        OnlineVideoStatus.ONLINE, OnlineVideoStatus.UNAVAILABLE -> nowMs - video.onlineCheckedAt >= RECHECK_INTERVAL_MS
    }

    private companion object {
        const val RETRY_AFTER_FAILURE_MS = 15 * 60 * 1_000L
        const val RECHECK_INTERVAL_MS = 24 * 60 * 60 * 1_000L
    }
}
