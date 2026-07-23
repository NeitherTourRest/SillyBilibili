package com.example.sillybilibili.data.repository

import com.example.sillybilibili.data.local.dao.VideoDao
import com.example.sillybilibili.data.local.entity.CategoryEntity
import com.example.sillybilibili.data.local.entity.VideoEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for Entity ↔ Domain mapping correctness.
 */
class EntityMappingTest {

    // ── VideoEntity field integrity ────────────────────────────

    @Test
    fun `VideoEntity stores all fields correctly`() {
        val entity = VideoEntity(
            id = 1, avid = 1001, cid = 2001, title = "Test Video",
            ownerName = "UP主", quality = "1080P", width = 1920, height = 1080,
            path = "/cache/video.m4s", audioPath = "/cache/audio.m4s",
            size = 50_000_000, duration = 120_000, categoryId = 5,
            coverPath = "/cache/cover.jpg", addedAt = 1000, exportedPath = "/out/v.mp4"
        )
        assertEquals(1L, entity.id)
        assertEquals(1001L, entity.avid)
        assertEquals(2001L, entity.cid)
        assertEquals("Test Video", entity.title)
        assertEquals("UP主", entity.ownerName)
        assertEquals("1080P", entity.quality)
        assertEquals(1920, entity.width)
        assertEquals(1080, entity.height)
        assertEquals("/cache/video.m4s", entity.path)
        assertEquals("/cache/audio.m4s", entity.audioPath)
        assertEquals(50_000_000L, entity.size)
        assertEquals(120_000L, entity.duration)
        assertEquals(5L, entity.categoryId)
        assertEquals("/cache/cover.jpg", entity.coverPath)
        assertEquals(1000L, entity.addedAt)
        assertEquals("/out/v.mp4", entity.exportedPath)
    }

    @Test
    fun `VideoEntity default values for optional fields`() {
        val entity = VideoEntity(
            id = 2, avid = 2002, cid = 3002, title = "Minimal",
            path = "/a.m4s", audioPath = "/b.m4s", size = 1000, duration = 5000
        )
        assertEquals("", entity.ownerName)
        assertEquals("", entity.quality)
        assertEquals(0, entity.width)
        assertEquals(0, entity.height)
        assertNull(entity.categoryId)
        assertNull(entity.coverPath)
        assertNull(entity.exportedPath)
    }

    // ── CategoryEntity ─────────────────────────────────────────

    @Test
    fun `CategoryEntity stores all fields correctly`() {
        val entity = CategoryEntity(id = 10, name = "Music", color = 0xFFFF0000, createdAt = 5000)
        assertEquals(10L, entity.id)
        assertEquals("Music", entity.name)
        assertEquals(0xFFFF0000, entity.color)
        assertEquals(5000L, entity.createdAt)
    }

    @Test
    fun `CategoryEntity default values`() {
        val entity = CategoryEntity(name = "New", color = 0xFF0000FF)
        assertEquals(0L, entity.id)
        assertTrue(entity.createdAt > 0)
    }

    // ── escapeForLike via VideoRepositoryImpl ──────────────────

    @Test
    fun `escapeForLike escapes backslash first`() {
        assertEquals("a\\\\b", VideoRepositoryImpl.escapeForLikeStatic("a\\b"))
    }

    @Test
    fun `escapeForLike escapes percent`() {
        assertEquals("a\\%b", VideoRepositoryImpl.escapeForLikeStatic("a%b"))
    }

    @Test
    fun `escapeForLike escapes underscore`() {
        assertEquals("a\\_b", VideoRepositoryImpl.escapeForLikeStatic("a_b"))
    }

    @Test
    fun `escapeForLike handles normal string unchanged`() {
        assertEquals("hello world", VideoRepositoryImpl.escapeForLikeStatic("hello world"))
    }

    @Test
    fun `escapeForLike escapes all wildcards combined`() {
        assertEquals("a\\\\b\\%c\\_d", VideoRepositoryImpl.escapeForLikeStatic("a\\b%c_d"))
    }
}
