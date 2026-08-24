package com.example.sillybilibili.ui.pages.guide

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sillybilibili.ui.components.AppTopBar
import com.example.sillybilibili.ui.theme.*
import com.example.sillybilibili.util.PermissionHelper
import rikka.shizuku.Shizuku

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidePage(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var hasPermission by remember { mutableStateOf(PermissionHelper.hasStoragePermission(context)) }
    var isShizukuAvailable by remember { mutableStateOf(false) }

    fun refreshAccessState() {
        hasPermission = PermissionHelper.hasStoragePermission(context)
        isShizukuAvailable = try { Shizuku.pingBinder() } catch (_: Exception) { false }
    }

    LaunchedEffect(Unit) { refreshAccessState() }

    Scaffold(
        topBar = {
            AppTopBar(title = "使用指南", subtitle = "权限、Shizuku 与扫描设置", onNavigateBack = onNavigateBack)
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(scrollState).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PermissionGuideCard(hasPermission = hasPermission, onGrant = { grantPermission(context); refreshAccessState() }, onSettings = { openAppSettings(context) })
            ShizukuGuideCard(
                isShizukuAvailable = isShizukuAvailable,
                onRefresh = ::refreshAccessState,
                onOpenShizuku = { openShizuku(context) }
            )
            UsageTipsCard()
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun PermissionGuideCard(hasPermission: Boolean, onGrant: () -> Unit, onSettings: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GuideCardTitle(Icons.Default.Lock, "文件权限", CyberVermilion)
            Text("Silly Bilibili 需要访问 B 站缓存目录来扫描与管理视频。若扫描页已经显示“目录可直接读取”，不需要配置 Shizuku。", style = MaterialTheme.typography.bodyMedium, color = DarkTextSecondary)
            Text("Android 11+ 的“管理所有文件”权限不能保证访问 Android/data；目录仍被隔离时，请继续按下方 Shizuku 指南操作。", style = MaterialTheme.typography.bodySmall, color = DarkTextTertiary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onGrant, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberGold)) {
                    Text(if (hasPermission) "已授权 ✓" else "授予权限", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkTextSecondary)) {
                    Text("系统设置")
                }
            }
        }
    }
}

