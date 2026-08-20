package com.example.sillybilibili.service

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ShizukuReadAheadCacheTest {
    @Test
    fun `read crossing a cache block boundary loads the complete requested range`() {
        val blockSize = 256 * 1024
        val source = ByteArray(blockSize * 2) { (it % 251).toByte() }
        val offset = blockSize.toLong() - 64L
        val length = 128

        val bytes = ShizukuReadAheadCache().read("/isolated/video.m4s", offset, length) { readOffset, readLength ->
            source.copyOfRange(readOffset.toInt(), (readOffset + readLength).toInt())
        }

        assertNotNull(bytes)
        assertArrayEquals(source.copyOfRange(offset.toInt(), offset.toInt() + length), bytes)
    }
}
