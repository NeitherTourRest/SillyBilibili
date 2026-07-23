package com.example.sillybilibili.ui.pages.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.sillybilibili.service.SettingsService
import com.example.sillybilibili.service.VideoConverterService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SettingsUiState(
    val outputPath: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsService: SettingsService,
    private val videoConverterService: VideoConverterService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(outputPath = settingsService.outputPath ?: videoConverterService.getDefaultOutputPath())
        }
    }

    fun updateOutputPath(path: String) { settingsService.outputPath = path; _uiState.update { it.copy(outputPath = path) } }
}
