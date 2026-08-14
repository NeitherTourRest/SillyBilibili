# Silly Bilibili

面向本地 B 站缓存的 Android 管理器：扫描、播放、整理并导出缓存视频。界面采用 Jetpack Compose 构建，支持简体中文与英文。

> 本项目只处理设备中已存在的本地缓存；不提供下载、绕过 DRM 或账号相关能力。

## 功能一览

| 功能 | 说明 |
|---|---|
| 缓存扫描 | 解析 `entry.json`、分辨率、时长、画质、视频/音频轨与封面。扫描中断会保留进度和提示。 |
| 多种访问方式 | 未隔离目录直接读取；隔离目录通过 Shizuku；Android 10 及以下可使用 SAF 系统目录选择器。 |
| 封面回退 | 优先使用缓存中的 cover 文件；没有时从视频抽帧生成预览。 |
| 管理与搜索 | 搜索、分类、多维过滤、无限滚动加载、右侧快速滑动条。 |
| 直接播放 | Media3/ExoPlayer 直接播放导出的 MP4 或缓存的 `video.m4s + audio.m4s`，无需先转换。 |
| 播放队列 | 从当前筛选结果选集、上一/下一条、倍速、全屏与定时停止播放。 |
| 全屏切换 | 全屏上下滑动可跟手切换相邻视频；未过阈值回弹，切换时自动继续播放。 |
| 后台播放 | 设置中可开关。启用后退出播放页仍继续播放，底部迷你播放器可返回或关闭。 |
| MP4 导出 | 使用 `MediaExtractor` + `MediaMuxer` 重封装，不重新编码；显示后台转换进度。 |
| 已导出同步 | 检测文件管理器中对已导出 MP4 的删除或变更，并更新应用列表。 |
| 线上状态 | 可显示“在线 / 已下架或不可访问 / 暂无法核验 / 待核验”，设置中可手动刷新全部状态。 |

## 使用前：选择正确的缓存访问方式

默认缓存目录通常位于：

```text
/storage/emulated/0/Android/data/tv.danmaku.bili/download/
```

| 设备情况 | 应使用的方式 |
|---|---|
| 目录可被普通文件 API 读取 | 直接扫描，不需要 Shizuku。 |
| Android 11+，`Android/data` 被系统隔离 | 配置并授权 Shizuku。 |
| Android 10 及以下，且系统文件选择器允许进入目录 | 可在扫描页选择 SAF 目录，无需 Shizuku。 |

Android 11 及以上的系统文件选择器通常不能授予 `Android/data` 访问权限；遇到这种情况请使用 Shizuku。扫描页会显示当前检测到的可用通道。

## Shizuku 接入

Shizuku 让应用通过 Android shell 权限读取系统隔离的缓存目录；它不是 Root，也不需要解锁设备。

1. 从 [Shizuku 官网](https://shizuku.rikka.app/download/) 或其 [GitHub Releases](https://github.com/RikkaApps/Shizuku/releases) 安装 Shizuku。
2. 在 Shizuku 中启动服务：Android 11+ 通常可选择“无线调试”，按系统提示完成配对与启动；也可按 Shizuku 自身说明使用电脑启动。
3. 打开 Silly Bilibili，接受 Shizuku 授权弹窗。
4. 进入“扫描”，确认状态显示 Shizuku 已连接，再选择或确认 B 站缓存目录并开始扫描。

常见排查：

- 重启手机后 Shizuku 服务可能需要重新启动。
- 如果授权过期，在 Shizuku 中撤销后重新打开本应用授权。
- 出现“目录不可读取”时，先确认 B 站确实已缓存内容，再检查 Shizuku 状态和扫描页提示。
- 应用会优先使用直接读取；只有目录被隔离时才回退到 Shizuku。

## 日常使用

1. 在 B 站中完成离线缓存。
2. 打开本应用的“扫描”页，确认访问方式后执行扫描；可按视频方向、时长、大小等条件限定扫描范围。
3. 在首页搜索、筛选或长按视频进行分类管理。
4. 点按视频卡片直接进入播放器；播放页可以选集、调整倍速、设置停止定时器，或将原始缓存转换为 MP4。
5. 转换完成后，在“已导出”页播放或管理 MP4；该页会同步外部删除与变更。

### 播放器手势与布局

- 点按视频画面：显示或隐藏控制栏。
- 非全屏：横屏、方形与竖屏视频按实际解码尺寸适配；竖屏会采用更高的观看区并保留必要黑边。
- 全屏：点击全屏按钮进入；上下拖动可跟手预览并切换播放队列中的上一/下一条。拖动不足约 20% 屏高会回弹。
- 后台播放：在“设置”中开启。离开播放页后，通过底部迷你播放器返回，或使用其关闭按钮结束播放。

### 线上状态含义

| 状态 | 含义 |
|---|---|
| 在线 | 已根据缓存中的 AV/BV 关联信息成功访问线上视频。 |
| 已下架/不可访问 | 线上接口明确返回不可访问；也可能是权限、地区或仅自己可见造成。 |
| 暂无法核验 | 网络、接口响应或缓存元数据不足导致本次无法得出结论，不等同于视频下架。 |
| 待核验 | 尚未请求线上状态。 |

## 构建

要求：JDK 17、Android SDK（compile SDK 34）。

```powershell
# 复制本地签名配置模板后，只在本机填写实际值
Copy-Item keystore.properties.example keystore.properties

# 运行单元测试
.\gradlew.bat testDebugUnitTest --no-daemon

# 构建签名 APK
.\gradlew.bat assembleRelease --no-daemon
```

输出 APK：`app/build/outputs/apk/release/app-release.apk`

`keystore.properties` 与 keystore 文件均被 Git 忽略，绝不能提交到仓库。示例配置：

```properties
storeFile=key
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

## 缓存与导出原理

典型缓存结构：

```text
download/
└── {avid}/
    └── c_{cid}/
        ├── entry.json       # 标题、UP 主、分辨率、时长等元数据
        ├── {quality}/
        │   ├── video.m4s
        │   ├── audio.m4s
        │   └── index.json
        ├── cover.jpg
        └── danmaku.xml / danmaku.pb
```

导出 MP4 使用 Android 原生 `MediaExtractor` 读取音视频轨，再交由 `MediaMuxer` 按时间戳写入 MP4。该过程是重封装（remux），不重新编码，因此不会额外损失画质或音质。

## 架构

```text
ui (Compose) → ViewModel (StateFlow) → domain repository → data (Room)
                                      ↘ service（扫描、播放、转换、Shizuku、外部同步）
```

- UI：Jetpack Compose + Material 3
- 数据：Room + Flow
- 依赖注入：Hilt
- 异步：Kotlin Coroutines
- 播放：AndroidX Media3 / ExoPlayer / MediaSession
- 图像：Coil
- 特权文件访问：Shizuku AIDL UserService

## 版本与文档维护

此仓库采用 Git 版本控制。每次功能、交互、权限、构建方式或使用步骤发生变化时：

1. 同一批改动内更新本 README 的功能说明、操作步骤和限制。
2. 运行与改动相称的测试，并构建 release APK。
3. 使用清晰的 Conventional Commit 信息提交，并推送到 `origin/master`。
4. 不提交 APK、keystore、密码、令牌、真实缓存视频或其他私密数据。

## 免责声明

- 本应用与 Bilibili 无关，亦非官方产品。
- 本地缓存内容的版权归原作者及权利人所有。
- 请遵守所在地法律、平台规则与版权要求，仅处理有权使用的本地内容。
