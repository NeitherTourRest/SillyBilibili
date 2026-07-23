# SillyBilibili 开发索引

> 每个类/函数都有"被谁调用"列，方便追溯调用链

## 项目结构

```
D:\SillyBilibili\app\src\main\java\com\example\sillybilibili\
├── SillyBilibiliApp.kt              ← Hilt 入口
├── MainActivity.kt                  ← App 大门
├── di/                              ← 依赖注入
├── domain/                          ← 纯数据层
│   ├── model/                       ←    数据模型
│   └── repository/                  ←    接口定义
├── data/                            ← 数据实现层
│   ├── local/                       ←    Room 数据库
│   │   ├── dao/                     ←        SQL 语句
│   │   ├── entity/                  ←        表结构
│   │   └── AppDatabase.kt           ←        数据库定义
│   └── repository/                  ←    接口实现 + Entity↔Domain 映射
├── service/                         ← 业务服务
├── ui/                              ← Compose 界面
│   ├── theme/                       ←    主题
│   ├── components/                  ←    可复用组件
│   ├── pages/                       ←    页面
│   │   ├── home/                    ←        首页+视频列表+详情
│   │   ├── categories/              ←        分类管理
│   │   ├── settings/                ←        设置
│   │   ├── guide/                   ←        使用指南
│   │   ├── scan/                    ←        扫描
│   │   └── player/                  ←        播放器
│   └── navigation/                  ←    导航路由
└── util/                            ← 工具类
```

---

## 1. 入口层

### SillyBilibiliApp.kt
| 类/函数 | 作用 | 被谁调用 |
|---------|------|---------|
| `SillyBilibiliApp` | Application 子类，`@HiltAndroidApp` 标记 Hilt 初始化入口 | Android 系统 |

### MainActivity.kt
| 类/函数 | 作用 | 被谁调用 |
|---------|------|---------|
| `MainActivity` | 唯一 Activity，`@AndroidEntryPoint` 使 Hilt 可用 | Android 系统 |
| `onCreate()` | 设置全屏 → 包裹 SillyBilibiliTheme → 直接启动 AppNavHost | 系统 |
| `setContent { }` | Compose 入口，创建 navController → AppNavHost | onCreate |

---

## 2. DI 层 (di/)

### DatabaseModule.kt
| 类/函数 | 作用 | 被谁调用 |
|---------|------|---------|
| `DatabaseModule` | `@Module` Hilt 模块，提供 DB 相关对象 | Hilt 框架 |
| `MIGRATION_1_2` | videos 表加 ownerName 列 | Room 自动 |
| `MIGRATION_2_3` | videos 表加 quality/width/height 列 | Room 自动 |
| `MIGRATION_3_4` | videos 表加 exportedPath 列 | Room 自动 |
| `provideAppDatabase()` | 创建 Room 数据库（silly_bilibili.db）+ 应用迁移 | Hilt 框架 |
| `provideCategoryDao()` | 提供 CategoryDao 实例 | Hilt 框架 |
| `provideVideoDao()` | 提供 VideoDao 实例 | Hilt 框架 |

### RepositoryModule.kt
| 类/函数 | 作用 | 被谁调用 |
|---------|------|---------|
| `RepositoryModule` | `@Module` Hilt 模块，绑定接口↔实现 | Hilt 框架 |
| `bindCategoryRepository()` | CategoryRepository ← CategoryRepositoryImpl | Hilt |
| `bindVideoRepository()` | VideoRepository ← VideoRepositoryImpl | Hilt |

---

## 3. 领域层 (domain/)

