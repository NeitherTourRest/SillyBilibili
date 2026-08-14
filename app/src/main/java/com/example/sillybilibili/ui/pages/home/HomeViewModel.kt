package com.example.sillybilibili.ui.pages.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sillybilibili.domain.model.Category
import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.domain.repository.CategoryRepository
import com.example.sillybilibili.domain.repository.VideoRepository
import com.example.sillybilibili.service.VideoScanService
import com.example.sillybilibili.service.CoverCacheService
import com.example.sillybilibili.service.ExternalMediaSyncService
import com.example.sillybilibili.service.OnlineVideoStatusService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MS = 300L

enum class Orientation { LANDSCAPE, PORTRAIT }

enum class DurationRange(val label: String, val minMs: Long, val maxMs: Long?) {
    UNDER_1MIN("<1min", 0, 60_000), MIN_1_5("1-5min", 60_000, 300_000),
    MIN_5_30("5-30min", 300_000, 1_800_000), OVER_30MIN(">30min", 1_800_000, null)
}

enum class SizeRange(val label: String, val minBytes: Long, val maxBytes: Long?) {
    UNDER_10MB("<10MB", 0, 10_485_760), MB_10_50("10-50MB", 10_485_760, 52_428_800),
    MB_50_100("50-100MB", 52_428_800, 104_857_600), OVER_100MB(">100MB", 104_857_600, null)
}

enum class TimeRange(val label: String, val minusMs: Long) {
    TODAY("today", 86_400_000), WEEK("7days", 604_800_000), MONTH("30days", 2_592_000_000)
}

data class FilterState(
    val quality: String? = null, val orientation: Orientation? = null,
    val durationRange: DurationRange? = null, val sizeRange: SizeRange? = null,
    val timeRange: TimeRange? = null, val hasCover: Boolean? = null
) {
    val isActive: Boolean get() = quality != null || orientation != null || durationRange != null || sizeRange != null || timeRange != null || hasCover != null
}

data class HomeUiState(
    val videos: List<Video> = emptyList(), val categories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null, val searchQuery: String = "",
    val filterState: FilterState = FilterState(), val currentPage: Int = 0,
    val isLoading: Boolean = false, val isLoadingMore: Boolean = false,
    val hasMoreData: Boolean = true, val isScanning: Boolean = false,
    val scanProgress: VideoScanService.ScanProgress? = null, val errorMessage: String? = null
) { companion object { const val PAGE_SIZE = 20 } }

internal data class FilterSnapshot(
    val filter: FilterState,
    val querySnapshot: String,
    val timeAnchorMs: Long
)

