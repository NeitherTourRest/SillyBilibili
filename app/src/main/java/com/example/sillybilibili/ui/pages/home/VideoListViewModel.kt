package com.example.sillybilibili.ui.pages.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sillybilibili.domain.model.Category
import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.domain.repository.CategoryRepository
import com.example.sillybilibili.domain.repository.VideoRepository
import com.example.sillybilibili.service.CoverCacheService
import com.example.sillybilibili.service.OnlineVideoStatusService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    val filterState: FilterState = FilterState()
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
    private val onlineVideoStatusService: OnlineVideoStatusService? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoListUiState())
    val uiState: StateFlow<VideoListUiState> = _uiState.asStateFlow()

    private val _categoryId = MutableStateFlow<Long?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _categoryRefreshTrigger = MutableStateFlow(0L)
    private val _loadDataTrigger = MutableStateFlow(0L)
    private val _filterSnapshot = MutableStateFlow(FilterSnapshot(FilterState(), "", 0L))

    private val _debouncedSearch = _searchQuery.debounce(300L).distinctUntilChanged()

    fun requestCover(video: Video) {
        viewModelScope.launch {
            coverCacheService.cacheCover(video)?.let { cachedPath ->
                videoRepository.updateVideo(video.copy(coverPath = cachedPath))
                reload()
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
            _uiState.update { it.copy(isLoading = true) }
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
            _uiState.update { it.copy(isLoading = true) }
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
}