### domain/model/Video.kt
| 类/字段 | 作用 | 被谁调用 |
|---------|------|---------|
| `Video` (data class) | 核心数据模型：标题/路径/大小/时长/画质/UP主/分辨率 | 全局 |
| `id: Long` | 数据库自增主键 | DB |
| `avid: Long` | B站 av 号 | 扫描/显示 |
| `cid: Long` | B站分P cid | 扫描/转换 |
| `title: String` | 视频标题 | VideoCard/详情页 |
| `ownerName: String` | UP主名 | VideoCard |
| `quality: String` | 画质描述（"1080P"） | VideoCard Badge |
| `width/height: Int` | 分辨率（≥0） | 竖屏判断/显示 |
| `path: String` | video.m4s 完整路径 | 转换时读取 |
| `audioPath: String` | audio.m4s 完整路径 | 转换时读取 |
| `size: Long` | 文件总大小（字节） | VideoCard 显示 |
| `duration: Long` | 时长（毫秒） | 过滤/显示 |
| `categoryId: Long?` | 所属分类 ID | 分类筛选 |
| `coverPath: String?` | 封面缓存路径 | Coil 封面加载 |
| `addedAt: Long` | 添加时间戳 | 排序/日期显示 |
| `exportedPath: String?` | 导出后的 .mp4 路径 | 播放/导出管理 |
| `isVertical` (get) | height > width ? 竖屏 : 横屏 | VideoCard 颜色选择 |
| `resolutionLabel` (get) | "1920×1080" 格式 | VideoCard/详情页 |
| `formattedSize` (get) | "12.34 MB" 格式 | VideoCard |
| `formattedDuration` (get) | "05:23" 格式 | VideoCard |

### domain/model/Category.kt
| 类/字段 | 作用 | 被谁调用 |
|---------|------|---------|
| `Category` (data class) | 分类模型：名称/颜色/视频计数 | 全局 |
| `videoCount: Int` | 该分类下的视频数（查询时不存 DB，实时算） | 分类标签显示 |

### domain/model/ConversionProgress.kt
| 类/字段 | 作用 | 被谁调用 |
|---------|------|---------|
| `ConversionProgress` | 转换进度消息（videoId + 百分比 + 状态 + 输出路径/错误） | 详情页显示 |
| `ConversionStatus` (enum) | PENDING / CONVERTING / COMPLETED / FAILED | ConversionStatusView |

### domain/repository/VideoRepository.kt
| 方法 | 作用 | 被谁调用 |
|------|------|---------|
| `getAllVideos()` | 返回 Flow<List<Video>>，数据库变化自动通知 | ViewModel |
| `getVideosByCategory(catId)` | 按分类筛选 | ViewModel |
| `getVideoById(id)` | 查单个视频 | 详情 ViewModel |
| `searchVideos(query)` | 模糊搜索 | ViewModel |
| `searchVideosInCategory(catId, query)` | 分类内搜索 | ViewModel |
| `insertVideo()` / `insertVideos()` | 插入一条/批量 | 扫描服务 |
| `updateVideo()` | 更新（如分配分类、标记导出） | ViewModel |
| `deleteVideo()` | 删除 | ViewModel |
| `getAllAvIds()` | 获取所有已扫描的 avid（去重用） | 扫描服务 |
| `getFilteredVideosPaginated(...)` | **核心方法**：统一查询（搜索+过滤+分类+分页），参数全可空 | HomeViewModel/VideoListViewModel |

### domain/repository/CategoryRepository.kt
| 方法 | 作用 | 被谁调用 |
|------|------|---------|
| `getAllCategories()` | 返回 Flow<List<Category>>，带视频计数 | ViewModel |
| `insertCategory()` | 新建 | CategoriesPage |
| `updateCategory()` | 更新 | CategoriesPage |
| `deleteCategory(id)` | 删除 | CategoriesPage |

---

## 4. 数据层 (data/)

### data/local/entity/VideoEntity.kt
| 注解/字段 | 作用 |
|-----------|------|
| `@Entity(tableName="videos")` | Room 表定义 |
| `@ForeignKey` | categoryId → categories.id，删分类时 SET_NULL |
| `@Index(path, unique=true)` | path 唯一索引，防重复扫描 |
| 字段 | 与 Video.kt 一一对应 |

### data/local/entity/CategoryEntity.kt
| 注解/字段 | 作用 |
|-----------|------|
| `@Entity(tableName="categories")` | Room 表定义 |
| 字段 | id / name / color / createdAt |

