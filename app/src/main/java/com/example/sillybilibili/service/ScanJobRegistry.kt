package com.example.sillybilibili.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps a scan alive when the Compose scan screen is removed from the navigation stack. */
@Singleton
class ScanJobRegistry @Inject constructor() {
    data class Session(
        val isScanning: Boolean = false,
        val progress: VideoScanService.ScanProgress? = null,
        val foundVideoCount: Int = 0,
        val scanComplete: Boolean = false,
        val resultMessage: String = ""
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scanJob: Job? = null
    private val _session = MutableStateFlow(Session())
    val session = _session.asStateFlow()

    /** @return false when another scan is already active. */
    fun start(flow: Flow<VideoScanService.ScanProgress>): Boolean {
        if (_session.value.isScanning) return false
        _session.value = Session(isScanning = true)
        scanJob = scope.launch {
            var lastProgress: VideoScanService.ScanProgress? = null
            try {
                flow.collect { progress ->
                    lastProgress = progress
                    _session.value = _session.value.copy(progress = progress, foundVideoCount = progress.foundVideoCount)
                }
                finish(lastProgress)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val message = buildFailureMessage(error)
                _session.value = Session(
                    progress = lastProgress?.copy(phase = VideoScanService.ScanPhase.ERROR, statusMessage = message),
                    foundVideoCount = lastProgress?.foundVideoCount ?: 0,
                    resultMessage = message
                )
            }
        }
        return true
    }

    fun clearResult() {
        if (!_session.value.isScanning) _session.value = Session()
    }

    private fun finish(lastProgress: VideoScanService.ScanProgress?) {
        val completed = lastProgress?.phase == VideoScanService.ScanPhase.COMPLETE
        val message = when {
            completed -> lastProgress?.statusMessage ?: "扫描完成"
            lastProgress?.phase == VideoScanService.ScanPhase.ERROR -> lastProgress.statusMessage
            else -> "扫描意外结束，未收到完成状态。请检查目录访问权限后重试。"
        }
        _session.value = Session(
            progress = lastProgress,
            foundVideoCount = lastProgress?.foundVideoCount ?: 0,
            scanComplete = completed,
            resultMessage = message
        )
    }

    private fun buildFailureMessage(error: Throwable): String = when (error) {
        is SecurityException -> "扫描被系统拒绝：请检查目录授权或 Shizuku 权限。"
        is java.io.IOException -> "读取缓存时发生 I/O 错误：${error.message ?: "存储设备可能不可用"}"
        else -> "扫描失败：${error.message ?: error.javaClass.simpleName}"
    }
}
