package com.example.sillybilibili.service

import com.example.sillybilibili.domain.model.OnlineVideoStatus
import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.domain.repository.VideoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineVideoStatusServiceTest {

    @Test
    fun `manual refresh requests once per AV and updates every scanned part`() = runTest {
        val repository = mockk<VideoRepository>(relaxed = true)
        val remote = mockk<OnlineVideoStatusRemoteDataSource>()
        val firstPart = video(id = 1, avid = 100, cid = 11)
        val secondPart = video(id = 2, avid = 100, cid = 12)
        val unavailable = video(id = 3, avid = 200, cid = 21)
        every { repository.getAllVideos() } returns flowOf(listOf(firstPart, secondPart, unavailable))
        coEvery { remote.fetchStatus(100) } returns OnlineVideoStatus.ONLINE
        coEvery { remote.fetchStatus(200) } returns OnlineVideoStatus.UNAVAILABLE

        val result = OnlineVideoStatusService(repository, remote).forceRefreshAll()

        assertEquals(3, result.videoCount)
        assertEquals(2, result.requestCount)
        assertEquals(2, result.onlineCount)
        assertEquals(1, result.unavailableCount)
        coVerify(exactly = 1) { remote.fetchStatus(100) }
        coVerify(exactly = 1) { remote.fetchStatus(200) }
        coVerify(exactly = 3) { repository.updateVideo(any()) }
    }

    @Test
    fun `manual refresh reports AV based progress from start through completion`() = runTest {
        val repository = mockk<VideoRepository>(relaxed = true)
        val remote = mockk<OnlineVideoStatusRemoteDataSource>()
        val firstPart = video(id = 1, avid = 100, cid = 11)
        val secondPart = video(id = 2, avid = 100, cid = 12)
        val unavailable = video(id = 3, avid = 200, cid = 21)
        every { repository.getAllVideos() } returns flowOf(listOf(firstPart, secondPart, unavailable))
        coEvery { remote.fetchStatus(100) } returns OnlineVideoStatus.ONLINE
        coEvery { remote.fetchStatus(200) } returns OnlineVideoStatus.UNAVAILABLE
        val progress = mutableListOf<OnlineStatusRefreshProgress>()

        OnlineVideoStatusService(repository, remote).forceRefreshAll { progress += it }

        assertEquals(listOf(0, 1, 2), progress.map { it.completedRequestCount })
        assertEquals(listOf(2, 2, 2), progress.map { it.totalRequestCount })
        assertEquals(2, progress.last().onlineCount)
        assertEquals(1, progress.last().unavailableCount)
        assertEquals(3, progress.last().processedVideoCount)
    }

    private fun video(id: Long, avid: Long, cid: Long) = Video(
        id = id,
        avid = avid,
        cid = cid,
        title = "video-$id",
        path = "/cache/$id/video.m4s",
        audioPath = "/cache/$id/audio.m4s",
        size = 1,
        duration = 1
    )
}