### data/local/AppDatabase.kt
| 类/函数 | 作用 |
|---------|------|
| `AppDatabase` | `@Database(version=4)` — 定义两张表 + 两个 DAO |
| `categoryDao()` | Room 自动生成的 DAO 获取方法 |
| `videoDao()` | Room 自动生成的 DAO 获取方法 |

### data/local/dao/VideoDao.kt
| 方法 | SQL | 返回类型 | 被谁调用 |
|------|-----|---------|---------|
| `getAllVideos()` | SELECT * ORDER BY addedAt DESC | Flow | getAllVideos() |
| `getVideosByCategory(id)` | WHERE categoryId=? | Flow | 分类筛选 |
| `getVideoById(id)` | WHERE id=? | suspend VideoEntity? | 详情页 |
| `searchVideos(query)` | WHERE title/ownerName/avid LIKE | Flow | 搜索 |
| `getFilteredVideosPaginated(query, ...)` | **统一查询**：WHERE 子句全部 `IS NULL OR ...` 模式，传 null 跳过 | suspend | loadFirstPage/loadMore/goToPage/loadAll |
| `insertVideo` / `insertVideos` | @Insert REPLACE | — | 扫描/转换 |
| `updateVideo` | @Update | — | 分配分类/标记导出 |
| `deleteVideo` | @Delete | — | 删除 |
| `getAllAvIds()` | SELECT avid | — | 扫描去重 |
| `getVideoCountByCategory(id)` | SELECT COUNT(*) WHERE categoryId=? | — | 分类计数 |

### data/local/dao/CategoryDao.kt
| 方法 | 作用 |
|------|------|
| `getAllCategories()` | Flow 可观察 |
| `insert/update/delete` | 增删改 |

### data/repository/VideoRepositoryImpl.kt
| 方法 | 作用 |
|------|------|
| 所有 VideoRepository 接口方法 | 委托 VideoDao → Entity → Domain 映射 |
| `VideoEntity.toDomain()` | Entity → Video 转换函数 |
| `Video.toEntity()` | Video → Entity 转换函数 |

### data/repository/CategoryRepositoryImpl.kt
| 方法 | 作用 |
|------|------|
| `getAllCategories()` | 查分类 + 每个分类的视频计数 |
| `CategoryEntity.toDomain(count)` | Entity → Category 带 videoCount |
| `Category.toEntity()` | Category → Entity |

---

## 5. 服务层 (service/)

### VideoScanService.kt
| 类 | 作用 |
|----|------|
| `ScanFilter` | 扫描过滤条件（画质/时长/大小/avid/模式） |
| `ScanMode` (enum) | FULL（检查文件）/ QUICK（只读 json） |
| `ScanProgress` | 扫描进度（阶段/总数/跳过/过滤/当前avid/已找到） |
| `ScannedVideo` | 临时对象：avid/cid/标题/UP主/画质/宽高/路径/大小/时长/封面/父文件夹 |

| 方法 | 作用 | 被谁调用 |
|------|------|---------|
| `getBilibiliPathConstant()` | 返回默认 B 站缓存路径 | 设置页/扫描页 |
| `scanDirectory(path, filter?)` | **核心方法**：返回 Flow<ScanProgress>，支持过滤+并行+批处理 | ScanViewModel |
| `processBatchPreFilter()` | 批处理读取 entry.json → 预过滤 | scanDirectory |
| `scanAvIdDirectory()` | 查找 cid → 读 entry.json → 检查文件 → 复制封面 | scanDirectory 并行处理 |
| `matchesFilter()` | 过滤匹配检查 | preFilter |
| `copyCoverToCache()` | 封面 → cacheDir/covers/ | scanAvIdDirectory |
| `toDomainModel()` | ScannedVideo → Video 转换 | 入库 |

