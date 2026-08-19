package com.example.sillybilibili.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.sillybilibili.ui.theme.*

/**
 * 胶囊形搜索栏：无边框填充风格，聚焦时描边高亮为主色。
 * 保持单行高度（48dp），不再展开 supportingText，列表页头部更紧凑。
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search...",
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).onFocusChanged { focused = it.isFocused },
        placeholder = { Text(placeholder, color = DarkTextTertiary, maxLines = 1) },
        leadingIcon = {
            Icon(
                Icons.Default.Search, "搜索",
                tint = if (focused) CyberVermilion else DarkTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, "清除", tint = DarkTextSecondary)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        textStyle = LocalTextStyle.current.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyberVermilion.copy(alpha = 0.7f),
            unfocusedBorderColor = Color.Transparent,
            cursorColor = CyberVermilion,
            focusedTextColor = DarkTextPrimary,
            unfocusedTextColor = DarkTextPrimary,
            focusedContainerColor = DarkSurfaceVariant,
            unfocusedContainerColor = DarkSurfaceVariant
        )
    )
}