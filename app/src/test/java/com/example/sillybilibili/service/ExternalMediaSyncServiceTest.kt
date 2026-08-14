package com.example.sillybilibili.service

import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.domain.repository.VideoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ExternalMediaSyncServiceTest {

    private fun exportedVideo(path: String, size: Long = 0L, modified: Long = 0L) = Video(
        id = 9L,
        avid = 1L,
        cid = 2L,
        title = "exported",
        path = "/cache/video.m4s",
        audioPath = "/cache/audio.m4s",
        size = 1L,
        duration = 1L,
        exportedPath = path,
        exportedSize = size,
        exportedLastModified = modified
    )

    @Test
    fun `updates the exported fingerprint when an MP4 was changed externally`() = runTest {
        val output = File.createTempFile("silly-export", ".mp4")
        try {
            output.writeText("changed outside the app")
            val video = exportedVideo(output.absolutePath)
            val repository = mockk<VideoRepository>(relaxed = true)
            coEvery { repository.getExportedVideosOnce() } returns listOf(video)

            ExternalMediaSyncService(repository).reconcileExportedFiles()

            coVerify {
                repository.updateVideo(
                    video.copy(
                        exportedSize = output.length(),
                        exportedLastModified = output.lastModified()
                    )
                )
            }
        } finally {
            output.delete()
        }
    }

    @Test
    fun `clears the export record when an MP4 was removed externally`() = runTest {
        val repository = mockk<VideoRepository>(relaxed = true)
        val video = exportedVideo("/not-present/silly-export.mp4", size = 42L, modified = 7L)
        coEvery { repository.getExportedVideosOnce() } returns listOf(video)

        ExternalMediaSyncService(repository).reconcileExportedFiles()

        coVerify {
            repository.updateVideo(video.copy(exportedPath = null, exportedSize = 0L, exportedLastModified = 0L))
        }
    }
}
