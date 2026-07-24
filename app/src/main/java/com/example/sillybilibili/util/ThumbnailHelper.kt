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

    // Extract frame at timeUs (default 1s) and save as JPEG in app cache.
    fun extractFrame(videoPath: String, outputFile: File, timeUs: Long = 1_000_000L): Boolean {
        if (outputFile.exists()) return true
        outputFile.parentFile?.mkdirs()
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(videoPath)
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
                saveJpeg(frame, outputFile)
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
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
                saveJpeg(frame, outputFile)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun saveJpeg(bitmap: Bitmap?, outputFile: File): Boolean {
        if (bitmap == null) return false
        return try {
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            bitmap.recycle()
        }
    }

    // Construct a child URI under a SAF tree URI.
    fun resolveChildUri(treeUri: Uri, childName: String): Uri? =
        android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, childName)
}
