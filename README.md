<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0+-34A853?logo=android" />
  <img src="https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin" />
  <img src="https://img.shields.io/badge/Compose-BOM_2024.02-4285F4?logo=jetpackcompose" />
  <img src="https://img.shields.io/badge/Shizuku-13.1.5-blueviolet" />
  <img src="https://img.shields.io/badge/Media3-ExoPlayer-FF6F00" />
</p>

<h1 align="center">⚡ SILLY BILIBILI</h1>

<p align="center">
  <strong>B 站缓存视频管理 & MP4 转换工具</strong>
  <br />
  赛博朋克风格的 Android 应用，浏览、搜索、过滤、转换 B 站离线缓存视频
  <br /><br />
  <em>Jetpack Compose · Shizuku · Material 3 · ExoPlayer</em>
</p>

---

## 功能

| 功能 | 说明 |
|---|---|
| 📂 **视频浏览** | 扫描 B 站缓存目录，展示封面、标题、UP 主、画质、时长、大小 |
| 🏷️ **分类管理** | 自定义分类，按颜色标记，视频归类整理 |
| 🔍 **全文搜索** | 按标题、UP 主名、av 号模糊搜索，300ms 防抖 |
| 🎯 **多维过滤** | 按画质（360P~4K）、横竖屏、时长、文件大小、扫描时间、有无封面筛选 |
| 🔄 **MP4 转换** | 将分离的 video.m4s + audio.m4s 合并为标准 MP4（remux，不重新编码，无损） |
| 🎬 **视频播放** | ExoPlayer（AndroidX Media3）内置播放器，支持手势亮度/音量、双击快进快退、倍速、锁定 |
| 📼 **导出管理** | 独立的"已转换"页面，查看所有 MP4，一键播放或删除 |
| 📱 **Shizuku 集成** | 无需 Root，通过 Shizuku 访问 Android 11+ 受保护的 `/Android/data/` 目录 |
| 📁 **SAF 选择器** | 在 Android 10 及以下可选择 B 站缓存目录（Android 11+ 系统隐藏了 Android/data/） |
| 🎨 **赛博朋克 UI** | 深色主题 + 霓虹粉/金/紫配色，Material 3 设计 |

### 它能做什么

B 站将缓存视频拆分为视频流（`video.m4s`）和音频流（`audio.m4s`）两个独立文件，存储在 `Android/data/tv.danmaku.bili/download/` 下，普通 App 无法访问。Silly Bilibili 做的事情：

1. **扫描** — 通过 Shizuku 以 shell 权限浏览 B 站缓存目录
2. **解析** — 读取 `entry.json` 元数据：标题、UP 主、画质、分辨率、时长
3. **管理** — 分类、搜索、过滤、分页浏览
4. **转换** — 用 Android 原生 `MediaExtractor` + `MediaMuxer` 将 m4s 双流 remux 为标准 MP4
5. **播放** — 内置 ExoPlayer 直接播放转换后的 MP4

---

## 环境要求

