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

/** B 站缓存中出现过的封面文件名（按优先级排列）。 */
internal val COVER_FILE_NAMES = listOf(
    "cover.jpg", "cover.webp", "cover.png", "cover_0.jpg",
    "index.webp", "index.jpg", "index.png"
)

internal val COVER_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

/** 从目录条目中挑选封面文件：优先常见文件名，否则任意图片文件；找不到返回 null。 */
internal fun pickCoverFileName(entries: List<String>): String? {
    COVER_FILE_NAMES.firstOrNull { it in entries }?.let { return it }
    return entries.firstOrNull { name ->
        name.substringAfterLast('.', "").lowercase() in COVER_IMAGE_EXTENSIONS
    }
}

/**
 * Shizuku 场景提取视频首帧时只复制文件头部的字节数。
 * B 站缓存的 fMP4（moof/mdat 循环）第一个关键帧位于文件开头几 MB 内，
 * 截断副本足够解码首帧，避免复制整个视频（可能数百 MB）。
 */
internal const val COVER_PREFIX_BYTES_FOR_FRAME = 16L * 1024 * 1024

/**
 * File-manager-style thumbnail generation: use the cached cover when it exists; otherwise find
 * the real cover file in the cid directory (the filename is not always cover.jpg), and if there
 * is none, obtain one representative frame from the video file and keep only that small JPEG
 * in app cache.
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
        val cacheFile = File(cacheDir, "${'$'}{video.id}_${'$'}{video.cid}.jpg")
        if (isReadableImage(cacheFile)) return@withContext cacheFile.absolutePath
        cacheFile.delete()

        val copied = copyOriginalCover(video, cacheFile)

        if (copied) cacheFile.absolutePath
        else if (extractPreviewFrame(video, cacheFile) && isReadableImage(cacheFile)) cacheFile.absolutePath
        else {
            cacheFile.delete()
            null
        }
    }

    /**
     * 先解析出真实存在的封面来源（文件名不一定是 cover.jpg），再复制到缓存。
     * 解析失败说明缓存目录里没有封面文件，调用方会回退到视频首帧。
     */
    private fun copyOriginalCover(video: Video, cacheFile: File): Boolean {
        val sourcePath = resolveCoverSource(video) ?: return false
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

    /**
     * 扫描时 coverSourcePath 指向 "{cidDir}/cover.jpg"，但实际封面文件可能叫
     * cover.webp / index.webp 等，也可能根本没有封面文件。这里在 cid 目录里重新发现：
     * 直接路径用目录列举，SAF 用父目录 document URI + 候选文件名。
     */
    private fun resolveCoverSource(video: Video): String? {
        val sourcePath = video.coverSourcePath ?: return null
        return if (sourcePath.startsWith("content://")) {
            resolveSafCoverSource(sourcePath)
        } else {
            resolveFileCoverSource(sourcePath)
        }
    }

    private fun resolveFileCoverSource(sourcePath: String): String? {
        val sourceFile = File(sourcePath)
        if (sourceFile.isFile) return sourcePath
        val cidDir = sourceFile.parentFile ?: return null
        val entries = shizukuHelper.listDirectoryEntries(cidDir.absolutePath, useShizuku = true)
        val found = pickCoverFileName(entries) ?: return null
        return File(cidDir, found).absolutePath
    }

    private fun resolveSafCoverSource(sourceUri: String): String? {
        val uri = Uri.parse(sourceUri)
        if (safFileHelper.exists(uri)) return sourceUri
        val parentUri = safFileHelper.parentDocumentUri(uri) ?: return null
        COVER_FILE_NAMES.forEach { name ->
            safFileHelper.findChild(parentUri, name)?.let { return it.toString() }
        }
        // 兜底：父目录里任意图片文件
        val entries = safFileHelper.listEntries(parentUri)
        val found = pickCoverFileName(entries) ?: return null
        return safFileHelper.findChild(parentUri, found)?.toString()
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
        // directory. Copy only while making the thumbnail, then remove the temporary file.
        val tempDir = File(context.cacheDir, "thumbnail-source")
        val tempVideo = File(tempDir, "${'$'}{video.id}_${'$'}{video.cid}.m4s")
        if (!tempDir.exists() && !tempDir.mkdirs()) return false
        return try {
            // 1) 只复制文件头（fMP4 首个关键帧在开头），几乎瞬间完成
            tempVideo.delete()
            val prefixOk = shizukuHelper.copyFileChunked(
                sourcePath, tempVideo.absolutePath, useShizuku = true,
                maxBytes = COVER_PREFIX_BYTES_FOR_FRAME
            ) && thumbnailHelper.extractFrame(tempVideo.absolutePath, cacheFile)
            if (prefixOk && isReadableImage(cacheFile)) {
                true
            } else {
                // 2) 回退：整文件复制再抽帧（兼容 moov 在尾部的非分片文件）
                cacheFile.delete()
                tempVideo.delete()
                shizukuHelper.copyFileChunked(sourcePath, tempVideo.absolutePath, useShizuku = true) &&
                    thumbnailHelper.extractFrame(tempVideo.absolutePath, cacheFile)
            }
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
