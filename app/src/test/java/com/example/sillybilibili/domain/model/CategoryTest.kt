package com.example.sillybilibili.domain.model

import org.junit.Assert.*
import org.junit.Test

class CategoryTest {

    @Test
    fun `Category default values are set correctly`() {
        val cat = Category(name = "Music", color = 0xFFFF0000)
        assertEquals(0L, cat.id)
        assertEquals("Music", cat.name)
        assertEquals(0xFFFF0000, cat.color)
        assertEquals(0, cat.videoCount)
        assertTrue(cat.createdAt > 0)
    }

    @Test
    fun `Category with all fields specified`() {
        val cat = Category(id = 42, name = "Gaming", color = 0xFF00FF00, createdAt = 1000, videoCount = 5)
        assertEquals(42L, cat.id)
        assertEquals("Gaming", cat.name)
        assertEquals(0xFF00FF00, cat.color)
        assertEquals(1000L, cat.createdAt)
        assertEquals(5, cat.videoCount)
    }

    @Test
    fun `Category equality based on data class properties`() {
        val a = Category(id = 1, name = "A", color = 0)
        val b = Category(id = 1, name = "A", color = 0)
        assertEquals(a, b)
    }
}
