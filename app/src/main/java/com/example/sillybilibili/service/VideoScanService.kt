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

import android.net.Uri
import android.os.Environment
import com.example.sillybilibili.domain.repository.VideoRepository
import com.example.sillybilibili.util.SafFileHelper
import com.example.sillybilibili.util.ShizukuFileHelper
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
private const val BATCH_SIZE = 32

@Singleton
class VideoScanService @Inject constructor(
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
    /** The access route selected at scan time. Direct access must always win over Shizuku. */
    enum class ScanAccess { DIRECT, SHIZUKU, UNAVAILABLE }
    enum class ScanOrientation { LANDSCAPE, PORTRAIT }
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
        val orientation: ScanOrientation? = null,
        val mode: ScanMode = ScanMode.FULL     // 扫描模式
    ) {
        val isActive: Boolean
            get() = quality != null || minDurationMs != null || maxDurationMs != null ||
                    minSizeBytes != null || maxSizeBytes != null || specificAvIds != null || orientation != null
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

    /** Persistent facts shown on the scan screen before, during and after each scan. */
    data class DirectorySnapshot(
        val access: ScanAccess = ScanAccess.UNAVAILABLE,
        val totalCacheFolders: Int = 0,
        val scannedVideoCount: Int = 0,
        val scannedCacheFolderCount: Int = 0,
        val statusMessage: String = "尚未检查目录"
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
        val parentFolder: String,
        val coverSourcePath: String? = null
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

    /**
     * A directory is directly usable only when it can be listed, not merely when it exists.
     * This keeps unisolated caches usable without asking the user to configure Shizuku.
     */
    fun canAccessDirectoryDirectly(path: String): Boolean {
        val directory = File(path)
        return directory.isDirectory && directory.list() != null
    }

    fun resolveScanAccess(path: String): ScanAccess = when {
        canAccessDirectoryDirectly(path) -> ScanAccess.DIRECT
        shizukuHelper.isShizukuAvailable() -> ScanAccess.SHIZUKU
        else -> ScanAccess.UNAVAILABLE
    }

    suspend fun inspectDirectory(path: String): DirectorySnapshot = withContext(Dispatchers.IO) {
        val access = resolveScanAccess(path)
        if (access == ScanAccess.UNAVAILABLE) {
            return@withContext DirectorySnapshot(access = access, statusMessage = "目录不可访问")
        }
        val useShizuku = access == ScanAccess.SHIZUKU
        val folderNames = shizukuHelper.listDirectories(path, useShizuku)
            .filter { it.all(Char::isDigit) }
        val basePrefix = path.trimEnd('/') + "/"
        val availablePathsInDirectory = videoRepository.getAvailableSourcePathsInDirectory(basePrefix)
        val scannedFolders = availablePathsInDirectory.mapNotNull { storedPath ->
            storedPath.removePrefix(basePrefix).substringBefore('/').takeIf { it.isNotBlank() }
        }.toSet().intersect(folderNames.toSet()).size
        DirectorySnapshot(
            access = access,
            totalCacheFolders = folderNames.size,
            scannedVideoCount = availablePathsInDirectory.size,
            scannedCacheFolderCount = scannedFolders,
            statusMessage = "目录可访问"
        )
    }

    suspend fun inspectDirectoryFromUri(treeUri: Uri): DirectorySnapshot = withContext(Dispatchers.IO) {
        if (!safHelper.isDirectory(treeUri)) {
            return@withContext DirectorySnapshot(statusMessage = "目录不可访问")
        }
        val folderNames = safHelper.listDirectories(treeUri).filter { it.all(Char::isDigit) }
        val directoryPrefix = safDirectoryPrefix(treeUri)
        val availablePaths = videoRepository.getAvailableSourcePathsInDirectory(directoryPrefix)
        val scannedFolders = availablePaths.map { it.safAvIdFrom(directoryPrefix) }.toSet().intersect(folderNames.toSet()).size
        DirectorySnapshot(
            access = ScanAccess.DIRECT,
            totalCacheFolders = folderNames.size,
            scannedVideoCount = availablePaths.size,
            scannedCacheFolderCount = scannedFolders,
            statusMessage = "已授权系统目录"
        )
    }

    // 检测 B 站默认缓存目录是否存在（普通文件访问优先，仅在被隔离时回退 Shizuku）
    suspend fun getDefaultBilibiliPath(): String? = withContext(Dispatchers.IO) {
        if (canAccessDirectoryDirectly(BILIBILI_PATH)) BILIBILI_PATH
        else if (shizukuHelper.isShizukuAvailable() && shizukuHelper.fileExists(BILIBILI_PATH, true)) BILIBILI_PATH
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
        val access = resolveScanAccess(path)
        val useShizuku = access == ScanAccess.SHIZUKU
        val f = filter ?: ScanFilter()

        // --- 检查目录是否存在 ---
        val dirExists = when (access) {
            ScanAccess.DIRECT -> canAccessDirectoryDirectly(path)
            ScanAccess.SHIZUKU ->
            shizukuHelper.fileExists(path, true) && shizukuHelper.isDirectory(path, true)
            ScanAccess.UNAVAILABLE -> false
        }
        if (!dirExists) {
            val message = if (access == ScanAccess.UNAVAILABLE) {
                "无法读取缓存目录。请配置 Shizuku 后重试。"
            } else {
                "Path not found: $path"
            }
            emit(ScanProgress(phase = ScanPhase.ERROR, statusMessage = message))
            return@flow
        }

        // --- Phase 1：列出所有 av 文件夹 ---
        emit(ScanProgress(phase = ScanPhase.COUNTING, statusMessage = if (useShizuku) "Listing folders with Shizuku..." else "Listing folders with direct access..."))

        val directoryListing = withContext(Dispatchers.IO) {
            if (useShizuku) {
                shizukuHelper.listDirectoriesResult(path, true)
            } else {
                val files = File(path).listFiles()
                ShizukuFileHelper.DirectoryListResult(
                    files?.filter { it.isDirectory }?.map { it.name } ?: emptyList(),
                    files != null
                )
            }
        }
        if (!directoryListing.completed) {
            emit(ScanProgress(phase = ScanPhase.ERROR, statusMessage = "Unable to list cache folders completely; no records were changed"))
            return@flow
        }
        val allAvidNames = directoryListing.directories.filter { it.all { c -> c.isDigit() } }

        // 如果指定了特定 avId，直接过滤
        val avidDirNames = if (f.specificAvIds != null) {
            allAvidNames.filter { it.toLongOrNull() in f.specificAvIds!! }
        } else {
            allAvidNames
        }

        val totalFolders = avidDirNames.size
        if (totalFolders == 0) {
            if (f.mode == ScanMode.FULL && !f.isActive) {
                withContext(Dispatchers.IO) {
                    videoRepository.syncCacheDirectory(
                        directoryPrefix = "${path.trimEnd('/')}/",
                        scannedVideos = emptyList(),
                        seenPaths = emptyList(),
                        scanTimestamp = System.currentTimeMillis(),
                        allowMissingSourceReconciliation = true
                    )
                }
                emit(ScanProgress(phase = ScanPhase.COMPLETE, statusMessage = "Cache check complete: cache directory is empty"))
                return@flow
            }
            emit(ScanProgress(phase = ScanPhase.ERROR, statusMessage = "No video folders found"))
            return@flow
        }

        emit(ScanProgress(phase = ScanPhase.COUNTING, totalFolders = totalFolders, statusMessage = "Found $totalFolders folders"))

        // --- Phase 2：读取已入库路径（路径而不是 avId 才能识别新增分 P） ---
        val existingPaths = withContext(Dispatchers.IO) { videoRepository.getAllVideoPaths().toHashSet() }
        val directoryPrefix = "${path.trimEnd('/')}/"
        val availableStoredPaths = withContext(Dispatchers.IO) {
            videoRepository.getAvailableSourcePathsInDirectory(directoryPrefix)
        }

        emit(ScanProgress(phase = ScanPhase.PROCESSING, totalFolders = totalFolders, newFolders = avidDirNames.size, statusMessage = "Reading cache metadata in batches..."))

        // --- Phase 3：批量读取 entry.json 并预过滤 ---
        // 每批仅启动一个 shell，单项扫描复用这里解析的元数据，不再重复 cat。
        val entryLoadResult = withContext(Dispatchers.IO) {
            loadEntryCandidates(path, avidDirNames, useShizuku)
        }
        val discoveredCandidates = entryLoadResult.candidates
        val metadataMatchedCandidates = discoveredCandidates.filter { candidate ->
            !f.isActive || matchesFilter(candidate.meta, f)
        }
        val skippedCount = metadataMatchedCandidates.count { candidate ->
            candidate.videoPath(path) in existingPaths
        }
        val candidateVideos = metadataMatchedCandidates.filterNot { candidate ->
            candidate.videoPath(path) in existingPaths
        }
        val filteredCount = discoveredCandidates.size - metadataMatchedCandidates.size
        if (f.isActive) {
            emit(ScanProgress(phase = ScanPhase.PROCESSING, totalFolders = totalFolders, skippedFolders = skippedCount, filteredFolders = filteredCount, newFolders = candidateVideos.size, statusMessage = "${candidateVideos.size} new candidates remain"))
        }

        // 仅在“完整且未筛选”的扫描完成后对账。筛选扫描只能看到数据子集，
        // 若据此标记缺失会误删仍存在的缓存记录。
        val shouldReconcileSources = f.mode == ScanMode.FULL && !f.isActive
        // A batch/read failure is otherwise indistinguishable from a removed entry.json.  Before
        // deleting anything, require metadata coverage for every still-present AV directory that
        // already has a visible database record. AV folders gone from the directory remain safely
        // eligible for reconciliation.
        val currentAvidNames = avidDirNames.toSet()
        val discoveredPaths = discoveredCandidates.map { it.videoPath(path) }.toSet()
        val hasCompleteMetadataCoverage = entryLoadResult.completed && availableStoredPaths
            .filter { storedPath -> storedPath.avIdFrom(directoryPrefix) in currentAvidNames }
            .all { it in discoveredPaths }
        val candidatesToValidate = if (shouldReconcileSources) {
            metadataMatchedCandidates
        } else {
            candidateVideos
        }
        var fileInfoBatchComplete = true
        val fileInfoByVideoPath = if (f.mode == ScanMode.FULL) {
            withContext(Dispatchers.IO) {
                candidatesToValidate.chunked(BATCH_SIZE).flatMap { batch ->
                    val batchResult = shizukuHelper.getVideoFileInfoBatchResult(
                        batch.map { candidate -> candidate.videoPath(path) to candidate.audioPath(path) },
                        useShizuku
                    )
                    if (!batchResult.completed) fileInfoBatchComplete = false
                    batchResult.fileInfo.toList()
                }.toMap()
            }
        } else emptyMap()
        val allowMissingSourceReconciliation = shouldReconcileSources && hasCompleteMetadataCoverage && fileInfoBatchComplete

        if (candidateVideos.isEmpty()) {
            if (shouldReconcileSources) {
                withContext(Dispatchers.IO) {
                    videoRepository.syncCacheDirectory(
                        directoryPrefix = "${path.trimEnd('/')}/",
                        scannedVideos = emptyList(),
                        seenPaths = fileInfoByVideoPath.keys.toList(),
                        scanTimestamp = System.currentTimeMillis(),
                        allowMissingSourceReconciliation = allowMissingSourceReconciliation
                    )
                }
            }
            val message = if (shouldReconcileSources && !allowMissingSourceReconciliation) {
                "Metadata incomplete: kept existing records and skipped deletion sync"
            } else "Cache check complete: no new videos found"
            emit(ScanProgress(phase = ScanPhase.COMPLETE, totalFolders = totalFolders, skippedFolders = skippedCount, filteredFolders = filteredCount, foundVideoCount = 0, statusMessage = message))
            return@flow
        }

        // --- Phase 4：并行处理候选视频 ---
        val videos = ConcurrentHashMap<String, ScannedVideo>() // path is unique across av/cid/typeTag
        val totalCandidates = candidateVideos.size

        candidateVideos.chunked(PARALLELISM).forEachIndexed { chunkIndex, chunk ->
            coroutineScope {
                chunk.map { candidate ->
                    async(Dispatchers.IO) {
                        val result = scanAvIdDirectory(
                            "$path/${candidate.avidName}", candidate, f.mode, useShizuku,
                            fileInfoByVideoPath[candidate.videoPath(path)]
                        )
                        if (result != null) {
                            videos[result.path] = result
                        }
                    }
                }.awaitAll()
            }

            val processed = min((chunkIndex + 1) * PARALLELISM, totalCandidates)
            emit(ScanProgress(
                phase = ScanPhase.PROCESSING, totalFolders = totalFolders,
                skippedFolders = skippedCount, filteredFolders = filteredCount,
                newFolders = totalCandidates, processedFolders = processed,
                foundVideoCount = videos.size,
                statusMessage = "Processing: $processed/$totalCandidates | Found: ${videos.size}"
            ))
        }

        val scannedVideos = videos.values.toList()

        // --- Phase 5：批量入库 ---
        emit(ScanProgress(phase = ScanPhase.SAVING, totalFolders = totalFolders, skippedFolders = skippedCount, newFolders = totalCandidates, foundVideoCount = scannedVideos.size, statusMessage = "Saving ${scannedVideos.size} videos..."))

        if (f.isActive) {
            // A filtered scan deliberately sees only a subset. Keep it strictly additive so it
            // cannot change availability or remove records that did not match this run.
            if (scannedVideos.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    videoRepository.insertVideos(scannedVideos.map { it.toDomainModel() })
                }
            }
        } else if (scannedVideos.isNotEmpty() || shouldReconcileSources) {
            withContext(Dispatchers.IO) {
                videoRepository.syncCacheDirectory(
                    directoryPrefix = directoryPrefix,
                    scannedVideos = scannedVideos.map { it.toDomainModel() },
                    seenPaths = fileInfoByVideoPath.keys.toList(),
                    scanTimestamp = System.currentTimeMillis(),
                    allowMissingSourceReconciliation = allowMissingSourceReconciliation
                )
            }
        }

        val completeMessage = if (shouldReconcileSources && !allowMissingSourceReconciliation) {
            "Saved ${scannedVideos.size} new videos; metadata incomplete, deletion sync skipped"
        } else "Done: ${scannedVideos.size} new videos saved"
        emit(ScanProgress(phase = ScanPhase.COMPLETE, totalFolders = totalFolders, skippedFolders = skippedCount, filteredFolders = filteredCount, newFolders = totalCandidates, foundVideoCount = scannedVideos.size, statusMessage = completeMessage))
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

        val directoryListing = withContext(Dispatchers.IO) {
            safHelper.listDirectoriesResult(treeUri)
        }
        if (!directoryListing.completed) {
            emit(ScanProgress(phase = ScanPhase.ERROR, statusMessage = "Unable to list cache folders completely; no records were changed"))
            return@flow
        }
        val allAvidNames = directoryListing.directories.filter { it.all { c -> c.isDigit() } }

        val avidDirNames = if (f.specificAvIds != null)
            allAvidNames.filter { it.toLongOrNull() in f.specificAvIds!! }
        else allAvidNames

        val totalFolders = avidDirNames.size
        if (totalFolders == 0) {
            if (f.mode == ScanMode.FULL && !f.isActive) {
                withContext(Dispatchers.IO) {
                    videoRepository.syncCacheDirectory(
                        directoryPrefix = safDirectoryPrefix(treeUri),
                        scannedVideos = emptyList(),
                        seenPaths = emptyList(),
                        scanTimestamp = System.currentTimeMillis(),
                        allowMissingSourceReconciliation = true
                    )
                }
                emit(ScanProgress(phase = ScanPhase.COMPLETE, statusMessage = "Cache check complete: cache directory is empty"))
                return@flow
            }
            emit(ScanProgress(phase = ScanPhase.ERROR, statusMessage = "No video folders found"))
            return@flow
        }

        emit(ScanProgress(phase = ScanPhase.COUNTING, totalFolders = totalFolders, statusMessage = "Found $totalFolders folders"))

        val directoryPrefix = safDirectoryPrefix(treeUri)
        val existingPaths = withContext(Dispatchers.IO) { videoRepository.getAllVideoPaths().toHashSet() }
        val availableStoredPaths = withContext(Dispatchers.IO) {
            videoRepository.getAvailableSourcePathsInDirectory(directoryPrefix)
        }
        emit(ScanProgress(phase = ScanPhase.PROCESSING, totalFolders = totalFolders, statusMessage = "Reading all cache parts..."))

        // SAF must use the same per-CID identity as direct/Shizuku scans. AV-only de-duplication
        // loses multi-part videos and prevents later downloaded parts from appearing.
        val discoveredCandidates = withContext(Dispatchers.IO) {
            buildList {
                avidDirNames.forEach { avidName ->
                    val avidUri = safHelper.findChild(treeUri, avidName) ?: return@forEach
                    safHelper.listSubDirectoriesWithEntryJson(avidUri).forEach { cidDirName ->
                        val cidUri = safHelper.findChild(avidUri, cidDirName) ?: return@forEach
                        val entryJsonUri = safHelper.findChild(cidUri, "entry.json") ?: return@forEach
                        val entry = safHelper.readFileContent(entryJsonUri) ?: return@forEach
                        val meta = try { parseEntryJsonMeta(JSONObject(entry)) } catch (_: Exception) { null } ?: return@forEach
                        val typeTagUri = safHelper.findChild(cidUri, meta.typeTag) ?: return@forEach
                        val videoUri = safHelper.findChild(typeTagUri, "video.m4s") ?: return@forEach
                        val audioUri = safHelper.findChild(typeTagUri, "audio.m4s") ?: return@forEach
                        add(SafCandidate(
                            avidName = avidName,
                            cidUri = cidUri,
                            typeTagUri = typeTagUri,
                            videoUri = videoUri,
                            audioUri = audioUri,
                            meta = meta
                        ))
                    }
                }
            }
        }

        val matchedCandidates = discoveredCandidates.filter { !f.isActive || matchesFilter(it.meta, f) }
        val skippedCount = matchedCandidates.count { it.videoUri.toString() in existingPaths }
        val newCandidates = matchedCandidates.filterNot { it.videoUri.toString() in existingPaths }
        val shouldReconcileSources = f.mode == ScanMode.FULL && !f.isActive
        val currentAvidNames = avidDirNames.toSet()
        val discoveredPaths = discoveredCandidates.map { it.videoUri.toString() }.toSet()
        val hasCompleteMetadataCoverage = availableStoredPaths
            .filter { storedPath -> storedPath.safAvIdFrom(directoryPrefix) in currentAvidNames }
            .all { it in discoveredPaths }
        // SAF does not expose a separate, reliable I/O-error channel for every document.
        // Any candidate that cannot be fully validated therefore makes deletion reconciliation
        // unsafe; it can still be repaired by a later successful full scan.
        var allCandidateFilesVerified = true

        val candidatesToCheck = if (shouldReconcileSources) matchedCandidates else newCandidates
        val validVideos = mutableMapOf<String, ScannedVideo>()
        candidatesToCheck.forEachIndexed { index, candidate ->
            val video = withContext(Dispatchers.IO) { scanSafCandidate(candidate, f.mode) }
            if (video != null) validVideos[video.path] = video else if (f.mode == ScanMode.FULL) allCandidateFilesVerified = false
            emit(ScanProgress(
                phase = ScanPhase.PROCESSING,
                totalFolders = totalFolders,
                skippedFolders = skippedCount,
                newFolders = newCandidates.size,
                processedFolders = index + 1,
                foundVideoCount = validVideos.keys.count { it !in existingPaths },
                statusMessage = "Checking cache parts: ${index + 1}/${candidatesToCheck.size}"
            ))
        }
        val scannedVideos = validVideos.values.filter { it.path !in existingPaths }
        val seenPaths = if (f.mode == ScanMode.FULL) validVideos.keys.toList() else emptyList()
        val allowMissingSourceReconciliation = shouldReconcileSources &&
            hasCompleteMetadataCoverage && allCandidateFilesVerified

        emit(ScanProgress(phase = ScanPhase.SAVING, totalFolders = totalFolders, skippedFolders = skippedCount, newFolders = newCandidates.size, foundVideoCount = scannedVideos.size, statusMessage = "Saving and syncing..."))
        withContext(Dispatchers.IO) {
            if (f.isActive) {
                // SAF filtering has the same contract as file-path filtering: add matches only.
                if (scannedVideos.isNotEmpty()) videoRepository.insertVideos(scannedVideos.map { it.toDomainModel() })
            } else {
                videoRepository.syncCacheDirectory(
                    directoryPrefix = directoryPrefix,
                    scannedVideos = scannedVideos.map { it.toDomainModel() },
                    seenPaths = seenPaths,
                    scanTimestamp = System.currentTimeMillis(),
                    allowMissingSourceReconciliation = allowMissingSourceReconciliation
                )
            }
        }
        val message = if (shouldReconcileSources && !allowMissingSourceReconciliation) {
            "Saved ${scannedVideos.size} videos; metadata incomplete, deletion sync skipped"
        } else "Done: ${scannedVideos.size} videos"
        emit(ScanProgress(phase = ScanPhase.COMPLETE, totalFolders = totalFolders, skippedFolders = skippedCount, newFolders = newCandidates.size, foundVideoCount = scannedVideos.size, statusMessage = message))
    }

    private data class SafCandidate(
        val avidName: String,
        val cidUri: Uri,
        val typeTagUri: Uri,
        val videoUri: Uri,
        val audioUri: Uri,
        val meta: ParsedMeta
    )

    private fun scanSafCandidate(candidate: SafCandidate, mode: ScanMode): ScannedVideo? {
        val size = if (mode == ScanMode.FULL) {
            val info = safHelper.getVideoFileInfo(candidate.typeTagUri) ?: return null
            info.first + info.second
        } else candidate.meta.totalBytes
        return ScannedVideo(
            avid = candidate.avidName.toLongOrNull() ?: 0L,
            cid = candidate.meta.cid,
            title = sanitizeFileName(candidate.meta.title),
            ownerName = candidate.meta.ownerName,
            quality = candidate.meta.quality,
            width = candidate.meta.width,
            height = candidate.meta.height,
            path = candidate.videoUri.toString(),
            audioPath = candidate.audioUri.toString(),
            coverPath = null,
            size = size,
            duration = candidate.meta.duration,
            parentFolder = candidate.avidName,
            coverSourcePath = safHelper.findChild(candidate.cidUri, "cover.jpg")?.toString()
        )
    }

    // ============================================================
    // Private: Batch pre-filter using entry.json only
    // ============================================================

    private data class ParsedMeta(
        val title: String, val ownerName: String, val quality: String,
        val width: Int, val height: Int, val duration: Long, val typeTag: String, val cid: Long,
        val totalBytes: Long
    )

    private data class EntryCandidate(
        val avidName: String,
        val cidDirName: String,
        val meta: ParsedMeta
    ) {
        fun videoPath(basePath: String): String =
            "$basePath/$avidName/$cidDirName/${meta.typeTag}/video.m4s"

        fun audioPath(basePath: String): String =
            "$basePath/$avidName/$cidDirName/${meta.typeTag}/audio.m4s"
    }

    // 批量预过滤：按 BATCH_SIZE 分批读 entry.json，用过滤条件筛掉不匹配的文件夹
    private data class EntryCandidateLoadResult(
        val candidates: List<EntryCandidate>,
        val completed: Boolean
    )

    private fun loadEntryCandidates(
        basePath: String,
        avidNames: List<String>,
        useShizuku: Boolean
    ): EntryCandidateLoadResult {
        var completed = true
        val candidates = avidNames.chunked(BATCH_SIZE).flatMap { batch ->
            val batchResult = shizukuHelper.readEntryJsonBatchResult(basePath, batch, useShizuku)
            if (!batchResult.completed) completed = false
            batchResult.entries.mapNotNull { entry ->
                val json = try { JSONObject(entry.content) } catch (_: Exception) { return@mapNotNull null }
                val meta = parseEntryJsonMeta(json) ?: return@mapNotNull null
                EntryCandidate(entry.avidName, entry.cidDirName, meta)
            }
        }
        return EntryCandidateLoadResult(candidates, completed)
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
            cid = json.optJSONObject("page_data")?.optLong("cid", 0L) ?: 0L,
            totalBytes = json.optLong("total_bytes", 0L)
        )
    }

    // 检查元数据是否匹配过滤条件（画质、时长；文件大小在完整扫描阶段检查）
    private fun matchesFilter(meta: ParsedMeta, filter: ScanFilter): Boolean {
        filter.quality?.let { if (!meta.quality.contains(it)) return false }
        filter.minDurationMs?.let { if (meta.duration < it) return false }
        filter.maxDurationMs?.let { if (meta.duration > it) return false }
        filter.minSizeBytes?.let { if (meta.totalBytes < it) return false }
        filter.maxSizeBytes?.let { if (meta.totalBytes > it) return false }
        filter.orientation?.let { orientation ->
            val isPortrait = meta.height > meta.width
            if ((orientation == ScanOrientation.PORTRAIT) != isPortrait) return false
        }
        return true
    }

    // ============================================================
    // Private: Scan individual av directory
    // ============================================================

    // entry.json and cid folder are discovered in the batch phase above.
    private fun scanAvIdDirectory(
        avidPath: String,
        candidate: EntryCandidate,
        mode: ScanMode,
        useShizuku: Boolean,
        preloadedFileInfo: Pair<Long, Long>?
    ): ScannedVideo? {
        val cidPath = "$avidPath/${candidate.cidDirName}"
        val meta = candidate.meta
        val qualityPath = "$cidPath/${meta.typeTag}"
        val videoFilePath = "$qualityPath/video.m4s"
        val audioFilePath = "$qualityPath/audio.m4s"

        // A successful stat of two non-empty files doubles as the existence check.
        val size = if (mode == ScanMode.FULL) {
            val info = preloadedFileInfo ?: if (useShizuku) {
                shizukuHelper.getVideoFileInfo(videoFilePath, audioFilePath, true)
            } else {
                shizukuHelper.getVideoFileInfo(videoFilePath, audioFilePath, false)
            }
            if (info == null) return null else info.first + info.second
        } else {
            meta.totalBytes
        }

        return ScannedVideo(
            avid = candidate.avidName.toLongOrNull() ?: 0L, cid = meta.cid,
            title = sanitizeFileName(meta.title), ownerName = meta.ownerName,
            quality = meta.quality, width = meta.width, height = meta.height,
            path = videoFilePath, audioPath = audioFilePath,
            coverPath = null, size = size, duration = meta.duration, parentFolder = candidate.avidName,
            coverSourcePath = "$cidPath/cover.jpg"
        )
    }

    // 替换文件名中的非法字符（\/:*?"<>|）为下划线
    private fun sanitizeFileName(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    private fun String.avIdFrom(directoryPrefix: String): String =
        removePrefix(directoryPrefix).substringBefore('/')

    /** Stable prefix shared by every document below the user-selected SAF tree. */
    private fun safDirectoryPrefix(treeUri: Uri): String {
        return safHelper.rootDocumentUri(treeUri).toString() + "%2F"
    }

    private fun String.safAvIdFrom(directoryPrefix: String): String {
        val decodedPath = Uri.parse(this).path ?: return ""
        val documentId = decodedPath.substringAfter("/document/", missingDelimiterValue = "")
        return documentId.substringAfter(':', documentId).substringBefore('/')
    }

    // ============================================================
    // Private: ScannedVideo to Domain Video mapping
    // ============================================================

    // 将扫描结果 ScannedVideo 转换为 domain 层的 Video 模型
    private fun ScannedVideo.toDomainModel() = com.example.sillybilibili.domain.model.Video(
        avid = avid, cid = cid, title = title, ownerName = ownerName,
        quality = quality, width = width, height = height,
        path = path, audioPath = audioPath, size = size,
        duration = duration, coverPath = coverPath, coverSourcePath = coverSourcePath
    )
}
