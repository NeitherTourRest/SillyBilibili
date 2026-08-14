package com.example.sillybilibili.ui.pages.exported

import com.example.sillybilibili.domain.model.Video
import java.io.File
import java.util.Locale

/** Filters that are specific to the locally exported MP4 library. */
sealed interface ExportedCategoryFilter {
    data object ALL : ExportedCategoryFilter
    data object UNCATEGORIZED : ExportedCategoryFilter
    data class Category(val id: Long) : ExportedCategoryFilter
}

enum class ExportedSourceFilter { ALL, CACHE_AVAILABLE, EXPORTED_ONLY }
enum class ExportedOrientationFilter { ALL, LANDSCAPE, PORTRAIT }
enum class ExportedSort { EXPORTED_RECENT, TITLE, FILE_SIZE }

data class ExportedLibraryFilter(
    val query: String = "",
    val category: ExportedCategoryFilter = ExportedCategoryFilter.ALL,
    val source: ExportedSourceFilter = ExportedSourceFilter.ALL,
    val orientation: ExportedOrientationFilter = ExportedOrientationFilter.ALL,
    val sort: ExportedSort = ExportedSort.EXPORTED_RECENT
) {
    val isActive: Boolean
        get() = query.isNotBlank() ||
            category != ExportedCategoryFilter.ALL ||
            source != ExportedSourceFilter.ALL ||
            orientation != ExportedOrientationFilter.ALL ||
            sort != ExportedSort.EXPORTED_RECENT
}

fun filterExportedVideos(videos: List<Video>, filter: ExportedLibraryFilter): List<Video> {
    val query = filter.query.trim().lowercase(Locale.ROOT)
    val comparator = when (filter.sort) {
        ExportedSort.EXPORTED_RECENT -> compareByDescending<Video> { it.exportedLastModified }
            .thenByDescending { it.addedAt }
            .thenBy { it.id }
        ExportedSort.TITLE -> compareBy<Video> { it.title.lowercase(Locale.ROOT) }.thenBy { it.id }
        ExportedSort.FILE_SIZE -> compareByDescending<Video> { it.exportedSize.takeIf { size -> size > 0L } ?: it.size }
            .thenBy { it.title.lowercase(Locale.ROOT) }
            .thenBy { it.id }
    }

    return videos.asSequence()
        .filter { video ->
            query.isEmpty() || searchableExportedText(video).contains(query)
        }
        .filter { video ->
            when (val category = filter.category) {
                ExportedCategoryFilter.ALL -> true
                ExportedCategoryFilter.UNCATEGORIZED -> video.categoryId == null
                is ExportedCategoryFilter.Category -> video.categoryId == category.id
            }
        }
        .filter { video ->
            when (filter.source) {
                ExportedSourceFilter.ALL -> true
                ExportedSourceFilter.CACHE_AVAILABLE -> video.sourceAvailable
                ExportedSourceFilter.EXPORTED_ONLY -> !video.sourceAvailable
            }
        }
        .filter { video ->
            when (filter.orientation) {
                ExportedOrientationFilter.ALL -> true
                ExportedOrientationFilter.LANDSCAPE -> !video.isVertical
                ExportedOrientationFilter.PORTRAIT -> video.isVertical
            }
        }
        .sortedWith(comparator)
        .toList()
}

private fun searchableExportedText(video: Video): String = buildString {
    append(video.title)
    append(' ')
    append(video.ownerName)
    append(' ')
    append(video.avid)
    append(' ')
    append(video.cid)
    append(' ')
    append(video.exportedPath?.let(::File)?.name.orEmpty())
}.lowercase(Locale.ROOT)

/** Returns a portable MP4 filename or null if it would escape/overwrite another path. */
fun normalizedExportFileName(rawName: String): String? {
    val trimmed = rawName.trim()
    if (trimmed.isEmpty() || trimmed == "." || trimmed == "..") return null
    if (trimmed.contains('/') || trimmed.contains('\\') || trimmed.any { it.code < 32 }) return null
    return if (trimmed.endsWith(".mp4", ignoreCase = true)) trimmed else "$trimmed.mp4"
}
