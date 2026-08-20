package com.example.sillybilibili.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.sillybilibili.R
import com.example.sillybilibili.ui.theme.CyberGold
import com.example.sillybilibili.ui.theme.CyberVermilion
import com.example.sillybilibili.ui.theme.DarkBackground
import com.example.sillybilibili.ui.theme.DarkTextSecondary
import com.example.sillybilibili.ui.theme.GlassBorder

/** Shared elevated app bar used across browsing, settings and scan surfaces. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AppTopBar(
    title: String,
    subtitle: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    /** 自定义标题内容（如 logo + 应用名），优先于 title/subtitle。必须放在 actions 之前，
     *  否则 trailing lambda 会绑定到它而不是 actions。 */
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(
        Modifier.fillMaxWidth().background(DarkBackground)
    ) {
        TopAppBar(
            // 紧凑高度：避免状态栏下方再留一大段空白
            modifier = Modifier.height(56.dp),
            title = {
                if (titleContent != null) {
                    titleContent()
                } else {
                Column(verticalArrangement = Arrangement.Center) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    subtitle?.let {
                        Spacer(Modifier.height(1.dp))
                        Text(it, style = MaterialTheme.typography.labelMedium, color = DarkTextSecondary)
                    }
                }
                }
            },
            navigationIcon = {
                onNavigateBack?.let { onBack ->
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = GlassBorder.copy(alpha = 0.10f),
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                actionIconContentColor = DarkTextSecondary
            )
        )
        Box(
            Modifier.fillMaxWidth().height(2.dp).background(
                Brush.horizontalGradient(listOf(GlassBorder, CyberVermilion.copy(alpha = 0.78f), CyberGold.copy(alpha = 0.50f), Color.Transparent))
            )
        )
    }
}
