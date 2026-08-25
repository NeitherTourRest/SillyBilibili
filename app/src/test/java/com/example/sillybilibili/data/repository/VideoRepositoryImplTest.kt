package com.example.sillybilibili.data.repository

import com.example.sillybilibili.data.local.dao.VideoDao
import com.example.sillybilibili.data.local.entity.VideoEntity
import com.example.sillybilibili.domain.model.Video
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class VideoRepositoryImplTest {

    private val dao = mockk<VideoDao>()
    private val repo = VideoRepositoryImpl(dao)

    // ── getAllVideos ───────────────────────────────────────────

    @Test
    fun `getAllVideos maps entities to domain models`() = runTest {
        val entity = VideoEntity(avid = 1, cid = 2, title = "T", path = "/v.m4s", audioPath = "/a.m4s", size = 100, duration = 1000)
        every { dao.getAllVideos() } returns flowOf(listOf(entity))

        val result = repo.getAllVideos().toList()

        assertEquals(1, result.size)
        val videos = result.first()
        assertEquals(1, videos.size)
        assertEquals("T", videos[0].title)
        assertEquals(1L, videos[0].avid)
    }

    @Test
    fun `getAllVideos returns empty list when no videos`() = runTest {
        every { dao.getAllVideos() } returns flowOf(emptyList())
        val result = repo.getAllVideos().toList()
        assertTrue(result.first().isEmpty())
    }

    // ── getVideoById ───────────────────────────────────────────

    @Test
    fun `getVideoById returns domain model when found`() = runTest {
        val entity = VideoEntity(id = 42, avid = 1, cid = 2, title = "Found", path = "/v", audioPath = "/a", size = 100, duration = 1000)
        coEvery { dao.getVideoById(42) } returns entity

        val video = repo.getVideoById(42)

        assertNotNull(video)
        assertEquals(42L, video!!.id)
        assertEquals("Found", video.title)
    }

    @Test
    fun `getVideoById returns null when not found`() = runTest {
        coEvery { dao.getVideoById(999) } returns null
        assertNull(repo.getVideoById(999))
    }

    // ── getVideoByPath ─────────────────────────────────────────

    @Test
    fun `getVideoByPath returns video when path exists`() = runTest {
        val entity = VideoEntity(id = 5, avid = 3, cid = 4, title = "P", path = "/unique.m4s", audioPath = "/a", size = 50, duration = 500)
        coEvery { dao.getVideoByPath("/unique.m4s") } returns entity

        val video = repo.getVideoByPath("/unique.m4s")

        assertNotNull(video)
        assertEquals("/unique.m4s", video!!.path)
    }

    @Test
    fun `getVideoByPath returns null for unknown path`() = runTest {
        coEvery { dao.getVideoByPath("/nonexistent") } returns null
        assertNull(repo.getVideoByPath("/nonexistent"))
    }

    // ── insertVideo ────────────────────────────────────────────

    @Test
    fun `insertVideo delegates to DAO and returns id`() = runTest {
        val video = Video(avid = 10, cid = 20, title = "New", path = "/v", audioPath = "/a", size = 100, duration = 1000)
        coEvery { dao.insertVideo(any()) } returns 99L

        val id = repo.insertVideo(video)
        assertEquals(99L, id)
    }

    // ── deleteVideo ────────────────────────────────────────────

    @Test
    fun `deleteVideo delegates to DAO`() = runTest {
        val video = Video(id = 1, avid = 10, cid = 20, title = "D", path = "/v", audioPath = "/a", size = 100, duration = 1000)
        coEvery { dao.deleteVideo(any()) } just Runs

        repo.deleteVideo(video)

        coVerify { dao.deleteVideo(any()) }
    }

    // ── deleteVideoByPath ──────────────────────────────────────

    @Test
    fun `deleteVideoByPath delegates to DAO`() = runTest {
        coEvery { dao.deleteVideoByPath("/to-delete") } just Runs
        repo.deleteVideoByPath("/to-delete")
        coVerify { dao.deleteVideoByPath("/to-delete") }
    }

    // ── getAllAvIds ────────────────────────────────────────────

    @Test
    fun `getAllAvIds returns avId list from DAO`() = runTest {
        coEvery { dao.getAllAvIds() } returns listOf(100L, 200L, 300L)
        val ids = repo.getAllAvIds()
        assertEquals(listOf(100L, 200L, 300L), ids)
    }

    @Test
    fun `getAllAvIds returns empty list when no videos`() = runTest {
        coEvery { dao.getAllAvIds() } returns emptyList()
        assertTrue(repo.getAllAvIds().isEmpty())
    }

    // ── getTotalVideoCount ─────────────────────────────────────

    @Test
    fun `getTotalVideoCount returns count from DAO`() = runTest {
        coEvery { dao.getTotalVideoCount() } returns 5
        assertEquals(5, repo.getTotalVideoCount())
    }

    // ── insertVideos ───────────────────────────────────────────

    @Test
    fun `insertVideos delegates batch insert to DAO`() = runTest {
        val videos = listOf(
            Video(avid = 1, cid = 1, title = "A", path = "/a", audioPath = "/a", size = 1, duration = 1),
            Video(avid = 2, cid = 2, title = "B", path = "/b", audioPath = "/b", size = 2, duration = 2)
        )
        coEvery { dao.insertVideos(any()) } just Runs

        repo.insertVideos(videos)

        coVerify { dao.insertVideos(match { it.size == 2 }) }
    }

    // ── deleteAllVideos ────────────────────────────────────────

    @Test
    fun `deleteAllVideos delegates to DAO`() = runTest {
        coEvery { dao.deleteAllVideos() } just Runs
        repo.deleteAllVideos()
        coVerify { dao.deleteAllVideos() }
    }

    // ── paginated queries ──────────────────────────────────────

    @Test
    fun `getAllVideosPaginated computes offset correctly`() = runTest {
        coEvery { dao.getVideosPaginated(20, 40) } returns emptyList()
        repo.getAllVideosPaginated(page = 2, pageSize = 20)
        coVerify { dao.getVideosPaginated(20, 40) } // offset = page * pageSize
    }

    @Test
    fun `getAllVideosPaginated page 0 has offset 0`() = runTest {
        coEvery { dao.getVideosPaginated(10, 0) } returns emptyList()
        repo.getAllVideosPaginated(page = 0, pageSize = 10)
        coVerify { dao.getVideosPaginated(10, 0) }
    }

    @Test
    fun `getFilteredVideosPaginated passes all parameters correctly`() = runTest {
        coEvery { dao.getFilteredVideosPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns emptyList()

        repo.getFilteredVideosPaginated(
            query = "test", qualityFilter = "1080P", isPortrait = 1,
            minDuration = 1000, maxDuration = 60000, minSize = 1000, maxSize = 1000000,
            minAddedAt = null, hasCover = 1, categoryId = 5,
            page = 1, pageSize = 20,
            sortField = "PUBLISHED_AT", sortAscending = true
        )

        coVerify {
            dao.getFilteredVideosPaginated(
                query = match { it != null && it.contains("test") },
                qualityFilter = "1080P", isPortrait = 1,
                minDuration = 1000, maxDuration = 60000, minSize = 1000, maxSize = 1000000,
                minAddedAt = null, hasCover = 1, categoryId = 5,
                limit = 20, offset = 20,
                sortField = "PUBLISHED_AT", sortAscending = 1
            )
        }
    }
}
