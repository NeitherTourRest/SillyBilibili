package com.example.sillybilibili.service

/**
 * A successful cache lookup often returns the same path already stored in Room. Rewriting that
 * unchanged value invalidates the home list and can repeatedly recreate visible cards.
 */
internal fun shouldPersistCoverPath(currentPath: String?, cachedPath: String?): Boolean =
    !cachedPath.isNullOrBlank() && cachedPath != currentPath

/**
 * 封面生成失败（源封面缺失且抽帧失败）后限频重试的间隔。
 * 首帧提取可能因临时原因失败（Shizuku 未授权、存储空间不足），
 * 永久放弃会让这些视频一直停留在占位图；限频重试则不会在滚动时反复重试。
 */
internal const val COVER_RETRY_INTERVAL_MS = 10 * 60 * 1000L
