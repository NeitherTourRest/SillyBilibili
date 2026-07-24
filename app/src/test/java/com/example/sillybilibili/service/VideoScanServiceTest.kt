package com.example.sillybilibili.service

import android.content.Context
import android.content.SharedPreferences
import android.content.ContentResolver
import android.net.Uri
import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.domain.repository.VideoRepository
import com.example.sillybilibili.util.SafFileHelper
import com.example.sillybilibili.util.ShizukuFileHelper
import com.example.sillybilibili.util.ThumbnailHelper
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class VideoScanServiceTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var shizukuHelper: ShizukuFileHelper
    private lateinit var safHelper: SafFileHelper
    private lateinit var thumbnailHelper: ThumbnailHelper
    private lateinit var videoRepository: VideoRepository
    private lateinit var service: VideoScanService

    @Before
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)

        context = mockk(relaxed = true)
        shizukuHelper = mockk()
        safHelper = mockk()
        thumbnailHelper = mockk()
        videoRepository = mockk(relaxed = true)

        every { thumbnailHelper.extractFrame(any<String>(), any(), any()) } returns false
        every { thumbnailHelper.extractFrame(any<Uri>(), any(), any()) } returns false
        every { thumbnailHelper.resolveChildUri(any(), any()) } returns null

        val prefs = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { context.contentResolver } returns mockk<ContentResolver>(relaxed = true)
        every { context.cacheDir } returns createTempDir("cache")

        service = VideoScanService(context, shizukuHelper, safHelper, thumbnailHelper, videoRepository)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun mockUri(): Uri = mockk(relaxed = true)

    @Test
    fun `scanDirectoryFromUri emits error when tree is not a directory`() = runTest {
        val treeUri = mockUri()
        every { safHelper.isDirectory(treeUri) } returns false

        val result = service.scanDirectoryFromUri(treeUri).toList()

        assertTrue(result.any { it.phase == VideoScanService.ScanPhase.ERROR })
    }

    @Test
    fun `scanDirectoryFromUri emits error when no av folders`() = runTest {
        val treeUri = mockUri()
        every { safHelper.isDirectory(treeUri) } returns true
        every { safHelper.listDirectories(treeUri) } returns emptyList()

        val result = service.scanDirectoryFromUri(treeUri).toList()

        assertTrue(result.any { it.phase == VideoScanService.ScanPhase.ERROR })
    }

    @Test
    fun `scanDirectoryFromUri skips already scanned avIds`() = runTest {
        val treeUri = mockUri()
        every { safHelper.isDirectory(treeUri) } returns true
        every { safHelper.listDirectories(treeUri) } returns listOf("12345")
        coEvery { videoRepository.getAllAvIds() } returns listOf(12345L)

        val result = service.scanDirectoryFromUri(treeUri).toList()

        assertTrue(result.any { it.statusMessage.contains("already scanned", ignoreCase = true) })
    }

    @Test
    fun `scanDirectoryFromUri scans new avId and saves video`() = runTest {
        val treeUri = mockUri()
        val avidUri = mockUri()
        val cidUri = mockUri()
        val entryUri = mockUri()
        val tagUri = mockUri()

        val entryJson = """
            {
                "title": "Test Video",
                "owner_name": "UP",
                "quality_pithy_description": "1080P",
                "type_tag": "80",
                "total_time_milli": 120000,
                "total_bytes": 50000000,
                "page_data": { "cid": 67890, "width": 1920, "height": 1080 }
            }
        """.trimIndent()

        every { safHelper.isDirectory(treeUri) } returns true
        every { safHelper.listDirectories(treeUri) } returns listOf("12345")
        coEvery { videoRepository.getAllAvIds() } returns emptyList()

        every { safHelper.findChild(treeUri, "12345") } returns avidUri
        every { safHelper.listSubDirectoriesWithEntryJson(avidUri) } returns listOf("c_67890")
        every { safHelper.findChild(avidUri, "c_67890") } returns cidUri
        every { safHelper.findChild(cidUri, "entry.json") } returns entryUri
        every { safHelper.readFileContent(entryUri) } returns entryJson
        every { safHelper.findChild(cidUri, "80") } returns tagUri
        every { safHelper.checkVideoFilesExist(tagUri) } returns true
        every { safHelper.getVideoFileInfo(tagUri) } returns (30_000_000L to 5_000_000L)
        every { safHelper.findChild(cidUri, "cover.jpg") } returns null

        val insertSlot = slot<List<Video>>()
        coEvery { videoRepository.insertVideos(capture(insertSlot)) } just Runs

        val result = service.scanDirectoryFromUri(treeUri).toList()

        assertTrue(result.any { it.phase == VideoScanService.ScanPhase.COMPLETE })
        assertEquals(1, insertSlot.captured.size)
        assertEquals("Test Video", insertSlot.captured[0].title)
        assertEquals("1080P", insertSlot.captured[0].quality)
        assertEquals(1920, insertSlot.captured[0].width)
        assertEquals(1080, insertSlot.captured[0].height)
    }

    @Test
    fun `ScanFilter isActive detects any filter`() {
        assertFalse(VideoScanService.ScanFilter().isActive)
        assertTrue(VideoScanService.ScanFilter(quality = "1080P").isActive)
        assertTrue(VideoScanService.ScanFilter(minDurationMs = 1000L).isActive)
        assertTrue(VideoScanService.ScanFilter(maxSizeBytes = 1000L).isActive)
        assertTrue(VideoScanService.ScanFilter(specificAvIds = listOf(1L)).isActive)
    }

    @Test
    fun `ScanPhase enum has expected values`() {
        val phases = VideoScanService.ScanPhase.values()
        assertEquals(5, phases.size)
        assertTrue(phases.contains(VideoScanService.ScanPhase.COUNTING))
        assertTrue(phases.contains(VideoScanService.ScanPhase.COMPLETE))
        assertTrue(phases.contains(VideoScanService.ScanPhase.ERROR))
    }
}
