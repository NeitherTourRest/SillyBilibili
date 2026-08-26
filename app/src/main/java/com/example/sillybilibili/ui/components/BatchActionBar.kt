package com.example.sillybilibili.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sillybilibili.ui.theme.CyberVermilion
import com.example.sillybilibili.ui.theme.DarkTextSecondary
import com.example.sillybilibili.ui.theme.DarkTextTertiary

/** 批量操作进度：label = 操作名，done = 已完成数，total = 总数。 */
data class BatchProgress(val label: String, val done: Int, val total: Int) {
    val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    val isFinished: Boolean get() = total > 0 && done >= total
}

/** Persistent, compact feedback after a batch conversion leaves selection mode. */
@Composable
fun BatchConversionStatusBanner(progress: BatchProgress, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = CyberVermilion.copy(alpha = 0.10f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Movie, null, Modifier.size(16.dp), tint = CyberVermilion)
                Spacer(Modifier.width(7.dp))
                Text("正在后台转换 MP4", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("${progress.done}/${progress.total}", style = MaterialTheme.typography.labelSmall, color = DarkTextSecondary)
            }
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = CyberVermilion,
                trackColor = CyberVermilion.copy(alpha = 0.16f)
            )
        }
    }
}

/** 多选模式下的批量操作条：分类、转换、状态刷新与完整性检查，下方附实时进度。 */
@Composable
fun BatchActionBar(
    hasSelection: Boolean,
    onAssignCategory: () -> Unit,
    onConvertToMp4: () -> Unit,
    onRefreshStatus: () -> Unit,
    onCheckIntegrity: () -> Unit,
    progress: BatchProgress? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BatchAction("加入分类", Icons.Default.Category, hasSelection, onAssignCategory, Modifier.weight(1f))
            BatchAction("转换 MP4", Icons.Default.Movie, hasSelection, onConvertToMp4, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BatchAction("刷新状态", Icons.Default.Refresh, hasSelection, onRefreshStatus, Modifier.weight(1f))
            BatchAction("检查文件", Icons.Default.FactCheck, hasSelection, onCheckIntegrity, Modifier.weight(1f))
        }
        progress?.let { p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(
                    progress = { p.fraction },
                    modifier = Modifier.weight(1f).height(6.dp),
                    color = CyberVermilion,
                    trackColor = CyberVermilion.copy(alpha = 0.16f)
                )
                Text(
                    "${p.done}/${p.total}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (p.isFinished) DarkTextSecondary else DarkTextTertiary
                )
            }
            Text(
                if (p.isFinished) "${p.label}已完成" else "${p.label}进行中…",
                style = MaterialTheme.typography.labelSmall,
                color = DarkTextTertiary
            )
        }
    }
}

@Composable
private fun BatchAction(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = if (enabled) CyberVermilion else DarkTextTertiary)
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
