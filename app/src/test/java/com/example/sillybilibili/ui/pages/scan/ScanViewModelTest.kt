package com.example.sillybilibili.ui.pages.scan

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.example.sillybilibili.service.SettingsService
import com.example.sillybilibili.service.ScanJobRegistry
import com.example.sillybilibili.service.VideoScanService
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {

    @get:Rule
    val dispatcherRule = StandardTestDispatcherRule()

    private lateinit var context: Context
    private lateinit var settingsService: SettingsService
    private lateinit var videoScanService: VideoScanService
    private lateinit var scanJobRegistry: ScanJobRegistry
    private lateinit var viewModel: ScanViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        context = mockk(relaxed = true)
        settingsService = mockk()
        videoScanService = mockk()
        scanJobRegistry = ScanJobRegistry()

        every { settingsService.scanPath } returns null
        every { videoScanService.getBilibiliPathConstant() } returns "/storage/emulated/0/Android/data/tv.danmaku.bili/download"
        every { videoScanService.isShizukuAvailable() } returns true
        every { videoScanService.canAccessDirectoryDirectly(any()) } returns false
        coEvery { videoScanService.inspectDirectory(any()) } returns VideoScanService.DirectorySnapshot(
            access = VideoScanService.ScanAccess.SHIZUKU,
            totalCacheFolders = 12,
            scannedVideoCount = 8,
            scannedCacheFolderCount = 7
        )
        coEvery { videoScanService.inspectDirectoryFromUri(any()) } returns VideoScanService.DirectorySnapshot()

        val prefs = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { prefs.getString("saf_tree_uri", null) } returns null

        viewModel = ScanViewModel(context, settingsService, videoScanService, scanJobRegistry)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init sets default scan path`() = runTest {
        advanceUntilIdle()
        assertEquals("/storage/emulated/0/Android/data/tv.danmaku.bili/download", viewModel.uiState.value.scanPath)
        assertTrue(viewModel.uiState.value.isShizukuAvailable)
        assertEquals(12, viewModel.uiState.value.directorySnapshot.totalCacheFolders)
        assertEquals(8, viewModel.uiState.value.directorySnapshot.scannedVideoCount)
    }

    @Test
    fun `toggleMode switches between Shizuku and SAF`() = runTest {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.useSaf)

        viewModel.toggleMode()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.useSaf)

        viewModel.toggleMode()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.useSaf)
    }

    @Test
    fun `setSafTreeUri persists URI and enables SAF mode`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.toString() } returns "content://tree/download"

        viewModel.setSafTreeUri(uri)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.useSaf)
        assertNotNull(viewModel.uiState.value.safTreeUri)
    }

    @Test
    fun `setSafTreeUri with null disables SAF mode`() = runTest {
        viewModel.setSafTreeUri(null)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.safTreeUri)
        assertFalse(viewModel.uiState.value.useSaf)
    }

    @Test
    fun `updateScanPath persists path`() = runTest {
        every { settingsService.scanPath = any() } just Runs

        viewModel.updateScanPath("/custom/path")
        advanceUntilIdle()

        assertEquals("/custom/path", viewModel.uiState.value.scanPath)
        verify { settingsService.scanPath = "/custom/path" }
    }

    @Test
    fun `updateFilterQuality stores value`() = runTest {
        viewModel.updateFilterQuality("1080P")
        advanceUntilIdle()
        assertEquals("1080P", viewModel.uiState.value.filterQuality)
    }

    @Test
    fun `updateFilterMinDuration strips non-digits`() = runTest {
        viewModel.updateFilterMinDuration("abc123")
        advanceUntilIdle()
        assertEquals("123", viewModel.uiState.value.filterMinDurationSec)
    }

    @Test
    fun `toggleQuickMode switches state`() = runTest {
        assertFalse(viewModel.uiState.value.filterQuickMode)
        viewModel.toggleQuickMode()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.filterQuickMode)
    }
}

class StandardTestDispatcherRule : TestWatcher() {
    val dispatcher = StandardTestDispatcher()
    override fun starting(description: Description?) { Dispatchers.setMain(dispatcher) }
    override fun finished(description: Description?) { Dispatchers.resetMain() }
}
