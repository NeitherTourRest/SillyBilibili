package com.example.sillybilibili.ui.pages.scan

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sillybilibili.service.SettingsService
import com.example.sillybilibili.service.VideoScanService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanUiState(
    val scanPath: String = "",
    val safTreeUri: Uri? = null,
    val isScanning: Boolean = false,
    val scanProgress: VideoScanService.ScanProgress? = null,
    val foundVideoCount: Int = 0,
    val scanComplete: Boolean = false,
    val scanResultMessage: String = "",
    val isShizukuAvailable: Boolean = false,
    val useSaf: Boolean = false,
    val filterQuality: String? = null,
    val filterMinDurationSec: String = "",
    val filterMaxDurationSec: String = "",
    val filterQuickMode: Boolean = false
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsService: SettingsService,
    private val videoScanService: VideoScanService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    init {
        val shizukuOk = videoScanService.isShizukuAvailable()
        val savedUri = loadSavedSafUri()
        val savedPath = settingsService.scanPath ?: videoScanService.getBilibiliPathConstant()
        _uiState.update {
            it.copy(
                scanPath = savedPath,
                safTreeUri = savedUri,
                isShizukuAvailable = shizukuOk,
                useSaf = !shizukuOk && savedUri != null
            )
        }
    }

    fun setSafTreeUri(uri: Uri?) {
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            saveSafUri(uri)
        }
        _uiState.update { it.copy(safTreeUri = uri, useSaf = uri != null) }
    }

    fun toggleMode() {
        _uiState.update { it.copy(useSaf = !it.useSaf) }
    }

    fun updateScanPath(path: String) { settingsService.scanPath = path; _uiState.update { it.copy(scanPath = path) } }
    fun updateFilterQuality(q: String?) { _uiState.update { it.copy(filterQuality = q) } }
    fun updateFilterMinDuration(d: String) { _uiState.update { it.copy(filterMinDurationSec = d.filter { it.isDigit() }) } }
    fun updateFilterMaxDuration(d: String) { _uiState.update { it.copy(filterMaxDurationSec = d.filter { it.isDigit() }) } }
    fun toggleQuickMode() { _uiState.update { it.copy(filterQuickMode = !it.filterQuickMode) } }

    fun startScan() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(isScanning = true, scanComplete = false, scanResultMessage = "", foundVideoCount = 0) }

            val filter = VideoScanService.ScanFilter(
                quality = state.filterQuality,
                minDurationMs = state.filterMinDurationSec.toLongOrNull()?.let { it * 1000 },
                maxDurationMs = state.filterMaxDurationSec.toLongOrNull()?.let { it * 1000 },
                mode = if (state.filterQuickMode) VideoScanService.ScanMode.QUICK else VideoScanService.ScanMode.FULL
            )

            val flow = if (state.useSaf && state.safTreeUri != null) {
                videoScanService.scanDirectoryFromUri(state.safTreeUri!!, filter)
            } else {
                videoScanService.scanDirectory(
                    state.scanPath.ifEmpty { videoScanService.getBilibiliPathConstant() },
                    filter
                )
            }

            flow.collect { progress ->
                _uiState.update { it.copy(scanProgress = progress, foundVideoCount = progress.foundVideoCount) }
            }
            _uiState.update { it.copy(isScanning = false, scanComplete = true, scanResultMessage = "Done: ${_uiState.value.foundVideoCount} new videos") }
        }
    }

    fun clearScanResult() { _uiState.update { it.copy(scanComplete = false, scanResultMessage = "", foundVideoCount = 0) } }

    private fun saveSafUri(uri: Uri) {
        context.getSharedPreferences("silly_bilibili_prefs", Context.MODE_PRIVATE)
            .edit().putString("saf_tree_uri", uri.toString()).apply()
    }

    private fun loadSavedSafUri(): Uri? {
        val str = context.getSharedPreferences("silly_bilibili_prefs", Context.MODE_PRIVATE)
            .getString("saf_tree_uri", null) ?: return null
        return try { Uri.parse(str) } catch (_: Exception) { null }
    }
}
