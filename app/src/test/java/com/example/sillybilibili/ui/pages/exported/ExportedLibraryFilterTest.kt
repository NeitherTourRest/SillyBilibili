package com.example.sillybilibili.ui.pages.exported

import com.example.sillybilibili.domain.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportedLibraryFilterTest {

    @Test
    fun `search includes title owner ids and exported file name`() {
        val video = video(title = "旅行记录", ownerName = "SillyUP", avid = 12345, cid = 678, exportedPath = "/Movies/summer-cut.mp4")

        listOf("旅行", "sillyup", "12345", "678", "summer").forEach { query ->
            assertEquals(listOf(video), filterExportedVideos(listOf(video), ExportedLibraryFilter(query = query)))
        }
    }

    @Test
    fun `category source and orientation filters compose`() {
        val cachedLandscape = video(id = 1, categoryId = 10, width = 1920, height = 1080, sourceAvailable = true)
        val exportedOnlyPortrait = video(id = 2, categoryId = 10, width = 1080, height = 1920, sourceAvailable = false)
        val otherCategory = video(id = 3, categoryId = 20, width = 1080, height = 1920, sourceAvailable = false)

        val actual = filterExportedVideos(
            listOf(cachedLandscape, exportedOnlyPortrait, otherCategory),
            ExportedLibraryFilter(
                category = ExportedCategoryFilter.Category(10),
                source = ExportedSourceFilter.EXPORTED_ONLY,
                orientation = ExportedOrientationFilter.PORTRAIT
            )
        )

        assertEquals(listOf(exportedOnlyPortrait), actual)
    }

    @Test
    fun `uncategorized filter only returns videos without a category`() {
        val uncategorized = video(id = 1, categoryId = null)
        val categorized = video(id = 2, categoryId = 9)

        val actual = filterExportedVideos(
            listOf(uncategorized, categorized),
            ExportedLibraryFilter(category = ExportedCategoryFilter.UNCATEGORIZED)
        )

        assertEquals(listOf(uncategorized), actual)
    }

    @Test
    fun `size sort uses actual exported size and has deterministic title tie breaker`() {
        val alpha = video(id = 2, title = "Alpha", exportedSize = 200)
        val beta = video(id = 1, title = "Beta", exportedSize = 200)
        val small = video(id = 3, title = "Small", exportedSize = 100)

        val actual = filterExportedVideos(
            listOf(beta, small, alpha),
            ExportedLibraryFilter(sort = ExportedSort.FILE_SIZE)
        )

        assertEquals(listOf(alpha, beta, small), actual)
    }

    @Test
    fun `export file name validation appends mp4 and rejects unsafe names`() {
        assertEquals("episode-01.mp4", normalizedExportFileName("episode-01"))
        assertEquals("episode-01.mp4", normalizedExportFileName("episode-01.mp4"))
        assertTrue(normalizedExportFileName("../escape") == null)
        assertTrue(normalizedExportFileName("a/b") == null)
        assertTrue(normalizedExportFileName("   ") == null)
    }

    private fun video(
        id: Long = 1,
        avid: Long = 1,
        cid: Long = 1,
        title: String = "Video",
        ownerName: String = "",
        categoryId: Long? = null,
        width: Int = 1920,
        height: Int = 1080,
        sourceAvailable: Boolean = true,
        exportedPath: String = "/Movies/video.mp4",
        exportedSize: Long = 100L
    ) = Video(
        id = id,
        avid = avid,
        cid = cid,
        title = title,
        ownerName = ownerName,
        width = width,
        height = height,
        path = "/cache/$id.m4s",
        audioPath = "/cache/$id-audio.m4s",
        size = 100L,
        duration = 1_000L,
        categoryId = categoryId,
        sourceAvailable = sourceAvailable,
        exportedPath = exportedPath,
        exportedSize = exportedSize
    )
}
