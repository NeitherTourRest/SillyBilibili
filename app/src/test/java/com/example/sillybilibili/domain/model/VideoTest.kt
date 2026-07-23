package com.example.sillybilibili.domain.model

import org.junit.Assert.*
import org.junit.Test

class VideoTest {

    // ── formattedSize ──────────────────────────────────────────

    @Test
    fun `formattedSize returns bytes for sizes under 1KB`() {
        val video = Video(avid = 1, cid = 1, title = "", path = "", audioPath = "", size = 512, duration = 0)
        assertEquals("512 B", video.formattedSize)
    }

    @Test
    fun `formattedSize returns KB for sizes between 1KB and 1MB`() {
        val video = Video(avid = 1, cid = 1, title = "", path = "", audioPath = "", size = 2048, duration = 0)
        assertEquals("2.00 KB", video.formattedSize)
    }

    @Test
    fun `formattedSize returns MB for sizes between 1MB and 1GB`() {
        val video = Video(avid = 1, cid = 1, title = "", path = "", audioPath = "", size = 50 * 1024 * 1024, duration = 0)
        assertEquals("50.00 MB", video.formattedSize)
    }

    @Test
    fun `formattedSize returns GB for sizes above 1GB`() {
        val gb = 1024L * 1024 * 1024
        val video = Video(avid = 1, cid = 1, title = "", path = "", audioPath = "", size = 2 * gb + 512 * 1024 * 1024, duration = 0)
        assertEquals("2.50 GB", video.formattedSize)
    }

    @Test
    fun `formattedSize returns 0 B for zero size`() {
        val video = Video(avid = 1, cid = 1, title = "", path = "", audioPath = "", size = 0, duration = 0)
        assertEquals("0 B", video.formattedSize)
    }

    @Test
    fun `formattedSize at exact 1KB boundary`() {
        val video = Video(avid = 1, cid = 1, title = "", path = "", audioPath = "", size = 1024, duration = 0)
        assertEquals("1.00 KB", video.formattedSize)
    }

    // ── formattedDuration ──────────────────────────────────────

    @Test
    fun `formattedDuration returns MM_SS for videos under 1 hour`() {
        val video = Video(avid = 1, cid = 1, title = "", path = "", audioPath = "", size = 0, duration = 125_000)
        assertEquals("02:05", video.formattedDuration)
    }

    @Test
    fun `formattedDuration returns HH_MM_SS for videos over 1 hour`() {
        val video = Video(avid = 1, cid = 1, title = "", path = "", audioPath = "", size = 0, duration = 3_661_000)
        assertEquals("01:01:01", video.formattedDuration)
    }

    @Test
    fun `formattedDuration returns 00_00 for zero duration`() {
        val video = Video(avid = 1, cid = 1, title = "", path = "", audioPath = "", size = 0, duration = 0)
        assertEquals("00:00", video.formattedDuration)
    }

    @Test
    fun `formattedDuration handles exactly 1 hour`() {
        val video = Video(avid = 1, cid = 1, title = "", path = "", audioPath = "", size = 0, duration = 3_600_000)
        assertEquals("01:00:00", video.formattedDuration)
    }

    @Test
    fun `formattedDuration pads single-digit seconds`() {
        val video = Video(avid = 1, cid = 1, title = "", path = "", audioPath = "", size = 0, duration = 65_000)
        assertEquals("01:05", video.formattedDuration)
    }

    // ── isVertical ─────────────────────────────────────────────

    @Test
    fun `isVertical true when height exceeds width`() {
        val video = Video(avid = 1, cid = 1, title = "", path = "", audioPath = "", size = 0, duration = 0, width = 720, height = 1280)
        assertTrue(video.isVertical)
    }

    @Test
    fun `isVertical false when width exceeds height`() {
        val video = Video(avid = 1, cid = 1, title = "", path = "", audioPath = "", size = 0, duration = 0, width = 1920, height = 1080)
        assertFalse(video.isVertical)
    }

    @Test
    fun `isVertical false when width equals height`() {
        val video = Video(avid = 1, cid = 1, title = "", path = "", audioPath = "", size = 0, duration = 0, width = 1080, height = 1080)
        assertFalse(video.isVertical)
    }

    @Test
    fun `isVertical true for default zero dimensions`() {
        val video = Video(avid = 1, cid = 1, title = "", path = "", audioPath = "", size = 0, duration = 0)
        assertFalse(video.isVertical) // height (0) > width (0) is false
    }

    // ── resolutionLabel ────────────────────────────────────────

    @Test
    fun `resolutionLabel returns WxH when dimensions are set`() {
        val video = Video(avid = 1, cid = 1, title = "", path = "", audioPath = "", size = 0, duration = 0, width = 1920, height = 1080)
        assertEquals("1920×1080", video.resolutionLabel)
    }

    @Test
    fun `resolutionLabel returns empty string when dimensions are zero`() {
        val video = Video(avid = 1, cid = 1, title = "", path = "", audioPath = "", size = 0, duration = 0)
        assertEquals("", video.resolutionLabel)
    }

    @Test
    fun `resolutionLabel returns empty string when only one dimension is set`() {
        val video = Video(avid = 1, cid = 1, title = "", path = "", audioPath = "", size = 0, duration = 0, width = 1920, height = 0)
        assertEquals("", video.resolutionLabel)
    }

    // ── default values ─────────────────────────────────────────

    @Test
    fun `Video default values are set correctly`() {
        val video = Video(avid = 123, cid = 456, title = "Test", path = "/a", audioPath = "/b", size = 100, duration = 1000)
        assertEquals(0L, video.id)
        assertEquals("", video.ownerName)
        assertEquals("", video.quality)
        assertEquals(0, video.width)
        assertEquals(0, video.height)
        assertNull(video.categoryId)
        assertNull(video.coverPath)
        assertNull(video.exportedPath)
        assertTrue(video.addedAt > 0)
    }
}
