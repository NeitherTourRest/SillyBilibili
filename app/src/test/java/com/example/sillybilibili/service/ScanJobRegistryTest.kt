package com.example.sillybilibili.service

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanJobRegistryTest {

    @Test
    fun `persists an unexpected scan failure as a visible error result`() = runBlocking {
        val registry = ScanJobRegistry()

        registry.start(flow {
            emit(VideoScanService.ScanProgress(phase = VideoScanService.ScanPhase.COUNTING, statusMessage = "正在读取目录"))
            throw SecurityException("denied")
        })

        repeat(100) {
            if (!registry.session.value.isScanning) return@repeat
            delay(10)
        }

        val session = registry.session.value
        assertFalse(session.isScanning)
        assertFalse(session.scanComplete)
        assertTrue(session.resultMessage.contains("系统拒绝"))
        assertEquals(VideoScanService.ScanPhase.ERROR, session.progress?.phase)
    }
}