/** Key used for distinctUntilChanged — avoids redundant DB calls when no actual change. */
private data class LoadKey(
    val categoryId: Long?,
    val query: String,
    val filter: FilterState,
    /** Forces a new database query after a mutation even when filters are unchanged. */
    val refreshVersion: Long
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    private val categoryRepository: CategoryRepository,
    private val videoScanService: VideoScanService,
    private val coverCacheService: CoverCacheService? = null,
    private val externalMediaSyncService: ExternalMediaSyncService? = null,
    private val onlineVideoStatusService: OnlineVideoStatusService? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _refreshTrigger = MutableStateFlow(0L)
    private val _categoryRefreshTrigger = MutableStateFlow(0L)
    private val _filterSnapshot = MutableStateFlow(FilterSnapshot(FilterState(), "", 0L))

    private val _debouncedSearch = _searchQuery.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged()

    init { loadCategories(); loadVideos(); reconcileExternalFiles() }

    private fun reconcileExternalFiles() {
        externalMediaSyncService ?: return
        viewModelScope.launch { externalMediaSyncService.reconcileExportedFiles() }
    }

    fun requestCover(video: Video) {
        val service = coverCacheService ?: return
        viewModelScope.launch {
            service.cacheCover(video)?.let { cachedPath ->
                videoRepository.updateVideo(video.copy(coverPath = cachedPath))
                _refreshTrigger.value++
            }
        }
    }

    fun requestOnlineStatus(video: Video) {
        val service = onlineVideoStatusService ?: return
        viewModelScope.launch {
            val status = service.checkIfNeeded(video)
            _uiState.update { state ->
                state.copy(videos = state.videos.map {
                    if (it.id == video.id) it.copy(onlineStatus = status, onlineCheckedAt = System.currentTimeMillis()) else it
                })
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            _categoryRefreshTrigger.flatMapLatest { categoryRepository.getAllCategories() }
                .collect { categories -> _uiState.update { it.copy(categories = categories) } }
        }
    }

    private fun loadVideos() {
        viewModelScope.launch {
            combine(
                _selectedCategoryId,
                _debouncedSearch,
                _refreshTrigger,
                _filterSnapshot
            ) { catId, debouncedQ, refreshVersion, fs ->
                // Use debounced search as primary; only fall back to snapshot when
                // debounce hasn't caught up (race between typing and filter apply)
                val effectiveQ = if (fs.filter.isActive && debouncedQ.isEmpty()) fs.querySnapshot else debouncedQ
                LoadKey(catId, effectiveQ, fs.filter, refreshVersion) to fs
            }
                .distinctUntilChanged { (old, _), (new, _) -> old == new }
                .collectLatest { (key, fs) ->
                    val videos = loadPage(
                        categoryId = _selectedCategoryId.value,
                        query = key.query, fs = fs, page = 0
                    )
                    _uiState.update {
                        it.copy(
                            videos = videos,
                            selectedCategoryId = _selectedCategoryId.value,
                            filterState = fs.filter,
                            currentPage = 0,
                            isLoading = false,
                            hasMoreData = videos.size == HomeUiState.PAGE_SIZE
                        )
                    }
                }
        }
    }

    // ── Single filter-to-DAO mapping ──────────────────────────

    private suspend fun loadPage(
        categoryId: Long?, query: String, fs: FilterSnapshot,
        page: Int, pageSize: Int = HomeUiState.PAGE_SIZE
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
                categoryId = _selectedCategoryId.value,
                query = state.searchQuery, fs = fs, page = nextPage
            )
            _uiState.update {
                it.copy(videos = it.videos + more, currentPage = nextPage,
                    isLoadingMore = false, hasMoreData = more.size == HomeUiState.PAGE_SIZE)
            }
        }
    }

    fun goToPage(page: Int) {
        if (page < 0) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val fs = _filterSnapshot.value
            val videos = loadPage(
                categoryId = _selectedCategoryId.value,
                query = _searchQuery.value, fs = fs, page = page
            )
            _uiState.update {
                it.copy(videos = videos, currentPage = page, isLoading = false,
                    hasMoreData = videos.size == HomeUiState.PAGE_SIZE)
            }
        }
    }

    fun loadAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val fs = _filterSnapshot.value
            val all = loadPage(
                categoryId = _selectedCategoryId.value,
                query = _searchQuery.value, fs = fs, page = 0, pageSize = Int.MAX_VALUE
            )
            _uiState.update { it.copy(videos = all, isLoading = false, hasMoreData = false) }
        }
    }

    // ── User actions (with no-op guards) ──────────────────────

    fun refreshVideos() { _refreshTrigger.value++ }

    fun selectCategory(categoryId: Long?) {
        if (_selectedCategoryId.value == categoryId) return
        _selectedCategoryId.value = categoryId
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun applyFilter(filter: FilterState) {
        if (_filterSnapshot.value.filter == filter) return
        _filterSnapshot.value = FilterSnapshot(
            filter = filter,
            querySnapshot = _searchQuery.value,
            timeAnchorMs = System.currentTimeMillis()
        )
    }

    fun clearFilter() {
        val empty = FilterState()
        if (_filterSnapshot.value.filter == empty) return
        _filterSnapshot.value = FilterSnapshot(empty, "", 0L)
    }

    fun assignVideoToCategory(videoId: Long, categoryId: Long?) {
        viewModelScope.launch {
            videoRepository.getVideoById(videoId)?.let {
                videoRepository.updateVideo(it.copy(categoryId = categoryId))
                _categoryRefreshTrigger.value++
                refreshVideos()
            }
        }
    }

    fun deleteVideo(video: Video) {
        viewModelScope.launch {
            videoRepository.deleteVideo(video)
            _categoryRefreshTrigger.value++
            _uiState.update { state -> state.copy(videos = state.videos.filterNot { it.id == video.id }) }
            refreshVideos()
        }
    }

    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }
    suspend fun getDefaultScanPath(): String? = videoScanService.getDefaultBilibiliPath()
}
