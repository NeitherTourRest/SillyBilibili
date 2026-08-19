package com.example.sillybilibili.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sillybilibili.ui.pages.home.*
import com.example.sillybilibili.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    currentFilter: FilterState,
    onDraftFilterChange: (FilterState) -> Unit,
    onApplyFilter: (FilterState) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Title ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("过滤视频", style = MaterialTheme.typography.titleLarge, color = Color(0xFFF0F0F8))
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .width(40.dp).height(4.dp).clip(RoundedCornerShape(4.dp))
                            .background(Brush.linearGradient(listOf(CyberVermilion, CyberGold)))
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "关闭", tint = Color(0xFF606080))
                }
            }

            // ── Filter sections ──

            val qualities = listOf(null to "全部") + listOf("360P", "480P", "720P", "1080P", "4K").map { it to it }
            FilterSection("画质") { FilterChipRow(qualities, currentFilter.quality) { onDraftFilterChange(currentFilter.copy(quality = it)) } }

            val orientations = listOf(null to "全部", Orientation.LANDSCAPE to "横屏", Orientation.PORTRAIT to "竖屏")
            FilterSection("方向") { FilterChipRow(orientations, currentFilter.orientation) { onDraftFilterChange(currentFilter.copy(orientation = it)) } }

            val durations = listOf(null to "全部") + DurationRange.entries.map { it to it.label }
            FilterSection("时长") { FilterChipRow(durations, currentFilter.durationRange) { onDraftFilterChange(currentFilter.copy(durationRange = it)) } }

            val sizes = listOf(null to "全部") + SizeRange.entries.map { it to it.label }
            FilterSection("文件大小") { FilterChipRow(sizes, currentFilter.sizeRange) { onDraftFilterChange(currentFilter.copy(sizeRange = it)) } }

            val times = listOf(null to "全部") + TimeRange.entries.map { it to it.label }
            FilterSection("扫描时间") { FilterChipRow(times, currentFilter.timeRange) { onDraftFilterChange(currentFilter.copy(timeRange = it)) } }

            val covers = listOf(null to "全部", true to "有封面", false to "无封面")
            FilterSection("封面") { FilterChipRow(covers, currentFilter.hasCover) { onDraftFilterChange(currentFilter.copy(hasCover = it)) } }

            // ── Bottom buttons ──
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onDraftFilterChange(FilterState()) },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                ) { Text("重置") }

                Button(
                    onClick = { onApplyFilter(currentFilter); onDismiss() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberVermilion)
                ) {
                    Text("确认", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = Color(0xFFC8CFDD), fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun <T> FilterChipRow(options: List<Pair<T?, String>>, selected: T?, onSelect: (T?) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(if (selected == value) null else value) },
                label = {
                    Text(label, fontWeight = if (selected == value) FontWeight.Bold else FontWeight.Normal)
                },
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyberVermilion.copy(alpha = 0.2f),
                    containerColor = DarkSurfaceVariant,
                    selectedLabelColor = CyberVermilion,
                    labelColor = Color(0xFF8080A0)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = DarkDivider,
                    selectedBorderColor = CyberVermilion,
                    enabled = true,
                    selected = selected == value
                )
            )
        }
    }
}
