package com.example.sillybilibili.ui.pages.guide

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sillybilibili.ui.theme.*
import com.example.sillybilibili.ui.components.AppTopBar
import com.example.sillybilibili.util.PermissionHelper
import rikka.shizuku.Shizuku

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidePage(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val hasPermission = remember { PermissionHelper.hasStoragePermission(context) }
    val isShizukuAvailable = remember {
        try { Shizuku.pingBinder() } catch (_: Exception) { false }
    }
    var shizukuChecked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try { shizukuChecked = Shizuku.pingBinder() } catch (_: Exception) {}
    }

    Scaffold(
        topBar = {
            AppTopBar(title = "Guide", subtitle = "Permissions and Shizuku setup", onNavigateBack = onNavigateBack)
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(scrollState).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // === Step 1: Permissions ===
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, tint = CyberVermilion, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("文件权限", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CyberVermilion)
                    }
                    Text("Silly Bilibili 需要访问 B 站缓存目录（Android/data/）来扫描和管理视频。", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFA0A0B8))
                    Text("Android 11+ 需要授予\"管理所有文件\"权限。", style = MaterialTheme.typography.bodySmall, color = Color(0xFF606080))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { grantPermission(context) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberGold)
                        ) {
                            Text(if (hasPermission) "已授权 ✓" else "授予权限", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { openAppSettings(context) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF606080))
                        ) { Text("系统设置") }
                    }
                }
            }

            // === Step 2: Shizuku ===
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, null, tint = NeonCyan, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Shizuku 设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = NeonCyan)
                    }
                    Text("Shizuku 是一个后台服务，让 App 能够访问通常受限的目录。\n\nAndroid 11+ 必须安装 Shizuku 才能读取 B 站缓存。", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFA0A0B8))

                    Text("安装步骤：", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = CyberGold)
                    Text("1. 下载 Shizuku App\n   github.com/RikkaApps/Shizuku/releases", style = MaterialTheme.typography.bodySmall, color = Color(0xFFA0A0B8))
                    Text("2. 打开 Shizuku → 无线调试 → 配对", style = MaterialTheme.typography.bodySmall, color = Color(0xFFA0A0B8))
                    Text("3. 回到本 App，点击下方按钮检查连接", style = MaterialTheme.typography.bodySmall, color = Color(0xFFA0A0B8))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { try { shizukuChecked = Shizuku.pingBinder() } catch (_: Exception) {} },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberVermilion),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(if (isShizukuAvailable) "Shizuku 已连接 ✓" else "检查 Shizuku", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                    if (intent != null) context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                        ) { Text("打开 Shizuku") }
                    }
                }
            }

            // === Step 3: Usage tips ===
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = CyberGold, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("使用提示", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CyberGold)
                    }
                    Text("• 权限和 Shizuku 都设置好后，去\"设置\" -> \"扫描视频\"", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFA0A0B8))
                    Text("• 视频卡片长按可分配分类、转换 MP4、删除", style = MaterialTheme.typography.bodySmall, color = Color(0xFFA0A0B8))
                    Text("• 转换 MP4 是把 .m4s 文件重新封装，不重新编码，速度很快", style = MaterialTheme.typography.bodySmall, color = Color(0xFFA0A0B8))
                    Text("• 支持搜索（标题/UP主/av号）和过滤（画质/时长/大小等）", style = MaterialTheme.typography.bodySmall, color = Color(0xFFA0A0B8))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun grantPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent); return
        }
    }
    val permissions = PermissionHelper.getRequiredPermissions()
    if (permissions.isNotEmpty()) { (context as? ComponentActivity)?.requestPermissions(permissions, 1001) }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}
