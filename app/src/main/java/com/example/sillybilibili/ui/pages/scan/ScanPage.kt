package com.example.sillybilibili.ui.pages.scan

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.sillybilibili.service.VideoScanService
import com.example.sillybilibili.R
import com.example.sillybilibili.ui.components.AppTopBar
import com.example.sillybilibili.ui.theme.CyberGold
import com.example.sillybilibili.ui.theme.CyberVermilion
import com.example.sillybilibili.ui.theme.DarkBackground
import com.example.sillybilibili.ui.theme.DarkCard
import com.example.sillybilibili.ui.theme.DarkDivider
import com.example.sillybilibili.ui.theme.DarkSurfaceVariant
import com.example.sillybilibili.ui.theme.DarkTextSecondary
import com.example.sillybilibili.ui.theme.DarkTextTertiary
import com.example.sillybilibili.ui.theme.NeonGreen
import com.example.sillybilibili.ui.theme.NeonRed

@Composable
fun ScanPage(
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val safPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        viewModel.setSafTreeUri(uri)
    }

    androidx.compose.material3.Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.scan_title), subtitle = stringResource(R.string.scan_subtitle), onNavigateBack = onNavigateBack) },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(scrollState).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DirectoryOverview(uiState)
            AccessAndPathCard(uiState, viewModel, onPickSaf = { safPicker.launch(null) })
            ScanFilters(uiState, viewModel)
            ScanAction(uiState, viewModel)
            ScanProgressCard(uiState)
            ScanResultCard(uiState, onRetry = viewModel::clearScanResult, onNavigateToHome = onNavigateToHome)
        }
    }
}

@Composable
private fun DirectoryOverview(state: ScanUiState) {
    val snapshot = state.directorySnapshot
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(CyberVermilion.copy(alpha = .6f), CyberGold.copy(alpha = .28f))))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = CyberVermilion.copy(alpha = .16f)) {
                    Icon(Icons.Default.Storage, null, tint = CyberVermilion, modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("缓存目录状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(snapshot.statusMessage, style = MaterialTheme.typography.bodySmall, color = DarkTextSecondary)
                }
                AccessBadge(snapshot.access)
            }
            Text(state.scanPath, maxLines = 1, overflow = TextOverflow.Ellipsis, color = DarkTextTertiary, style = MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("目录缓存包", snapshot.totalCacheFolders, Icons.Default.FolderOpen, CyberGold)
                StatItem("已扫描视频", snapshot.scannedVideoCount, Icons.Default.VideoLibrary, CyberVermilion)
                StatItem("已覆盖缓存包", snapshot.scannedCacheFolderCount, Icons.Default.CheckCircle, NeonGreen)
            }
            if (state.isScanning) {
                Text("扫描中：统计将在保存后自动刷新", style = MaterialTheme.typography.labelSmall, color = CyberGold)
            }
        }
    }
}

@Composable
private fun AccessBadge(access: VideoScanService.ScanAccess) {
    val (label, color) = when (access) {
        VideoScanService.ScanAccess.DIRECT -> "直接访问" to NeonGreen
        VideoScanService.ScanAccess.SHIZUKU -> "Shizuku" to CyberGold
        VideoScanService.ScanAccess.UNAVAILABLE -> "待授权" to NeonRed
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = .14f)) {
        Text(label, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatItem(label: String, value: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(17.dp))
        Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = DarkTextTertiary)
    }
}

