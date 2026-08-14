package com.example.sillybilibili.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts a preview frame from a video to use as a fallback cover.
 * Works with both file paths and SAF content:// URIs.
 */
@Singleton
class ThumbnailHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // Cached Bilibili segments can begin after a sparse keyframe, so retry a few positions.
    fun extractFrame(videoPath: String, outputFile: File, timeUs: Long = 1_000_000L): Boolean {
        if (outputFile.exists()) return true
        outputFile.parentFile?.mkdirs()
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(videoPath)
                saveJpeg(firstAvailableFrame(retriever, timeUs), outputFile)
            }
        } catch (e: Exception) {
            false
        }
    }

    // Extract frame from a content:// URI (SAF).
    fun extractFrame(videoUri: Uri, outputFile: File, timeUs: Long = 1_000_000L): Boolean {
        if (outputFile.exists()) return true
        outputFile.parentFile?.mkdirs()
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, videoUri)
                saveJpeg(firstAvailableFrame(retriever, timeUs), outputFile)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun saveJpeg(bitmap: Bitmap?, outputFile: File): Boolean {
        if (bitmap == null) return false
        val maxEdge = 640
        val scale = maxOf(bitmap.width, bitmap.height).toFloat() / maxEdge
        val preview = if (scale > 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width / scale).toInt(), (bitmap.height / scale).toInt(), true)
        } else bitmap
        return try {
            FileOutputStream(outputFile).use { out ->
                preview.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            if (preview !== bitmap) preview.recycle()
            bitmap.recycle()
        }
    }

    private fun firstAvailableFrame(retriever: MediaMetadataRetriever, preferredTimeUs: Long): Bitmap? {
        val durationUs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()?.times(1_000L)
        val candidates = buildList {
            add(preferredTimeUs)
            add(0L)
            if (durationUs != null && durationUs > 0L) {
                add(durationUs / 10L)
                add(durationUs / 3L)
            }
        }.distinct().filter { durationUs == null || it <= durationUs }
        return candidates.firstNotNullOfOrNull { timeUs ->
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } ?: retriever.frameAtTime
    }

    // Construct a child URI under a SAF tree URI.
    fun resolveChildUri(treeUri: Uri, childName: String): Uri? =
        android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, childName)
}
