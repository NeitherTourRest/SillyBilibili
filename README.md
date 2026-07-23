
<p align="center">
  <img src="https://img.shields.io/badge/Android-34-darkgreen?logo=android" />
  <img src="https://img.shields.io/badge/Compose-BOM%202024.02-ff69b4?logo=jetpackcompose" />
  <img src="https://img.shields.io/badge/Shizuku-13.1.5-blueviolet" />
  <img src="https://img.shields.io/badge/Kotlin-17-7F52FF?logo=kotlin" />
  <img src="https://img.shields.io/badge/arch-MVVM-blue" />
</p>

<h1 align="center">⚡ SILLY BILIBILI</h1>

<p align="center">
  <strong>B 站缓存视频管理 & 转换工具</strong>
  <br />
  A sleek, cyberpunk-styled Android app for managing and converting Bilibili cached videos.
  <br /><br />
  <em>Browse · Filter · Search · Convert to MP4</em>
  <br />
  <sub>Built with Jetpack Compose + Shizuku + Material 3</sub>
</p>

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 📂 **Video Browser** | Browse all cached Bilibili videos with cover previews and metadata |
| 🏷️ **Category Management** | Organize videos into custom categories with color-coded labels |
| 🔍 **Full-Text Search** | Search by title, uploader name, or av ID with 300ms debounce |
| 🎯 **Smart Filtering** | Filter by quality (360P~4K), orientation, duration, file size, scan time |
| 🔄 **MP4 Conversion** | Merge separate `.m4s` video/audio streams into a single `.mp4` file |
| 📱 **Shizuku Integration** | Access Android `data/` restricted directories without root |
| 🎨 **Cyberpunk Theme** | Dark, neon-accented UI with Bilibili-inspired pink/cyan aesthetics |
| 📄 **Pagination** | 20 videos per page with prev/next controls |
| 📐 **Responsive** | Adaptive layout with lazy loading for smooth scrolling |

### What It Does

Bilibili caches videos in a fragmented format — video (`.m4s`) and audio (`.m4s`) are stored as **separate files** inside `Android/data/tv.danmaku.bilibili/download/`. Silly Bilibili:

1. **Scans** the Bilibili cache directory via Shizuku (no root required)
2. **Parses** `entry.json` metadata (title, up name, quality, resolution, duration)
3. **Organizes** videos into custom categories you create
4. **Converts** the split video+audio segments into a standard **MP4** file using Android's native `MediaExtractor` + `MediaMuxer`

---

## 📸 Preview

```
╭──────────────────────────────────────────╮
│  ⚡ SILLY BILIBILI         🔍 🏷️ ⚙️    │
│  ─────────────────────────────────────── │
│  [🔍 Search videos...  ]                 │
│                                          │
│  [All] [🎮 Gaming] [🎵 Music] ...       │
│                                          │
│  ┌────────────────────────────────────┐ │
│  │ ▌🖼   up_name  ·  1920×1080       │ │
│  │ ▌     Video Title Here             │ │
│  │ ▌    12.34 MB ● 05:23             │ │
│  │ ▌    2025-06-28                    │ │
│  └────────────────────────────────────┘ │
│                                          │
│  [< Prev]   PAGE 1   [Next >]           │
│  [LOAD ALL  (20+)]                       │
╰──────────────────────────────────────────╯
```

*(Actual screenshots: see `screenshots/` or build and run)*

---

## 📋 Requirements

