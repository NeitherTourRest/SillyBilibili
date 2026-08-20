package com.example.sillybilibili.ui.pages.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortraitViewportCollapseTest {
    @Test
    fun `portrait viewport expands to theatre height and collapses to sixteen by nine`() {
        val heights = portraitViewportHeightsPx(contentWidthPx = 360, videoAspectRatio = 9f / 16f)

        requireNotNull(heights)
        assertEquals(525, heights.expandedHeightPx)
        assertEquals(203, heights.collapsedHeightPx)
    }

    @Test
    fun `landscape video does not opt into portrait collapse`() {
        assertNull(portraitViewportHeightsPx(contentWidthPx = 360, videoAspectRatio = 16f / 9f))
    }
}
