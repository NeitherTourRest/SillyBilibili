package com.example.sillybilibili.ui.pages.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerViewportLayoutTest {
    @Test
    fun `landscape video keeps the standard widescreen viewing area`() {
        assertEquals(16f / 9f, nonFullscreenViewportAspectRatio(16f / 9f), 0.001f)
    }

    @Test
    fun `nine by sixteen portrait video gets a taller theater area with side breathing room`() {
        assertEquals(0.686f, nonFullscreenViewportAspectRatio(9f / 16f), 0.002f)
    }

    @Test
    fun `square video uses a square viewing area`() {
        assertEquals(1f, nonFullscreenViewportAspectRatio(1f), 0.001f)
    }

    @Test
    fun `invalid video metadata falls back to a safe widescreen area`() {
        assertEquals(16f / 9f, nonFullscreenViewportAspectRatio(0f), 0.001f)
    }
}
