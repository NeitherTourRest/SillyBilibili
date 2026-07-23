package com.example.sillybilibili.ui.pages.scan

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.sillybilibili.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPage(
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(LocalContext.current as ComponentActivity)
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Videos", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White.copy(alpha = 0.8f)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(scrollState).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Shizuku status
            if (!uiState.isShizukuAvailable) {
                Card(colors = CardDefaults.cardColors(containerColor = NeonRed.copy(alpha = 0.1f)), shape = RoundedCornerShape(0.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, null, tint = NeonRed)
                        Spacer(Modifier.width(8.dp))
                        Text("Shizuku not available — required for Android 11+", color = NeonRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Scan path
            Text("Scan Path", style = MaterialTheme.typography.labelMedium, color = Color(0xFF8080A0))
            OutlinedTextField(
                value = uiState.scanPath, onValueChange = { viewModel.updateScanPath(it) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(0.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberVermilion, unfocusedBorderColor = DarkDivider,
                    cursorColor = CyberVermilion, focusedTextColor = Color(0xFFE8E8F0), unfocusedTextColor = Color(0xFFE8E8F0),
                    focusedContainerColor = DarkSurfaceVariant, unfocusedContainerColor = DarkSurfaceVariant
                )
            )

            // Filters section
            Text("Filters", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = CyberGold)

            // Quality filter
            val qualities = listOf(null to "All") + listOf("360P","480P","720P","1080P","4K").map { it to it }
            var qualityExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = qualityExpanded, onExpandedChange = { qualityExpanded = it }) {
                OutlinedTextField(
                    value = uiState.filterQuality ?: "All", onValueChange = {}, readOnly = true,
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    label = { Text("Quality", color = Color(0xFF606080)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qualityExpanded) },
                    shape = RoundedCornerShape(0.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberVermilion, unfocusedBorderColor = DarkDivider,
                        focusedTextColor = Color(0xFFE8E8F0), unfocusedTextColor = Color(0xFFE8E8F0),
                        focusedContainerColor = DarkSurfaceVariant, unfocusedContainerColor = DarkSurfaceVariant
                    )
                )
                ExposedDropdownMenu(expanded = qualityExpanded, onDismissRequest = { qualityExpanded = false }) {
                    qualities.forEach { (v, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { viewModel.updateFilterQuality(v); qualityExpanded = false })
                    }
                }
            }

            // Duration range
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.filterMinDurationSec, onValueChange = { viewModel.updateFilterMinDuration(it) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    label = { Text("Min (sec)", color = Color(0xFF606080)) },
                    shape = RoundedCornerShape(0.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberVermilion, unfocusedBorderColor = DarkDivider,
                        focusedTextColor = Color(0xFFE8E8F0), unfocusedTextColor = Color(0xFFE8E8F0),
                        focusedContainerColor = DarkSurfaceVariant, unfocusedContainerColor = DarkSurfaceVariant
                    )
                )
                OutlinedTextField(
                    value = uiState.filterMaxDurationSec, onValueChange = { viewModel.updateFilterMaxDuration(it) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    label = { Text("Max (sec)", color = Color(0xFF606080)) },
                    shape = RoundedCornerShape(0.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberVermilion, unfocusedBorderColor = DarkDivider,
                        focusedTextColor = Color(0xFFE8E8F0), unfocusedTextColor = Color(0xFFE8E8F0),
                        focusedContainerColor = DarkSurfaceVariant, unfocusedContainerColor = DarkSurfaceVariant
                    )
                )
            }

            // Quick mode toggle
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Quick Mode (skip file checks)", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFA0A0B8))
                Switch(
                    checked = uiState.filterQuickMode, onCheckedChange = { viewModel.toggleQuickMode() },
                    colors = SwitchDefaults.colors(checkedThumbColor = CyberVermilion, checkedTrackColor = CyberVermilion.copy(alpha = 0.3f))
                )
            }

            // Scan button
            Button(
                onClick = { viewModel.startScan() }, enabled = !uiState.isScanning,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(0.dp), colors = ButtonDefaults.buttonColors(containerColor = CyberVermilion)
            ) {
                Icon(Icons.Default.Storage, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(if (uiState.isScanning) "Scanning..." else "Scan for Videos", fontWeight = FontWeight.Bold, color = Color.White)
            }

            // Progress
            if (uiState.isScanning && uiState.scanProgress != null) {
                Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(0.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(uiState.scanProgress!!.statusMessage, style = MaterialTheme.typography.bodyMedium, color = CyberGold, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(
                            progress = { uiState.scanProgress!!.processedFolders.toFloat() / uiState.scanProgress!!.totalFolders.coerceAtLeast(1).toFloat() },
                            modifier = Modifier.fillMaxWidth(), color = CyberVermilion, trackColor = CyberVermilion.copy(alpha = 0.1f)
                        )
                        Text("Found: ${uiState.foundVideoCount} | Processed: ${uiState.scanProgress!!.processedFolders}/${uiState.scanProgress!!.totalFolders}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8080A0))
                        if (uiState.scanProgress!!.skippedFolders > 0) Text("Skipped: ${uiState.scanProgress!!.skippedFolders} (already scanned)", style = MaterialTheme.typography.bodySmall, color = Color(0xFF606080))
                        if (uiState.scanProgress!!.filteredFolders > 0) Text("Filtered out: ${uiState.scanProgress!!.filteredFolders}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF606080))
                    }
                }
            }

            // Complete
            if (uiState.scanComplete) {
                Card(colors = CardDefaults.cardColors(containerColor = CyberVermilion.copy(alpha = 0.1f)), shape = RoundedCornerShape(0.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(uiState.scanResultMessage, style = MaterialTheme.typography.titleSmall, color = NeonGreen, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.clearScanResult() }, modifier = Modifier.weight(1f)) { Text("Scan Again") }
                            Button(onClick = { onNavigateToHome() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = CyberVermilion), shape = RoundedCornerShape(0.dp)) { Text("Back to Home") }
                        }
                    }
                }
            }
        }
    }
}
