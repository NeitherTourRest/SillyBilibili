package com.example.sillybilibili.ui.pages.exported

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.domain.repository.VideoRepository
import com.example.sillybilibili.service.CoverCacheService
import com.example.sillybilibili.service.ExternalMediaSyncService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ExportedUiState(
    val videos: List<Video> = emptyList(),
    val isLoading: Boolean = false,
    val totalSize: Long = 0,
    val deleteConfirmVideo: Video? = null
)

@HiltViewModel
class ExportedViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    private val coverCacheService: CoverCacheService,
    private val externalMediaSyncService: ExternalMediaSyncService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportedUiState())
    val uiState: StateFlow<ExportedUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { externalMediaSyncService.reconcileExportedFiles() }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            videoRepository.getExportedVideos().collect { videos ->
                _uiState.update {
                    it.copy(
                        videos = videos,
                        isLoading = false,
                        totalSize = videos.sumOf { v -> v.exportedSize.takeIf { it > 0L } ?: v.size }
                    )
                }
            }
        }
    }

    fun showDeleteConfirm(video: Video) {
        _uiState.update { it.copy(deleteConfirmVideo = video) }
    }

    fun requestCover(video: Video) {
        viewModelScope.launch {
            coverCacheService.cacheCover(video)?.let { cachedPath ->
                videoRepository.updateVideo(video.copy(coverPath = cachedPath))
            }
        }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(deleteConfirmVideo = null) }
    }

    fun deleteExported(video: Video) {
        viewModelScope.launch {
            // Delete the MP4 file from disk
            video.exportedPath?.let { File(it).delete() }
            // Clear exportedPath in DB
            videoRepository.updateVideo(
                video.copy(exportedPath = null, exportedSize = 0L, exportedLastModified = 0L)
            )
            _uiState.update { it.copy(deleteConfirmVideo = null) }
        }
    }
}
