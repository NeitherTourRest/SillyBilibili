package com.example.sillybilibili.service

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsService @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREFS_NAME = "silly_bilibili_prefs"
        private const val KEY_SCAN_PATH = "scan_path"
        private const val KEY_OUTPUT_PATH = "output_path"
        private const val KEY_AUTO_SCAN = "auto_scan"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var scanPath: String?
        get() = prefs.getString(KEY_SCAN_PATH, null)
        set(value) = prefs.edit().putString(KEY_SCAN_PATH, value).apply()

    var outputPath: String?
        get() = prefs.getString(KEY_OUTPUT_PATH, null)
        set(value) = prefs.edit().putString(KEY_OUTPUT_PATH, value).apply()

    var autoScan: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SCAN, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SCAN, value).apply()

    fun clear() = prefs.edit().clear().apply()
}
