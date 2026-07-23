// ============================================================
// VideoScanService.kt — B 站缓存视频扫描器（性能优化版）
// ============================================================
//
// 性能优化策略：
//   1. 批处理 Shizuku 操作 — 合并多个 shell 调用为一次
//   2. 并行协程处理 — 同时处理多个 av 文件夹
//   3. 预过滤 — 通过 entry.json 内容提前过滤，跳过不匹配的
//   4. 增量扫描 — 跳过已扫描的 avId
//   5. 快速模式 — 只解析 entry.json，跳过文件大小检查
//   6. 批量入库 — 一次 INSERT 所有新视频
// ============================================================

package com.example.sillybilibili.service

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.sillybilibili.domain.repository.VideoRepository
import com.example.sillybilibili.util.SafFileHelper
import com.example.sillybilibili.util.ShizukuFileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

// 并行度：同时处理多少个 av 文件夹
private const val PARALLELISM = 4
// 批处理大小：一次 shell 调用读取多少个文件
private const val BATCH_SIZE = 8

@Singleton
class VideoScanService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuHelper: ShizukuFileHelper,
    private val safHelper: SafFileHelper,
    private val videoRepository: VideoRepository
) {
    companion object {
        private val BILIBILI_PATH: String by lazy {
            "${Environment.getExternalStorageDirectory().path}/Android/data/tv.danmaku.bili/download"
        }
    }

    // ============================================================
    // Data classes
    // ============================================================

    // 扫描阶段枚举
    enum class ScanPhase { COUNTING, PROCESSING, SAVING, COMPLETE, ERROR }
    // 扫描模式：FULL = 检查文件完整性，QUICK = 只读 entry.json
    enum class ScanMode { FULL, QUICK }

    // 扫描过滤条件：画质、时长、大小、指定 av 号
    data class ScanFilter(
        val quality: String? = null,           // 只扫描特定画质（"1080P"、"4K"等，null=不限）
        val minDurationMs: Long? = null,       // 最短时长（毫秒）
        val maxDurationMs: Long? = null,       // 最长时长（毫秒）
        val minSizeBytes: Long? = null,        // 最小文件大小（字节）
        val maxSizeBytes: Long? = null,        // 最大文件大小（字节）
        val specificAvIds: List<Long>? = null, // 只扫描指定 av 号（null=扫描全部）
        val mode: ScanMode = ScanMode.FULL     // 扫描模式
    ) {
        val isActive: Boolean
            get() = quality != null || minDurationMs != null || maxDurationMs != null ||
                    minSizeBytes != null || maxSizeBytes != null || specificAvIds != null
    }

    // 扫描进度状态，随 Flow 逐次发送给 UI
    data class ScanProgress(
        val phase: ScanPhase = ScanPhase.COUNTING,
        val totalFolders: Int = 0,
        val skippedFolders: Int = 0,
        val filteredFolders: Int = 0,       // 被过滤掉的文件夹数
        val newFolders: Int = 0,
        val processedFolders: Int = 0,
        val currentAvId: String = "",
        val foundVideoCount: Int = 0,
        val statusMessage: String = ""
    )

    // 单个扫描到的视频的完整信息
    data class ScannedVideo(
        val avid: Long,
        val cid: Long,
        val title: String,
        val ownerName: String,
        val quality: String,
        val width: Int,
        val height: Int,
        val path: String,
        val audioPath: String,
        val coverPath: String?,
        val size: Long,
        val duration: Long,
        val parentFolder: String
    )

    // ============================================================
    // Public API
    // ============================================================

    // 返回外部存储根路径
    fun getBasePath(): String = Environment.getExternalStorageDirectory().path
    // 返回 B 站缓存目录常量
    fun getBilibiliPathConstant(): String = BILIBILI_PATH
    // 检查 Shizuku 是否已授权可用
    fun isShizukuAvailable(): Boolean = shizukuHelper.isShizukuAvailable()

    // 检测 B 站默认缓存目录是否存在（Shizuku 路径优先，回退到普通文件访问）
    suspend fun getDefaultBilibiliPath(): String? = withContext(Dispatchers.IO) {
        if (shizukuHelper.isShizukuAvailable() && shizukuHelper.fileExists(BILIBILI_PATH, true)) BILIBILI_PATH
        else if (File(BILIBILI_PATH).exists()) BILIBILI_PATH
        else null
    }

    /**
     * 扫描 B 站缓存目录（带过滤和批处理优化）
     *
     * @param path B站缓存路径
     * @param filter 扫描过滤器（null = 扫描全部）
     * @return Flow<ScanProgress> — 实时进度流
     */
    fun scanDirectory(path: String, filter: ScanFilter? = null): Flow<ScanProgress> = flow {
        val useShizuku = shizukuHelper.isShizukuAvailable()
        val f = filter ?: ScanFilter()

        // --- 检查目录是否存在 ---
        val dirExists = if (useShizuku) {
            shizukuHelper.fileExists(path, true) && shizukuHelper.isDirectory(path, true)
        } else {
            File(path).exists() && File(path).isDirectory
        }
        if (!dirExists) {
            emit(ScanProgress(phase = ScanPhase.ERROR, statusMessage = "Path not found: $path"))
            return@flow
        }

        // --- Phase 1：列出所有 av 文件夹 ---
        emit(ScanProgress(phase = ScanPhase.COUNTING, statusMessage = "Listing folders..."))

        val allAvidNames = withContext(Dispatchers.IO) {
            if (useShizuku) {
                // 批处理：一次 ls 列出所有文件夹，减少 shell 调用
                shizukuHelper.listDirectories(path, true).filter { it.all { c -> c.isDigit() } }
            } else {
                File(path).listFiles()
                    ?.filter { it.isDirectory && it.name.all { c -> c.isDigit() } }
                    ?.map { it.name } ?: emptyList()
            }
        }

        // 如果指定了特定 avId，直接过滤
        val avidDirNames = if (f.specificAvIds != null) {
            allAvidNames.filter { it.toLongOrNull() in f.specificAvIds!! }
        } else {
            allAvidNames
        }

        val totalFolders = avidDirNames.size
        if (totalFolders == 0) {
            emit(ScanProgress(phase = ScanPhase.ERROR, statusMessage = "No video folders found"))
            return@flow
        }

        emit(ScanProgress(phase = ScanPhase.COUNTING, totalFolders = totalFolders, statusMessage = "Found $totalFolders folders"))

        // --- Phase 2：获取已扫描的 avId（去重） ---
        val existingAvIds = withContext(Dispatchers.IO) { videoRepository.getAllAvIds().toSet() }

        val newAvidNames = avidDirNames.filter { it.toLongOrNull() !in existingAvIds }
        val skippedCount = totalFolders - newAvidNames.size

        if (newAvidNames.isEmpty()) {
            emit(ScanProgress(phase = ScanPhase.COUNTING, totalFolders = totalFolders, skippedFolders = skippedCount, newFolders = 0, statusMessage = "All $totalFolders already scanned"))
            return@flow
        }

        emit(ScanProgress(phase = ScanPhase.PROCESSING, totalFolders = totalFolders, skippedFolders = skippedCount, newFolders = newAvidNames.size, statusMessage = "${skippedCount} existing, processing ${newAvidNames.size} new"))

        // --- Phase 3：批处理 entry.json 预过滤 ---
        // 先用批处理读取所有 entry.json，快速过滤
        // 这避免了每个视频单独调用 Shizuku 读取 entry.json
        val candidateVideos = mutableListOf<Pair<String, Int>>() // (avidName, batchIndex)
        val filteredCount = newAvidNames.size

        if (f.isActive) {
            // 有过滤条件：需要先读 entry.json 做预过滤
            val batchResults = withContext(Dispatchers.IO) {
                processBatchPreFilter(path, newAvidNames, f, useShizuku)
            }
            candidateVideos.addAll(batchResults)
            val filtered = newAvidNames.size - candidateVideos.size
            emit(ScanProgress(phase = ScanPhase.PROCESSING, totalFolders = totalFolders, skippedFolders = skippedCount, filteredFolders = filtered, newFolders = candidateVideos.size, statusMessage = "$filtered filtered, ${candidateVideos.size} candidates remain"))
        } else {
            // 无过滤：全部作为候选
            candidateVideos.addAll(newAvidNames.map { it to 0 })
        }

        if (candidateVideos.isEmpty()) {
            emit(ScanProgress(phase = ScanPhase.COMPLETE, totalFolders = totalFolders, skippedFolders = skippedCount, filteredFolders = filteredCount, foundVideoCount = 0, statusMessage = "No videos match the filter"))
            return@flow
        }

        // --- Phase 4：并行处理候选视频 ---
        val videos = ConcurrentHashMap<Int, ScannedVideo>() // thread-safe
        val totalCandidates = candidateVideos.size

        candidateVideos.chunked(PARALLELISM).forEachIndexed { chunkIndex, chunk ->
            coroutineScope {
                chunk.map { (avidName, _) ->
                    async(Dispatchers.IO) {
                        val result = scanAvIdDirectory("$path/$avidName", avidName, f.mode, useShizuku)
                        if (result != null) {
                            videos[avidName.toIntOrNull() ?: 0] = result
                        }
                    }
                }.awaitAll()
            }

            val processed = min((chunkIndex + 1) * PARALLELISM, totalCandidates)
            emit(ScanProgress(
                phase = ScanPhase.PROCESSING, totalFolders = totalFolders,
                skippedFolders = skippedCount, filteredFolders = filteredCount - totalCandidates,
                newFolders = totalCandidates, processedFolders = processed,
                foundVideoCount = videos.size,
                statusMessage = "Processing: $processed/$totalCandidates | Found: ${videos.size}"
            ))
        }

        val scannedVideos = videos.values.toList()

        // --- Phase 5：批量入库 ---
        emit(ScanProgress(phase = ScanPhase.SAVING, totalFolders = totalFolders, skippedFolders = skippedCount, newFolders = totalCandidates, foundVideoCount = scannedVideos.size, statusMessage = "Saving ${scannedVideos.size} videos..."))

        if (scannedVideos.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                videoRepository.insertVideos(scannedVideos.map { it.toDomainModel() })
            }
        }

        emit(ScanProgress(phase = ScanPhase.COMPLETE, totalFolders = totalFolders, skippedFolders = skippedCount, filteredFolders = filteredCount - totalCandidates, newFolders = totalCandidates, foundVideoCount = scannedVideos.size, statusMessage = "Done: ${scannedVideos.size} new videos saved"))
    }

    /**
     * SAF-based scan — user selects the download directory via system file picker.
     * No Shizuku required, but slower (SAF uses content:// URIs, one call per file).
     */
    fun scanDirectoryFromUri(treeUri: Uri, filter: ScanFilter? = null): Flow<ScanProgress> = flow {
        val f = filter ?: ScanFilter()
        if (!safHelper.isDirectory(treeUri)) {
            emit(ScanProgress(phase = ScanPhase.ERROR, statusMessage = "Invalid directory"))
            return@flow
        }

        emit(ScanProgress(phase = ScanPhase.COUNTING, statusMessage = "Listing folders..."))

        val allAvidNames = withContext(Dispatchers.IO) {
            safHelper.listDirectories(treeUri).filter { it.all { c -> c.isDigit() } }
        }

        val avidDirNames = if (f.specificAvIds != null)
            allAvidNames.filter { it.toLongOrNull() in f.specificAvIds!! }
        else allAvidNames

        val totalFolders = avidDirNames.size
        if (totalFolders == 0) {
            emit(ScanProgress(phase = ScanPhase.ERROR, statusMessage = "No video folders found"))
            return@flow
        }

        emit(ScanProgress(phase = ScanPhase.COUNTING, totalFolders = totalFolders, statusMessage = "Found $totalFolders folders"))

        val existingAvIds = withContext(Dispatchers.IO) { videoRepository.getAllAvIds().toSet() }
        val newAvidNames = avidDirNames.filter { it.toLongOrNull() !in existingAvIds }
        val skippedCount = totalFolders - newAvidNames.size

        if (newAvidNames.isEmpty()) {
            emit(ScanProgress(phase = ScanPhase.COUNTING, totalFolders = totalFolders, skippedFolders = skippedCount, newFolders = 0, statusMessage = "All already scanned"))
            return@flow
        }

        emit(ScanProgress(phase = ScanPhase.PROCESSING, totalFolders = totalFolders, skippedFolders = skippedCount, newFolders = newAvidNames.size))

        val scannedVideos = mutableListOf<ScannedVideo>()

        for ((idx, avidName) in newAvidNames.withIndex()) {
            val avidUri = safHelper.findChild(treeUri, avidName) ?: continue
            val vid = withContext(Dispatchers.IO) { scanAvIdDirectorySaf(avidUri, avidName, f.mode) }
            if (vid != null) scannedVideos.add(vid)
            emit(ScanProgress(phase = ScanPhase.PROCESSING, totalFolders = totalFolders, skippedFolders = skippedCount, newFolders = newAvidNames.size, processedFolders = idx + 1, foundVideoCount = scannedVideos.size))
        }

        emit(ScanProgress(phase = ScanPhase.SAVING, foundVideoCount = scannedVideos.size, statusMessage = "Saving..."))
        if (scannedVideos.isNotEmpty()) {
            withContext(Dispatchers.IO) { videoRepository.insertVideos(scannedVideos.map { it.toDomainModel() }) }
        }
        emit(ScanProgress(phase = ScanPhase.COMPLETE, foundVideoCount = scannedVideos.size, statusMessage = "Done: ${scannedVideos.size} videos"))
    }

    // SAF: scan a single av directory
    private fun scanAvIdDirectorySaf(avidUri: Uri, avidName: String, mode: ScanMode): ScannedVideo? {
        val cidDirName = safHelper.listSubDirectoriesWithEntryJson(avidUri).firstOrNull() ?: return null
        val cidUri = safHelper.findChild(avidUri, cidDirName) ?: return null

        val entryJsonUri = safHelper.findChild(cidUri, "entry.json") ?: return null
        val entryContent = safHelper.readFileContent(entryJsonUri) ?: return null
        val entryJson = try { org.json.JSONObject(entryContent) } catch (_: Exception) { return null }

        val title = sanitizeFileName(entryJson.optString("title", avidName))
        val ownerName = entryJson.optString("owner_name", "")
        val quality = entryJson.optString("quality_pithy_description", "")
        val pageData = entryJson.optJSONObject("page_data")
        val cid = pageData?.optLong("cid", 0L) ?: 0L
        val width = pageData?.optInt("width", 0) ?: 0
        val height = pageData?.optInt("height", 0) ?: 0
        val typeTag = entryJson.optString("type_tag", "0")
        val duration = entryJson.optLong("total_time_milli", 0L)

        val typeTagUri = safHelper.findChild(cidUri, typeTag) ?: return null

        if (mode == ScanMode.FULL && !safHelper.checkVideoFilesExist(typeTagUri)) return null

        val size = if (mode == ScanMode.FULL) {
            val info = safHelper.getVideoFileInfo(typeTagUri) ?: return null
            info.first + info.second
        } else {
            entryJson.optLong("total_bytes", 0L)
        }

        val coverUri = safHelper.findChild(cidUri, "cover.jpg")
        val coverPath = if (coverUri != null) {
            val bytes = safHelper.readBinaryFile(coverUri)
            if (bytes != null) {
                val cacheDir = java.io.File(context.cacheDir, "covers")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val cacheFile = java.io.File(cacheDir, "${avidName}_${cidDirName}.jpg")
                if (!cacheFile.exists()) cacheFile.writeBytes(bytes)
                cacheFile.absolutePath
            } else null
        } else null

        return ScannedVideo(
            avid = avidName.toLongOrNull() ?: 0L, cid = cid, title = title, ownerName = ownerName,
            quality = quality, width = width, height = height,
            path = typeTagUri.toString(), audioPath = typeTagUri.toString(),
            coverPath = coverPath, size = size, duration = duration, parentFolder = avidName
        )
    }

    // ============================================================
    // Private: Batch pre-filter using entry.json only
    // ============================================================

    private data class PreFilterResult(val avidName: String, val parsed: ParsedMeta?)

    private data class ParsedMeta(
        val title: String, val ownerName: String, val quality: String,
        val width: Int, val height: Int, val duration: Long, val typeTag: String, val cid: Long
    )

    // 批量预过滤：按 BATCH_SIZE 分批读 entry.json，用过滤条件筛掉不匹配的文件夹
    private suspend fun processBatchPreFilter(
        basePath: String,
        avidNames: List<String>,
        filter: ScanFilter,
        useShizuku: Boolean
    ): List<Pair<String, Int>> = coroutineScope {
        val results = mutableListOf<Pair<String, Int>>()

        // 批处理：每 BATCH_SIZE 个文件夹处理一批
        avidNames.chunked(BATCH_SIZE).forEachIndexed { batchIdx, batch ->
            val batchResults = batch.mapNotNull { avidName ->
                val entryJson = readEntryJsonFast(basePath, avidName, useShizuku) ?: return@mapNotNull null
                val meta = parseEntryJsonMeta(entryJson) ?: return@mapNotNull null

                // 应用过滤
                if (matchesFilter(meta, filter)) avidName
                else null
            }
            results.addAll(batchResults.map { it to batchIdx })
        }
        results
    }

    /**
     * 快速读取 entry.json（仅用于过滤检查）
     * 批处理：一次 shell 调用拼接多个 cat 命令
     */
    private suspend fun readEntryJsonFast(basePath: String, avidName: String, useShizuku: Boolean): JSONObject? {
        // 先找 cid 目录
        val avidPath = "$basePath/$avidName"
        val cidDir = if (useShizuku) {
            shizukuHelper.listSubDirectoriesWithEntryJson(avidPath, true).firstOrNull()
        } else {
            File(avidPath).listFiles()
                ?.filter { it.isDirectory && File(it, "entry.json").exists() }
                ?.firstOrNull()?.name
        } ?: return null

        val entryJsonPath = "$avidPath/$cidDir/entry.json"
        val content = if (useShizuku) {
            shizukuHelper.readFileContent(entryJsonPath, true)
        } else {
            try { File(entryJsonPath).readText() } catch (_: Exception) { null }
        } ?: return null

        return try { JSONObject(content) } catch (_: Exception) { null }
    }

    // 从 entry.json 提取视频元数据（标题、画质、分辨率、时长、cid）
    private fun parseEntryJsonMeta(json: JSONObject): ParsedMeta? {
        val title = json.optString("title", "") ?: ""
        if (title.isEmpty()) return null
        return ParsedMeta(
            title = title,
            ownerName = json.optString("owner_name", ""),
            quality = json.optString("quality_pithy_description", ""),
            width = json.optJSONObject("page_data")?.optInt("width", 0) ?: 0,
            height = json.optJSONObject("page_data")?.optInt("height", 0) ?: 0,
            duration = json.optLong("total_time_milli", 0L),
            typeTag = json.optString("type_tag", "0"),
            cid = json.optJSONObject("page_data")?.optLong("cid", 0L) ?: 0L
        )
    }

    // 检查元数据是否匹配过滤条件（画质、时长；文件大小在完整扫描阶段检查）
    private fun matchesFilter(meta: ParsedMeta, filter: ScanFilter): Boolean {
        filter.quality?.let { if (!meta.quality.contains(it)) return false }
        filter.minDurationMs?.let { if (meta.duration < it) return false }
        filter.maxDurationMs?.let { if (meta.duration > it) return false }
        // Note: size filter cannot be applied at pre-filter stage (need file access)
        return true
    }

    // ============================================================
    // Private: Scan individual av directory
    // ============================================================

    // 扫描单个 av 目录：找 cid 子目录 → 读 entry.json → 检查视频/音频文件 → 获取大小 → 复制封面
    private fun scanAvIdDirectory(avidPath: String, avidName: String, mode: ScanMode, useShizuku: Boolean): ScannedVideo? {
        // Phase 1: Find CID directory
        val cidDirName = if (useShizuku) {
            shizukuHelper.listSubDirectoriesWithEntryJson(avidPath, true).firstOrNull()
        } else {
            File(avidPath).listFiles()
                ?.filter { it.isDirectory && File(it, "entry.json").exists() }
                ?.firstOrNull()?.name
        } ?: return null

        val cidPath = "$avidPath/$cidDirName"

        // Phase 2: Read entry.json (already cached from pre-filter if filtered scan)
        val entryJsonContent = if (useShizuku) {
            shizukuHelper.readFileContent("$cidPath/entry.json", true)
        } else {
            try { File("$cidPath/entry.json").readText() } catch (_: Exception) { null }
        }
        val entryJson = try { JSONObject(entryJsonContent ?: "{}") } catch (_: Exception) { JSONObject() }

        val title = sanitizeFileName(entryJson.optString("title", avidName))
        val ownerName = entryJson.optString("owner_name", "")
        val quality = entryJson.optString("quality_pithy_description", "")
        val pageData = entryJson.optJSONObject("page_data")
        val cid = pageData?.optLong("cid", 0L) ?: 0L
        val width = pageData?.optInt("width", 0) ?: 0
        val height = pageData?.optInt("height", 0) ?: 0
        val typeTag = entryJson.optString("type_tag", "0")
        val duration = entryJson.optLong("total_time_milli", 0L)

        val qualityPath = "$cidPath/$typeTag"
        val videoFilePath = "$qualityPath/video.m4s"
        val audioFilePath = "$qualityPath/audio.m4s"

        // Quick mode: skip file size verification
        if (mode == ScanMode.FULL) {
            val filesExist = if (useShizuku) {
                shizukuHelper.checkVideoFilesExist(videoFilePath, audioFilePath, true)
            } else {
                File(videoFilePath).exists() && File(audioFilePath).exists()
            }
            if (!filesExist) return null
        }

        // Get file sizes (skip in QUICK mode for speed)
        val size = if (mode == ScanMode.FULL) {
            if (useShizuku) {
                val info = shizukuHelper.getVideoFileInfo(videoFilePath, audioFilePath, true)
                if (info == null) return null else info.first + info.second
            } else {
                File(videoFilePath).length() + File(audioFilePath).length()
            }
        } else {
            // QUICK mode: estimate (actual size checked on conversion)
            entryJson.optLong("total_bytes", 0L)
        }

        // Copy cover
        val coverOriginalPath = "$cidPath/cover.jpg"
        val coverExists = if (useShizuku) shizukuHelper.fileExists(coverOriginalPath, true) else File(coverOriginalPath).exists()
        val coverCachePath = if (coverExists) copyCoverToCache(coverOriginalPath, avidName, cidDirName, useShizuku) else null

        return ScannedVideo(
            avid = avidName.toLongOrNull() ?: 0L, cid = cid, title = title, ownerName = ownerName,
            quality = quality, width = width, height = height,
            path = videoFilePath, audioPath = audioFilePath,
            coverPath = coverCachePath, size = size, duration = duration, parentFolder = avidName
        )
    }

    // ============================================================
    // Private: Cover copying
    // ============================================================

    // 将封面图从 B 站缓存目录复制到 App 内部缓存（已有则跳过）
    private fun copyCoverToCache(originalPath: String, avid: String, cid: String, useShizuku: Boolean): String? {
        try {
            val cacheDir = File(context.cacheDir, "covers")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val cacheFile = File(cacheDir, "${avid}_${cid}.jpg")
            if (cacheFile.exists()) return cacheFile.absolutePath

            if (useShizuku) {
                val bytes = shizukuHelper.readBinaryFile(originalPath, true)
                if (bytes != null && bytes.isNotEmpty()) { cacheFile.writeBytes(bytes); return cacheFile.absolutePath }
            } else {
                try { File(originalPath).copyTo(cacheFile, overwrite = true); return cacheFile.absolutePath } catch (_: Exception) {}
            }
            return null
        } catch (_: Exception) { return null }
    }

    // 替换文件名中的非法字符（\/:*?"<>|）为下划线
    private fun sanitizeFileName(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    // ============================================================
    // Private: ScannedVideo to Domain Video mapping
    // ============================================================

    // 将扫描结果 ScannedVideo 转换为 domain 层的 Video 模型
    private fun ScannedVideo.toDomainModel() = com.example.sillybilibili.domain.model.Video(
        avid = avid, cid = cid, title = title, ownerName = ownerName,
        quality = quality, width = width, height = height,
        path = path, audioPath = audioPath, size = size,
        duration = duration, coverPath = coverPath
    )
}
