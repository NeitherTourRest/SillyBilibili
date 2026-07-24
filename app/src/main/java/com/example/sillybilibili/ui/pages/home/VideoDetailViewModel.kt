package com.example.sillybilibili.ui.pages.home

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sillybilibili.domain.model.ConversionProgress
import com.example.sillybilibili.domain.model.ConversionStatus
import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.domain.repository.VideoRepository
import com.example.sillybilibili.service.SettingsService
import com.example.sillybilibili.service.VideoConverterService
import com.example.sillybilibili.util.ThumbnailHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
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
    @ApplicationContext private val context: Context,
    private val videoRepository: VideoRepository,
    private val videoConverterService: VideoConverterService,
    private val thumbnailHelper: ThumbnailHelper,
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
                    var updated = video.copy(exportedPath = progress.outputPath)
                    if (video.coverPath == null) {
                        val coverFile = File(context.cacheDir, "covers/${video.id}_converted.jpg")
                        if (thumbnailHelper.extractFrame(progress.outputPath, coverFile)) {
                            updated = updated.copy(coverPath = coverFile.absolutePath)
                        }
                    }
                    videoRepository.updateVideo(updated)
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
