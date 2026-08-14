package com.example.sillybilibili.domain.model

/**
 * Result of a conservative online availability lookup. A failed lookup is deliberately not
 * treated as a removed video: it can also be caused by connectivity, login or access limits.
 */
enum class OnlineVideoStatus {
    UNCHECKED,
    ONLINE,
    UNAVAILABLE,
    UNVERIFIABLE;

    companion object {
        fun fromStorage(value: String): OnlineVideoStatus =
            entries.firstOrNull { it.name == value } ?: UNCHECKED
    }
}

data class Video(
    val id: Long = 0,
    val avid: Long,
    val cid: Long,
    val title: String,
    val ownerName: String = "",
    val quality: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val path: String,
    val audioPath: String,
    val size: Long,
    val duration: Long,
    val categoryId: Long? = null,
    val coverPath: String? = null,
    /** Original cover location. It is copied to [coverPath] only when the card is displayed. */
    val coverSourcePath: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val exportedPath: String? = null,
    /** False after a successful full scan confirms that the original Bilibili cache is gone. */
    val sourceAvailable: Boolean = true,
    val sourceLastSeenAt: Long = 0L,
    /** Fingerprint of the exported MP4 for lightweight external-change detection. */
    val exportedSize: Long = 0L,
    val exportedLastModified: Long = 0L,
    /** Last conservative availability result for the original Bilibili page. */
    val onlineStatus: OnlineVideoStatus = OnlineVideoStatus.UNCHECKED,
    val onlineCheckedAt: Long = 0L
) {
    /** Original covers remain visible while a local cache copy is created. */
    val displayCoverPath: String? get() = coverPath ?: coverSourcePath

    val isVertical: Boolean get() = height > width

    /** Portrait previews keep their full subject framing instead of being cropped into a landscape slot. */
    val previewAspectRatio: Float
        get() = if (isVertical) 3f / 4f else 16f / 10f

    val resolutionLabel: String
        get() = if (width > 0 && height > 0) "${width}×${height}" else ""

    val formattedSize: String
        get() = when {
            size >= 1024 * 1024 * 1024 -> "%.2f GB".format(size / (1024.0 * 1024.0 * 1024.0))
            size >= 1024 * 1024 -> "%.2f MB".format(size / (1024.0 * 1024.0))
            size >= 1024 -> "%.2f KB".format(size / 1024.0)
            else -> "$size B"
        }

    val formattedDuration: String
        get() {
            val totalSeconds = duration / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                "%02d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
        }
}
