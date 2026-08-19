package com.example.sillybilibili.ui.pages.scan

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sillybilibili.service.SettingsService
import com.example.sillybilibili.service.ScanJobRegistry
import com.example.sillybilibili.service.VideoScanService
import com.example.sillybilibili.util.SafCapabilityChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ScanDurationPreset(val label: String, val minSeconds: Long?, val maxSeconds: Long?) {
    ALL("全部时长", null, null), SHORT("短视频 · <1 分钟", null, 60),
    MEDIUM("中等 · 1–10 分钟", 60, 600), LONG("长视频 · >10 分钟", 600, null),
    CUSTOM("自定义范围", null, null)
}

enum class ScanSizePreset(val label: String, val minBytes: Long?, val maxBytes: Long?) {
    ALL("全部大小", null, null), SMALL("小于 100 MB", null, 100L * 1024 * 1024),
    MEDIUM("100–500 MB", 100L * 1024 * 1024, 500L * 1024 * 1024),
    LARGE("大于 500 MB", 500L * 1024 * 1024, null)
}

data class ScanUiState(
    val scanPath: String = "",
    val safTreeUri: Uri? = null,
    val isScanning: Boolean = false,
    val scanProgress: VideoScanService.ScanProgress? = null,
    val directorySnapshot: VideoScanService.DirectorySnapshot = VideoScanService.DirectorySnapshot(),
    val foundVideoCount: Int = 0,
    val scanComplete: Boolean = false,
    val scanResultMessage: String = "",
    val isShizukuAvailable: Boolean = false,
    val isDirectAccessAvailable: Boolean = false,
    val useSaf: Boolean = false,
    val safCanAccessAndroidData: Boolean = true,
    val safLimitationMessage: String = "",
    val filterQuality: String? = null,
    val filterOrientation: VideoScanService.ScanOrientation? = null,
    val filterDurationPreset: ScanDurationPreset = ScanDurationPreset.ALL,
    val filterSizePreset: ScanSizePreset = ScanSizePreset.ALL,
    val filterMinDurationSec: String = "",
    val filterMaxDurationSec: String = "",
    val filterSpecificAvIds: String = "",
    val filterQuickMode: Boolean = false
) {
    val activeFilterCount: Int
        get() = listOfNotNull(
            filterQuality,
            filterOrientation,
            filterDurationPreset.takeIf { it != ScanDurationPreset.ALL },
            filterSizePreset.takeIf { it != ScanSizePreset.ALL },
            filterMinDurationSec.takeIf { it.isNotBlank() },
            filterMaxDurationSec.takeIf { it.isNotBlank() },
            filterSpecificAvIds.takeIf { it.isNotBlank() }
        ).size
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsService: SettingsService,
    private val videoScanService: VideoScanService,
    private val scanJobRegistry: ScanJobRegistry
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            scanJobRegistry.session.collect { session ->
                _uiState.update {
                    it.copy(
                        isScanning = session.isScanning,
                        scanProgress = session.progress,
                        foundVideoCount = session.foundVideoCount,
                        scanComplete = session.scanComplete,
                        scanResultMessage = session.resultMessage
                    )
                }
                if (!session.isScanning && session.resultMessage.isNotBlank()) refreshDirectorySnapshot()
            }
        }
        val savedPath = settingsService.scanPath ?: videoScanService.getBilibiliPathConstant()
        val shizukuOk = videoScanService.isShizukuAvailable()
        val directAccessOk = videoScanService.canAccessDirectoryDirectly(savedPath)
        val savedUri = loadSavedSafUri()
        val safCanAccessData = SafCapabilityChecker.canAccessAndroidData()
        _uiState.update {
            it.copy(
                scanPath = savedPath,
                safTreeUri = savedUri,
                isShizukuAvailable = shizukuOk,
                isDirectAccessAvailable = directAccessOk,
                useSaf = safCanAccessData && !directAccessOk && !shizukuOk && savedUri != null,
                safCanAccessAndroidData = safCanAccessData,
                safLimitationMessage = SafCapabilityChecker.limitationMessage()
            )
        }
        refreshDirectorySnapshot()
        // 启动 Shizuku / 授予权限后自动变为可用并重新检查目录，无需重启应用。
        viewModelScope.launch {
            videoScanService.shizukuState.collect { available ->
                _uiState.update { state ->
                    state.copy(
                        isShizukuAvailable = available,
                        useSaf = state.safCanAccessAndroidData && !state.isDirectAccessAvailable && !available && state.safTreeUri != null
                    )
                }
                refreshDirectorySnapshot()
            }
        }
    }

    fun setSafTreeUri(uri: Uri?) {
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            saveSafUri(uri)
        }
        _uiState.update { it.copy(safTreeUri = uri, useSaf = uri != null) }
        refreshDirectorySnapshot()
    }

    fun toggleMode() {
        // Cannot enable SAF on Android 11+ because Android/data/ is hidden
        if (!uiState.value.safCanAccessAndroidData && !uiState.value.useSaf) return
        _uiState.update { it.copy(useSaf = !it.useSaf) }
        refreshDirectorySnapshot()
    }

    fun updateScanPath(path: String) {
        settingsService.scanPath = path
        _uiState.update {
            it.copy(
                scanPath = path,
                isDirectAccessAvailable = videoScanService.canAccessDirectoryDirectly(path),
                useSaf = false
            )
        }
        refreshDirectorySnapshot()
    }
    fun updateFilterQuality(q: String?) { _uiState.update { it.copy(filterQuality = q) } }
    fun updateFilterOrientation(orientation: VideoScanService.ScanOrientation?) { _uiState.update { it.copy(filterOrientation = orientation) } }
    fun updateDurationPreset(preset: ScanDurationPreset) {
        _uiState.update {
            it.copy(
                filterDurationPreset = preset,
                filterMinDurationSec = if (preset == ScanDurationPreset.CUSTOM) it.filterMinDurationSec else "",
                filterMaxDurationSec = if (preset == ScanDurationPreset.CUSTOM) it.filterMaxDurationSec else ""
            )
        }
    }
    fun updateSizePreset(preset: ScanSizePreset) { _uiState.update { it.copy(filterSizePreset = preset) } }
    fun updateFilterMinDuration(d: String) { _uiState.update { it.copy(filterMinDurationSec = d.filter { it.isDigit() }) } }
    fun updateFilterMaxDuration(d: String) { _uiState.update { it.copy(filterMaxDurationSec = d.filter { it.isDigit() }) } }
    fun updateFilterSpecificAvIds(input: String) { _uiState.update { it.copy(filterSpecificAvIds = input.filter { it.isDigit() || it == ',' || it == '，' || it.isWhitespace() }) } }
    fun toggleQuickMode() { _uiState.update { it.copy(filterQuickMode = !it.filterQuickMode) } }
    fun clearFilters() {
        _uiState.update {
            it.copy(
                filterQuality = null,
                filterOrientation = null,
                filterDurationPreset = ScanDurationPreset.ALL,
                filterSizePreset = ScanSizePreset.ALL,
                filterMinDurationSec = "",
                filterMaxDurationSec = "",
                filterSpecificAvIds = ""
            )
        }
    }

    fun startScan() {
        val state = _uiState.value
        if (state.isScanning) return

        val durationPreset = state.filterDurationPreset
        val sizePreset = state.filterSizePreset
        val filter = VideoScanService.ScanFilter(
                quality = state.filterQuality,
                minDurationMs = (durationPreset.minSeconds ?: state.filterMinDurationSec.toLongOrNull())?.times(1000),
                maxDurationMs = (durationPreset.maxSeconds ?: state.filterMaxDurationSec.toLongOrNull())?.times(1000),
                minSizeBytes = sizePreset.minBytes,
                maxSizeBytes = sizePreset.maxBytes,
                specificAvIds = state.filterSpecificAvIds
                    .split(',', '，', ' ')
                    .mapNotNull { it.trim().toLongOrNull() }
                    .takeIf { it.isNotEmpty() },
                orientation = state.filterOrientation,
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

        scanJobRegistry.start(flow)
    }

    fun clearScanResult() = scanJobRegistry.clearResult()

    private fun refreshDirectorySnapshot() {
        viewModelScope.launch {
            val state = _uiState.value
            val snapshot = if (state.useSaf && state.safTreeUri != null) {
                videoScanService.inspectDirectoryFromUri(state.safTreeUri)
            } else {
                videoScanService.inspectDirectory(state.scanPath.ifBlank { videoScanService.getBilibiliPathConstant() })
            }
            _uiState.update {
                it.copy(
                    directorySnapshot = snapshot,
                    isShizukuAvailable = videoScanService.isShizukuAvailable(),
                    isDirectAccessAvailable = snapshot.access == VideoScanService.ScanAccess.DIRECT
                )
            }
        }
    }

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
