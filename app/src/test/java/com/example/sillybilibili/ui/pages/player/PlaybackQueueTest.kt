package com.example.sillybilibili.ui.pages.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueTest {

    @Test
    fun `only a cached video and audio pair can be converted to MP4`() {
        assertTrue(PlaybackQueueItem(1, "缓存", "/cache/video.m4s", "/cache/audio.m4s").canConvertToMp4)
        assertFalse(PlaybackQueueItem(1, "已导出", "/export/video.mp4").canConvertToMp4)
    }
    @Test
    fun `queue keeps separate m4s audio track`() {
        val queue = PlaybackQueueStore.replace(
            listOf(PlaybackQueueItem(1, "缓存视频", "/cache/video.m4s", "/cache/audio.m4s")),
            selectedVideoId = 1
        )

        assertEquals("/cache/video.m4s", queue.items.single().videoPath)
        assertEquals("/cache/audio.m4s", queue.items.single().audioPath)
        assertFalse(queue.items.single().isMuxedFile)
    }

    @Test
    fun `single file is a muxed playback item`() {
        val queue = PlaybackQueueStore.prepareSingle("/export/video.mp4", "导出视频")
        assertTrue(queue.items.single().isMuxedFile)
    }

    @Test
    fun `active queue marker lets the mini player reopen the same queue`() {
        val queue = PlaybackQueueStore.prepareSingle("/export/video.mp4", "导出视频")
        PlaybackQueueStore.markActive(queue.id)

        assertEquals(queue.id, PlaybackQueueStore.activeQueueId())
        assertEquals(queue, PlaybackQueueStore.currentQueue())
    }

    @Test
    fun `queue exposes neighbouring items for Media3 standard previous and next controls`() {
        val queue = PlaybackQueueStore.replace(
            listOf(
                PlaybackQueueItem(1, "第一集", "/cache/1.m4s", "/cache/1-audio.m4s"),
                PlaybackQueueItem(2, "第二集", "/cache/2.m4s", "/cache/2-audio.m4s")
            ),
            selectedVideoId = 1
        )

        assertEquals(0, queue.selectedIndex)
        assertEquals("第二集", queue.items[queue.selectedIndex + 1].title)
    }
}
