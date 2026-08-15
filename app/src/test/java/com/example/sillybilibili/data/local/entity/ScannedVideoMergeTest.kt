package com.example.sillybilibili.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannedVideoMergeTest {
    @Test
    fun `rescan keeps cached cover category export and online state`() {
        val existing = videoEntity(
            id = 7,
            title = "旧标题",
            categoryId = 9,
            coverPath = "/app-cache/covers/7.jpg",
            coverSourcePath = "/old/cover.jpg",
            exportedPath = "/Movies/export.mp4",
            exportedSize = 123,
            exportedLastModified = 456,
            onlineStatus = "ONLINE",
            onlineCheckedAt = 789
        )
        val scanned = videoEntity(title = "新标题", coverSourcePath = "/new/cover.jpg", size = 999)

        val merged = mergeScannedVideo(existing, scanned)

        assertEquals(7L, merged.id)
        assertEquals("新标题", merged.title)
        assertEquals(999L, merged.size)
        assertEquals(9L, merged.categoryId)
        assertEquals("/app-cache/covers/7.jpg", merged.coverPath)
        assertEquals("/new/cover.jpg", merged.coverSourcePath)
        assertEquals("/Movies/export.mp4", merged.exportedPath)
        assertEquals(123L, merged.exportedSize)
        assertEquals("ONLINE", merged.onlineStatus)
        assertTrue(merged.sourceAvailable)
    }

    @Test
    fun `rescan does not replace a known cover source with a blank one`() {
        val existing = videoEntity(coverSourcePath = "/old/cover.jpg")
        val merged = mergeScannedVideo(existing, videoEntity(coverSourcePath = null))

        assertEquals("/old/cover.jpg", merged.coverSourcePath)
    }

    private fun videoEntity(
        id: Long = 0,
        title: String = "video",
        size: Long = 1,
        categoryId: Long? = null,
        coverPath: String? = null,
        coverSourcePath: String? = null,
        exportedPath: String? = null,
        exportedSize: Long = 0,
        exportedLastModified: Long = 0,
        onlineStatus: String = "UNCHECKED",
        onlineCheckedAt: Long = 0
    ) = VideoEntity(
        id = id,
        avid = 1,
        cid = 2,
        title = title,
        path = "/cache/video.m4s",
        audioPath = "/cache/audio.m4s",
        size = size,
        duration = 1,
        categoryId = categoryId,
        coverPath = coverPath,
        coverSourcePath = coverSourcePath,
        exportedPath = exportedPath,
        exportedSize = exportedSize,
        exportedLastModified = exportedLastModified,
        onlineStatus = onlineStatus,
        onlineCheckedAt = onlineCheckedAt
    )
}
