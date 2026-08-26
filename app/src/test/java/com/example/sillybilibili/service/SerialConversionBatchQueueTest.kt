package com.example.sillybilibili.service

import com.example.sillybilibili.domain.model.ConversionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SerialConversionBatchQueueTest {
    @Test
    fun `runs exactly one queued conversion until its terminal result arrives`() {
        val queue = SerialConversionBatchQueue()

        assertEquals(3, queue.enqueue(listOf(
            QueuedConversion(1, "one"),
            QueuedConversion(2, "two"),
            QueuedConversion(3, "three")
        )))
        assertEquals(1L, queue.takeNext()?.videoId)
        assertNull(queue.takeNext())

        assertTrue(queue.finish(1, ConversionStatus.COMPLETED))
        assertEquals(1, queue.snapshot.done)
        assertEquals(2L, queue.takeNext()?.videoId)

        assertTrue(queue.finish(2, ConversionStatus.FAILED))
        assertEquals(2, queue.snapshot.done)
        assertEquals(1, queue.snapshot.failed)
        assertEquals(3L, queue.takeNext()?.videoId)

        assertTrue(queue.finish(3, ConversionStatus.COMPLETED))
        assertFalse(queue.snapshot.isRunning)
        assertEquals(3, queue.snapshot.done)
    }

    @Test
    fun `deduplicates enqueued videos and ignores unrelated terminal jobs`() {
        val queue = SerialConversionBatchQueue()

        assertEquals(2, queue.enqueue(listOf(
            QueuedConversion(7, "first"),
            QueuedConversion(7, "duplicate"),
            QueuedConversion(8, "second")
        )))
        assertEquals(7L, queue.takeNext()?.videoId)
        assertFalse(queue.finish(8, ConversionStatus.COMPLETED))
        assertEquals(0, queue.snapshot.done)

        assertTrue(queue.finish(7, ConversionStatus.COMPLETED))
        assertEquals(8L, queue.takeNext()?.videoId)
    }
}
