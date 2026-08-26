package com.example.sillybilibili.service

import com.example.sillybilibili.domain.model.ConversionStatus

/** A small, platform-free state machine for a single serial conversion batch. */
data class QueuedConversion(
    val videoId: Long,
    val videoName: String
)

data class ConversionBatchSnapshot(
    val total: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val currentVideoId: Long? = null,
    val currentVideoName: String? = null,
    val isRunning: Boolean = false
) {
    val done: Int get() = completed + failed
}

/**
 * Holds only queue state. The coordinator owns service launching and feeds terminal results back
 * through [finish], so a new item can never start before the current one has really ended.
 */
internal class SerialConversionBatchQueue {
    private val waiting = ArrayDeque<QueuedConversion>()
    private val batchIds = mutableSetOf<Long>()
    private var current: QueuedConversion? = null
    private var completed = 0
    private var failed = 0

    var snapshot: ConversionBatchSnapshot = ConversionBatchSnapshot()
        private set

    fun enqueue(items: List<QueuedConversion>): Int {
        if (!snapshot.isRunning) {
            waiting.clear()
            batchIds.clear()
            current = null
            completed = 0
            failed = 0
        }
        val accepted = items.filter { it.videoId > 0 && batchIds.add(it.videoId) }
        waiting.addAll(accepted)
        publish()
        return accepted.size
    }

    fun takeNext(): QueuedConversion? {
        if (current != null) return null
        current = waiting.removeFirstOrNull()
        publish()
        return current
    }

    fun finish(videoId: Long, status: ConversionStatus): Boolean {
        if (status != ConversionStatus.COMPLETED && status != ConversionStatus.FAILED) return false
        val active = current ?: return false
        if (active.videoId != videoId) return false
        if (status == ConversionStatus.COMPLETED) completed++ else failed++
        current = null
        publish()
        return true
    }

    private fun publish() {
        snapshot = ConversionBatchSnapshot(
            total = batchIds.size,
            completed = completed,
            failed = failed,
            currentVideoId = current?.videoId,
            currentVideoName = current?.videoName,
            isRunning = current != null || waiting.isNotEmpty()
        )
    }
}
