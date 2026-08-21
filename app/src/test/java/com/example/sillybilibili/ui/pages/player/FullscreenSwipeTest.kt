package com.example.sillybilibili.ui.pages.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FullscreenSwipeTest {
    @Test
    fun `horizontal drag previews a bounded playback position`() {
        assertEquals(75_000L, horizontalSwipeSeekPosition(30_000L, 180_000L, 1_000f, 500f))
        assertEquals(0L, horizontalSwipeSeekPosition(10_000L, 90_000L, 1_000f, -500f))
    }

    @Test
    fun `horizontal seek feedback states its direction and delta`() {
        assertEquals("快进 0:45", horizontalSeekPreviewHint(30_000L, 75_000L))
        assertEquals("回退 0:10", horizontalSeekPreviewHint(30_000L, 20_000L))
    }
    @Test
    fun `upward swipe beyond threshold opens the next video`() {
        assertEquals(2, fullscreenSwipeTargetIndex(activeIndex = 1, itemCount = 4, offsetPx = -80f, viewportHeightPx = 1_000))
    }

    @Test
    fun `downward swipe beyond threshold opens the previous video`() {
        assertEquals(0, fullscreenSwipeTargetIndex(activeIndex = 1, itemCount = 4, offsetPx = 80f, viewportHeightPx = 1_000))
    }

    @Test
    fun `short drag springs back without switching`() {
        assertNull(fullscreenSwipeTargetIndex(activeIndex = 1, itemCount = 4, offsetPx = -60f, viewportHeightPx = 1_000))
    }

    @Test
    fun `queue boundaries do not switch outside the available videos`() {
        assertNull(fullscreenSwipeTargetIndex(activeIndex = 0, itemCount = 4, offsetPx = 250f, viewportHeightPx = 1_000))
        assertNull(fullscreenSwipeTargetIndex(activeIndex = 3, itemCount = 4, offsetPx = -250f, viewportHeightPx = 1_000))
    }
}
