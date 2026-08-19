package com.example.sillybilibili.ui.pages.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sillybilibili.domain.model.Category
import com.example.sillybilibili.domain.model.OnlineVideoStatus
import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.domain.repository.CategoryRepository
import com.example.sillybilibili.domain.repository.VideoRepository
import com.example.sillybilibili.service.ConversionForegroundService
import com.example.sillybilibili.service.ConversionJobRegistry
import com.example.sillybilibili.service.MediaIntegrityChecker
import com.example.sillybilibili.service.MediaIntegrityStatus
import com.example.sillybilibili.service.CoverCacheService
import com.example.sillybilibili.service.COVER_RETRY_INTERVAL_MS
import com.example.sillybilibili.service.OnlineVideoStatusService
import com.example.sillybilibili.service.SettingsService
import com.example.sillybilibili.service.VideoConverterService
import com.example.sillybilibili.service.shouldPersistCoverPath
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
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
    val currentPage: Int = 0,
    val categories: List<Category> = emptyList(),
    val filterState: FilterState = FilterState(),
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
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
    private val conversionJobRegistry: ConversionJobRegistry? = null,
    private val settingsService: SettingsService? = null,
    private val videoConverterService: VideoConverterService? = null,
    @ApplicationContext private val appContext: Context? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoListUiState())
    val uiState: StateFlow<VideoListUiState> = _uiState.asStateFlow()

    private val _categoryId = MutableStateFlow<Long?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _categoryRefreshTrigger = MutableStateFlow(0L)
    private val _loadDataTrigger = MutableStateFlow(0L)
    private val _filterSnapshot = MutableStateFlow(FilterSnapshot(FilterState(), "", 0L))

    private val _debouncedSearch = _searchQuery.debounce(300L).distinctUntilChanged()

    /** 批量转换队列：一次只跑一个，前一个结束后自动启动下一个。 */
    private val conversionQueue = ArrayDeque<Video>()
    private var conversionPump: Job? = null

    /** 封面成功请求会话内去重；失败限频重试；并发受限，避免滚动时爆发 IO。 */
    private val coverRequested = HashSet<Long>()
    private val statusRequested = HashSet<Long>()
    private val coverFailedAt = HashMap<Long, Long>()
    private val coverSemaphore = Semaphore(2)
    private val statusSemaphore = Semaphore(3)

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

                    _uiState.update {
                        it.copy(videos = videos, categories = categories, categoryName = categoryName,
                            currentPage = 0, isLoading = false, searchQuery = query,
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
            val nextPage = state.currentPage + 1
            val fs = _filterSnapshot.value
            val more = loadPage(
                categoryId = _categoryId.value,
                query = state.searchQuery, fs = fs, page = nextPage
            )
            _uiState.update {
                it.copy(videos = it.videos + more, currentPage = nextPage,
                    isLoadingMore = false, hasMoreData = more.size == VideoListUiState.PAGE_SIZE)
            }
        }
    }

    fun goToPage(page: Int) {
        if (page < 0) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.videos.isEmpty()) }
            val fs = _filterSnapshot.value
            val videos = loadPage(
                categoryId = _categoryId.value,
                query = _searchQuery.value, fs = fs, page = page
            )
            _uiState.update {
                it.copy(videos = videos, currentPage = page, isLoading = false,
                    hasMoreData = videos.size == VideoListUiState.PAGE_SIZE)
            }
        }
    }

    fun loadAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.videos.isEmpty()) }
            val fs = _filterSnapshot.value
            val all = loadPage(
                categoryId = _categoryId.value,
                query = _searchQuery.value, fs = fs, page = 0, pageSize = Int.MAX_VALUE
            )
            _uiState.update { it.copy(videos = all, isLoading = false, hasMoreData = false) }
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

    fun deleteVideo(video: Video) {
        viewModelScope.launch {
            videoRepository.deleteVideo(video)
            _uiState.update { state -> state.copy(videos = state.videos.filterNot { it.id == video.id }) }
            reload()
        }
    }

    // ── 多选 / 批量操作 ────────────────────────────────────────

    fun enterSelectionMode() { _uiState.update { it.copy(isSelectionMode = true) } }

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

    fun selectedVideos(): List<Video> {
        val state = _uiState.value
        return state.videos.filter { it.id in state.selectedIds }
    }

    fun batchConvertToMp4() {
        val videos = selectedVideos()
        if (videos.isEmpty()) return
        exitSelectionMode()
        val registry = conversionJobRegistry ?: return
        val context = appContext ?: return
        conversionQueue.addAll(videos)
        showMessage("已加入 ${videos.size} 个转换任务，将逐个执行")
        if (conversionPump == null) {
            conversionPump = viewModelScope.launch {
                registry.jobs.collect { launchNextConversion(context) }
            }
        }
        launchNextConversion(context)
    }

    private fun launchNextConversion(context: Context) {
        val registry = conversionJobRegistry ?: return
        val next = conversionQueue.firstOrNull() ?: return
        if (registry.isRunning(next.id)) return
        conversionQueue.removeFirst()
        val outputDir = settingsService?.outputPath ?: videoConverterService?.getDefaultOutputPath() ?: return
        ConversionForegroundService.start(
            context,
            ConversionForegroundService.ConversionRequest(
                videoId = next.id,
                videoPath = next.path,
                audioPath = next.audioPath,
                outputDir = outputDir,
                outputFileName = next.title
            )
        )
    }

    fun batchRefreshOnlineStatus() {
        val videos = selectedVideos()
        if (videos.isEmpty()) return
        exitSelectionMode()
        val service = onlineVideoStatusService ?: return
        viewModelScope.launch {
            var online = 0; var unavailable = 0; var unverifiable = 0
            videos.forEach { video ->
                val status = service.forceCheck(video)
                when (status) {
                    OnlineVideoStatus.ONLINE -> online++
                    OnlineVideoStatus.UNAVAILABLE -> unavailable++
                    else -> unverifiable++
                }
                _uiState.update { state ->
                    state.copy(videos = state.videos.map {
                        if (it.id == video.id) it.copy(onlineStatus = status, onlineCheckedAt = System.currentTimeMillis()) else it
                    })
                }
            }
            showMessage("状态刷新完成：$online 在线、$unavailable 不可用、$unverifiable 无法核验")
        }
    }

    fun batchCheckIntegrity() {
        val videos = selectedVideos()
        if (videos.isEmpty()) return
        exitSelectionMode()
        val checker = integrityChecker ?: return
        viewModelScope.launch {
            val results = checker.checkAll(videos)
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
