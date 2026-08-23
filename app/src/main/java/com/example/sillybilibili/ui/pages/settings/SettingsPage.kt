package com.example.sillybilibili.ui.pages.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.sillybilibili.R
import com.example.sillybilibili.service.SettingsService
import com.example.sillybilibili.ui.components.AppTopBar
import com.example.sillybilibili.ui.theme.CyberGold
import com.example.sillybilibili.ui.theme.CyberVermilion
import com.example.sillybilibili.ui.theme.DarkBackground
import com.example.sillybilibili.ui.theme.NeonCyan
import com.example.sillybilibili.ui.theme.NeonGreen
import com.example.sillybilibili.ui.theme.NeonPurple
import com.example.sillybilibili.ui.theme.DarkCard
import com.example.sillybilibili.ui.theme.DarkDivider
import com.example.sillybilibili.ui.theme.DarkSurfaceVariant
import com.example.sillybilibili.ui.theme.DarkTextPrimary
import com.example.sillybilibili.ui.theme.DarkTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.settings_title), subtitle = stringResource(R.string.settings_subtitle)) },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = DarkCard)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingHeader(Icons.Default.Language, NeonPurple, stringResource(R.string.language))
                    Text(stringResource(R.string.language_description), style = MaterialTheme.typography.bodySmall, color = DarkTextSecondary)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SettingsService.AppLanguage.entries.forEachIndexed { index, language ->
                            SegmentedButton(
                                selected = uiState.appLanguage == language,
                                onClick = {
                                    if (uiState.appLanguage != language) {
                                        viewModel.updateAppLanguage(language)
                                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.languageTag))
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, SettingsService.AppLanguage.entries.size),
                                label = { Text(if (language == SettingsService.AppLanguage.SIMPLIFIED_CHINESE) stringResource(R.string.language_simplified_chinese) else stringResource(R.string.language_english)) }
                            )
                        }
                    }
                }
            }
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = DarkCard)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingHeader(Icons.Default.CloudSync, NeonCyan, stringResource(R.string.online_status_refresh))
                    Text(stringResource(R.string.online_status_refresh_description), style = MaterialTheme.typography.bodySmall, color = DarkTextSecondary)
                    FilledTonalButton(
                        onClick = viewModel::refreshOnlineStatuses,
                        enabled = !uiState.isRefreshingOnlineStatuses
                    ) {
                        if (uiState.isRefreshingOnlineStatuses) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.online_status_refreshing))
                        } else {
                            Text(stringResource(R.string.online_status_refresh_action))
                        }
                    }
                    uiState.onlineStatusRefreshProgress?.takeIf { uiState.isRefreshingOnlineStatuses }?.let { progress ->
                        LinearProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier.fillMaxWidth(),
                            color = NeonCyan,
                            trackColor = DarkSurfaceVariant
                        )
                        Text(
                            stringResource(
                                R.string.online_status_refresh_progress,
                                progress.completedRequestCount,
                                progress.totalRequestCount,
                                progress.processedVideoCount,
                                progress.videoCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkTextSecondary
                        )
                    }
                    uiState.onlineStatusRefreshResult?.let { result ->
                        Text(
                            stringResource(
                                R.string.online_status_refresh_result,
                                result.videoCount,
                                result.requestCount,
                                result.onlineCount,
                                result.unavailableCount,
                                result.unverifiableCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkTextSecondary
                        )
                    }
                }
            }
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = DarkCard)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingHeader(Icons.Default.FolderOpen, CyberGold, stringResource(R.string.mp4_export_location))
                    Text(stringResource(R.string.mp4_export_description), style = MaterialTheme.typography.bodySmall, color = DarkTextSecondary)
                    OutlinedTextField(
                        value = uiState.outputPath,
                        onValueChange = viewModel::updateOutputPath,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberVermilion,
                            unfocusedBorderColor = DarkDivider,
                            cursorColor = CyberVermilion,
                            focusedTextColor = DarkTextPrimary,
                            unfocusedTextColor = DarkTextPrimary,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant
                        )
                    )
                }
            }
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = DarkCard)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SettingHeader(Icons.Default.Headphones, NeonGreen, stringResource(R.string.background_playback))
                        Text(stringResource(R.string.background_playback_description), style = MaterialTheme.typography.bodySmall, color = DarkTextSecondary)
                    }
                    Switch(
                        checked = uiState.backgroundPlaybackEnabled,
                        onCheckedChange = viewModel::updateBackgroundPlayback
                    )
                }
            }
        }
    }
}

/** 设置项标题行：圆角图标底 + 标题。 */
@Composable
private fun SettingHeader(icon: ImageVector, tint: Color, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = tint.copy(alpha = 0.14f)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp).size(20.dp), tint = tint)
        }
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}
