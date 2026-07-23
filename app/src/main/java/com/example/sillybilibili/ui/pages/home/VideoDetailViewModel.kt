package com.example.sillybilibili.ui.pages.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sillybilibili.domain.model.ConversionProgress
import com.example.sillybilibili.domain.model.ConversionStatus
import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.domain.repository.VideoRepository
import com.example.sillybilibili.service.SettingsService
import com.example.sillybilibili.service.VideoConverterService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VideoDetailUiState(
    val video: Video? = null,
    val conversionProgress: ConversionProgress? = null,
    val isLoading: Boolean = false,
    val isConverting: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val videoRepository: VideoRepository,
    private val videoConverterService: VideoConverterService,
    private val settingsService: SettingsService
) : ViewModel() {

    private val videoId: Long = savedStateHandle.get<Long>("videoId") ?: 0L

    private val _uiState = MutableStateFlow(VideoDetailUiState())
    val uiState: StateFlow<VideoDetailUiState> = _uiState.asStateFlow()

    init {
        loadVideo()
    }

    private fun loadVideo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val video = videoRepository.getVideoById(videoId)
            _uiState.update { it.copy(video = video, isLoading = false) }
        }
    }

    fun convertToMp4(outputDir: String) {
        val video = _uiState.value.video ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isConverting = true, conversionProgress = null) }

            videoConverterService.convertToMp4(
                videoPath = video.path,
                audioPath = video.audioPath,
                outputDir = outputDir,
                outputFileName = video.title,
                videoId = video.id
            ).collect { progress ->
                _uiState.update {
                    it.copy(
                        isConverting = progress.status == ConversionStatus.CONVERTING,
                        conversionProgress = progress
                    )
                }
                if (progress.status == ConversionStatus.COMPLETED && progress.outputPath != null) {
                    videoRepository.updateVideo(video.copy(exportedPath = progress.outputPath))
                    loadVideo()
                }
            }
        }
    }

    fun clearConversionStatus() {
        _uiState.update { it.copy(conversionProgress = null) }
    }

    fun getDefaultOutputPath(): String {
        return settingsService.outputPath ?: videoConverterService.getDefaultOutputPath()
    }
}
