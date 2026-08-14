# AGENTS.md — SillyBilibili

## 项目概括
SillyBilibili 是一个 Android 上的 Bilibili 客户端，用于浏览和管理本地缓存的 B 站视频。
用户可以从 B 站缓存目录扫描视频，按分类整理，查看详情，并转换为 MP4。

## 技术栈
| 层 | 技术 |
|---|---|
| 语言 | Kotlin 100% |
| UI | Jetpack Compose + Material3 |
| 数据库 | Room (SQLite) |
| DI | Hilt |
| 异步 | Kotlin Coroutines + Flow |
| 图片加载 | Coil |
| 文件访问 | Shizuku (Root 提权) |
| 视频处理 | MediaExtractor + MediaMuxer (API 18+) |
| 构建 | Gradle 8.9 |

## 架构：四层 Clean Architecture

```
domain/    ← 定义"有什么数据"和"能做什么事"（纯 Kotlin，不依赖 Android）
  model/   ← Video, Category, ConversionProgress（数据模型）
  repository/ ← VideoRepository, CategoryRepository（接口）

data/      ← 真正去数据库干活
  local/
    entity/ ← VideoEntity, CategoryEntity（Room 表结构，含外键/索引）
    dao/    ← VideoDao, CategoryDao（SQL 语句存放处）
    AppDatabase.kt（数据库自身，版本号 2）
  repository/ ← VideoRepositoryImpl, CategoryRepositoryImpl（接口实现 + Entity↔Domain 转换）

di/        ← Hilt 自动装配
  DatabaseModule.kt（提供 Room DB、DAO）
  RepositoryModule.kt（接口 → 实现绑定）

ui/        ← Compose 界面
  theme/   ← Color, Type, Theme
  components/ ← VideoCard, FilterSheet, SearchBar
  pages/   ← HomePage, VideoDetailPage, CategoriesPage, VideoListPage
  navigation/ ← AppNavHost（导航路由）

service/   ← 重活（Android 后台服务）
  VideoScanService（扫描 B 站缓存目录）
  VideoConverterService（m4s → mp4 转换）
  SettingsService（SharedPreferences 封装）
  ShellService.java（Shizuku AIDL IPC）
  util/ShizukuFileHelper.kt（Shell 命令操作文件）

MainActivity.kt ← Hilt 入口 + 权限请求
SillyBilibiliApp.kt ← @HiltAndroidApp
```

## 数据流向
```
UI (Compose)
  ↓ collect() 观察 Flow
ViewModel (UiState)
  ↓ 调用 Repository 接口
domain/repository/ (接口)
  ↓ Hilt 注入实现
data/repository/Impl (调用 DAO，Entity→Domain 映射)
  ↓
data/local/dao/ (@Dao + @Query SQL)
  ↓
Room → SQLite
```

## 构建和开发命令
| 命令 | 用途 |
|---|---|
| `.\gradlew assembleDebug` | 编译 Debug APK |
| `.\gradlew test` | 运行单元测试 |
| `.\gradlew lint` | 静态代码检查 |
| `.\gradlew build --no-daemon` | 不使用守护进程的完整构建 |
| `.\gradlew --stop` | 停止所有 Gradle 守护进程 |

## 编码约定

### Kotlin 格式
- 使用 Kotlin 自身的 `"".format()` 替代 `String.format()`
  - 正确：`"%.2f GB".format(size / 1024.0)`
  - 错误：`String.format("%.2f GB", size / 1024.0)` ← IDE 会报 Unresolved reference

### 架构规则
- Entity 只在 data 层内部使用，不泄漏到 domain/ui
- Repository 是唯一的数据入口，ViewModel 不直接调用 DAO
- Data Flow 用 `Flow<T>` 返回（可观察），一次性查询用 `suspend fun`
- Entity ↔ Domain 的转换在 RepositoryImpl 中做，用 `.map { it.toDomain() }`

### 数据库
- 所有字符串条件用 SQLite 的 `LIKE '%' || :param || '%'` 拼接，不用模板字符串
- 分页统一用 `LIMIT :limit OFFSET :offset`
- 统一过滤查询用了动态 WHERE：每个参数可 null，传 null 跳过该条件

