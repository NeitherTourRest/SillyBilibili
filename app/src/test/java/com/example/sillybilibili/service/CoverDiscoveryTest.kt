package com.example.sillybilibili.service

import com.example.sillybilibili.util.parentDocumentId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverDiscoveryTest {

    @Test
    fun `prefers known cover names over other images`() {
        assertEquals(
            "cover.webp",
            pickCoverFileName(listOf("audio.m4s", "video.m4s", "cover.webp", "thumb.png"))
        )
    }

    @Test
    fun `falls back to any image file in directory order`() {
        assertEquals("index.webp", pickCoverFileName(listOf("video.m4s", "index.webp")))
        assertEquals("thumb_123.png", pickCoverFileName(listOf("video.m4s", "thumb_123.png", "audio.m4s")))
    }

    @Test
    fun `returns null when the directory has no image`() {
        assertNull(pickCoverFileName(listOf("video.m4s", "audio.m4s", "entry.json")))
    }

    @Test
    fun `image extension match is case insensitive`() {
        assertEquals("cover.JPG", pickCoverFileName(listOf("cover.JPG")))
        assertNull(pickCoverFileName(listOf("video.m4s")))
    }

    @Test
    fun `parent id strips the last path segment`() {
        assertEquals(
            "primary:Android/data/tv.danmaku.bili/download/123/456",
            parentDocumentId("primary:Android/data/tv.danmaku.bili/download/123/456/cover.jpg")
        )
    }

    @Test
    fun `parent id is null for a root document`() {
        assertNull(parentDocumentId("primary:"))
        assertNull(parentDocumentId(""))
        assertNull(parentDocumentId("primary:single"))
    }
}
