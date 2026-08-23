package com.example.sillybilibili.ui.pages.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackProgressBarTest {

    @Test
    fun `progress mapping clamps touch positions to the visible track`() {
        assertEquals(0f, playbackProgressAt(-18f, 240f), 0.0001f)
        assertEquals(0.5f, playbackProgressAt(120f, 240f), 0.0001f)
        assertEquals(1f, playbackProgressAt(280f, 240f), 0.0001f)
    }

    @Test
    fun `progress mapping stays safe before the track is measured`() {
        assertEquals(0f, playbackProgressAt(24f, 0f), 0.0001f)
    }
}