| 项目 | 要求 |
|---|---|
| **Android** | 8.0+（API 26+） |
| **Shizuku** | v13+（[下载](https://shizuku.rikka.app/download/)） |
| **B 站 App** | 已安装并有缓存视频 |
| **存储空间** | ~200MB（App + 缓存 + 转换输出） |

> ⚠️ Android 11+ 必须安装 Shizuku 才能读取 B 站的 `/Android/data/` 缓存目录。SAF 系统文件选择器在 Android 11+ 不能访问该目录（Google 平台限制）。

---

## 快速开始

### 1. 安装 Shizuku

从 [GitHub Releases](https://github.com/RikkaApps/Shizuku/releases) 或 [Google Play](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) 下载。

### 2. 启动 Shizuku

- 打开 Shizuku App
- 选择「通过无线调试启动」（Android 11+ 无需电脑）
- 按提示操作，确认状态显示「正在运行」

### 3. 安装 Silly Bilibili

```bash
git clone https://github.com/NeitherTourRest/Videos-Manager-for-B.git
cd Videos-Manager-for-B
./gradlew assembleRelease
```

APK 位置：`app/build/outputs/apk/release/app-release.apk`

### 4. 首次启动

1. 授予「所有文件访问权限」
2. 授予 Shizuku 权限
3. 进入 **Settings**（顶部齿轮图标）
4. 点击 **Scan for Videos** 扫描 B 站缓存

---

## 使用指南

### 视频管理

| 操作 | 方式 |
|---|---|
| 查看详情 | 点击视频卡片 |
| 右键菜单 | 长按视频卡片 |
| 分配分类 | 长按 → 分配分类 |
| 转换 MP4 | 进入详情页 → Convert to MP4 |
| 删除视频 | 长按 → 删除 |
| 已转换列表 | 顶部栏文件夹图标 → 查看/播放/删除所有 MP4 |

### 搜索 & 过滤

- **搜索**：顶部搜索栏输入 — 匹配标题、UP 主名、av 号
- **过滤**：点击漏斗图标 — 选择条件后点「确认」应用
- **分类**：点击分类标签只显示该分类视频
- **重置**：过滤面板内点「重置」清空草稿，再点「确认」生效

### 播放器

- **亮度/音量**：左侧竖滑调亮度，右侧竖滑调音量
- **快进快退**：双击左侧 -10s，双击右侧 +10s
- **倍速**：顶栏 1.0x 按钮循环切换 0.5x / 1.0x / 1.5x / 2.0x
- **锁定**：锁定后禁用所有触控，防止误操作
- **返回**：左上角箭头

### 分页

- 列表每页 **20 条**
- 底部 **< Prev / Next >** 翻页
- **LOAD ALL** 加载全部

---

## B 站缓存结构

```
/storage/emulated/0/Android/data/tv.danmaku.bilibili/download/
└── {avid}/                          ← av 号目录
    └── c_{cid}/                     ← 分 P 目录
        ├── entry.json               ← 视频元数据（标题、画质、分辨率等）
        ├── {type_tag}/              ← 画质标签（如 80 = 1080P）
        │   ├── video.m4s            ← H.264 视频流
        │   ├── audio.m4s            ← AAC 音频流
        │   └── index.json           ← 流索引
        ├── cover.jpg                ← 封面图
        └── danmaku.xml              ← 弹幕文件
```

---

## MP4 转换原理

```
video.m4s ──┐
             ├── Shizuku shell 复制 → App 缓存目录
audio.m4s ──┘
                  │
                  ▼
           MediaExtractor 分别读取视频/音频轨
                  │
                  ▼
           检测 track（video/avc, audio/mp4a）
                  │
                  ▼
           MediaMuxer 按时间戳交错写入 → output.mp4
                  │
                  ▼
            标准 MP4 文件（任意播放器可播）
```

> 转换是 **remux（重封装）而非重新编码**。不经过解码器，画质/音质无任何损失。速度约 1~3 秒/分钟视频。

---

## 技术架构

```
┌─────────────────────────────────────────────────────┐
│  UI 层 (Jetpack Compose)                             │
│  HomePage · VideoDetailPage · ExportedPage          │
│  PlayerPage · CategoriesPage · SettingsPage          │
├─────────────────────────────────────────────────────┤
│  ViewModel 层 (Hilt + StateFlow)                     │
│  管理 UiState，调用 Repository，响应 Flow 变化       │
├─────────────────────────────────────────────────────┤
│  Domain 层 (纯 Kotlin)                               │
│  Video · Category · ConversionProgress · Repository  │
├─────────────────────────────────────────────────────┤
│  Data 层                                             │
│  Room (SQLite) · VideoDao · CategoryDao              │
│  RepositoryImpl (Entity ↔ Domain 映射)               │
├─────────────────────────────────────────────────────┤
│  Service 层                                          │
│  VideoScanService    — 扫描 B 站缓存目录             │
│  VideoConverterService — m4s → mp4 remux            │
│  SettingsService     — SharedPreferences 封装        │
│  ShellService.java   — Shizuku AIDL Shell 执行器     │
├─────────────────────────────────────────────────────┤
│  Util 层                                             │
│  ShizukuFileHelper — shell ls/cat/stat/dd + base64  │
└─────────────────────────────────────────────────────┘
```

### 关键设计决策

| 决策 | 理由 |
|---|---|
| **Shizuku 而非 Root** | 无需解锁设备，支持所有 Android 11+ 手机 |
| **AIDL UserService** | Shizuku `newProcess()` 在 Android 13 已废弃，AIDL 是官方推荐方案 |
| **Android MediaMuxer** | 省去 FFmpeg（28MB+），用系统内置 MP4 muxer 零拷贝 remux |
| **dd + base64 分块** | Binder 事务缓冲区 1MB 限制，512KB 分块读取避免溢出 |
| **Room + Flow** | 响应式数据层，数据库变化自动刷新 UI |
| **ExoPlayer (Media3)** | 自带手势控制、编解码支持广泛，替代手写 MediaPlayer |

---

## 项目结构

```
app/src/main/java/com/example/sillybilibili/
├── SillyBilibiliApp.kt          Application（Hilt 入口）
├── MainActivity.kt              入口 + 权限请求
├── di/                          Hilt 依赖注入模块
├── data/
│   ├── local/
│   │   ├── dao/                 Room DAO（VideoDao, CategoryDao）
│   │   ├── entity/              Room 实体（VideoEntity, CategoryEntity）
│   │   └── AppDatabase.kt       Room 数据库 + 迁移（v1→v4）
│   └── repository/              Repository 实现
├── domain/
│   ├── model/                   数据模型（Video, Category, ConversionProgress）
│   └── repository/              Repository 接口
├── service/
│   ├── VideoScanService.kt      缓存目录扫描器（批处理 + 并行）
│   ├── VideoConverterService.kt MP4 转换引擎（remux）
│   ├── SettingsService.kt       用户偏好设置
│   └── ShellService.java        Shizuku AIDL Shell 执行
├── ui/
│   ├── components/              可复用组件
│   │   ├── VideoCard.kt         视频卡片
│   │   ├── FilterSheet.kt       过滤面板（6 维过滤 + 确认/重置）
│   │   ├── SearchBar.kt         搜索栏
│   │   ├── VideoContextMenu.kt  长按菜单
│   │   └── ConversionStatusView.kt 转换进度
│   ├── pages/
│   │   ├── home/                首页 + 视频列表 + 详情
│   │   ├── player/              ExoPlayer 播放器
│   │   ├── exported/            已转换 MP4 管理
│   │   ├── categories/          分类管理
│   │   ├── scan/                扫描页面
│   │   ├── guide/               引导页
│   │   └── settings/            设置页
│   └── theme/                   颜色、字体、主题
└── util/
    └── ShizukuFileHelper.kt     特权文件 I/O（ls/stat/cat/cp/dd/base64）
```

---

## 依赖库

| 库 | 用途 |
|---|---|
| Jetpack Compose (BOM 2024.02) | 声明式 UI 框架 |
| Material 3 | 设计系统 |
| Material Icons Extended | 扩展图标集 |
| Room (2.6.1) | 本地 SQLite + Flow |
| Hilt (2.48.1) | 依赖注入 |
| Navigation Compose | 类型安全路由 |
| Coil (2.5.0) | 图片加载 |
| Media3 ExoPlayer (1.3.1) | 视频播放 |
| Shizuku API (13.1.5) | 特权文件访问 |

---

## 免责声明

- 本应用**与 Bilibili 无关**，非官方产品
- 所有缓存视频内容版权归原作者和 B 站所有
- 转换功能仅合并已下载的缓存分片，**不破解任何 DRM**
- 请遵守当地版权法律，合理使用

---

<p align="center">
  <sub>⚡ SILLY BILIBILI — 因为 B 站缓存，值得更好的管理</sub>
</p>