@Composable
private fun ShizukuGuideCard(isShizukuAvailable: Boolean, onRefresh: () -> Unit, onOpenShizuku: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GuideCardTitle(Icons.Default.Terminal, "Shizuku 配置指南", NeonCyan)
            Text("Shizuku 让本应用通过系统授权读取通常被隔离的 B 站 Android/data 缓存。它不是 root；本应用只会在你授权后使用它。", style = MaterialTheme.typography.bodyMedium, color = DarkTextSecondary)
            ShizukuConnectionStatus(isShizukuAvailable)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRefresh, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = CyberVermilion), shape = MaterialTheme.shapes.medium) {
                    Text(if (isShizukuAvailable) "Shizuku 已连接 ✓" else "检查 Shizuku", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onOpenShizuku, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)) {
                    Text("打开 Shizuku")
                }
            }
            HorizontalDivider(color = DarkDivider.copy(alpha = 0.7f))
            Text("按你的手机展开对应说明", style = MaterialTheme.typography.labelMedium, color = CyberGold, fontWeight = FontWeight.SemiBold)
            ShizukuExpandableGuide(
                title = "先判断是否需要 Shizuku",
                summary = "目录未隔离时无需配置",
                icon = Icons.Default.Info,
                initiallyExpanded = true
            ) {
                GuideStep("先从 Shizuku 官网或 GitHub Releases 安装 Shizuku App。")
                GuideStep("进入底部“扫描”页，查看目录访问提示。")
                GuideStep("显示“目录可直接读取”时，直接扫描即可。")
                GuideStep("显示“目录已隔离，需要配置 Shizuku”时，再继续以下步骤。")
                GuideNote("应用内授权只针对本应用；请在 Shizuku 的已授权应用列表中确认 Silly Bilibili 已允许。")
            }
            ShizukuExpandableGuide(
                title = "通用：Android 11 及以上（推荐无线调试）",
                summary = "配对一次；每次重启后重新启动",
                icon = Icons.Default.Wifi,
                initiallyExpanded = true
            ) {
                GuideStep("安装并打开 Shizuku，选择“通过无线调试启动”。")
                GuideStep("系统设置 → 关于手机 → 连续点击版本号/Build number 约 7 次，开启开发者选项。不同品牌名称可能是“版本信息”“OS 版本”。")
                GuideStep("系统设置 → 系统与更新/更多设置 → 开发者选项，打开“USB 调试”和“无线调试”。")
                GuideStep("回到 Shizuku 点“开始配对”；在系统“无线调试”中点“使用配对码配对设备”。")
                GuideStep("将系统给出的配对码填入 Shizuku 通知或配对界面，等待显示服务已启动。")
                GuideStep("回到本页点“检查 Shizuku”，再去扫描页确认已连接并允许 Silly Bilibili 的授权弹窗。")
                GuideNote("无线调试首次配对通常只需要一次；手机重启后需要在 Shizuku 中重新启动服务。若启动失败，可关闭再打开“无线调试”后重试。")
            }
            ShizukuExpandableGuide(
                title = "Android 10 及以下：使用电脑 ADB",
                summary = "无 root 时需要电脑，每次重启后重新执行",
                icon = Icons.Default.Computer
            ) {
                GuideStep("在电脑安装 Google Android SDK Platform Tools，并在手机开发者选项中打开“USB 调试”。")
                GuideStep("用数据线连接手机，确认手机弹出的“允许 USB 调试”对话框；电脑终端执行 adb devices，设备应显示为 device。")
                GuideStep("在终端执行下方命令启动 Shizuku v11.2.0+。Windows PowerShell 若提示找不到 adb，可使用 .\\adb。")
                Surface(shape = RoundedCornerShape(10.dp), color = Color.Black.copy(alpha = 0.25f)) {
                    Text("adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh", modifier = Modifier.padding(10.dp), color = NeonCyan, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                }
                GuideNote("已 root 的手机可直接在 Shizuku 中选择 root 启动，无需电脑。")
            }
            ShizukuExpandableGuide(
                title = "小米 / Redmi / POCO（MIUI、HyperOS）",
                summary = "额外开启安全 USB 调试，避免系统清理",
                icon = Icons.Default.PhoneAndroid
            ) {
                GuideStep("开发者选项中除“USB 调试”外，如有“USB 调试（安全设置）”，也请开启；它们是两个独立选项。")
                GuideStep("若 Shizuku 输入配对码立即失败：设置 → 通知与控制中心/通知显示设置，将通知样式切换为“原生/Android”。菜单会随 MIUI/HyperOS 版本变化。")
                GuideStep("在应用电池管理中允许 Shizuku 后台运行或设为“不限制”；不要用手机管家的扫描/优化功能关闭开发者选项。")
                GuideNote("若服务总是自动停止，可在开发者选项中启用“停用 ADB 授权超时功能”（若存在）。")
            }
            ShizukuExpandableGuide(
                title = "OPPO / OnePlus / realme（ColorOS 系）",
                summary = "关闭权限监控并允许后台运行",
                icon = Icons.Default.Settings
            ) {
                GuideStep("开发者选项中如有“权限监控/Permission monitoring”，请关闭；这是 ColorOS 对 ADB 权限的额外限制。")
                GuideStep("电池或应用管理中允许 Shizuku 自启动、后台活动或“不受限制”，避免锁屏后配对服务被终止。")
                GuideStep("若菜单名不同，优先在系统设置搜索“权限监控”“无线调试”或“开发者选项”。")
            }
            ShizukuExpandableGuide(
                title = "华为 / 荣耀（EMUI、HarmonyOS）",
                summary = "允许“仅充电”模式下的 ADB",
                icon = Icons.Default.BatteryChargingFull
            ) {
                GuideStep("开发者选项中如有“仅充电模式下允许 ADB 调试”，请开启。")
                GuideStep("在电池/启动管理中允许 Shizuku 后台运行，避免自动管理限制它的本地网络与配对服务。")
                GuideStep("系统找不到无线调试时，先确认 Android 版本为 11 或更高；较低版本请使用上面的电脑 ADB 方案。")
            }
            ShizukuExpandableGuide(
                title = "vivo / iQOO / 魅族 / 其他系统",
                summary = "重点是后台运行与系统额外调试限制",
                icon = Icons.Default.Tune
            ) {
                GuideStep("vivo / iQOO：在电池设置或 i 管家中允许 Shizuku 后台运行、自启动和网络访问，避免省电策略结束配对。")
                GuideStep("魅族 Flyme：开发者选项中如有“Flyme 支付保护”，请关闭；它可能限制 ADB 权限。")
                GuideStep("其他系统：保留开发者选项、USB 调试和无线调试开启；将 Shizuku 电池策略改为“不限制/允许后台”。")
                GuideNote("品牌菜单经常因机型、地区与系统版本改名。找不到时直接在系统设置搜索“开发者选项”“无线调试”“后台运行”。")
            }
            ShizukuExpandableGuide(
                title = "连不上、配对失败或服务总停止",
                summary = "常见故障排查",
                icon = Icons.Default.Build
            ) {
                GuideStep("确认手机和 Shizuku 都未关闭“无线调试”“USB 调试”或开发者选项。")
                GuideStep("Shizuku 一直显示搜索配对服务：允许它后台运行，并关闭后重新打开无线调试。")
                GuideStep("服务随机停止：开发者选项中将默认 USB 配置设为“不进行数据传输/仅充电”（若存在），并允许 Shizuku 后台运行。")
                GuideStep("本应用仍显示未连接：在 Shizuku 的授权应用中撤销本应用后重新授权，再回此页点“检查 Shizuku”。")
                GuideNote("重启手机、关闭无线调试或系统结束 Shizuku 后，服务会失效；重新在 Shizuku 启动即可，本应用无需重装。")
            }
        }
    }
}

