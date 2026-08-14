package com.example.sillybilibili.service

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        private const val KEY_BACKGROUND_PLAYBACK = "background_playback"
        private const val KEY_APP_LANGUAGE = "app_language"
    }

    enum class AppLanguage(val languageTag: String) {
        SIMPLIFIED_CHINESE("zh-CN"),
        ENGLISH("en");

        companion object {
            fun fromLanguageTag(value: String?): AppLanguage =
                entries.firstOrNull { it.languageTag == value } ?: SIMPLIFIED_CHINESE
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _backgroundPlaybackEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_BACKGROUND_PLAYBACK, true)
    )
    val backgroundPlaybackEnabledFlow: StateFlow<Boolean> = _backgroundPlaybackEnabled
    private val _appLanguage = MutableStateFlow(AppLanguage.fromLanguageTag(prefs.getString(KEY_APP_LANGUAGE, null)))
    val appLanguageFlow: StateFlow<AppLanguage> = _appLanguage

    var scanPath: String?
        get() = prefs.getString(KEY_SCAN_PATH, null)
        set(value) = prefs.edit().putString(KEY_SCAN_PATH, value).apply()

    var outputPath: String?
        get() = prefs.getString(KEY_OUTPUT_PATH, null)
        set(value) = prefs.edit().putString(KEY_OUTPUT_PATH, value).apply()

    var autoScan: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SCAN, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SCAN, value).apply()

    /** When disabled, leaving the playback page stops and clears the MediaSession. */
    var backgroundPlaybackEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND_PLAYBACK, true)
        set(value) {
            prefs.edit().putBoolean(KEY_BACKGROUND_PLAYBACK, value).apply()
            _backgroundPlaybackEnabled.value = value
        }

    /** App language is independent of the device locale and defaults to Simplified Chinese. */
    var appLanguage: AppLanguage
        get() = AppLanguage.fromLanguageTag(prefs.getString(KEY_APP_LANGUAGE, null))
        set(value) {
            prefs.edit().putString(KEY_APP_LANGUAGE, value.languageTag).apply()
            _appLanguage.value = value
        }

    fun clear() {
        prefs.edit().clear().apply()
        _backgroundPlaybackEnabled.value = true
        _appLanguage.value = AppLanguage.SIMPLIFIED_CHINESE
    }
}
