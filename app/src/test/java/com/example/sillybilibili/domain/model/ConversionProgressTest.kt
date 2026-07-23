package com.example.sillybilibili.domain.model

import org.junit.Assert.*
import org.junit.Test

class ConversionProgressTest {

    @Test
    fun `ConversionProgress with all fields`() {
        val progress = ConversionProgress(
            videoId = 1,
            videoName = "test.mp4",
            progress = 0.5f,
            status = ConversionStatus.CONVERTING,
            outputPath = "/out/test.mp4",
            errorMessage = null
        )
        assertEquals(1L, progress.videoId)
        assertEquals("test.mp4", progress.videoName)
        assertEquals(0.5f, progress.progress)
        assertEquals(ConversionStatus.CONVERTING, progress.status)
        assertEquals("/out/test.mp4", progress.outputPath)
        assertNull(progress.errorMessage)
    }

    @Test
    fun `ConversionProgress minimal fields`() {
        val progress = ConversionProgress(
            videoId = 2, videoName = "v", progress = 0f, status = ConversionStatus.PENDING
        )
        assertEquals(0f, progress.progress)
        assertEquals(ConversionStatus.PENDING, progress.status)
        assertNull(progress.outputPath)
        assertNull(progress.errorMessage)
    }

    @Test
    fun `ConversionProgress FAILED with error message`() {
        val progress = ConversionProgress(
            videoId = 3, videoName = "f", progress = 0f,
            status = ConversionStatus.FAILED, errorMessage = "File not found"
        )
        assertEquals(ConversionStatus.FAILED, progress.status)
        assertEquals("File not found", progress.errorMessage)
    }

    @Test
    fun `ConversionStatus enum values`() {
        assertEquals(4, ConversionStatus.entries.size)
        assertTrue(ConversionStatus.entries.containsAll(listOf(
            ConversionStatus.PENDING, ConversionStatus.CONVERTING,
            ConversionStatus.COMPLETED, ConversionStatus.FAILED
        )))
    }
}
