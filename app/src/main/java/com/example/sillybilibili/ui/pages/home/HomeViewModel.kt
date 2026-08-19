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
import com.example.sillybilibili.service.VideoScanService
import com.example.sillybilibili.service.CoverCacheService
import com.example.sillybilibili.service.ExternalMediaSyncService
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

private const val SEARCH_DEBOUNCE_MS = 300L
internal const val HOME_PREFETCH_DISTANCE = 12
private const val HOME_PAGE_SWITCH_DISTANCE = 2

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
    val scanProgress: VideoScanService.ScanProgress? = null, val errorMessage: String? = null,
    val isSelectionMode: Boolean = false, val selectedIds: Set<Long> = emptySet(),
    /** 扫描库中的视频总数（sourceAvailable = 1），用于页头位置指示。 */
    val totalVideoCount: Int = 0
) { companion object { const val PAGE_SIZE = 40 } }

/** Starts the next database page early enough that long lists do not visibly pause at the end. */
internal fun shouldPrefetchHomePage(
    lastVisibleIndex: Int?,
    loadedItemCount: Int,
    hasMoreData: Boolean
): Boolean = hasMoreData && lastVisibleIndex != null && loadedItemCount > 0 &&
    lastVisibleIndex >= loadedItemCount - HOME_PREFETCH_DISTANCE

/** A page change happens only at the tail, after the following page has been warmed up. */
internal fun shouldAdvanceHomePage(
    lastVisibleIndex: Int?,
    loadedItemCount: Int,
    hasMoreData: Boolean
): Boolean = hasMoreData && lastVisibleIndex != null && loadedItemCount > 0 &&
    lastVisibleIndex >= loadedItemCount - HOME_PAGE_SWITCH_DISTANCE

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

