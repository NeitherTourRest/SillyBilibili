package com.example.sillybilibili.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.sillybilibili.R
import com.example.sillybilibili.ui.theme.DarkCard
import com.example.sillybilibili.ui.theme.DarkDivider
import com.example.sillybilibili.ui.theme.DarkTextSecondary

/**
 * A single-row application bar. Keeping title and actions in one row prevents
 * the home screen from splitting its controls into a detached second toolbar.
 */
@Composable
fun AppTopBar(
    title: String,
    subtitle: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    /** 自定义标题内容（如 logo + 应用名），优先于 title/subtitle。必须放在 actions 之前，
     *  否则 trailing lambda 会绑定到它而不是 actions。 */
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(color = DarkCard) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                onNavigateBack?.let { onBack ->
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(start = if (onNavigateBack == null) 4.dp else 0.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (titleContent != null) {
                        titleContent()
                    } else {
                        Column(verticalArrangement = Arrangement.Center) {
                            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            subtitle?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = DarkTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 12.dp)) {
                androidx.compose.foundation.layout.Spacer(
                    Modifier.fillMaxWidth().height(1.dp).background(DarkDivider.copy(alpha = 0.72f))
                )
            }
        }
    }
}