### VideoConverterService.kt
| 方法 | 作用 | 被谁调用 |
|------|------|---------|
| `getDefaultOutputPath()` | /Movies/SillyBilibili/Converted | 详情页 |
| `ensureAccessible(path)` | **核心方法**：4 级策略确保文件可读 | convertToMp4 |
| `convertToMp4(videoPath, audioPath, outputDir, title, videoId)` | 完整转换管线 → Flow<ConversionProgress> | VideoDetailViewModel |
| `convertToMp4Suspend()` | 同步版本 | 未用 |
| `cancelAll()` | 空实现 | — |

### SettingsService.kt
| 方法 | 作用 | 被谁调用 |
|------|------|---------|
| `scanPath` (property) | 扫描路径 | 扫描页 |
| `outputPath` (property) | 导出路径 | 设置页/详情页 |
| `autoScan` (property) | 自动扫描开关 | — |
| `clear()` | 清除所有设置 | — |

### ShellService.java
| 方法 | 作用 |
|------|------|
| `exec(command)` | Shizuku AIDL：`Runtime.exec("sh -c command")` → 返回 stdout/stderr |
| `destroy()` | System.exit(0) |

---

## 6. 工具层 (util/)

### ShizukuFileHelper.kt
| 方法 | 作用 | 被谁调用 |
|------|------|---------|
| `isShizukuAvailable()` | 检查 Shizuku 是否安装+运行 | 全局 |
| `fileExists(path)` | 检查文件是否存在 | 扫描/转换 |
| `listDirectories(path)` | ls 列出目录 | 扫描 |
| `listSubDirectoriesWithEntryJson(path)` | 找含 entry.json 的子目录 | 扫描（找 cid） |
| `readFileContent(path)` | cat 读文本（entry.json） | 扫描 |
| `readBinaryFile(path)` | base64 读二进制（cover.jpg） | 扫描 |
| `readBinaryFileChunked(path)` | dd 512KB 块 + base64 读大文件 | 转换兜底 |
| `copyFile(src, dest)` | cp src → dest + 核验大小 | 转换 |
| `makeReadable(path)` | chmod 644 + restorecon | 转换策略0 |
| `execShell(command)` | 执行 shell 命令 | 转换中转 |
| `fileLength(path)` | stat -c %s | 扫描/转换 |
| `checkVideoFilesExist(v,a)` | test -f 检查 video+audio | 扫描 |
| `getVideoFileInfo(v,a)` | stat 获取文件大小 | 扫描 |

### PermissionHelper.kt
| 方法 | 作用 | 被谁调用 |
|------|------|---------|
| `hasStoragePermission()` | 检查当前 Android 版本的存储权限 | 指南页 |
| `getRequiredPermissions()` | 返回对应 Android 版本需要的权限数组 | 指南页 |
| `createManageStorageIntent()` | 创建跳转系统设置页的 Intent | 指南页 |

---

## 7. UI 主题 (ui/theme/)

