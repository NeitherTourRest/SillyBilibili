package com.example.sillybilibili.service

import android.net.Uri
import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.domain.repository.VideoRepository
import com.example.sillybilibili.util.SafFileHelper
import com.example.sillybilibili.util.ShizukuFileHelper
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class VideoScanServiceTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var shizukuHelper: ShizukuFileHelper
    private lateinit var safHelper: SafFileHelper
    private lateinit var videoRepository: VideoRepository
    private lateinit var service: VideoScanService

    @Before
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)

        shizukuHelper = mockk()
        safHelper = mockk()
        videoRepository = mockk(relaxed = true)

        service = VideoScanService(shizukuHelper, safHelper, videoRepository)
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
        val rootUri = mockUri()
        every { safHelper.isDirectory(treeUri) } returns true
        every { safHelper.listDirectoriesResult(treeUri) } returns SafFileHelper.DirectoryListResult(emptyList(), true)
        every { safHelper.rootDocumentUri(treeUri) } returns rootUri
        every { rootUri.toString() } returns "content://docs/document/root"

        val result = service.scanDirectoryFromUri(treeUri).toList()

        assertEquals(VideoScanService.ScanPhase.COMPLETE, result.last().phase)
    }

    @Test
    fun `scanDirectoryFromUri does not reject a folder just because an AV was scanned before`() = runTest {
        val treeUri = mockUri()
        every { safHelper.isDirectory(treeUri) } returns true
        every { safHelper.listDirectoriesResult(treeUri) } returns SafFileHelper.DirectoryListResult(listOf("12345"), true)
        every { safHelper.rootDocumentUri(treeUri).toString() } returns "content://docs/document/root"
        coEvery { videoRepository.getAllVideoPaths() } returns emptyList()
        coEvery { videoRepository.getAvailableSourcePathsInDirectory(any()) } returns emptyList()
        val avidUri = mockUri()
        every { safHelper.findChild(treeUri, "12345") } returns avidUri
        every { safHelper.listSubDirectoriesWithEntryJson(avidUri) } returns emptyList()

        val result = service.scanDirectoryFromUri(treeUri).toList()

        assertTrue(result.any { it.phase == VideoScanService.ScanPhase.COMPLETE })
    }

    @Test
    fun `scanDirectoryFromUri scans new avId and saves video`() = runTest {
        val treeUri = mockUri()
        val avidUri = mockUri()
        val cidUri = mockUri()
        val entryUri = mockUri()
        val tagUri = mockUri()
        val videoUri = mockUri()
        val audioUri = mockUri()

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
        every { safHelper.listDirectoriesResult(treeUri) } returns SafFileHelper.DirectoryListResult(listOf("12345"), true)
        every { safHelper.rootDocumentUri(treeUri).toString() } returns "content://docs/document/root"
        coEvery { videoRepository.getAllVideoPaths() } returns emptyList()
        coEvery { videoRepository.getAvailableSourcePathsInDirectory(any()) } returns emptyList()

        every { safHelper.findChild(treeUri, "12345") } returns avidUri
        every { safHelper.listSubDirectoriesWithEntryJson(avidUri) } returns listOf("c_67890")
        every { safHelper.findChild(avidUri, "c_67890") } returns cidUri
        every { safHelper.findChild(cidUri, "entry.json") } returns entryUri
        every { safHelper.readFileContent(entryUri) } returns entryJson
        every { safHelper.findChild(cidUri, "80") } returns tagUri
        every { safHelper.findChild(tagUri, "video.m4s") } returns videoUri
        every { safHelper.findChild(tagUri, "audio.m4s") } returns audioUri
        every { videoUri.toString() } returns "content://docs/document/root%2F12345%2Fc_67890%2F80%2Fvideo.m4s"
        every { audioUri.toString() } returns "content://docs/document/root%2F12345%2Fc_67890%2F80%2Faudio.m4s"
        every { safHelper.getVideoFileInfo(tagUri) } returns (30_000_000L to 5_000_000L)
        every { safHelper.findChild(cidUri, "cover.jpg") } returns null

        val saved = slot<List<Video>>()

        val result = service.scanDirectoryFromUri(treeUri).toList()

        assertTrue(result.any { it.phase == VideoScanService.ScanPhase.COMPLETE })
        coVerify { videoRepository.syncCacheDirectory(any(), capture(saved), any(), any(), any()) }
        assertEquals(1, saved.captured.size)
        assertEquals("Test Video", saved.captured[0].title)
        assertEquals("1080P", saved.captured[0].quality)
        assertEquals(1920, saved.captured[0].width)
        assertEquals(1080, saved.captured[0].height)
    }

    @Test
    fun `SAF scan keeps every CID under the same AV and stores separate track URIs`() = runTest {
        val treeUri = mockUri()
        val avidUri = mockUri()
        val cidOneUri = mockUri()
        val cidTwoUri = mockUri()
        val tagOneUri = mockUri()
        val tagTwoUri = mockUri()
        val videoOneUri = mockUri()
        val audioOneUri = mockUri()
        val videoTwoUri = mockUri()
        val audioTwoUri = mockUri()
        val entryOneUri = mockUri()
        val entryTwoUri = mockUri()
        every { treeUri.toString() } returns "content://docs/tree/root"
        every { safHelper.rootDocumentUri(treeUri).toString() } returns "content://docs/document/root"
        every { safHelper.isDirectory(treeUri) } returns true
        every { safHelper.listDirectoriesResult(treeUri) } returns SafFileHelper.DirectoryListResult(listOf("12345"), true)
        coEvery { videoRepository.getAllVideoPaths() } returns emptyList()
        coEvery { videoRepository.getAvailableSourcePathsInDirectory(any()) } returns emptyList()
        every { safHelper.findChild(treeUri, "12345") } returns avidUri
        every { safHelper.listSubDirectoriesWithEntryJson(avidUri) } returns listOf("c_1", "c_2")
        every { safHelper.findChild(avidUri, "c_1") } returns cidOneUri
        every { safHelper.findChild(avidUri, "c_2") } returns cidTwoUri
        every { safHelper.findChild(cidOneUri, "entry.json") } returns entryOneUri
        every { safHelper.findChild(cidTwoUri, "entry.json") } returns entryTwoUri
        every { safHelper.readFileContent(entryOneUri) } returns """{"title":"P1","type_tag":"80","total_bytes":10,"page_data":{"cid":1}}"""
        every { safHelper.readFileContent(entryTwoUri) } returns """{"title":"P2","type_tag":"80","total_bytes":10,"page_data":{"cid":2}}"""
        every { safHelper.findChild(cidOneUri, "80") } returns tagOneUri
        every { safHelper.findChild(cidTwoUri, "80") } returns tagTwoUri
        every { safHelper.findChild(tagOneUri, "video.m4s") } returns videoOneUri
        every { safHelper.findChild(tagOneUri, "audio.m4s") } returns audioOneUri
        every { safHelper.findChild(tagTwoUri, "video.m4s") } returns videoTwoUri
        every { safHelper.findChild(tagTwoUri, "audio.m4s") } returns audioTwoUri
        every { safHelper.findChild(any(), "cover.jpg") } returns null
        every { videoOneUri.toString() } returns "content://docs/document/root%2F12345%2Fc_1%2F80%2Fvideo.m4s"
        every { audioOneUri.toString() } returns "content://docs/document/root%2F12345%2Fc_1%2F80%2Faudio.m4s"
        every { videoTwoUri.toString() } returns "content://docs/document/root%2F12345%2Fc_2%2F80%2Fvideo.m4s"
        every { audioTwoUri.toString() } returns "content://docs/document/root%2F12345%2Fc_2%2F80%2Faudio.m4s"

        service.scanDirectoryFromUri(treeUri, VideoScanService.ScanFilter(mode = VideoScanService.ScanMode.QUICK)).toList()

        val saved = slot<List<Video>>()
        coVerify { videoRepository.syncCacheDirectory(any(), capture(saved), any(), any(), any()) }
        assertEquals(listOf(1L, 2L), saved.captured.map { it.cid }.sorted())
        assertEquals("content://docs/document/root%2F12345%2Fc_1%2F80%2Faudio.m4s", saved.captured.first { it.cid == 1L }.audioPath)
    }

    @Test
    fun `scanDirectory adds a new cid when its avId already exists`() = runTest {
        val root = createTempDir("bili-cache")
        val avid = "12345"
        assertTrue(File(root, avid).mkdirs())

        every { shizukuHelper.isShizukuAvailable() } returns false
        every { shizukuHelper.readEntryJsonBatchResult(root.absolutePath, listOf(avid), false) } returns ShizukuFileHelper.EntryJsonBatchResult(listOf(
            ShizukuFileHelper.EntryJsonFile(avid, "c_1", """{"title":"P1","type_tag":"80","total_bytes":10,"page_data":{"cid":1}}"""),
            ShizukuFileHelper.EntryJsonFile(avid, "c_2", """{"title":"P2","type_tag":"80","total_bytes":10,"page_data":{"cid":2}}""")
        ), true)
        coEvery { videoRepository.getAllVideoPaths() } returns listOf(
            "${root.absolutePath}/$avid/c_1/80/video.m4s"
        )
        val saved = slot<List<Video>>()

        service.scanDirectory(
            root.absolutePath,
            VideoScanService.ScanFilter(mode = VideoScanService.ScanMode.QUICK)
        ).toList()

        coVerify { videoRepository.syncCacheDirectory(any(), capture(saved), any(), any(), any()) }
        assertEquals(listOf(2L), saved.captured.map { it.cid })
    }

    @Test
    fun `filtered scan only inserts matches and never synchronizes existing cache records`() = runTest {
        val root = createTempDir("bili-cache")
        val avid = "12345"
        assertTrue(File(root, avid).mkdirs())
        every { shizukuHelper.isShizukuAvailable() } returns false
        every { shizukuHelper.readEntryJsonBatchResult(root.absolutePath, listOf(avid), false) } returns
            ShizukuFileHelper.EntryJsonBatchResult(listOf(
                ShizukuFileHelper.EntryJsonFile(avid, "c_1", """{"title":"P1","quality_pithy_description":"1080P","type_tag":"80","total_bytes":10,"page_data":{"cid":1}}""")
            ), true)
        coEvery { videoRepository.getAllVideoPaths() } returns emptyList()
        val saved = slot<List<Video>>()

        service.scanDirectory(
            root.absolutePath,
            VideoScanService.ScanFilter(quality = "1080P", mode = VideoScanService.ScanMode.QUICK)
        ).toList()

        coVerify(exactly = 1) { videoRepository.insertVideos(capture(saved)) }
        coVerify(exactly = 0) { videoRepository.syncCacheDirectory(any(), any(), any(), any(), any()) }
        assertEquals(listOf(1L), saved.captured.map { it.cid })
    }

    @Test
    fun `full unfiltered scan reconciles every verified cache path`() = runTest {
        val root = createTempDir("bili-cache")
        val avid = "12345"
        assertTrue(File(root, avid).mkdirs())
        val videoPath = "${root.absolutePath}/$avid/c_1/80/video.m4s"

        every { shizukuHelper.isShizukuAvailable() } returns false
        every { shizukuHelper.readEntryJsonBatchResult(root.absolutePath, listOf(avid), false) } returns ShizukuFileHelper.EntryJsonBatchResult(listOf(
            ShizukuFileHelper.EntryJsonFile(avid, "c_1", """{"title":"P1","type_tag":"80","total_bytes":10,"page_data":{"cid":1}}""")
        ), true)
        coEvery { videoRepository.getAllVideoPaths() } returns listOf(videoPath)
        coEvery { videoRepository.getAvailableSourcePathsInDirectory(any()) } returns listOf(videoPath)
        every { shizukuHelper.getVideoFileInfoBatchResult(any(), false) } returns
            ShizukuFileHelper.VideoFileInfoBatchResult(mapOf(videoPath to (10L to 5L)), true)

        service.scanDirectory(root.absolutePath).toList()

        coVerify {
            videoRepository.syncCacheDirectory(
                directoryPrefix = "${root.absolutePath}/",
                scannedVideos = emptyList(),
                seenPaths = listOf(videoPath),
                scanTimestamp = any(),
                allowMissingSourceReconciliation = true
            )
        }
    }

    @Test
    fun `incomplete entry batch never permits deletion reconciliation`() = runTest {
        val root = createTempDir("bili-cache")
        val avid = "12345"
        assertTrue(File(root, avid).mkdirs())
        val videoPath = "${root.absolutePath}/$avid/c_1/80/video.m4s"
        every { shizukuHelper.isShizukuAvailable() } returns false
        every { shizukuHelper.readEntryJsonBatchResult(root.absolutePath, listOf(avid), false) } returns
            ShizukuFileHelper.EntryJsonBatchResult(emptyList(), false)
        coEvery { videoRepository.getAllVideoPaths() } returns listOf(videoPath)
        coEvery { videoRepository.getAvailableSourcePathsInDirectory(any()) } returns listOf(videoPath)
        every { shizukuHelper.getVideoFileInfoBatchResult(any(), false) } returns
            ShizukuFileHelper.VideoFileInfoBatchResult(emptyMap(), true)

        val results = service.scanDirectory(root.absolutePath).toList()

        coVerify {
            videoRepository.syncCacheDirectory(
                directoryPrefix = "${root.absolutePath}/",
                scannedVideos = emptyList(),
                seenPaths = emptyList(),
                scanTimestamp = any(),
                allowMissingSourceReconciliation = false
            )
        }
        assertTrue(results.last().statusMessage.contains("跳过失效源文件清理"))
    }

    @Test
    fun `full direct scan reconciles records when the cache directory is empty`() = runTest {
        val root = createTempDir("bili-cache")
        every { shizukuHelper.isShizukuAvailable() } returns false

        val result = service.scanDirectory(root.absolutePath).toList()

        coVerify {
            videoRepository.syncCacheDirectory(
                directoryPrefix = "${root.absolutePath}/",
                scannedVideos = emptyList(),
                seenPaths = emptyList(),
                scanTimestamp = any(),
                allowMissingSourceReconciliation = true
            )
        }
        assertEquals(VideoScanService.ScanPhase.COMPLETE, result.last().phase)
    }

    @Test
    fun `empty full scan reports previously scanned sources that are no longer on disk`() = runTest {
        val root = createTempDir("bili-cache")
        every { shizukuHelper.isShizukuAvailable() } returns false
        coEvery { videoRepository.getAvailableSourcePathsInDirectory(any()) } returns listOf(
            "${root.absolutePath}/123/c_1/80/video.m4s",
            "${root.absolutePath}/456/c_2/80/video.m4s"
        )

        val result = service.scanDirectory(root.absolutePath).toList().last()

        assertEquals(2, result.unavailableSourceCount)
        assertTrue(result.reconciliationPerformed)
        assertTrue(result.statusMessage.contains("2"))
    }

    @Test
    fun `overall scan progress advances between named workflow steps`() {
        assertTrue(
            scanOverallProgress(VideoScanService.ScanStep.DISCOVERING_FOLDERS, 1f) <
                scanOverallProgress(VideoScanService.ScanStep.READING_METADATA, 0f)
        )
        assertTrue(
            scanOverallProgress(VideoScanService.ScanStep.VALIDATING_FILES, 1f) <
                scanOverallProgress(VideoScanService.ScanStep.RECONCILING_HISTORY, 0f)
        )
        assertEquals(1f, scanOverallProgress(VideoScanService.ScanStep.FINISHED, 0f), 0.001f)
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
