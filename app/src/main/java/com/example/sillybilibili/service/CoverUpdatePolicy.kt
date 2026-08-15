package com.example.sillybilibili.service

/**
 * A successful cache lookup often returns the same path already stored in Room. Rewriting that
 * unchanged value invalidates the home list and can repeatedly recreate visible cards.
 */
internal fun shouldPersistCoverPath(currentPath: String?, cachedPath: String?): Boolean =
    !cachedPath.isNullOrBlank() && cachedPath != currentPath
