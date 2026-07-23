package com.example.sillybilibili.domain.model

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
    val addedAt: Long = System.currentTimeMillis(),
    val exportedPath: String? = null
) {
    val isVertical: Boolean get() = height > width

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
