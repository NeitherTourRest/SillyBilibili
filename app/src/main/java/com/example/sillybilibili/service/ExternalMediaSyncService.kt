package com.example.sillybilibili.service

import com.example.sillybilibili.domain.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Lightweight check for changes made to exported MP4 files outside this app. */
@Singleton
class ExternalMediaSyncService @Inject constructor(
    private val videoRepository: VideoRepository
) {
    suspend fun reconcileExportedFiles() = withContext(Dispatchers.IO) {
        videoRepository.getExportedVideosOnce().forEach { video ->
            val path = video.exportedPath ?: return@forEach
            val file = File(path)
            when {
                !file.isFile -> videoRepository.updateVideo(
                    video.copy(exportedPath = null, exportedSize = 0L, exportedLastModified = 0L)
                )
                file.length() != video.exportedSize || file.lastModified() != video.exportedLastModified ->
                    videoRepository.updateVideo(
                        video.copy(exportedSize = file.length(), exportedLastModified = file.lastModified())
                    )
            }
        }
    }
}