@Composable
private fun AccessAndPathCard(state: ScanUiState, viewModel: ScanViewModel, onPickSaf: () -> Unit) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = DarkCard)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("访问方式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    val hint = when {
                        state.useSaf -> "使用系统授权目录"
                        state.isDirectAccessAvailable -> "目录可直接读取，不需要 Shizuku"
                        state.isShizukuAvailable -> "目录已隔离，使用 Shizuku"
                        else -> "目录已隔离，需要配置 Shizuku"
                    }
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = DarkTextSecondary)
                }
                if (!state.isDirectAccessAvailable && state.safCanAccessAndroidData) {
                    Switch(
                        checked = state.useSaf,
                        onCheckedChange = { viewModel.toggleMode() },
                        enabled = state.isShizukuAvailable || state.safTreeUri != null,
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberGold, checkedTrackColor = CyberGold.copy(alpha = .4f))
                    )
                }
            }
            if (state.useSaf && state.safCanAccessAndroidData) {
                OutlinedButton(onClick = onPickSaf, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.safTreeUri == null) "选择 B 站下载目录" else "更换已授权目录")
                }
            } else {
                OutlinedTextField(
                    value = state.scanPath,
                    onValueChange = viewModel::updateScanPath,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("扫描目录") },
                    colors = scanFieldColors()
                )
            }
            if (!state.isDirectAccessAvailable && !state.isShizukuAvailable && !state.useSaf) {
                Surface(shape = RoundedCornerShape(12.dp), color = NeonRed.copy(alpha = .10f)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = NeonRed, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("当前目录不可读取。连接并授权 Shizuku 后可扫描。", color = NeonRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanFilters(state: ScanUiState, viewModel: ScanViewModel) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = DarkCard)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = CyberGold.copy(alpha = .12f)) {
                    Icon(Icons.Default.FilterList, null, tint = CyberGold, modifier = Modifier.padding(8.dp).size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("扫描筛选", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (state.activeFilterCount > 0) {
                    TextButton(onClick = viewModel::clearFilters) { Text("清除 ${state.activeFilterCount} 项") }
                }
            }
            FilterRow("画质", listOf(null to "全部", "360P" to "360P", "480P" to "480P", "720P" to "720P", "1080P" to "1080P", "4K" to "4K"), state.filterQuality) { viewModel.updateFilterQuality(it) }
            OrientationFilter(state.filterOrientation, viewModel::updateFilterOrientation)
            DurationFilter(state.filterDurationPreset, viewModel::updateDurationPreset)
            if (state.filterDurationPreset == ScanDurationPreset.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(state.filterMinDurationSec, viewModel::updateFilterMinDuration, Modifier.weight(1f), singleLine = true, label = { Text("最短秒数") }, colors = scanFieldColors())
                    OutlinedTextField(state.filterMaxDurationSec, viewModel::updateFilterMaxDuration, Modifier.weight(1f), singleLine = true, label = { Text("最长秒数") }, colors = scanFieldColors())
                }
            }
            PresetFilter("大小", ScanSizePreset.values().toList(), state.filterSizePreset, viewModel::updateSizePreset)
            OutlinedTextField(
                value = state.filterSpecificAvIds,
                onValueChange = viewModel::updateFilterSpecificAvIds,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("指定 AV 号（可选，逗号分隔）") },
                supportingText = { Text("只扫描输入的缓存包；留空则扫描全部") },
                colors = scanFieldColors()
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("快速扫描", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text("只读取元数据，跳过文件完整性检查", style = MaterialTheme.typography.bodySmall, color = DarkTextSecondary)
                }
                Switch(state.filterQuickMode, onCheckedChange = { viewModel.toggleQuickMode() }, colors = SwitchDefaults.colors(checkedThumbColor = CyberVermilion, checkedTrackColor = CyberVermilion.copy(alpha = .4f)))
            }
        }
    }
}

@Composable
private fun FilterRow(label: String, values: List<Pair<String?, String>>, selected: String?, onSelect: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = DarkTextSecondary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { (value, text) ->
                FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(text) }, colors = filterChipColors())
            }
        }
    }
}

@Composable
private fun OrientationFilter(selected: VideoScanService.ScanOrientation?, onSelect: (VideoScanService.ScanOrientation?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("画面方向", style = MaterialTheme.typography.labelLarge, color = DarkTextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("全部") }, colors = filterChipColors())
            FilterChip(selected = selected == VideoScanService.ScanOrientation.LANDSCAPE, onClick = { onSelect(VideoScanService.ScanOrientation.LANDSCAPE) }, label = { Text("横屏") }, leadingIcon = { Icon(Icons.Default.AspectRatio, null, Modifier.size(15.dp)) }, colors = filterChipColors())
            FilterChip(selected = selected == VideoScanService.ScanOrientation.PORTRAIT, onClick = { onSelect(VideoScanService.ScanOrientation.PORTRAIT) }, label = { Text("竖屏") }, leadingIcon = { Icon(Icons.Default.AspectRatio, null, Modifier.size(15.dp)) }, colors = filterChipColors())
        }
    }
}

