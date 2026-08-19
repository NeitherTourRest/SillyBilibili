package com.example.sillybilibili.ui.pages.exported

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sillybilibili.domain.model.Category
import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.domain.repository.CategoryRepository
import com.example.sillybilibili.domain.repository.VideoRepository
import com.example.sillybilibili.service.CoverCacheService
import com.example.sillybilibili.service.ExternalMediaSyncService
import com.example.sillybilibili.service.COVER_RETRY_INTERVAL_MS
import com.example.sillybilibili.service.shouldPersistCoverPath
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class ExportedUiState(
    val videos: List<Video> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val totalExportedCount: Int = 0,
    val totalSize: Long = 0,
    val filter: ExportedLibraryFilter = ExportedLibraryFilter(),
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val deleteConfirmVideos: List<Video> = emptyList(),
    val renameTarget: Video? = null,
    val renameInput: String = "",
    val operationMessage: String? = null
)

@HiltViewModel
class ExportedViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    private val categoryRepository: CategoryRepository,
    private val coverCacheService: CoverCacheService,
    private val externalMediaSyncService: ExternalMediaSyncService
) : ViewModel() {

    private val _filter = MutableStateFlow(ExportedLibraryFilter())
    private val _uiState = MutableStateFlow(ExportedUiState(isLoading = true))
    val uiState: StateFlow<ExportedUiState> = _uiState.asStateFlow()

    /** 封面成功请求会话内去重；失败限频重试；并发受限，避免快速滚动时反复复制 m4s。 */
    private val coverRequested = HashSet<Long>()
    private val coverFailedAt = HashMap<Long, Long>()
    private val coverSemaphore = Semaphore(2)

    init {
        refreshExternalChanges(silent = true)
        viewModelScope.launch {
            combine(
                videoRepository.getExportedVideos(),
                categoryRepository.getAllCategories(),
                _filter
            ) { videos, categories, filter ->
                val visibleVideos = filterExportedVideos(videos, filter)
                ExportedLibrarySnapshot(
                    videos = visibleVideos,
                    categories = categories,
                    totalExportedCount = videos.size,
                    totalSize = videos.sumOf { video -> video.exportedSize.takeIf { it > 0L } ?: video.size },
                    filter = filter
                )
            }.collect { snapshot ->
                _uiState.update { state ->
                    state.copy(
                        videos = snapshot.videos,
                        categories = snapshot.categories,
                        totalExportedCount = snapshot.totalExportedCount,
                        totalSize = snapshot.totalSize,
                        filter = snapshot.filter,
                        isLoading = false,
                        selectedIds = state.selectedIds.intersect(snapshot.videos.mapTo(mutableSetOf()) { it.id })
                    )
                }
            }
        }
    }

    fun updateQuery(query: String) = updateFilter { it.copy(query = query) }

    fun updateFilter(transform: (ExportedLibraryFilter) -> ExportedLibraryFilter) {
        _filter.update(transform)
    }

    fun resetFilter() {
        _filter.value = ExportedLibraryFilter()
    }

    fun toggleSelection(videoId: Long) {
        _uiState.update { state ->
            val updated = state.selectedIds.toMutableSet()
            if (!updated.add(videoId)) updated.remove(videoId)
            state.copy(selectedIds = updated)
        }
    }

    fun enterSelectionMode(videoId: Long? = null) {
        _uiState.update { state ->
            state.copy(
                isSelectionMode = true,
                selectedIds = videoId?.let { state.selectedIds + it } ?: state.selectedIds
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(isSelectionMode = false, selectedIds = emptySet()) }
    }

    /** 全选当前搜索/筛选条件下的全部导出视频；已全部选中时取消全选。 */
    fun toggleSelectAll() {
        _uiState.update { state ->
            val allIds = state.videos.map { it.id }.toSet()
            val allSelected = state.selectedIds.containsAll(allIds)
            state.copy(selectedIds = if (allSelected) emptySet() else allIds)
        }
    }

    fun selectedVideos(): List<Video> {
        val selectedIds = _uiState.value.selectedIds
        return _uiState.value.videos.filter { it.id in selectedIds }
    }

    fun assignVideoToCategory(videoId: Long, categoryId: Long?) {
        viewModelScope.launch {
            videoRepository.getVideoById(videoId)?.let { video ->
                videoRepository.updateVideo(video.copy(categoryId = categoryId))
                postMessage(if (categoryId == null) "已移除分类" else "分类已更新")
            }
        }
    }

    fun assignSelectedToCategory(categoryId: Long?) {
        val selected = selectedVideos()
        if (selected.isEmpty()) return
        viewModelScope.launch {
            selected.forEach { video -> videoRepository.updateVideo(video.copy(categoryId = categoryId)) }
            clearSelection()
            postMessage("已更新 ${selected.size} 个视频的分类")
        }
    }

    fun showDeleteConfirm(video: Video) = showDeleteConfirm(listOf(video))

    fun showDeleteConfirm(videos: List<Video>) {
        if (videos.isNotEmpty()) _uiState.update { it.copy(deleteConfirmVideos = videos) }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(deleteConfirmVideos = emptyList()) }
    }

    fun showRename(video: Video) {
        _uiState.update {
            it.copy(
                renameTarget = video,
                renameInput = video.exportedPath?.let(::File)?.nameWithoutExtension.orEmpty()
            )
        }
    }

    fun updateRenameInput(value: String) {
        _uiState.update { it.copy(renameInput = value) }
    }

    fun dismissRename() {
        _uiState.update { it.copy(renameTarget = null, renameInput = "") }
    }

    fun renameExported() {
        val target = _uiState.value.renameTarget ?: return
        val requestedName = normalizedExportFileName(_uiState.value.renameInput)
        if (requestedName == null) {
            postMessage("文件名不能为空，也不能包含路径符号")
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { renameFile(target, requestedName) }
            result.fold(
                onSuccess = { updatedVideo ->
                    videoRepository.updateVideo(updatedVideo)
                    dismissRename()
                    postMessage("导出文件已重命名")
                },
                onFailure = { postMessage(it.message ?: "重命名失败") }
            )
        }
    }

    fun requestCover(video: Video) {
        if (video.id in coverRequested) return
        val lastFail = coverFailedAt[video.id]
        if (lastFail != null && System.currentTimeMillis() - lastFail < COVER_RETRY_INTERVAL_MS) return
        viewModelScope.launch {
            coverSemaphore.withPermit {
                val cachedPath = coverCacheService.cacheCover(video)
                if (cachedPath == null) {
                    coverFailedAt[video.id] = System.currentTimeMillis()
                    return@withPermit
                }
                coverRequested.add(video.id)
                if (!shouldPersistCoverPath(video.coverPath, cachedPath)) return@withPermit
                videoRepository.updateVideo(video.copy(coverPath = cachedPath))
            }
        }
    }

    fun deleteExported(videos: List<Video> = _uiState.value.deleteConfirmVideos) {
        if (videos.isEmpty()) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { deleteFiles(videos) }
            result.deleted.forEach { video ->
                if (video.sourceAvailable) {
                    videoRepository.updateVideo(video.copy(exportedPath = null, exportedSize = 0L, exportedLastModified = 0L))
                } else {
                    videoRepository.deleteVideo(video)
                }
            }
            _uiState.update { it.copy(deleteConfirmVideos = emptyList()) }
            clearSelection()
            val message = if (result.failedCount == 0) {
                "已删除 ${result.deleted.size} 个导出文件"
            } else {
                "已删除 ${result.deleted.size} 个，${result.failedCount} 个文件删除失败"
            }
            postMessage(message)
        }
    }

    fun refreshExternalChanges(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _uiState.update { it.copy(isRefreshing = true) }
            externalMediaSyncService.reconcileExportedFiles()
            if (!silent) {
                _uiState.update { it.copy(isRefreshing = false) }
                postMessage("已核对导出文件的外部修改")
            }
        }
    }

    fun consumeOperationMessage() {
        _uiState.update { it.copy(operationMessage = null) }
    }

    private fun postMessage(message: String) {
        _uiState.update { it.copy(operationMessage = message) }
    }

    private fun renameFile(video: Video, fileName: String): Result<Video> = runCatching {
        val source = File(requireNotNull(video.exportedPath) { "找不到导出文件" })
        check(source.isFile) { "导出文件已不存在，请先刷新" }
        val destination = File(requireNotNull(source.parentFile) { "导出目录不可用" }, fileName)
        check(destination.absolutePath == source.absolutePath || !destination.exists()) { "同名文件已存在" }
        if (destination.absolutePath != source.absolutePath) {
            check(source.renameTo(destination)) { "无法重命名文件，请检查文件权限" }
        }
        video.copy(
            exportedPath = destination.absolutePath,
            exportedSize = destination.length(),
            exportedLastModified = destination.lastModified()
        )
    }

    private fun deleteFiles(videos: List<Video>): DeleteFilesResult {
        val deleted = mutableListOf<Video>()
        var failedCount = 0
        videos.forEach { video ->
            val file = video.exportedPath?.let(::File)
            if (file == null || !file.exists() || file.delete()) deleted += video else failedCount++
        }
        return DeleteFilesResult(deleted, failedCount)
    }
}

private data class ExportedLibrarySnapshot(
    val videos: List<Video>,
    val categories: List<Category>,
    val totalExportedCount: Int,
    val totalSize: Long,
    val filter: ExportedLibraryFilter
)

private data class DeleteFilesResult(val deleted: List<Video>, val failedCount: Int)
