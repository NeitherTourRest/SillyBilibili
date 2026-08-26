package com.example.sillybilibili.ui.pages.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sillybilibili.domain.model.Category
import com.example.sillybilibili.domain.model.OnlineVideoStatus
import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.domain.repository.CategoryRepository
import com.example.sillybilibili.ui.components.BatchProgress
import com.example.sillybilibili.domain.repository.VideoRepository
import com.example.sillybilibili.service.ConversionBatchCoordinator
import com.example.sillybilibili.service.MediaIntegrityChecker
import com.example.sillybilibili.service.MediaIntegrityStatus
import com.example.sillybilibili.service.CoverCacheService
import com.example.sillybilibili.service.COVER_RETRY_INTERVAL_MS
import com.example.sillybilibili.service.OnlineVideoStatusService
import com.example.sillybilibili.service.SettingsService
import com.example.sillybilibili.service.shouldPersistCoverPath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

data class VideoListUiState(
    val videos: List<Video> = emptyList(),
    val categoryName: String = "Videos",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreData: Boolean = true,
    val searchQuery: String = "",
    val categories: List<Category> = emptyList(),
    val filterState: FilterState = FilterState(),
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    /** 批量操作实时进度（转换/刷新状态/完整性检查）。 */
    val batchProgress: BatchProgress? = null,
    val errorMessage: String? = null
) {
    companion object { const val PAGE_SIZE = 20 }
}

/** Key for distinctUntilChanged to skip redundant DB calls. */
private data class VideoListLoadKey(
    val categoryId: Long?,
    val query: String,
    val filter: FilterState,
    /** Forces a new database query after a mutation even when filters are unchanged. */
    val refreshVersion: Long
)

