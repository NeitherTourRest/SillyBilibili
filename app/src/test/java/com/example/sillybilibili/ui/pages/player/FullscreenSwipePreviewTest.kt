package com.example.sillybilibili.ui.pages.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FullscreenSwipePreviewTest {
    @Test
    fun `settled target keeps its preview above the player until its first frame renders`() {
        assertEquals(
            3,
            fullscreenSwipePreviewIndex(
                activeIndex = 2,
                itemCount = 5,
                offsetPx = 0f,
                settledTargetIndex = 3
            )
        )
    }

    @Test
    fun `live upward drag previews the next item`() {
        assertEquals(
            3,
            fullscreenSwipePreviewIndex(
                activeIndex = 2,
                itemCount = 5,
                offsetPx = -40f,
                settledTargetIndex = null
            )
        )
    }

    @Test
    fun `idle page has no preview`() {
        assertNull(
            fullscreenSwipePreviewIndex(
                activeIndex = 2,
                itemCount = 5,
                offsetPx = 0f,
                settledTargetIndex = null
            )
        )
    }
}
