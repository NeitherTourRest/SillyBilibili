package com.example.sillybilibili.service

import com.example.sillybilibili.domain.repository.VideoRepository
import com.example.sillybilibili.util.SafFileHelper
import com.example.sillybilibili.util.ShizukuFileHelper
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class VideoScanAccessTest {
    @Test
    fun `directly readable directory wins over available Shizuku`() {
        val shizuku = mockk<ShizukuFileHelper>()
        every { shizuku.isShizukuAvailable() } returns true
        val service = VideoScanService(shizuku, mockk<SafFileHelper>(), mockk<VideoRepository>())
        val directory = createTempDir("direct-cache")

        assertEquals(VideoScanService.ScanAccess.DIRECT, service.resolveScanAccess(directory.absolutePath))
    }

    @Test
    fun `isolated path falls back to Shizuku`() {
        val shizuku = mockk<ShizukuFileHelper>()
        every { shizuku.isShizukuAvailable() } returns true
        val service = VideoScanService(shizuku, mockk<SafFileHelper>(), mockk<VideoRepository>())

        assertEquals(VideoScanService.ScanAccess.SHIZUKU, service.resolveScanAccess(File("missing-cache").absolutePath))
    }
}