@HiltViewModel
class VideoListViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    private val categoryRepository: CategoryRepository,
    private val coverCacheService: CoverCacheService,
    private val onlineVideoStatusService: OnlineVideoStatusService? = null,
    private val integrityChecker: MediaIntegrityChecker? = null,
    private val settingsService: SettingsService? = null,
    private val conversionBatchCoordinator: ConversionBatchCoordinator? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoListUiState())
    val uiState: StateFlow<VideoListUiState> = _uiState.asStateFlow()

    private val _categoryId = MutableStateFlow<Long?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _categoryRefreshTrigger = MutableStateFlow(0L)
    private val _loadDataTrigger = MutableStateFlow(0L)
    private val _filterSnapshot = MutableStateFlow(FilterSnapshot(FilterState(), "", 0L))
    /** 无限滚动：已追加加载的批次数。 */
    private var loadedBatchCount = 0

    private val _debouncedSearch = _searchQuery.debounce(300L).distinctUntilChanged()

    /** 封面成功请求会话内去重；失败限频重试；并发受限，避免滚动时爆发 IO。 */
    private val coverRequested = HashSet<Long>()
    private val statusRequested = HashSet<Long>()
    private val coverFailedAt = HashMap<Long, Long>()
    private val coverSemaphore = Semaphore(2)
    private val statusSemaphore = Semaphore(3)

    init {
        conversionBatchCoordinator?.let { coordinator ->
            viewModelScope.launch {
                coordinator.state.collect { batch ->
                    _uiState.update {
                        it.copy(
                            batchProgress = if (batch.isRunning) {
                                BatchProgress("转换 MP4", batch.done, batch.total)
                            } else null
                        )
                    }
                }
            }
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
                _uiState.update { state ->
                    state.copy(videos = state.videos.map {
                        if (it.id == video.id) it.copy(coverPath = cachedPath) else it
                    })
                }
            }
        }
    }

    fun requestOnlineStatus(video: Video) {
        val service = onlineVideoStatusService ?: return
        if (!statusRequested.add(video.id)) return
        viewModelScope.launch {
            statusSemaphore.withPermit {
                val status = service.checkIfNeeded(video)
                _uiState.update { state ->
                    state.copy(videos = state.videos.map {
                        if (it.id == video.id) it.copy(onlineStatus = status, onlineCheckedAt = System.currentTimeMillis()) else it
                    })
                }
            }
        }
    }

    fun setCategoryId(categoryId: Long?) {
        if (_categoryId.value == categoryId) return
        _categoryId.value = categoryId
        _loadDataTrigger.value++
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun applyFilter(filter: FilterState) {
        if (_filterSnapshot.value.filter == filter) return
        _filterSnapshot.value = FilterSnapshot(filter, _searchQuery.value, System.currentTimeMillis())
    }

    fun clearFilter() {
        val empty = FilterState()
        if (_filterSnapshot.value.filter == empty) return
        _filterSnapshot.value = FilterSnapshot(empty, "", 0L)
    }

    private fun reload() { _loadDataTrigger.value++ }

    init {
        viewModelScope.launch {
            combine(
                _categoryId,
                _debouncedSearch,
                _loadDataTrigger,
                _filterSnapshot
            ) { catId, debouncedQ, refreshVersion, fs ->
                val effectiveQ = if (fs.filter.isActive && debouncedQ.isEmpty()) fs.querySnapshot else debouncedQ
                VideoListLoadKey(catId, effectiveQ, fs.filter, refreshVersion) to Triple(catId, effectiveQ, fs)
            }
                .distinctUntilChanged { (old, _), (new, _) -> old == new }
                .collectLatest { (_, data) ->
                    val (catId, query, fs) = data
                    val isInitialLoad = _uiState.value.videos.isEmpty()
                    if (isInitialLoad) _uiState.update { it.copy(isLoading = true) }

                    val categories = _categoryRefreshTrigger.flatMapLatest {
                        categoryRepository.getAllCategories()
                    }.first()

                    val categoryName = if (catId != null) {
                        categoryRepository.getCategoryById(catId)?.name ?: "Videos"
                    } else "All Videos"

                    val videos = loadPage(catId, query, fs, page = 0)
                    val hasMore = videos.size == VideoListUiState.PAGE_SIZE
                    loadedBatchCount = 0

                    _uiState.update {
                        it.copy(videos = videos, categories = categories, categoryName = categoryName,
                            isLoading = false, searchQuery = query,
                            filterState = fs.filter, hasMoreData = hasMore)
                    }
                }
        }
    }

    // ── Single filter → DAO mapping ───────────────────────────

    private suspend fun loadPage(
        categoryId: Long?, query: String, fs: FilterSnapshot,
        page: Int, pageSize: Int = VideoListUiState.PAGE_SIZE
    ): List<Video> {
        return videoRepository.getFilteredVideosPaginated(
            query = query.trim().takeIf { it.isNotEmpty() },
            qualityFilter = fs.filter.quality,
            isPortrait = fs.filter.orientation?.let { if (it == Orientation.PORTRAIT) 1 else 0 },
            minDuration = fs.filter.durationRange?.minMs,
            maxDuration = fs.filter.durationRange?.maxMs,
            minSize = fs.filter.sizeRange?.minBytes,
            maxSize = fs.filter.sizeRange?.maxBytes,
            minAddedAt = fs.filter.timeRange?.let { fs.timeAnchorMs - it.minusMs },
            hasCover = fs.filter.hasCover?.let { if (it) 1 else 0 },
            categoryId = categoryId,
            sortField = fs.filter.sortField.name,
            sortAscending = fs.filter.sortAscending,
            page = page,
            pageSize = pageSize
        )
    }

    // ── Pagination ────────────────────────────────────────────

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMoreData) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val batch = loadedBatchCount + 1
            val fs = _filterSnapshot.value
            val more = loadPage(
                categoryId = _categoryId.value,
                query = state.searchQuery, fs = fs, page = batch
            )
            loadedBatchCount = batch
            _uiState.update {
                it.copy(videos = it.videos + more,
                    isLoadingMore = false, hasMoreData = more.size == VideoListUiState.PAGE_SIZE)
            }
        }
    }

    fun assignVideoToCategory(videoId: Long, categoryId: Long?) {
        viewModelScope.launch {
            videoRepository.getVideoById(videoId)?.let {
                videoRepository.updateVideo(it.copy(categoryId = categoryId))
                _categoryRefreshTrigger.value++
                reload()
            }
        }
    }

    /** 批量归类按选中 ID 回查，避免跨分页全选时只更新当前可见页。 */
    fun assignSelectedToCategory(categoryId: Long?) {
        val selectedIds = _uiState.value.selectedIds
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            var updatedCount = 0
            selectedIds.forEach { videoId ->
                videoRepository.getVideoById(videoId)?.let { video ->
                    videoRepository.updateVideo(video.copy(categoryId = categoryId))
                    updatedCount++
                }
            }
            _categoryRefreshTrigger.value++
            exitSelectionMode()
            reload()
            if (updatedCount > 0) {
                showMessage(
                    if (categoryId == null) "已移除 $updatedCount 个视频的分类"
                    else "已将 $updatedCount 个视频加入分类"
                )
            }
        }
    }

    fun deleteVideo(video: Video) {
        viewModelScope.launch {
            videoRepository.deleteVideo(video)
            _uiState.update { state -> state.copy(videos = state.videos.filterNot { it.id == video.id }) }
            reload()
        }
    }

    // ── 多选 / 批量操作 ────────────────────────────────────────

    fun enterSelectionMode() { _uiState.update { it.copy(isSelectionMode = true) } }

    /** Long press starts bulk selection and immediately includes the pressed card. */
    fun startSelectionFromLongPress(videoId: Long) {
        _uiState.update { it.copy(isSelectionMode = true, selectedIds = setOf(videoId)) }
    }

    fun exitSelectionMode() {
        _uiState.update { it.copy(isSelectionMode = false, selectedIds = emptySet()) }
    }

    fun toggleSelection(videoId: Long) {
        _uiState.update { state ->
            val updated = state.selectedIds.toMutableSet()
            if (!updated.add(videoId)) updated.remove(videoId)
            state.copy(selectedIds = updated)
        }
    }

    fun toggleSelectAll() {
        _uiState.update { state ->
            val allIds = state.videos.map { it.id }.toSet()
            val allSelected = state.selectedIds.containsAll(allIds)
            state.copy(selectedIds = if (allSelected) emptySet() else allIds)
        }
    }

    /**
     * 全选当前搜索/筛选条件下的全部匹配视频（跨分页，含未加载的页）；
     * 当前页已全部选中且没有更多数据时，再次点击取消全选。
     */
    fun toggleSelectAllFiltered() {
        val state = _uiState.value
        val allLoadedSelected = state.videos.isNotEmpty() && state.selectedIds.containsAll(state.videos.map { it.id })
        if (allLoadedSelected && !state.hasMoreData) {
            _uiState.update { it.copy(selectedIds = emptySet()) }
            return
        }
        viewModelScope.launch {
            val fs = _filterSnapshot.value
            val all = loadPage(
                categoryId = _categoryId.value,
                query = _searchQuery.value, fs = fs, page = 0, pageSize = Int.MAX_VALUE
            )
            _uiState.update { it.copy(selectedIds = all.map { it.id }.toSet()) }
            if (all.size > state.videos.size) showMessage("已全选 ${all.size} 个匹配视频")
        }
    }

    /** 拖拽连续选择：选中 [fromIndex, toIndex]（含端点）区间内的视频，已选中的保持不变。 */
    fun selectRange(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            if (state.videos.isEmpty()) return@update state
            val start = minOf(fromIndex, toIndex).coerceIn(0, state.videos.size - 1)
            val end = maxOf(fromIndex, toIndex).coerceIn(0, state.videos.size - 1)
            val ids = (start..end).map { state.videos[it].id }
            state.copy(selectedIds = state.selectedIds + ids)
        }
    }

    /** Used by the non-conversion batch actions, which currently operate on visible cards only. */
    private fun selectedVideos(): List<Video> {
        val state = _uiState.value
        return state.videos.filter { it.id in state.selectedIds }
    }

    fun batchConvertToMp4() {
        val selectedIds = _uiState.value.selectedIds
        if (selectedIds.isEmpty()) return
        exitSelectionMode()
        val coordinator = conversionBatchCoordinator
        if (coordinator == null) {
            showMessage("转换服务不可用，请重启应用后重试")
            return
        }
        viewModelScope.launch {
            val videos = selectedIds.map { videoRepository.getVideoById(it) }.filterNotNull()
            val result = coordinator.enqueue(videos)
            val missing = selectedIds.size - videos.size
            showMessage(
                buildString {
                    append("已加入 ${result.queued} 个转换任务，将逐个在后台执行")
                    if (result.skippedAlreadyRunning > 0) append("；${result.skippedAlreadyRunning} 个正在转换")
                    if (missing > 0) append("；${missing} 个已不存在")
                }
            )
        }
    }

    fun batchRefreshOnlineStatus() {
        val videos = selectedVideos()
        if (videos.isEmpty()) return
        exitSelectionMode()
        val service = onlineVideoStatusService ?: return
        viewModelScope.launch {
            var online = 0; var unavailable = 0; var unverifiable = 0
            videos.forEachIndexed { index, video ->
                _uiState.update { it.copy(batchProgress = BatchProgress("刷新在线状态", index, videos.size)) }
                val status = service.forceCheck(video)
                when (status) {
                    OnlineVideoStatus.ONLINE -> online++
                    OnlineVideoStatus.UNAVAILABLE -> unavailable++
                    else -> unverifiable++
                }
                _uiState.update { state ->
                    state.copy(
                        videos = state.videos.map {
                            if (it.id == video.id) it.copy(onlineStatus = status, onlineCheckedAt = System.currentTimeMillis()) else it
                        },
                        batchProgress = BatchProgress("刷新在线状态", index + 1, videos.size)
                    )
                }
            }
            _uiState.update { it.copy(batchProgress = null) }
            showMessage("状态刷新完成：$online 在线、$unavailable 不可用、$unverifiable 无法核验")
        }
    }

    fun batchCheckIntegrity() {
        val videos = selectedVideos()
        if (videos.isEmpty()) return
        exitSelectionMode()
        val checker = integrityChecker ?: return
        viewModelScope.launch {
            val results = mutableListOf<MediaIntegrityChecker.CheckResult>()
            videos.forEachIndexed { index, video ->
                _uiState.update { it.copy(batchProgress = BatchProgress("检查文件完整性", index, videos.size)) }
                results += checker.check(video)
            }
            _uiState.update { it.copy(batchProgress = null) }
            val ok = results.count { it.status == MediaIntegrityStatus.OK }
            val videoMissing = results.count { it.status == MediaIntegrityStatus.VIDEO_MISSING || it.status == MediaIntegrityStatus.BOTH_MISSING }
            val audioMissing = results.count { it.status == MediaIntegrityStatus.AUDIO_MISSING }
            val unknown = results.count { it.status == MediaIntegrityStatus.UNKNOWN }
            showMessage(
                "完整性检查完成：$ok 完好、$videoMissing 视频缺失、$audioMissing 音频缺失、$unknown 无法确认"
            )
        }
    }

    private fun showMessage(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }
}