private data class PrefetchedHomePage(
    val epoch: Long,
    val page: Int,
    val videos: List<Video>
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    private val categoryRepository: CategoryRepository,
    private val videoScanService: VideoScanService,
    private val coverCacheService: CoverCacheService? = null,
    private val externalMediaSyncService: ExternalMediaSyncService? = null,
    private val onlineVideoStatusService: OnlineVideoStatusService? = null,
    private val integrityChecker: MediaIntegrityChecker? = null,
    private val conversionJobRegistry: ConversionJobRegistry? = null,
    private val settingsService: SettingsService? = null,
    private val videoConverterService: VideoConverterService? = null,
    @ApplicationContext private val appContext: Context? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _refreshTrigger = MutableStateFlow(0L)
    private val _categoryRefreshTrigger = MutableStateFlow(0L)
    private val _filterSnapshot = MutableStateFlow(FilterSnapshot(FilterState(), "", 0L))
    /** Invalidates preloaded pages after search, filter, category or database changes. */
    private var pagingEpoch = 0L
    private var prefetchedPage: PrefetchedHomePage? = null

    /**
     * 卡片进入屏幕时每个视频的封面/在线核验在会话内只做必要的次数：成功的请求永久去重，
     * 失败的（源封面缺失且抽帧失败）限频重试，避免滚动时反复排队；信号量限制并发，
     * 避免首屏一次性打爆磁盘 IO（抽帧需复制 m4s）和网络。
     */
    private val coverRequested = HashSet<Long>()
    private val statusRequested = HashSet<Long>()
    private val coverFailedAt = HashMap<Long, Long>()
    private val coverSemaphore = Semaphore(2)
    private val statusSemaphore = Semaphore(3)

    /** 可注入的时间源（测试中用于推进限频重试间隔）。 */
    internal var coverClock: () -> Long = System::currentTimeMillis

    /** 批量转换队列：一次只跑一个，前一个结束后自动启动下一个。 */
    private val conversionQueue = ArrayDeque<Video>()
    private var conversionPump: Job? = null

    private val _debouncedSearch = _searchQuery.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged()

    init { loadCategories(); loadVideos(); reconcileExternalFiles() }

    private fun reconcileExternalFiles() {
        externalMediaSyncService ?: return
        viewModelScope.launch { externalMediaSyncService.reconcileExportedFiles() }
    }

    fun requestCover(video: Video) {
        val service = coverCacheService ?: return
        if (video.id in coverRequested) return
        val lastFail = coverFailedAt[video.id]
        if (lastFail != null && coverClock() - lastFail < COVER_RETRY_INTERVAL_MS) return
        viewModelScope.launch {
            coverSemaphore.withPermit {
                val cachedPath = service.cacheCover(video)
                if (cachedPath == null) {
                    // 失败限频重试：间隔过后卡片再次进入组合时会重新尝试生成封面
                    coverFailedAt[video.id] = coverClock()
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
        // 每个视频每会话最多核验一次，避免滚动时反复发起网络请求。
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
                    val epoch = ++pagingEpoch
                    prefetchedPage = null
                    // 已有列表时静默刷新：旧结果保留到新结果到达，避免闪烁。
                    _uiState.update { it.copy(isLoading = it.videos.isEmpty(), isLoadingMore = false) }
                    val videos = loadPage(
                        categoryId = key.categoryId,
                        query = key.query, fs = fs, page = 0
                    )
                    if (epoch != pagingEpoch) return@collectLatest
                    val totalVideoCount = videoRepository.getTotalVideoCount()
                    _uiState.update {
                        it.copy(
                            videos = videos,
                            selectedCategoryId = key.categoryId,
                            filterState = fs.filter,
                            currentPage = 0,
                            isLoading = false,
                            hasMoreData = videos.size == HomeUiState.PAGE_SIZE,
                            totalVideoCount = totalVideoCount
                        )
                    }
                    if (videos.size == HomeUiState.PAGE_SIZE) {
                        prefetchPage(page = 1, epoch = epoch, key = key, fs = fs)
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

    /** Preloads the following database page without creating more Compose card nodes. */
    fun prefetchNextPage() {
        val state = _uiState.value
        if (!state.hasMoreData || state.isLoading || state.isLoadingMore) return
        val page = state.currentPage + 1
        val cache = prefetchedPage
        if (cache?.epoch == pagingEpoch && cache.page == page) return
        prefetchPage(
            page = page,
            epoch = pagingEpoch,
            key = currentLoadKey(),
            fs = _filterSnapshot.value
        )
    }

    /** Replaces the visible page instead of appending indefinitely, keeping scrolling fast. */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || state.isLoading || !state.hasMoreData) return
        val page = state.currentPage + 1
        val epoch = pagingEpoch
        val key = currentLoadKey()
        val fs = _filterSnapshot.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val cached = prefetchedPage?.takeIf { it.epoch == epoch && it.page == page }
            val videos = cached?.videos ?: loadPage(key.categoryId, key.query, fs, page)
            if (epoch != pagingEpoch) return@launch
            prefetchedPage = null
            _uiState.update {
                it.copy(
                    videos = videos,
                    currentPage = page,
                    isLoadingMore = false,
                    hasMoreData = videos.size == HomeUiState.PAGE_SIZE
                )
            }
            if (videos.size == HomeUiState.PAGE_SIZE) {
                prefetchPage(page + 1, epoch, key, fs)
            }
        }
    }

    fun goToPage(page: Int) {
        if (page < 0) return
        viewModelScope.launch {
            val epoch = ++pagingEpoch
            prefetchedPage = null
            _uiState.update { it.copy(isLoading = it.videos.isEmpty()) }
            val fs = _filterSnapshot.value
            val videos = loadPage(
                categoryId = _selectedCategoryId.value,
                query = _searchQuery.value, fs = fs, page = page
            )
            if (epoch != pagingEpoch) return@launch
            _uiState.update {
                it.copy(videos = videos, currentPage = page, isLoading = false,
                    hasMoreData = videos.size == HomeUiState.PAGE_SIZE)
            }
            if (videos.size == HomeUiState.PAGE_SIZE) {
                prefetchPage(page + 1, epoch, currentLoadKey(), fs)
            }
        }
    }

    fun loadAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.videos.isEmpty()) }
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

    /** 批量转换为 MP4：一次一个，逐个排队执行（由前台服务串行处理）。 */
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

    /** 批量强制刷新在线状态（忽略缓存窗口）。 */
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

    /** 批量检查媒体文件完整性（video/audio.m4s 是否存在且非空）。 */
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

    private fun currentLoadKey(): LoadKey = LoadKey(
        categoryId = _selectedCategoryId.value,
        query = _searchQuery.value,
        filter = _filterSnapshot.value.filter,
        refreshVersion = _refreshTrigger.value
    )

    private fun prefetchPage(page: Int, epoch: Long, key: LoadKey, fs: FilterSnapshot) {
        if (prefetchedPage?.let { it.epoch == epoch && it.page == page } == true) return
        viewModelScope.launch {
            val videos = loadPage(key.categoryId, key.query, fs, page)
            if (epoch == pagingEpoch) prefetchedPage = PrefetchedHomePage(epoch, page, videos)
        }
    }
}