| Requirement | Version |
|-------------|---------|
| **Android** | 8.0+ (API 26+) |
| **Shizuku** | v13+ ([Download](https://shizuku.rikka.app/download/)) |
| **Storage** | ~200MB free for app + cache |
| **Bilibili App** | Installed with cached videos |

> ⚠️ **Shizuku is required** on Android 11+ to access the Bilibili app's `Android/data/` directory due to scoped storage restrictions.

---

## 🚀 Setup

### 1. Install Shizuku

```bash
# Download from GitHub Releases
https://github.com/RikkaApps/Shizuku/releases

# Or get it from Google Play:
# https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api
```

### 2. Enable Shizuku

- Open the Shizuku app
- Go to **"Start via wireless debugging"** (no PC required on Android 11+)
- Follow the on-screen instructions
- Verify the status shows "Running"

### 3. Build the App

```bash
git clone https://github.com/yourusername/silly-bilibili.git
cd silly-bilibili
./gradlew assembleDebug
```

Or download the pre-built APK from [Releases](https://github.com/yourusername/silly-bilibili/releases).

### 4. Permissions

On first launch, the app requests:

- **Manage All Files Access** (Android 11+) — required to read Bilibili's cache
- **Shizuku Permission** — required to bypass scoped storage on `Android/data/`

---

## 🎮 Usage

### First Run

1. Grant storage permission when prompted
2. Grant Shizuku permission when prompted
3. Go to **Settings** (top bar gear icon)
4. Tap **"Scan for Videos"** — the app will scan the default Bilibili cache path

### Managing Videos

| Action | How |
|--------|-----|
| **View details** | Tap a video card |
| **Context menu** | Long-press a video card |
| **Assign category** | Long-press → "分配分类" |
| **Convert to MP4** | Long-press → "转换 MP4" or tap → "Convert to MP4" |
| **Delete** | Long-press → "删除视频" |

### Search & Filter

- **Search**: Type in the search bar — matches title, up name, and av ID
- **Filter**: Tap the funnel icon in the top bar — filter by quality, orientation, duration, size, scan time, cover presence
- **Categories**: Tap a category chip to show only videos in that group

### Pagination

- All video lists show **20 items per page**
- Use **`< Prev` / `Next >`** buttons to navigate pages
- **`LOAD ALL`** loads all matching videos at once

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────┐
│                    UI Layer                       │
│  ┌──────────┐  ┌──────────┐  ┌───────────────┐  │
│  │ HomePage │  │VideoList │  │ VideoDetail   │  │
│  │(MainView)│  │  (Cat.)  │  │ (Convert UI)  │  │
│  └────┬─────┘  └────┬─────┘  └──────┬────────┘  │
│       │              │               │           │
│  ┌────▼──────────────▼───────────────▼────────┐  │
│  │        ViewModels + UiState               │  │
│  └────────────────┬─────────────────────────┘  │
├───────────────────┼────────────────────────────┤
│                   │         Domain Layer        │
│  ┌────────────────▼─────────────────────────┐  │
│  │     Repositories (interfaces)            │  │
│  │     Models (Video, Category, etc.)       │  │
│  └────────────────┬─────────────────────────┘  │
├───────────────────┼────────────────────────────┤
│                   │         Data Layer          │
│  ┌────────────────▼─────────────────────────┐  │
│  │  Room Database (VideoDao, CategoryDao)    │  │
│  │  Room Migrations (v1→v2→v3)              │  │
│  ├─────────────────────────────────────────┤  │
│  │  Services                               │  │
│  │  ├─ VideoScanService (scan Bili cache)  │  │
│  │  ├─ VideoConverterService (m4s → mp4)   │  │
│  │  └─ SettingsService (preferences)       │  │
│  ├─────────────────────────────────────────┤  │
│  │  Utilities                              │  │
│  │  ├─ ShizukuFileHelper (elevated IO)     │  │
│  │  └─ ShellService (AIDL bridge)          │  │
│  └─────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

### Key Technical Decisions

| Decision | Rationale |
|----------|-----------|
| **Shizuku over root** | No device root required; works on stock Android 11+ |
| **AIDL UserService** | `Shizuku.newProcess()` deprecated in Android 13; AIDL-based shell execution is the supported approach |
| **Native MediaMuxer** | Avoids FFmpeg binary (28MB+ size); uses Android's built-in MP4 muxer for zero-copy stream remuxing |
| **Chunked file reading** | Binder transaction buffer limit (1MB) prevents direct base64 of large files; `dd` chunked reads via shell |
| **Room + Flow** | Reactive data layer with automatic UI updates on database changes |
| **Dark-only theme** | Consistent cyberpunk aesthetic; neon accents pop against dark backgrounds |

---

## 🔧 Technical Details

### Bilibili Cache Structure

```
/storage/emulated/0/Android/data/tv.danmaku.bilibili/download/
└── {avid}/
    └── c_{cid}/
        ├── entry.json          ← metadata (title, quality, resolution, etc.)
        ├── {type_tag}/
        │   ├── video.m4s       ← H.264 video stream (fragmented MP4)
        │   ├── audio.m4s       ← AAC audio stream
        │   └── index.json      ← stream index
        ├── cover.jpg
        └── danmaku.xml
```

### MP4 Conversion Pipeline

```
video.m4s ──┐
             ├── Shizuku chunked copy → cacheDir(rename .m4s→.mp4)
audio.m4s ──┘
                        │
                        ▼
                MediaExtractor.setDataSource()
                        │
                        ▼
                  Track detection (video H.264 / audio AAC)
                        │
                        ▼
              MediaMuxer → output.mp4
                        │
                        ▼
              Result: valid standalone MP4 file
```

> **Note**: Conversion is a **remux**, not a re-encode. It copies streams without quality loss. Processing time is typically 1–3 seconds per minute of video.

### Room Database Migrations

| Version | Change |
|---------|--------|
| **v1** | Initial schema |
| **v2** | Added `ownerName` column |
| **v3** | Added `quality`, `width`, `height` columns |

### Shizuku File Access

On Android 11+, files in `/Android/data/` are restricted. The app uses **Shizuku UserService** to:

1. List directories via `ls`/`stat` shell commands
2. Read `entry.json` via `cat`
3. Copy `.m4s` files in **512KB chunks** via `dd | base64` (each chunk < 1MB Binder limit)
4. Reassemble chunks in the app process with correct SELinux context

---

## 🧩 Dependencies

| Library | Purpose |
|---------|---------|
| **Jetpack Compose (BOM 2024.02)** | Modern declarative UI |
| **Material 3** | Design system with custom theming |
| **Room** | Local SQLite database with Flow support |
| **Hilt** | Dependency injection |
| **Navigation Compose** | Type-safe navigation |
| **Coil** | Image loading with Compose support |
| **Shizuku API** | Elevated file access for Android 11+ |
| **Material Icons Extended** | Additional icon set |

---

## 📁 Project Structure

```
app/src/main/java/com/example/sillybilibili/
├── SillyBilibiliApp.kt          ← Application class (Hilt entry)
├── MainActivity.kt              ← Entry point with permission flow
├── di/
│   ├── DatabaseModule.kt        ← Room DB + DAO provides
│   └── ...
├── data/
│   ├── local/
│   │   ├── dao/                 ← Room DAOs (VideoDao, CategoryDao)
│   │   ├── entity/              ← Room entities (VideoEntity, CategoryEntity)
│   │   └── AppDatabase.kt       ← Room database + migrations
│   └── repository/              ← Repository implementations
├── domain/
│   ├── model/                   ← Domain models (Video, Category, etc.)
│   └── repository/              ← Repository interfaces
├── service/
│   ├── VideoScanService.kt      ← Cache directory scanner
│   ├── VideoConverterService.kt ← MP4 conversion engine
│   ├── SettingsService.kt       ← User preferences
│   └── ShellService.java        ← Shizuku AIDL shell executor
├── ui/
│   ├── components/              ← Reusable composables
│   │   ├── VideoCard.kt         ← Video card + CategoryChip
│   │   ├── SearchBar.kt         ← Search input
│   │   ├── FilterSheet.kt       ← Filter bottom sheet
│   │   ├── VideoContextMenu.kt  ← Long-press menu + dialog
│   │   ├── CategoryCard.kt      ← Category card + ColorPicker
│   │   └── ConversionStatusView.kt ← Conversion progress widget
│   ├── pages/
│   │   └── home/
│   │       ├── HomePage.kt      ← Main video list
│   │       ├── HomeViewModel.kt ← Main view model
│   │       ├── VideoListPage.kt ← Category video list
│   │       ├── VideoListViewModel.kt
│   │       ├── VideoDetailPage.kt ← Detail + convert
│   │       ├── VideoDetailViewModel.kt
│   │       ├── CategoriesPage.kt ← Category management
│   │       └── SettingsPage.kt  ← Scan & preferences
│   ├── navigation/              ← NavHost setup
│   └── theme/                   ← Colors, Typography, Theme
└── util/
    └── ShizukuFileHelper.kt     ← Elevated file I/O
```

---

## ⚠️ Disclaimer

- This app is **not affiliated with or endorsed by Bilibili**.
- All cached video content is property of the original uploaders and Bilibili.
- The conversion feature **does not remove DRM** — it only merges already-downloaded segments.
- Use at your own risk. Respect copyright laws in your jurisdiction.

## 🙏 Credits

- **[RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)** — Elevated file access API
- **Bilibili** — For the entertainment platform
- **Material 3 / Jetpack Compose** — UI framework

---

<p align="center">
  <sub>Built with 💖 by <a href="https://github.com/yourusername">Sisyphus</a></sub>
  <br />
  <sub>⚡ SILLY BILIBILI — 因为 B 站缓存，值得更好的管理体验</sub>
</p>
