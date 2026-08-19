package com.example.sillybilibili.service

import android.content.Context
import android.net.Uri
import com.example.sillybilibili.ui.pages.player.PlaybackQueueItem
import com.example.sillybilibili.util.SafFileHelper
import com.example.sillybilibili.util.ShizukuFileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class PreparedPlaybackItem(
    val id: Long,
    val title: String,
    val videoUri: Uri,
    val audioUri: Uri? = null,
    /** True only for the no-copy Shizuku data source path. */
    val usesShizukuDataSource: Boolean = false
)

/**
 * Turns database paths into sources ExoPlayer can open. m4s pairs stay as separate tracks; no
 * MP4 is generated. Shizuku sources use random reads and automatically have a copy fallback.
 */
@Singleton
class PlaybackMediaResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuHelper: ShizukuFileHelper,
    private val safFileHelper: SafFileHelper
) {
    suspend fun prepare(
        items: List<PlaybackQueueItem>,
        forceTemporaryCopies: Boolean = false
    ): Result<List<PreparedPlaybackItem>> = withContext(Dispatchers.IO) {
        runCatching { items.map { prepareItem(it, forceTemporaryCopies) } }
    }

    private fun prepareItem(item: PlaybackQueueItem, forceTemporaryCopies: Boolean): PreparedPlaybackItem {
        val video = resolve(item.videoPath, item.id, "video", forceTemporaryCopies)
        // 音频轨缺失或为空时降级为只播视频，而不是整个播放失败。
        val audio = item.audioPath?.takeIf { it.isNotBlank() }?.let {
            try {
                resolve(it, item.id, "audio", forceTemporaryCopies)
            } catch (_: IllegalStateException) {
                null
            }
        }
        return PreparedPlaybackItem(
            id = item.id,
            title = item.title,
            videoUri = video.uri,
            audioUri = audio?.uri,
            usesShizukuDataSource = video.isShizukuDataSource || audio?.isShizukuDataSource == true
        )
    }

    private data class ResolvedPath(val uri: Uri, val isShizukuDataSource: Boolean)

    private fun resolve(path: String, videoId: Long, track: String, forceTemporaryCopies: Boolean): ResolvedPath {
        val trackLabel = if (track == "audio") "音频" else "视频"
        if (path.startsWith("content://")) {
            val parentUri = Uri.parse(path)
            val childName = if (track == "audio") "audio.m4s" else "video.m4s"
            val sourceUri = safFileHelper.findChild(parentUri, childName) ?: parentUri
            if (safFileHelper.fileLength(sourceUri) <= 0L) {
                throw IllegalStateException("缓存${trackLabel}文件缺失或为空")
            }
            return ResolvedPath(sourceUri, false)
        }

        val file = File(path)
        if (file.isFile) {
            if (file.length() <= 0L) throw IllegalStateException("缓存${trackLabel}文件为空")
            return ResolvedPath(Uri.fromFile(file), false)
        }
        if (!shizukuHelper.isShizukuAvailable()) {
            throw IllegalStateException("无法读取${trackLabel}缓存：请授权 Shizuku 或重新选择目录")
        }
        // 直读前确认源文件存在且非空，避免播放器读不到数据时报底层 I/O 错误。
        if (shizukuHelper.fileLength(path, useShizuku = true) <= 0L) {
            throw IllegalStateException("缓存${trackLabel}文件已被清除或为空，请重新扫描")
        }

        if (!forceTemporaryCopies) {
            return ResolvedPath(Uri.Builder().scheme("shizuku").path(path).build(), true)
        }

        val tempDir = File(context.cacheDir, "playback-media")
        if (!tempDir.exists() && !tempDir.mkdirs()) throw IllegalStateException("无法创建播放临时目录")
        val key = Integer.toHexString(path.hashCode())
        val tempFile = File(tempDir, "${videoId}_${track}_$key.m4s")
        if (!tempFile.isFile && !shizukuHelper.copyFileChunked(path, tempFile.absolutePath, useShizuku = true)) {
            tempFile.delete()
            throw IllegalStateException("无法准备${if (track == "audio") "音频" else "视频"}缓存文件")
        }
        return ResolvedPath(Uri.fromFile(tempFile), false)
    }
}
