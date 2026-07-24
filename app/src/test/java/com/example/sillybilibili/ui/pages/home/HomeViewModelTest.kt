package com.example.sillybilibili.ui.pages.home

import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.domain.repository.CategoryRepository
import com.example.sillybilibili.domain.repository.VideoRepository
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
    fun `goToPage requests target page from repository`() = runTest {
        val page2 = listOf(Video(avid = 3, cid = 4, title = "T", path = "/v", audioPath = "/a", size = 1, duration = 1))
        coEvery { videoRepository.getFilteredVideosPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(2), any()) } returns page2

        viewModel.goToPage(2)
        advanceUntilIdle()

        coVerify { videoRepository.getFilteredVideosPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(2), any()) }
    }

    @Test
    fun `goToPage negative page is ignored`() = runTest {
        viewModel.goToPage(-1)
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.currentPage)
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
    fun `clearError clears errorMessage`() = runTest {
        viewModel.clearError()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)
    }
}

class StandardTestDispatcherRule : TestWatcher() {
    val dispatcher = StandardTestDispatcher()
    override fun starting(description: Description?) { Dispatchers.setMain(dispatcher) }
    override fun finished(description: Description?) { Dispatchers.resetMain() }
}
