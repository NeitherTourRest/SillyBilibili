package com.example.sillybilibili.service

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.util.SafFileHelper
import com.example.sillybilibili.util.ShizukuFileHelper
import com.example.sillybilibili.util.ThumbnailHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File-manager-style thumbnail generation: use cover.jpg when it exists; otherwise obtain one
 * representative frame from the video file and keep only that small JPEG in app cache.
 */
@Singleton
class CoverCacheService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuHelper: ShizukuFileHelper,
    private val safFileHelper: SafFileHelper,
    private val thumbnailHelper: ThumbnailHelper
) {
    suspend fun cacheCover(video: Video): String? = withContext(Dispatchers.IO) {
        video.coverPath?.let { cachedPath ->
            val cachedFile = File(cachedPath)
            if (isReadableImage(cachedFile)) return@withContext cachedPath
            cachedFile.delete()
        }

        val cacheDir = File(context.cacheDir, "covers")
        if (!cacheDir.exists() && !cacheDir.mkdirs()) return@withContext null
        val cacheFile = File(cacheDir, "${video.id}_${video.cid}.jpg")
        if (isReadableImage(cacheFile)) return@withContext cacheFile.absolutePath
        cacheFile.delete()

        val copied = copyOriginalCover(video.coverSourcePath, cacheFile)

        if (copied) cacheFile.absolutePath
        else if (extractPreviewFrame(video, cacheFile) && isReadableImage(cacheFile)) cacheFile.absolutePath
        else {
            cacheFile.delete()
            null
        }
    }

    private fun copyOriginalCover(sourcePath: String?, cacheFile: File): Boolean {
        if (sourcePath == null) return false
        return try {
            if (sourcePath.startsWith("content://")) {
                safFileHelper.readBinaryFile(Uri.parse(sourcePath))?.takeIf { it.isNotEmpty() }?.let {
                    cacheFile.writeBytes(it)
                    isReadableImage(cacheFile)
                } ?: false
            } else {
                val source = File(sourcePath)
                when {
                    source.isFile -> {
                        source.copyTo(cacheFile, overwrite = true)
                        isReadableImage(cacheFile)
                    }
                    shizukuHelper.isShizukuAvailable() -> {
                        shizukuHelper.copyFileChunked(sourcePath, cacheFile.absolutePath, useShizuku = true) &&
                            isReadableImage(cacheFile)
                    }
                    else -> false
                }
            }
        } catch (_: Exception) {
            cacheFile.delete()
            false
        }
    }

    private fun extractPreviewFrame(video: Video, cacheFile: File): Boolean {
        // A converted MP4 is already readable by the app. Otherwise use the scanned video.m4s.
        val source = video.exportedPath?.takeIf { File(it).isFile } ?: video.path
        return if (source.startsWith("content://")) {
            val typeTagUri = Uri.parse(source)
            val videoFileUri = safFileHelper.findChild(typeTagUri, "video.m4s") ?: typeTagUri
            thumbnailHelper.extractFrame(videoFileUri, cacheFile)
        } else {
            extractFilePreview(source, video, cacheFile)
        }
    }

    private fun extractFilePreview(sourcePath: String, video: Video, cacheFile: File): Boolean {
        val source = File(sourcePath)
        if (source.isFile) return thumbnailHelper.extractFrame(sourcePath, cacheFile)
        if (!shizukuHelper.isShizukuAvailable()) return false

        // MediaMetadataRetriever runs inside this app and cannot open another app's Android/data
        // directory. Copy only while making the thumbnail, then remove the large temporary file.
        val tempDir = File(context.cacheDir, "thumbnail-source")
        val tempVideo = File(tempDir, "${video.id}_${video.cid}.m4s")
        if (!tempDir.exists() && !tempDir.mkdirs()) return false
        tempVideo.delete()
        return try {
            shizukuHelper.copyFileChunked(sourcePath, tempVideo.absolutePath, useShizuku = true) &&
                thumbnailHelper.extractFrame(tempVideo.absolutePath, cacheFile)
        } finally {
            tempVideo.delete()
        }
    }

    private fun isReadableImage(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth > 0 && options.outHeight > 0
    }
}
