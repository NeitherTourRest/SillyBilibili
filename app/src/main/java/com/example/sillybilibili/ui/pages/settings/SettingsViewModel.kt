package com.example.sillybilibili.ui.pages.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sillybilibili.service.OnlineStatusRefreshResult
import com.example.sillybilibili.service.OnlineStatusRefreshProgress
import com.example.sillybilibili.service.OnlineVideoStatusService
import com.example.sillybilibili.service.SettingsService
import com.example.sillybilibili.service.VideoConverterService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val outputPath: String = "",
    val backgroundPlaybackEnabled: Boolean = true,
    val appLanguage: SettingsService.AppLanguage = SettingsService.AppLanguage.SIMPLIFIED_CHINESE,
    val isRefreshingOnlineStatuses: Boolean = false,
    val onlineStatusRefreshProgress: OnlineStatusRefreshProgress? = null,
    val onlineStatusRefreshResult: OnlineStatusRefreshResult? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsService: SettingsService,
    private val videoConverterService: VideoConverterService,
    private val onlineVideoStatusService: OnlineVideoStatusService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(
                outputPath = settingsService.outputPath ?: videoConverterService.getDefaultOutputPath(),
                backgroundPlaybackEnabled = settingsService.backgroundPlaybackEnabled,
                appLanguage = settingsService.appLanguage
            )
        }
    }

    fun updateOutputPath(path: String) { settingsService.outputPath = path; _uiState.update { it.copy(outputPath = path) } }

    fun updateBackgroundPlayback(enabled: Boolean) {
        settingsService.backgroundPlaybackEnabled = enabled
        _uiState.update { it.copy(backgroundPlaybackEnabled = enabled) }
    }

    fun updateAppLanguage(language: SettingsService.AppLanguage) {
        settingsService.appLanguage = language
        _uiState.update { it.copy(appLanguage = language) }
    }

    fun refreshOnlineStatuses() {
        if (_uiState.value.isRefreshingOnlineStatuses) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRefreshingOnlineStatuses = true,
                    onlineStatusRefreshProgress = null,
                    onlineStatusRefreshResult = null
                )
            }
            val result = onlineVideoStatusService.forceRefreshAll { progress ->
                _uiState.update { it.copy(onlineStatusRefreshProgress = progress) }
            }
            _uiState.update {
                it.copy(
                    isRefreshingOnlineStatuses = false,
                    onlineStatusRefreshResult = result
                )
            }
        }
    }
}
