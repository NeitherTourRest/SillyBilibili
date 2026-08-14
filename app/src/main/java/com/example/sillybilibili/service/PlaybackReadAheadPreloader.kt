package com.example.sillybilibili.service

import com.example.sillybilibili.ui.pages.player.PlaybackQueueItem
import com.example.sillybilibili.util.ShizukuFileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Warms only nearby Shizuku-backed tracks; regular files and SAF already use direct system I/O. */
@Singleton
class PlaybackReadAheadPreloader @Inject constructor(
    private val shizukuHelper: ShizukuFileHelper,
    private val readAheadCache: ShizukuReadAheadCache
) {
    suspend fun preload(items: List<PlaybackQueueItem>) = withContext(Dispatchers.IO) {
        if (items.isEmpty() || !shizukuHelper.isShizukuAvailable()) return@withContext
        items.forEach { item ->
            currentCoroutineContext().ensureActive()
            preloadPath(item.videoPath, VIDEO_PRELOAD_BYTES)
            item.audioPath?.takeIf { it.isNotBlank() }?.let { audioPath ->
                preloadPath(audioPath, AUDIO_PRELOAD_BYTES)
            }
        }
    }

    private fun preloadPath(path: String, targetBytes: Int) {
        if (path.startsWith("content://") || File(path).isFile) return
        val length = readAheadCache.fileLength(path)
            ?: shizukuHelper.fileLength(path, useShizuku = true).also {
                readAheadCache.storeFileLength(path, it)
            }
        if (length <= 0L) return
        readAheadCache.preload(path, minOf(targetBytes.toLong(), length).toInt()) { offset, size ->
            shizukuHelper.readFileRange(path, offset, size, useShizuku = true)
        }
    }

    private companion object {
        const val VIDEO_PRELOAD_BYTES = 256 * 1024
        const val AUDIO_PRELOAD_BYTES = 128 * 1024
    }
}
