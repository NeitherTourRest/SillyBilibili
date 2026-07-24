package com.example.sillybilibili.util

import android.os.Build

/**
 * SAF (Storage Access Framework) cannot access Android/data/ on Android 11+ (API 30+).
 * The system file picker intentionally hides these directories.
 * Shizuku or root is required for Bilibili cache access on modern Android.
 */
object SafCapabilityChecker {

    // Android 11+ hides Android/data/ and Android/obb/ from SAF
    fun canAccessAndroidData(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R

    fun limitationMessage(): String = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            "Android 11+ 系统文件选择器无法访问 Android/data/ 目录，请使用 Shizuku 模式"
        else -> ""
    }
}
