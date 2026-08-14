package com.example.sillybilibili

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.lifecycleScope
import com.example.sillybilibili.service.ExternalMediaSyncService
import com.example.sillybilibili.service.SettingsService
import com.example.sillybilibili.ui.navigation.AppNavHost
import com.example.sillybilibili.ui.theme.SillyBilibiliTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var externalMediaSyncService: ExternalMediaSyncService
    @Inject lateinit var settingsService: SettingsService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(settingsService.appLanguage.languageTag)
        )
        enableEdgeToEdge()
        setContent {
            SillyBilibiliTheme {
                val navController = rememberNavController()
                AppNavHost(navController = navController)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Files may have been removed or replaced in a file manager while the app was backgrounded.
        lifecycleScope.launch { externalMediaSyncService.reconcileExportedFiles() }
    }
}
