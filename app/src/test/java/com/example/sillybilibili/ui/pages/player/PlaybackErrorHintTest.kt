package com.example.sillybilibili.ui.pages.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorHintTest {

    @Test
    fun `io errors explain the cache may be missing or corrupted`() {
        assertTrue(playbackErrorHint(PlaybackException.ERROR_CODE_IO_UNSPECIFIED).contains("缓存文件读取失败"))
        assertTrue(playbackErrorHint(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND).contains("缓存文件读取失败"))
        assertTrue(playbackErrorHint(PlaybackException.ERROR_CODE_IO_NO_PERMISSION).contains("缓存文件读取失败"))
    }

    @Test
    fun `parsing errors explain the cache may be corrupted`() {
        assertTrue(playbackErrorHint(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED).contains("视频格式无法解析"))
        assertTrue(playbackErrorHint(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED).contains("视频格式无法解析"))
    }

    @Test
    fun `decoder errors explain codec support`() {
        assertTrue(playbackErrorHint(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED).contains("编码格式"))
        assertTrue(playbackErrorHint(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED).contains("编码格式"))
    }

    @Test
    fun `audio track errors explain audio output`() {
        assertTrue(playbackErrorHint(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED).contains("音频输出"))
    }

    @Test
    fun `unknown error codes keep the raw name`() {
        val hint = playbackErrorHint(9999)
        assertTrue(hint.contains("无法播放此视频"))
    }
}
