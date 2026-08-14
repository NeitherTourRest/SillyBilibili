package com.example.sillybilibili.ui.pages.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQueuePreloadTest {
    private val queue = (0..5).map { index ->
        PlaybackQueueItem(
            id = index.toLong(),
            title = "Video $index",
            videoPath = "/cache/$index/video.m4s",
            audioPath = "/cache/$index/audio.m4s"
        )
    }

    @Test
    fun `preload window warms two following and two previous items nearest first`() {
        assertEquals(
            listOf(3L, 1L, 4L, 0L),
            adjacentPlaybackPreloadItems(queue, activeIndex = 2).map { it.id }
        )
    }

    @Test
    fun `preload window stays inside queue bounds`() {
        assertEquals(listOf(1L, 2L), adjacentPlaybackPreloadItems(queue, activeIndex = 0).map { it.id })
        assertEquals(listOf(4L, 3L), adjacentPlaybackPreloadItems(queue, activeIndex = 5).map { it.id })
    }
}
