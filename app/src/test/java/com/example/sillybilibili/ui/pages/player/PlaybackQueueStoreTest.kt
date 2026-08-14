package com.example.sillybilibili.ui.pages.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQueueStoreTest {
    @Test
    fun `keeps the selected item position after blank paths are removed`() {
        val queue = PlaybackQueueStore.replace(
            listOf(
                PlaybackQueueItem(1, "Unavailable", ""),
                PlaybackQueueItem(2, "Second", "/video/second.mp4"),
                PlaybackQueueItem(3, "Third", "/video/third.mp4")
            ),
            selectedVideoId = 3
        )

        assertEquals(2, queue.items.size)
        assertEquals(1, queue.selectedIndex)
        assertEquals("Third", queue.items[queue.selectedIndex].title)
    }
}
