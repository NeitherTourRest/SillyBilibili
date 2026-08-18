package com.example.sillybilibili.service

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.sillybilibili.util.ShizukuFileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decodes a still at timestamp zero for the adjacent short-video page.  It deliberately uses a
 * metadata decoder rather than a second ExoPlayer so a held drag does not create another audio
 * session or compete with the video currently playing.
 */
@Singleton
class PlaybackFirstFrameExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuHelper: ShizukuFileHelper,
    private val readAheadCache: ShizukuReadAheadCache
) {
    suspend fun extract(item: PreparedPlaybackItem): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            MediaMetadataRetriever().use { retriever ->
                if (item.videoUri.scheme == SHIZUKU_SCHEME) {
                    retriever.setDataSource(ShizukuPreviewDataSource(item.videoUri, shizukuHelper, readAheadCache))
                } else {
                    retriever.setDataSource(context, item.videoUri)
                }
                retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.downscaleForPreview()
            }
        }.getOrNull()
    }

    private fun Bitmap.downscaleForPreview(): Bitmap {
        val longestSide = max(width, height)
        if (longestSide <= MAX_PREVIEW_EDGE_PX) return this
        val scale = MAX_PREVIEW_EDGE_PX.toFloat() / longestSide
        return Bitmap.createScaledBitmap(
            this,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            true
        ).also { scaled -> if (scaled !== this) recycle() }
    }

    private class ShizukuPreviewDataSource(
        uri: Uri,
        private val shizukuHelper: ShizukuFileHelper,
        private val readAheadCache: ShizukuReadAheadCache
    ) : MediaDataSource() {
        private val path = uri.path.orEmpty()
        private val length by lazy {
            readAheadCache.fileLength(path)
                ?: shizukuHelper.fileLength(path, useShizuku = true).also {
                    readAheadCache.storeFileLength(path, it)
                }
        }

        override fun getSize(): Long = length

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position < 0L || size <= 0 || position >= length) return -1
            val requestSize = minOf(size.toLong(), length - position, MAX_RANGE_BYTES.toLong()).toInt()
            val bytes = readAheadCache.read(path, position, requestSize) { readOffset, readSize ->
                shizukuHelper.readFileRange(path, readOffset, readSize, useShizuku = true)
            } ?: return -1
            if (bytes.isEmpty()) return -1
            val copied = minOf(bytes.size, requestSize)
            bytes.copyInto(buffer, destinationOffset = offset, endIndex = copied)
            return copied
        }

        override fun close() = Unit
    }

    private companion object {
        const val SHIZUKU_SCHEME = "shizuku"
        const val MAX_PREVIEW_EDGE_PX = 1_280
        const val MAX_RANGE_BYTES = 256 * 1024
    }
}