### DI 规则
- 数据库相关的单例（Database、DAO）在 DatabaseModule.kt 中 @Provides
- 接口↔实现绑定在 RepositoryModule.kt 中用 @Binds
- ViewModel 用 @HiltViewModel + @Inject constructor

## 关键文件索引

### 入口
| 文件 | 作用 |
|---|---|
| `MainActivity.kt` | App 入口 + Shizuku 权限请求 |
| `SillyBilibiliApp.kt` | @HiltAndroidApp 标记 |
| `di/DatabaseModule.kt` | 数据库 + DAO 提供 |
| `di/RepositoryModule.kt` | 接口→实现绑定 |

### 核心数据
| 文件 | 作用 |
|---|---|
| `domain/model/Video.kt` | 视频数据模型 |
| `domain/model/Category.kt` | 分类数据模型 |
| `domain/model/ConversionProgress.kt` | 转换进度消息 |
| `data/local/entity/VideoEntity.kt` | videos 表结构 |
| `data/local/entity/CategoryEntity.kt` | categories 表结构 |
| `data/local/dao/VideoDao.kt` | 视频 SQL（含核心统一过滤查询） |
| `data/local/dao/CategoryDao.kt` | 分类 SQL |
| `data/repository/VideoRepositoryImpl.kt` | 仓库实现 + Entity/Domain 映射 |
| `data/repository/CategoryRepositoryImpl.kt` | 分类仓库实现 + 视频计数 |

### 功能服务
| 文件 | 作用 |
|---|---|
| `service/VideoScanService.kt` | 两阶段扫描 B 站缓存目录 |
| `service/VideoConverterService.kt` | m4s→mp4 转换管线 |
| `service/ShellService.java` | Shizuku AIDL |
| `util/ShizukuFileHelper.kt` | Shell 命令文件操作（分块读取） |
| `service/SettingsService.kt` | SharedPreferences 封装 |

### UI
| 文件 | 作用 |
|---|---|
| `ui/pages/home/HomePage.kt` | 主页（LazyColumn + VideoCard） |
| `ui/pages/home/HomeViewModel.kt` | 主页 VM（combine + collectLatest + 分页） |
| `ui/components/VideoCard.kt` | 视频卡片（封面 + 标题 + 时长 + 大小） |
| `ui/components/FilterSheet.kt` | 过滤弹窗（分类/画质/时长/大小/方向） |
| `ui/components/SearchBar.kt` | 搜索栏 |
| `ui/navigation/AppNavHost.kt` | 导航路由 |
| `ui/theme/Theme.kt` | 深色主题配置 |

## Hermes Skills（项目级）

本项目绑定以下 Hermes skill，遇到对应场景时必须加载：

| Skill | 触发场景 |
|---|---|
| `systematic-debugging` | 任何 bug / 崩溃 / 测试失败 / 异常行为 — 先找根因再修 |
| `test-driven-development` | 新功能、bug 修复、重构 — RED-GREEN-REFACTOR，先写测试 |

加载方式：`skill_view(name='systematic-debugging')` 或 `skill_view(name='test-driven-development')`

## 版本控制与文档

- 每次影响用户功能、交互、权限、构建或使用步骤的改动，都必须在同一提交中更新 `README.md`。
- 默认开发分支为 `dev`。完成测试与 release APK 构建后，使用清晰的 Conventional Commit 信息提交，并推送到 `origin/dev`。
- 严禁提交 APK、keystore、`keystore.properties`、密码、令牌、真实缓存媒体或其他私密数据。
- release 签名信息只从本机忽略的 `keystore.properties` 读取；参照 `keystore.properties.example` 配置。

## 已知问题
1. `String.format()` 在 Kotlin 中报 Unresolved reference → 用 Kotlin 的 `"".format()` 替代
2. Gradle 构建需要网络下载依赖（首次构建慢）
3. Shizuku 需要在设备上启动并向本应用授权；不要求 Root
