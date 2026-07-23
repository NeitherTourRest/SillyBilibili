// ============================================================
// PermissionHelper.kt — Android 权限检查工具类
// ============================================================
// Android 不同版本对文件访问的权限要求不同：
//   Android 10 (API 29) 以下 → READ_EXTERNAL_STORAGE
//   Android 11 (API 30)     → Manage All Files
//   Android 13 (API 33)     → READ_MEDIA_VIDEO
// 这个类统一处理这些差异。
// 被 MainActivity.kt 调用。
// ============================================================

package com.example.sillybilibili.util

// Manifest = Android 系统权限名称列表（如 READ_EXTERNAL_STORAGE）
import android.Manifest
// Context = Android 上下文
import android.content.Context
// Intent = Android 的"意图"，用于启动另一个页面（如系统设置页）
import android.content.Intent
// PackageManager = 包管理器，用于检查权限是否已授权
import android.content.pm.PackageManager
// Uri = 统一资源标识符，用于构造跳转链接（如 "package:com.example.xxx"）
import android.net.Uri
// Build = 获取当前 Android 系统版本号（如 SDK_INT = 33）
import android.os.Build
// Environment = 系统环境（检查是否已有"管理所有文件"权限）
import android.os.Environment
// Settings = 系统的设置页面，用于跳转到权限设置页
import android.provider.Settings
// ContextCompat = AndroidX 的兼容版权限检查工具
import androidx.core.content.ContextCompat

object PermissionHelper {

    // 检查当前是否有文件存储权限
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+：需要 READ_MEDIA_VIDEO 权限
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11-12：需要 "管理所有文件" 权限
            Environment.isExternalStorageManager()
        } else {
            // Android 10 以下：需要 READ_EXTERNAL_STORAGE
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun needsAllFilesAccess(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    fun needsManageStoragePermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    fun createManageStorageIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }
}
