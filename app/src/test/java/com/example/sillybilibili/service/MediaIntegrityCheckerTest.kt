package com.example.sillybilibili.service

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaIntegrityCheckerTest {

    @Test
    fun `both files present and non-empty is ok`() {
        assertEquals(MediaIntegrityStatus.OK, classifyIntegrity(videoSize = 100L, audioSize = 50L, hasAudioTrack = true))
    }

    @Test
    fun `missing video is reported`() {
        assertEquals(MediaIntegrityStatus.VIDEO_MISSING, classifyIntegrity(videoSize = 0L, audioSize = 50L, hasAudioTrack = true))
    }

    @Test
    fun `missing audio is reported`() {
        assertEquals(MediaIntegrityStatus.AUDIO_MISSING, classifyIntegrity(videoSize = 100L, audioSize = 0L, hasAudioTrack = true))
    }

    @Test
    fun `both missing is reported`() {
        assertEquals(MediaIntegrityStatus.BOTH_MISSING, classifyIntegrity(videoSize = 0L, audioSize = 0L, hasAudioTrack = true))
    }

    @Test
    fun `video without audio track only needs the video file`() {
        assertEquals(MediaIntegrityStatus.OK, classifyIntegrity(videoSize = 100L, audioSize = 0L, hasAudioTrack = false))
        assertEquals(MediaIntegrityStatus.VIDEO_MISSING, classifyIntegrity(videoSize = 0L, audioSize = 0L, hasAudioTrack = false))
    }

    @Test
    fun `unreachable isolated paths are unknown`() {
        assertEquals(MediaIntegrityStatus.UNKNOWN, classifyIntegrity(videoSize = -1L, audioSize = -1L, hasAudioTrack = true))
        assertEquals(MediaIntegrityStatus.UNKNOWN, classifyIntegrity(videoSize = -1L, audioSize = 50L, hasAudioTrack = true))
        assertEquals(MediaIntegrityStatus.UNKNOWN, classifyIntegrity(videoSize = 100L, audioSize = -1L, hasAudioTrack = true))
    }
}
