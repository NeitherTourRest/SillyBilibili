package com.example.sillybilibili.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sillybilibili.ui.theme.*

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
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).onFocusChanged { focused = it.isFocused },
        placeholder = { Text(placeholder, color = DarkTextTertiary) },
        leadingIcon = { Icon(Icons.Default.Search, "搜索", tint = if (focused) CyberVermilion else DarkTextSecondary) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, "清除", tint = DarkTextSecondary)
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        supportingText = if (focused && query.isBlank()) ({ Text("支持标题、UP 主和 AV 号", color = DarkTextTertiary) }) else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyberVermilion, unfocusedBorderColor = DarkDivider,
            cursorColor = CyberVermilion, focusedTextColor = DarkTextPrimary,
            unfocusedTextColor = DarkTextPrimary, focusedContainerColor = DarkSurfaceVariant,
            unfocusedContainerColor = DarkSurfaceVariant
        )
    )
}