@Composable
private fun DurationFilter(selected: ScanDurationPreset, onSelect: (ScanDurationPreset) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("时长", style = MaterialTheme.typography.labelLarge, color = DarkTextSecondary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScanDurationPreset.values().forEach { preset ->
                FilterChip(selected = selected == preset, onClick = { onSelect(preset) }, label = { Text(preset.label) }, colors = filterChipColors())
            }
        }
    }
}

@Composable
private fun PresetFilter(label: String, values: List<ScanSizePreset>, selected: ScanSizePreset, onSelect: (ScanSizePreset) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = DarkTextSecondary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { preset ->
                FilterChip(selected = selected == preset, onClick = { onSelect(preset) }, label = { Text(preset.label) }, colors = filterChipColors())
            }
        }
    }
}

@Composable
private fun ScanAction(state: ScanUiState, viewModel: ScanViewModel) {
    Button(
        onClick = viewModel::startScan,
        enabled = !state.isScanning,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = CyberVermilion)
    ) {
        if (state.isScanning) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("正在扫描并更新视频库…", fontWeight = FontWeight.Bold)
        } else {
            Icon(Icons.Default.Storage, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (state.activeFilterCount > 0) "按 ${state.activeFilterCount} 个条件扫描" else "开始扫描缓存", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ScanProgressCard(state: ScanUiState) {
    val progress = state.scanProgress ?: return
    if (!state.isScanning) return
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = DarkCard)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Refresh, null, tint = CyberGold, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(progress.statusMessage, color = CyberGold, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${(progress.processedFolders * 100 / progress.totalFolders.coerceAtLeast(1))}%", color = CyberGold, style = MaterialTheme.typography.labelLarge)
            }
            LinearProgressIndicator(
                progress = { progress.processedFolders.toFloat() / progress.totalFolders.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().height(7.dp),
                color = CyberVermilion,
                trackColor = DarkSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ProgressDetail("目标", progress.totalFolders)
                ProgressDetail("已处理", progress.processedFolders)
                ProgressDetail("新发现", progress.foundVideoCount)
                ProgressDetail("跳过", progress.skippedFolders)
            }
            if (progress.filteredFolders > 0) Text("已按筛选条件排除 ${progress.filteredFolders} 个缓存包", color = DarkTextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ProgressDetail(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Text(label, color = DarkTextTertiary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ScanResultCard(state: ScanUiState, onRetry: () -> Unit, onNavigateToHome: () -> Unit) {
    val error = !state.isScanning && !state.scanComplete && state.scanResultMessage.isNotBlank()
    if (!state.scanComplete && !error) return
    val success = state.scanComplete
    val color = if (success) NeonGreen else NeonRed
    val message = state.scanResultMessage
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = .10f))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (success) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = color)
                Spacer(Modifier.width(8.dp))
                Text(message, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            if (success) Text("本次新增 ${state.foundVideoCount} 个视频，目录统计已更新。", style = MaterialTheme.typography.bodySmall, color = DarkTextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("再次扫描") }
                if (success) Button(onClick = onNavigateToHome, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = CyberVermilion)) { Text("查看视频库") }
            }
        }
    }
}

@Composable
private fun filterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = CyberVermilion.copy(alpha = .18f),
    selectedLabelColor = CyberVermilion,
    selectedLeadingIconColor = CyberVermilion,
    containerColor = DarkSurfaceVariant,
    labelColor = DarkTextSecondary
)

@Composable
private fun scanFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CyberVermilion,
    unfocusedBorderColor = DarkDivider,
    cursorColor = CyberVermilion,
    focusedTextColor = Color(0xFFE8E8F0),
    unfocusedTextColor = Color(0xFFE8E8F0),
    focusedContainerColor = DarkSurfaceVariant,
    unfocusedContainerColor = DarkSurfaceVariant
)