@Composable
private fun GuideCardTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun ShizukuConnectionStatus(isShizukuAvailable: Boolean) {
    Surface(shape = RoundedCornerShape(10.dp), color = if (isShizukuAvailable) NeonGreen.copy(alpha = 0.12f) else DarkSurfaceVariant) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isShizukuAvailable) Icons.Default.CheckCircle else Icons.Default.Info, null, tint = if (isShizukuAvailable) NeonGreen else DarkTextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text(shizukuGuideStatusText(isShizukuAvailable), color = if (isShizukuAvailable) NeonGreen else DarkTextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ShizukuExpandableGuide(
    title: String,
    summary: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(14.dp),
        color = DarkSurfaceVariant.copy(alpha = 0.58f),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkDivider.copy(alpha = 0.72f))
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = DarkTextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(summary, color = DarkTextTertiary, style = MaterialTheme.typography.labelSmall)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "收起" else "展开", tint = DarkTextSecondary)
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
            }
        }
    }
}

@Composable
private fun GuideStep(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Text("•", color = CyberGold, fontWeight = FontWeight.Bold)
        Text(text, modifier = Modifier.weight(1f), color = DarkTextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun GuideNote(text: String) {
    Surface(shape = RoundedCornerShape(9.dp), color = CyberGold.copy(alpha = 0.09f)) {
        Text("提示：$text", modifier = Modifier.padding(9.dp), color = CyberGold, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun UsageTipsCard() {
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GuideCardTitle(Icons.Default.Info, "扫描与使用提示", CyberGold)
            Text("• 完成目录访问配置后，去底部“扫描”页开始扫描缓存。", style = MaterialTheme.typography.bodyMedium, color = DarkTextSecondary)
            Text("• 长按视频卡片进入多选，可批量加入分类、转换 MP4、刷新状态或检查文件。", style = MaterialTheme.typography.bodySmall, color = DarkTextSecondary)
            Text("• 转换 MP4 只重封装 .m4s 音视频轨，不重新编码；导出后可在“已导出”页管理。", style = MaterialTheme.typography.bodySmall, color = DarkTextSecondary)
        }
    }
}

internal fun shizukuGuideStatusText(isAvailable: Boolean): String = if (isAvailable) {
    "已连接 · 扫描页可使用 Shizuku 访问缓存"
} else {
    "未连接 · 可在 Shizuku 中启动服务后重新检查"
}

private fun grantPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply { data = Uri.parse("package:${context.packageName}") })
        return
    }
    val permissions = PermissionHelper.getRequiredPermissions()
    if (permissions.isNotEmpty()) (context as? ComponentActivity)?.requestPermissions(permissions, 1001)
}

private fun openAppSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${context.packageName}") })
}

private fun openShizuku(context: Context) {
    try {
        context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let(context::startActivity)
    } catch (_: Exception) {
        // The guide stays usable even when Shizuku has not been installed yet.
    }
}