### Color.kt
- `CyberVermilion` (#d4553a) — 主色，按钮/选中/强调
- `CyberGold` (#c9a227) — 辅色，边框/文字强调
- `NeonCyan` (#00d4ff) — 霓虹青，技术信息
- `NeonPurple` (#a020f0) — 霓虹紫，装饰
- `NeonGreen` / `NeonRed` / `NeonYellow` — 状态色
- `DarkBackground` / `DarkSurface` / `DarkSurfaceVariant` / `DarkCard` / `DarkDivider` — 背景色
- `CategoryColors` (8色) — 分类可选颜色

### Theme.kt
- `DarkColorScheme` — darkColorScheme 映射 Cyber Chinese 颜色
- `SillyBilibiliTheme(content)` — @Composable 包裹 MaterialTheme

### Type.kt
- `Typography` — 字体排版，Monospace 用于技术信息

---

## 8. UI 组件 (ui/components/)

### VideoCard.kt
| 函数 | 作用 | 被谁调用 |
|------|------|---------|
| `VideoCard(video, onClick, onLongClick)` | 视频卡片：左侧渐变条 + 封面缩略图 + 画质Badge + UP主/分辨率/标题/大小/时长/日期 | HomePage/VideoListPage |
| `CategoryChip(name, color, count, selected, onClick)` | 分类标签 Chip：名称 + 视频数 | HomePage |
| `formatDate(timestamp)` | 时间戳 → "yyyy-MM-dd" | VideoCard |

### SearchBar.kt
| 函数 | 作用 | 被谁调用 |
|------|------|---------|
| `SearchBar(query, onQueryChange, placeholder)` | OutlinedTextField：搜索图标 + 清除按钮 + 防抖由 ViewModel 处理 | HomePage/VideoListPage |

### FilterSheet.kt
| 函数 | 作用 | 被谁调用 |
|------|------|---------|
| `FilterSheet(currentFilter, onDraftFilterChange, onApplyFilter, onDismiss)` | ModalBottomSheet：画质/方向/时长/大小/时间/封面 过滤选项 | HomePage |
| `FilterSection(title, content)` | 每个过滤分类的标题+内容 | FilterSheet |
| `FilterChipRow(options, selected, onSelect)` | 水平排列的 FilterChip 行 | FilterSheet |

### VideoContextMenu.kt
| 函数 | 作用 | 被谁调用 |
|------|------|---------|
| `VideoContextMenu(video, onDismiss, onAssign, onDelete, onConvert)` | 长按弹出 ModalBottomSheet：分配分类/转换MP4/删除 | HomePage/VideoListPage |
| `AssignCategoryDialog(video, categories, onDismiss, onAssign)` | 分配分类 AlertDialog：选择已有分类或移除 | HomePage/VideoListPage |

### CategoryCard.kt
| 函数 | 作用 | 被谁调用 |
|------|------|---------|
| `CategoryCard(category, onClick, onEdit, onDelete)` | 分类卡片：左色条 + 首字母 + 名称 + 计数 + 编辑/删除按钮 | CategoriesPage |
| `ColorPicker(selectedColor, onColorSelected)` | 8 色圆形选择器 | CategoriesPage |

### ConversionStatusView.kt
| 函数 | 作用 | 被谁调用 |
|------|------|---------|
| `ConversionStatusView(status, progress, message)` | 转换状态卡片：排队/进度条/完成/失败 + 文字 | VideoDetailPage |

---

## 9. UI 页面 (ui/pages/)

### home/HomePage.kt + HomeViewModel.kt

**HomeViewModel:**
| 类/函数 | 作用 |
|---------|------|
| `HomeUiState` | 状态容器：videos/categories/searchQuery/filterState/currentPage/isLoading 等 |
| `FilterState` | 过滤条件集合（quality/orientation/durationRange/sizeRange/timeRange/hasCover），各有 isActive |
| `Orientation/DurationRange/SizeRange/TimeRange` | 过滤相关的枚举 |
| `HomeViewModel` | @HiltViewModel |
| `loadVideos()` | init 调用，combine 监听 4 个 trigger → loadFirstPage |
| `loadFirstPage()` | 调用 getFilteredVideosPaginated（page=0） |
| `loadMore()` | 增量加载下一页 |
| `goToPage(page)` | 跳到指定页码（替换列表） |
| `loadAll()` | 加载所有匹配过滤条件的视频（pageSize=Int.MAX_VALUE） |
| `refreshVideos()` | _refreshTrigger++ |
| `selectCategory(id)` | 切换分类筛选 |
| `updateSearchQuery(q)` | 设置搜索文字 + 实时更新 UI |
| `applyFilter(f)` / `clearFilter()` | 设置/清除过滤条件 |
| `assignVideoToCategory()` / `deleteVideo()` | 分配分类/删除 |

**HomePage:**
| 参数 | 作用 |
|------|------|
| `HomePage(onNavigateTo...)` | TopAppBar（Filter/Scan/Guide/Categories/Settings）+ SearchBar + 分类Chips + LazyColumn + 分页栏 + LoadAll |

### home/VideoListPage.kt + VideoListViewModel.kt
| 类 | 作用 |
|----|------|
| `VideoListViewModel` | 按分类筛选的视频列表，支持搜索+分页+loadAll |
| `VideoListPage` | 类似 HomePage 但无分类 Chips |

### home/VideoDetailPage.kt + VideoDetailViewModel.kt
| 类 | 作用 |
|----|------|
| `VideoDetailViewModel` | 加载视频、转换MP4、保存 exportedPath |
| `VideoDetailPage` | 封面全宽 + 信息Chips + 转换按钮 + 导出状态 + 播放按钮 |

### categories/CategoriesPage.kt + CategoriesViewModel.kt
| 类 | 作用 |
|----|------|
| `CategoriesViewModel` | 增删改查分类 |
| `CategoriesPage` | 分类列表 Card + 新建/编辑 Dialog + ColorPicker |

### settings/SettingsPage.kt + SettingsViewModel.kt
| 类 | 作用 |
|----|------|
| `SettingsViewModel` | 管理 outputPath |
| `SettingsPage` | 输出路径输入框 |

### scan/ScanPage.kt + ScanViewModel.kt
| 类 | 作用 |
|----|------|
| `ScanViewModel` | 扫描路径管理 + 过滤参数 + startScan() + 进度跟踪。**Activity 级作用域**，导航后不销毁 |
| `ScanPage` | 扫描路径 + 画质下拉/时长输入/快速模式开关 + 扫描按钮 + 进度卡片 + 完成卡片 |

### guide/GuidePage.kt
| 类 | 作用 |
|----|------|
| `GuidePage` | 使用指南页面：权限申请 + Shizuku 安装步骤 + 使用提示 |

### player/PlayerPage.kt
| 类 | 作用 |
|----|------|
| `PlayerPage(filePath, videoTitle, onNavigateBack)` | 视频播放器：SurfaceView + SeekBar + 时间显示 + 倍速(0.5×-2×) + 3秒自动隐藏控制栏 + 点击切换显隐 |

---

## 10. 导航 (ui/navigation/)

### AppNavHost.kt
| 类 | 作用 |
|----|------|
| `Screen` (sealed class) | 路由定义：Home/Categories/Guide/Scan/Settings/VideoList/VideoDetail/Player |
| `AppNavHost(navController)` | NavHost 容器，声明所有 composable 路由 + 参数传递 |

---

## 11. 完整调用链（按场景）

### 场景：用户打开 App → 看视频列表
```
MainActivity.onCreate()
  → AppNavHost(startDestination=Home)
    → HomePage
      → HomeViewModel.init()
        → loadVideos() → combine(4 triggers) → loadFirstPage()
          → videoRepository.getFilteredVideosPaginated(...)
            → VideoDao.getFilteredVideosPaginated(SQL)
              → Room → SQLite → List<VideoEntity>
        → _uiState.update(videos=...)
      → LazyColumn { VideoCard(video) }
```

### 场景：扫描视频
```
HomePage → Scan 按钮
  → ScanPage
    → ScanViewModel.startScan()
      → scanDirectory(path, filter) → Flow<ScanProgress>
        → 遍历 avid 文件夹 → 并行 processBatchPreFilter
          → scanAvIdDirectory → 读 entry.json → copyCover
        → 批量 insertVideos
    → 进度实时显示
  扫描完成 → "Back to Home" → HomePage 重新创建 → 刷新列表
```

### 场景：转换 MP4
```
HomePage → 点击视频 → VideoDetailPage
  → "Convert to MP4" → 选输出目录 → convertToMp4()
    → ensureAccessible(videoPath)
      → canRead()? || makeReadable || copyFile || 中转 cat || chunked
    → MediaExtractor.setDataSource → 读 track
    → MediaMuxer → 合并写 .mp4
    → Flow<ConversionProgress> → UI 实时进度
  完成 → 保存 exportedPath 到 DB → 显示"已导出 ✓" + 播放按钮
```

### 场景：播放视频
```
VideoDetailPage → "Play" 按钮
  → PlayerPage(filePath, title)
    → MediaPlayer + SurfaceView
    → SeekBar + 时间 + 倍速 + 自动隐藏
```
