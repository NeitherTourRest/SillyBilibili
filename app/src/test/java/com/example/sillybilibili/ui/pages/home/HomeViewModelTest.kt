package com.example.sillybilibili.ui.pages.home

import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.domain.repository.CategoryRepository
import com.example.sillybilibili.domain.repository.VideoRepository
import com.example.sillybilibili.service.COVER_RETRY_INTERVAL_MS
import com.example.sillybilibili.service.CoverCacheService
import com.example.sillybilibili.service.VideoScanService
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val dispatcherRule = StandardTestDispatcherRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var videoRepository: VideoRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var videoScanService: VideoScanService
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        videoRepository = mockk()
        categoryRepository = mockk()
        videoScanService = mockk()

        every { categoryRepository.getAllCategories() } returns flowOf(emptyList())
        coEvery { videoRepository.getFilteredVideosPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { videoRepository.getVideoById(any()) } returns null
        coEvery { videoRepository.getTotalVideoCount() } returns 100
        coEvery { videoRepository.updateVideo(any()) } just Runs
        coEvery { videoRepository.deleteVideo(any()) } just Runs
        coEvery { videoRepository.getAllAvIds() } returns emptyList()

        viewModel = HomeViewModel(videoRepository, categoryRepository, videoScanService)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selectCategory updates selectedCategoryId`() = runTest {
        viewModel.selectCategory(5L)
        advanceUntilIdle()
        assertEquals(5L, viewModel.uiState.value.selectedCategoryId)
    }

    @Test
    fun `selectCategory with same id is no-op`() = runTest {
        viewModel.selectCategory(5L)
        advanceUntilIdle()
        viewModel.selectCategory(5L)
        advanceUntilIdle()
        assertEquals(5L, viewModel.uiState.value.selectedCategoryId)
    }

    @Test
    fun `updateSearchQuery updates state`() = runTest {
        viewModel.updateSearchQuery("test")
        advanceUntilIdle()
        assertEquals("test", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun `applyFilter updates filterState when changed`() = runTest {
        val filter = FilterState(quality = "1080P")
        viewModel.applyFilter(filter)
        advanceUntilIdle()
        assertEquals(filter, viewModel.uiState.value.filterState)
    }

    @Test
    fun `applyFilter same filter twice is no-op`() = runTest {
        val filter = FilterState(quality = "1080P")
        viewModel.applyFilter(filter)
        advanceUntilIdle()
        viewModel.applyFilter(filter)
        advanceUntilIdle()
        assertEquals(filter, viewModel.uiState.value.filterState)
    }

    @Test
    fun `clearFilter resets to empty`() = runTest {
        viewModel.applyFilter(FilterState(quality = "1080P"))
        advanceUntilIdle()
        viewModel.clearFilter()
        advanceUntilIdle()
        assertEquals(FilterState(), viewModel.uiState.value.filterState)
        assertFalse(viewModel.uiState.value.filterState.isActive)
    }

    @Test
    fun `loadMore requests next page from repository`() = runTest {
        val page1 = listOf(Video(avid = 1, cid = 2, title = "T", path = "/v", audioPath = "/a", size = 1, duration = 1))
        coEvery { videoRepository.getFilteredVideosPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(1), any()) } returns page1

        viewModel.loadMore()
        advanceUntilIdle()

        coVerify { videoRepository.getFilteredVideosPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(1), any()) }
    }

    @Test
    fun `loadMore appends the next batch to the list for infinite scroll`() = runTest {
        val firstPage = List(HomeUiState.PAGE_SIZE) { index ->
            Video(id = index.toLong() + 1, avid = index.toLong(), cid = 1, title = "P0-$index", path = "/v/$index", audioPath = "/a/$index", size = 1, duration = 1)
        }
        val secondPage = List(3) { index ->
            Video(id = 200L + index, avid = 200L + index, cid = 1, title = "P1-$index", path = "/v2/$index", audioPath = "/a2/$index", size = 1, duration = 1)
        }
        coEvery { videoRepository.getFilteredVideosPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(0), any()) } returns firstPage
        coEvery { videoRepository.getFilteredVideosPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(1), any()) } returns secondPage

        advanceUntilIdle()
        assertEquals(firstPage, viewModel.uiState.value.videos)

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(firstPage + secondPage, viewModel.uiState.value.videos)
        assertFalse(viewModel.uiState.value.hasMoreData)
    }


    @Test
    fun `assignVideoToCategory updates video category`() = runTest {
        val video = Video(id = 1, avid = 1, cid = 2, title = "T", path = "/v", audioPath = "/a", size = 1, duration = 1)
        coEvery { videoRepository.getVideoById(1) } returns video
        coEvery { videoRepository.updateVideo(any()) } just Runs

        viewModel.assignVideoToCategory(1L, 99L)
        advanceUntilIdle()

        coVerify { videoRepository.updateVideo(video.copy(categoryId = 99L)) }
    }

    @Test
    fun `deleteVideo delegates to repository`() = runTest {
        val video = Video(id = 1, avid = 1, cid = 2, title = "T", path = "/v", audioPath = "/a", size = 1, duration = 1)
        coEvery { videoRepository.deleteVideo(video) } just Runs

        viewModel.deleteVideo(video)
        advanceUntilIdle()

        coVerify { videoRepository.deleteVideo(video) }
    }

    @Test
    fun `deleteVideo reloads the displayed page after removing the record`() = runTest {
        val video = Video(id = 1, avid = 1, cid = 2, title = "T", path = "/v", audioPath = "/a", size = 1, duration = 1)
        coEvery { videoRepository.getFilteredVideosPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(0), any()) } returnsMany listOf(listOf(video), emptyList())

        advanceUntilIdle()
        assertEquals(listOf(video), viewModel.uiState.value.videos)

        viewModel.deleteVideo(video)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.videos.isEmpty())
        coVerify(exactly = 2) { videoRepository.getFilteredVideosPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(0), any()) }
    }

    @Test
    fun `clearError clears errorMessage`() = runTest {
        viewModel.clearError()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `requestCover success is requested only once per session`() = runTest {
        val coverService = mockk<CoverCacheService>()
        coEvery { coverService.cacheCover(any()) } returns "/cache/covers/1_2.jpg"
        viewModel = HomeViewModel(videoRepository, categoryRepository, videoScanService, coverService)
        val video = Video(id = 1, avid = 1, cid = 2, title = "T", path = "/v", audioPath = "/a", size = 1, duration = 1)

        viewModel.requestCover(video)
        advanceUntilIdle()
        viewModel.requestCover(video)
        advanceUntilIdle()

        coVerify(exactly = 1) { coverService.cacheCover(video) }
    }

    @Test
    fun `requestCover failure is rate limited and retried after interval`() = runTest {
        val coverService = mockk<CoverCacheService>()
        coEvery { coverService.cacheCover(any()) } returns null
        viewModel = HomeViewModel(videoRepository, categoryRepository, videoScanService, coverService)
        var fakeNow = 1_000_000L
        viewModel.coverClock = { fakeNow }
        val video = Video(id = 2, avid = 2, cid = 3, title = "T", path = "/v", audioPath = "/a", size = 1, duration = 1)

        viewModel.requestCover(video)
        advanceUntilIdle()
        // 失败后立即重试被限频拦截
        viewModel.requestCover(video)
        advanceUntilIdle()
        coVerify(exactly = 1) { coverService.cacheCover(video) }

        // 超过重试间隔后允许再次尝试
        fakeNow += COVER_RETRY_INTERVAL_MS + 1_000
        viewModel.requestCover(video)
        advanceUntilIdle()
        coVerify(exactly = 2) { coverService.cacheCover(video) }
    }

    @Test
    fun `selectRange selects the range between indices inclusive`() = runTest {
        val videos = List(5) { index ->
            Video(id = index.toLong() + 1, avid = index.toLong(), cid = 1, title = "T$index", path = "/v$index", audioPath = "/a$index", size = 1, duration = 1)
        }
        coEvery { videoRepository.getFilteredVideosPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns videos
        advanceUntilIdle()

        viewModel.enterSelectionMode()
        viewModel.selectRange(1, 3)
        assertEquals(setOf(2L, 3L, 4L), viewModel.uiState.value.selectedIds)
    }

    @Test
    fun `selectRange handles reversed indices`() = runTest {
        val videos = List(5) { index ->
            Video(id = index.toLong() + 1, avid = index.toLong(), cid = 1, title = "T$index", path = "/v$index", audioPath = "/a$index", size = 1, duration = 1)
        }
        coEvery { videoRepository.getFilteredVideosPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns videos
        advanceUntilIdle()

        viewModel.selectRange(3, 1)
        assertEquals(setOf(2L, 3L, 4L), viewModel.uiState.value.selectedIds)
    }

    @Test
    fun `selectRange keeps previously selected ids`() = runTest {
        val videos = List(5) { index ->
            Video(id = index.toLong() + 1, avid = index.toLong(), cid = 1, title = "T$index", path = "/v$index", audioPath = "/a$index", size = 1, duration = 1)
        }
        coEvery { videoRepository.getFilteredVideosPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns videos
        advanceUntilIdle()

        viewModel.selectRange(0, 0)
        viewModel.selectRange(3, 4)
        assertEquals(setOf(1L, 4L, 5L), viewModel.uiState.value.selectedIds)
    }

    @Test
    fun `selectRange clamps out of bounds indices`() = runTest {
        val videos = List(3) { index ->
            Video(id = index.toLong() + 1, avid = index.toLong(), cid = 1, title = "T$index", path = "/v$index", audioPath = "/a$index", size = 1, duration = 1)
        }
        coEvery { videoRepository.getFilteredVideosPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns videos
        advanceUntilIdle()

        viewModel.selectRange(-5, 99)
        assertEquals(setOf(1L, 2L, 3L), viewModel.uiState.value.selectedIds)
    }

    @Test
    fun `toggleSelectAllFiltered selects all matching videos across pages`() = runTest {
        val page1 = List(3) { index ->
            Video(id = index.toLong() + 1, avid = index.toLong(), cid = 1, title = "T$index", path = "/v$index", audioPath = "/a$index", size = 1, duration = 1)
        }
        val all = List(8) { index ->
            Video(id = index.toLong() + 1, avid = index.toLong(), cid = 1, title = "T$index", path = "/v$index", audioPath = "/a$index", size = 1, duration = 1)
        }
        coEvery { videoRepository.getFilteredVideosPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(Int.MAX_VALUE)) } returns all
        advanceUntilIdle()

        viewModel.enterSelectionMode()
        viewModel.toggleSelectAllFiltered()
        advanceUntilIdle()

        assertEquals(all.map { it.id }.toSet(), viewModel.uiState.value.selectedIds)
    }

    @Test
    fun `toggleSelectAllFiltered deselects when everything is selected and loaded`() = runTest {
        val videos = List(3) { index ->
            Video(id = index.toLong() + 1, avid = index.toLong(), cid = 1, title = "T$index", path = "/v$index", audioPath = "/a$index", size = 1, duration = 1)
        }
        coEvery { videoRepository.getFilteredVideosPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns videos
        advanceUntilIdle()

        viewModel.toggleSelectAllFiltered()
        advanceUntilIdle()
        assertEquals(setOf(1L, 2L, 3L), viewModel.uiState.value.selectedIds)

        viewModel.toggleSelectAllFiltered()
        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())
    }

    @Test
    fun `batchRefreshOnlineStatus reports progress and clears it at the end`() = runTest {
        val videos = listOf(
            Video(id = 11, avid = 1, cid = 1, title = "A", path = "/v1", audioPath = "/a1", size = 1, duration = 1),
            Video(id = 12, avid = 2, cid = 2, title = "B", path = "/v2", audioPath = "/a2", size = 1, duration = 1),
            Video(id = 13, avid = 3, cid = 3, title = "C", path = "/v3", audioPath = "/a3", size = 1, duration = 1)
        )
        coEvery { videoRepository.getFilteredVideosPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns videos
        val statusService = mockk<com.example.sillybilibili.service.OnlineVideoStatusService>()
        coEvery { statusService.forceCheck(any()) } returns com.example.sillybilibili.domain.model.OnlineVideoStatus.ONLINE
        viewModel = HomeViewModel(videoRepository, categoryRepository, videoScanService, null, null, statusService)
        advanceUntilIdle()

        viewModel.enterSelectionMode()
        viewModel.toggleSelectAll()
        viewModel.batchRefreshOnlineStatus()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.batchProgress)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("3 在线"))
        coVerify(exactly = 3) { statusService.forceCheck(any()) }
    }

    @Test
    fun `batchCheckIntegrity checks each video and clears progress`() = runTest {
        val videos = listOf(
            Video(id = 21, avid = 1, cid = 1, title = "A", path = "/v1", audioPath = "/a1", size = 1, duration = 1),
            Video(id = 22, avid = 2, cid = 2, title = "B", path = "/v2", audioPath = "/a2", size = 1, duration = 1)
        )
        coEvery { videoRepository.getFilteredVideosPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns videos
        val checker = mockk<com.example.sillybilibili.service.MediaIntegrityChecker>()
        coEvery { checker.check(any()) } returns com.example.sillybilibili.service.MediaIntegrityChecker.CheckResult(0L, com.example.sillybilibili.service.MediaIntegrityStatus.OK)
        viewModel = HomeViewModel(videoRepository, categoryRepository, videoScanService, null, null, null, checker)
        advanceUntilIdle()

        viewModel.enterSelectionMode()
        viewModel.toggleSelectAll()
        viewModel.batchCheckIntegrity()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.batchProgress)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("2 完好"))
        coVerify(exactly = 2) { checker.check(any()) }
    }

    @Test
    fun `toggleGridView flips grid view state`() = runTest {
        assertFalse(viewModel.uiState.value.gridViewEnabled)
        viewModel.toggleGridView()
        assertTrue(viewModel.uiState.value.gridViewEnabled)
        viewModel.toggleGridView()
        assertFalse(viewModel.uiState.value.gridViewEnabled)
    }

    @Test
    fun `selection mode toggles, selects all and reports selection`() = runTest {
        val videos = listOf(
            Video(id = 11, avid = 1, cid = 1, title = "A", path = "/v1", audioPath = "/a1", size = 1, duration = 1),
            Video(id = 12, avid = 2, cid = 2, title = "B", path = "/v2", audioPath = "/a2", size = 1, duration = 1)
        )
        coEvery { videoRepository.getFilteredVideosPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns videos
        advanceUntilIdle()

        viewModel.enterSelectionMode()
        assertTrue(viewModel.uiState.value.isSelectionMode)

        viewModel.toggleSelection(11L)
        assertEquals(setOf(11L), viewModel.uiState.value.selectedIds)

        viewModel.toggleSelectAll()
        assertEquals(setOf(11L, 12L), viewModel.uiState.value.selectedIds)
        assertEquals(listOf(11L, 12L), viewModel.selectedVideos().map { it.id })

        viewModel.toggleSelectAll()
        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())

        viewModel.exitSelectionMode()
        assertFalse(viewModel.uiState.value.isSelectionMode)
    }
}

class StandardTestDispatcherRule : TestWatcher() {
    val dispatcher = StandardTestDispatcher()
    override fun starting(description: Description?) { Dispatchers.setMain(dispatcher) }
    override fun finished(description: Description?) { Dispatchers.resetMain() }
}