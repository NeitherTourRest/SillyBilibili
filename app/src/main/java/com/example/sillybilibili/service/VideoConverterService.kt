package com.example.sillybilibili.service

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Environment
import android.os.Build
import android.util.Log
import com.example.sillybilibili.domain.model.ConversionProgress
import com.example.sillybilibili.domain.model.ConversionStatus
import com.example.sillybilibili.util.ShizukuFileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

// Remux video.m4s + audio.m4s → single .mp4

@Singleton
class VideoConverterService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuHelper: ShizukuFileHelper
) {
    companion object {
        private const val DEFAULT_OUTPUT_DIR = "SillyBilibili/Converted"
        private const val BUFFER_SIZE = 1024 * 1024
    }

    fun getDefaultOutputPath(): String =
        "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)}/$DEFAULT_OUTPUT_DIR"

    private fun ensureOutputWritable(outputDir: String): Pair<File, String?> {
        val dir = File(outputDir)
        if (!dir.exists() && !dir.mkdirs())
            return dir to "Cannot create output directory: $outputDir — check storage permission"
        val testFile = File(dir, ".silly_write_test_${System.currentTimeMillis()}")
        return try {
            testFile.createNewFile(); testFile.delete(); dir to null
        } catch (e: Exception) {
            dir to "Cannot write to output directory: ${e.message} — on Android 11+ grant 'Manage All Files'"
        }
    }

    private fun cacheFileName(originalName: String): String =
        if (originalName.endsWith(".m4s")) originalName.substringBeforeLast(".m4s") + ".mp4" else originalName

    // Four-strategy fallback to make Android/data/ files readable
    private fun ensureAccessible(path: String): String? {
        val file = File(path)
        if (file.canRead()) return path
        val cacheFile = File(context.cacheDir, "converter/${cacheFileName(file.name)}")
        cacheFile.parentFile?.mkdirs()

        if (cacheFile.exists()) {
            val sourceLen = if (shizukuHelper.isShizukuAvailable()) shizukuHelper.fileLength(path, true) else file.length()
            if (sourceLen > 0 && cacheFile.length() == sourceLen) return cacheFile.absolutePath
            cacheFile.delete()
        }

        if (shizukuHelper.isShizukuAvailable()) {
            if (shizukuHelper.makeReadable(path)) return path
            if (shizukuHelper.copyFile(path, cacheFile.absolutePath)) {
                val sourceLen = shizukuHelper.fileLength(path, true)
                if (sourceLen > 0 && cacheFile.length() == sourceLen) return cacheFile.absolutePath
                cacheFile.delete()
            }
            val canWrite = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
            if (canWrite) {
                val stagingDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ".silly_staging")
                if (stagingDir.mkdirs() || stagingDir.exists()) {
                    val stagingFile = File(stagingDir, cacheFileName(file.name))
                    try {
                        val src = shizukuHelper.escapeSingleQuote(file.absolutePath)
                        val dst = shizukuHelper.escapeSingleQuote(stagingFile.absolutePath)
                        if (shizukuHelper.execShell("cat '$src' > '$dst' && chmod 644 '$dst'") && stagingFile.canRead() && stagingFile.length() > 0) {
                            stagingFile.copyTo(cacheFile, overwrite = true)
                            val sourceLen = shizukuHelper.fileLength(path, true)
                            if (sourceLen > 0 && cacheFile.length() == sourceLen) return cacheFile.absolutePath
                            cacheFile.delete()
                        }
                    } catch (e: Exception) { Log.e("VideoConverter", "Staging redirect failed", e) }
                    finally { if (stagingFile.exists()) stagingFile.delete() }
                }
            }
        }

        if (shizukuHelper.copyFileChunked(path, cacheFile.absolutePath)) return cacheFile.absolutePath
        return null
    }

    fun convertToMp4(
        videoPath: String, audioPath: String, outputDir: String,
        outputFileName: String, videoId: Long = 0
    ): Flow<ConversionProgress> = callbackFlow {
        trySend(ConversionProgress(videoId, outputFileName, 0f, ConversionStatus.PENDING, statusMessage = "正在准备视频文件…"))
        val actualVideoPath = ensureAccessible(videoPath) ?: run {
            val reason = when {
                !shizukuHelper.isShizukuAvailable() -> "Shizuku not running — open Shizuku app first"
                !shizukuHelper.fileExists(videoPath, true) -> "Video file missing — may have been deleted by Bilibili"
                else -> "Cannot access video file — check Shizuku and permissions"
            }
            trySend(ConversionProgress(videoId, outputFileName, 0f, ConversionStatus.FAILED, errorMessage = reason, statusMessage = "准备视频文件失败"))
            close(); return@callbackFlow
        }
        val actualAudioPath = ensureAccessible(audioPath) ?: run {
            val reason = when {
                !shizukuHelper.isShizukuAvailable() -> "Shizuku not running"
                !shizukuHelper.fileExists(audioPath, true) -> "Audio file missing"
                else -> "Cannot access audio file"
            }
            trySend(ConversionProgress(videoId, outputFileName, 0f, ConversionStatus.FAILED, errorMessage = reason, statusMessage = "准备音频文件失败"))
            close(); return@callbackFlow
        }

        val (outputDirFile, outputError) = ensureOutputWritable(outputDir)
        if (outputError != null) {
            trySend(ConversionProgress(videoId, outputFileName, 0f, ConversionStatus.FAILED, errorMessage = outputError, statusMessage = "输出目录不可用"))
            close(); return@callbackFlow
        }

        val outputFile = File(outputDirFile, "$outputFileName.mp4")
        trySend(ConversionProgress(videoId, outputFileName, 0f, ConversionStatus.PENDING, statusMessage = "正在读取音视频轨道…"))

        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            videoExtractor = MediaExtractor().apply { setDataSource(actualVideoPath) }
            audioExtractor = MediaExtractor().apply { setDataSource(actualAudioPath) }

            var videoTrackIndex = -1; var audioTrackIndex = -1
            var videoFormat: MediaFormat? = null; var audioFormat: MediaFormat? = null

            for (i in 0 until videoExtractor.trackCount) {
                val fmt = videoExtractor.getTrackFormat(i)
                if ((fmt.getString(MediaFormat.KEY_MIME) ?: "").startsWith("video/")) {
                    videoTrackIndex = i; videoFormat = fmt; break
                }
            }
            for (i in 0 until audioExtractor.trackCount) {
                val fmt = audioExtractor.getTrackFormat(i)
                if ((fmt.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    audioTrackIndex = i; audioFormat = fmt; break
                }
            }

            if (videoTrackIndex == -1 || audioTrackIndex == -1) {
                trySend(ConversionProgress(videoId, outputFileName, 0f, ConversionStatus.FAILED, errorMessage = "Could not find video or audio track", statusMessage = "未找到可转换的音视频轨道"))
                close(); return@callbackFlow
            }

            videoExtractor.selectTrack(videoTrackIndex)
            audioExtractor.selectTrack(audioTrackIndex)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerVideoTrack = muxer.addTrack(videoFormat!!)
            val muxerAudioTrack = muxer.addTrack(audioFormat!!)
            muxer.start()

            trySend(ConversionProgress(videoId, outputFileName, 0f, ConversionStatus.CONVERTING, statusMessage = "正在合并音视频…"))

            val totalDuration = maxOf(videoFormat.getLong(MediaFormat.KEY_DURATION), audioFormat.getLong(MediaFormat.KEY_DURATION))

            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            val bufferInfo = MediaCodec.BufferInfo()

            var videoDone = false; var audioDone = false
            var videoBytes = 0L; var audioBytes = 0L
            var sampleCount = 0
            var lastWrittenTimeUs = 0L
            val totalFileSize = File(actualVideoPath).length() + File(actualAudioPath).length()

            while (!videoDone || !audioDone) {
                val videoTime = if (!videoDone) videoExtractor.sampleTime else Long.MAX_VALUE
                val audioTime = if (!audioDone) audioExtractor.sampleTime else Long.MAX_VALUE

                if (videoTime <= audioTime && !videoDone) {
                    buffer.clear()
                    val size = videoExtractor.readSampleData(buffer, 0)
                    if (size > 0) {
                        bufferInfo.offset = 0; bufferInfo.size = size
                        bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                        bufferInfo.flags = videoExtractor.sampleFlags
                        muxer.writeSampleData(muxerVideoTrack, buffer, bufferInfo)
                        lastWrittenTimeUs = maxOf(lastWrittenTimeUs, videoExtractor.sampleTime)
                        videoBytes += size; videoExtractor.advance()
                    } else videoDone = true
                } else if (!audioDone) {
                    buffer.clear()
                    val size = audioExtractor.readSampleData(buffer, 0)
                    if (size > 0) {
                        bufferInfo.offset = 0; bufferInfo.size = size
                        bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                        bufferInfo.flags = audioExtractor.sampleFlags
                        muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                        lastWrittenTimeUs = maxOf(lastWrittenTimeUs, audioExtractor.sampleTime)
                        audioBytes += size; audioExtractor.advance()
                    } else audioDone = true
                }

                if (videoTime < 0 && audioTime < 0) break

                sampleCount++
                if (sampleCount == 1 || sampleCount % 10 == 0 || videoDone || audioDone) {
                    val progress = if (totalDuration > 0)
                        (lastWrittenTimeUs.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                    else
                        ((videoBytes + audioBytes).toFloat() / totalFileSize.toFloat()).coerceIn(0f, 1f)
                    send(ConversionProgress(
                        videoId,
                        outputFileName,
                        progress,
                        ConversionStatus.CONVERTING,
                        statusMessage = "正在写入 MP4：${(progress * 100).toInt()}%"
                    ))
                }
            }

            send(ConversionProgress(videoId, outputFileName, 1f, ConversionStatus.COMPLETED, outputPath = outputFile.absolutePath, statusMessage = "转换完成"))
        } catch (e: Exception) {
            trySend(ConversionProgress(videoId, outputFileName, 0f, ConversionStatus.FAILED, errorMessage = e.message ?: "Unknown error", statusMessage = "转换失败"))
        } finally {
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { videoExtractor?.release() } catch (_: Exception) {}
            try { audioExtractor?.release() } catch (_: Exception) {}
        }

        close()
    }

    suspend fun convertToMp4Suspend(
        videoPath: String, audioPath: String, outputDir: String, outputFileName: String
    ): Result<String> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val actualVideoPath = ensureAccessible(videoPath) ?: return@withContext Result.failure(Exception("Cannot access video file"))
        val actualAudioPath = ensureAccessible(audioPath) ?: return@withContext Result.failure(Exception("Cannot access audio file"))

        val (outputDirFile, outputError) = ensureOutputWritable(outputDir)
        if (outputError != null) return@withContext Result.failure(Exception(outputError))

        val outputFile = File(outputDirFile, "$outputFileName.mp4")

        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            videoExtractor = MediaExtractor().apply { setDataSource(actualVideoPath) }
            audioExtractor = MediaExtractor().apply { setDataSource(actualAudioPath) }

            var videoTrackIndex = -1; var audioTrackIndex = -1
            var videoFormat: MediaFormat? = null; var audioFormat: MediaFormat? = null

            for (i in 0 until videoExtractor.trackCount) {
                val fmt = videoExtractor.getTrackFormat(i)
                if ((fmt.getString(MediaFormat.KEY_MIME) ?: "").startsWith("video/")) { videoTrackIndex = i; videoFormat = fmt; break }
            }
            for (i in 0 until audioExtractor.trackCount) {
                val fmt = audioExtractor.getTrackFormat(i)
                if ((fmt.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) { audioTrackIndex = i; audioFormat = fmt; break }
            }

            if (videoTrackIndex == -1 || audioTrackIndex == -1) return@withContext Result.failure(Exception("Track not found"))

            videoExtractor.selectTrack(videoTrackIndex)
            audioExtractor.selectTrack(audioTrackIndex)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val mvTrack = muxer.addTrack(videoFormat!!)
            val maTrack = muxer.addTrack(audioFormat!!)
            muxer.start()

            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            val bufferInfo = MediaCodec.BufferInfo()
            var videoDone = false; var audioDone = false

            while (!videoDone || !audioDone) {
                val vTime = if (!videoDone) videoExtractor.sampleTime else Long.MAX_VALUE
                val aTime = if (!audioDone) audioExtractor.sampleTime else Long.MAX_VALUE

                if (vTime <= aTime && !videoDone) {
                    buffer.clear()
                    if (videoExtractor.readSampleData(buffer, 0) > 0) {
                        bufferInfo.offset = 0; bufferInfo.size = buffer.limit()
                        bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                        bufferInfo.flags = videoExtractor.sampleFlags
                        muxer.writeSampleData(mvTrack, buffer, bufferInfo)
                        videoExtractor.advance()
                    } else videoDone = true
                } else if (!audioDone) {
                    buffer.clear()
                    if (audioExtractor.readSampleData(buffer, 0) > 0) {
                        bufferInfo.offset = 0; bufferInfo.size = buffer.limit()
                        bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                        bufferInfo.flags = audioExtractor.sampleFlags
                        muxer.writeSampleData(maTrack, buffer, bufferInfo)
                        audioExtractor.advance()
                    } else audioDone = true
                }
                if (vTime < 0 && aTime < 0) break
            }

            Result.success(outputFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { videoExtractor?.release() } catch (_: Exception) {}
            try { audioExtractor?.release() } catch (_: Exception) {}
        }
    }

    fun cancelAll() {} // ponytail: cancel not implemented yet, add when needed
}
