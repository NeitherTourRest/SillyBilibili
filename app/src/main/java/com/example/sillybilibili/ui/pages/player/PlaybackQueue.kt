package com.example.sillybilibili.ui.pages.player

import java.util.UUID

/** A lightweight, in-process queue passed from the current browse result to the player. */
data class PlaybackQueueItem(
    val id: Long,
    val title: String,
    /** MP4 path or the video.m4s path. */
    val videoPath: String,
    /** Null for an already muxed media file such as an exported MP4. */
    val audioPath: String? = null,
    /** Optional cache/export cover for the inline playback list. */
    val coverPath: String? = null
) {
    val isMuxedFile: Boolean get() = audioPath.isNullOrBlank()
    /** Bilibili cache is stored as separate DASH tracks; exported MP4 files are already muxed. */
    val canConvertToMp4: Boolean get() = id > 0L && videoPath.isNotBlank() && !audioPath.isNullOrBlank()
}

data class PlaybackQueue(
    val id: String,
    val items: List<PlaybackQueueItem>,
    val selectedIndex: Int
)

/**
 * Navigation arguments are intentionally kept small. The media session owns the real queue after
 * playback starts; this store is only needed while navigating from a result list to the player.
 */
object PlaybackQueueStore {
    @Volatile
    private var currentQueue: PlaybackQueue? = null
    @Volatile
    private var activeQueueId: String? = null

    fun replace(items: List<PlaybackQueueItem>, selectedVideoId: Long): PlaybackQueue {
        val usableItems = items.filter { it.videoPath.isNotBlank() }
        val selectedIndex = usableItems.indexOfFirst { it.id == selectedVideoId }.coerceAtLeast(0)
        return PlaybackQueue(UUID.randomUUID().toString(), usableItems, selectedIndex).also {
            currentQueue = it
        }
    }

    fun prepareSingle(filePath: String, title: String): PlaybackQueue =
        PlaybackQueue(UUID.randomUUID().toString(), listOf(PlaybackQueueItem(-1L, title, filePath)), 0)
            .also { currentQueue = it }

    fun preparePlayback(videoId: Long, videoPath: String, title: String, audioPath: String? = null): PlaybackQueue {
        val previous = currentQueue
        val index = previous?.items?.indexOfFirst { it.id == videoId } ?: -1
        if (previous == null || index < 0) {
            return PlaybackQueue(UUID.randomUUID().toString(), listOf(PlaybackQueueItem(videoId, title, videoPath, audioPath)), 0)
                .also { currentQueue = it }
        }
        val updatedItems = previous.items.toMutableList().apply {
            set(index, PlaybackQueueItem(videoId, title, videoPath, audioPath, previous.items[index].coverPath))
        }
        return PlaybackQueue(UUID.randomUUID().toString(), updatedItems, index).also {
            currentQueue = it
        }
    }

    fun queueFor(id: String): PlaybackQueue? = currentQueue?.takeIf { it.id == id }

    fun currentQueue(): PlaybackQueue? = currentQueue

    fun activeQueueId(): String? = activeQueueId

    fun markActive(id: String) { activeQueueId = id }
}
