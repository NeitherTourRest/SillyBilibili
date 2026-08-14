package com.example.sillybilibili.service

import com.example.sillybilibili.domain.model.ConversionProgress
import com.example.sillybilibili.domain.model.ConversionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps conversion progress observable while a foreground service owns the actual work. */
@Singleton
class ConversionJobRegistry @Inject constructor() {
    private val _jobs = MutableStateFlow<Map<Long, ConversionProgress>>(emptyMap())
    val jobs: StateFlow<Map<Long, ConversionProgress>> = _jobs.asStateFlow()

    fun isRunning(videoId: Long): Boolean = _jobs.value[videoId]?.status in setOf(
        ConversionStatus.PENDING,
        ConversionStatus.CONVERTING
    )

    fun begin(progress: ConversionProgress): Boolean {
        if (isRunning(progress.videoId)) return false
        update(progress)
        return true
    }

    fun update(progress: ConversionProgress) {
        _jobs.value = _jobs.value + (progress.videoId to progress)
    }

    fun progressFor(videoId: Long) = jobs.map { it[videoId] }

    fun clear(videoId: Long) {
        _jobs.value = _jobs.value - videoId
    }
}
