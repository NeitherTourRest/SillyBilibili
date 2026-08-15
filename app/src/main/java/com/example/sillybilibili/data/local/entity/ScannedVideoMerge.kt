package com.example.sillybilibili.data.local.entity

/**
 * Merges fresh cache metadata into an existing record without throwing away app-owned state.
 * A scan is a source-of-truth for cache paths/metadata, not for the user's category, exported
 * MP4, cached cover or online verification result.
 */
internal fun mergeScannedVideo(existing: VideoEntity?, scanned: VideoEntity): VideoEntity {
    if (existing == null) return scanned.copy(sourceAvailable = true)
    return scanned.copy(
        id = existing.id,
        categoryId = existing.categoryId,
        coverPath = existing.coverPath,
        coverSourcePath = scanned.coverSourcePath?.takeIf { it.isNotBlank() } ?: existing.coverSourcePath,
        addedAt = existing.addedAt,
        exportedPath = existing.exportedPath,
        sourceAvailable = true,
        sourceLastSeenAt = existing.sourceLastSeenAt,
        exportedSize = existing.exportedSize,
        exportedLastModified = existing.exportedLastModified,
        onlineStatus = existing.onlineStatus,
        onlineCheckedAt = existing.onlineCheckedAt
    )
}
