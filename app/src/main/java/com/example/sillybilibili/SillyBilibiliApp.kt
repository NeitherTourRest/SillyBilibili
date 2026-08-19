// ============================================================
// SillyBilibiliApp.kt — App 的 Application 类
// ============================================================
// Application 是 Android App 启动时第一个创建的类。
// @HiltAndroidApp 注解让 Hilt 在这里初始化依赖注入框架。
// 这个类本身没有额外逻辑，只是 Hilt 的入口。
// 被 AndroidManifest.xml 引用（android:name）→ 系统自动调用。
// ============================================================

package com.example.sillybilibili

// Application = Android App 的基类，系统在启动 App 时第一个创建它
import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
// @HiltAndroidApp = Hilt 注解，标记这是 Hilt 依赖注入的根入口
// 加了这行，Hilt 才会在整个 App 中生效
import dagger.hilt.android.HiltAndroidApp
import java.io.File

@HiltAndroidApp  // Hilt 依赖注入的初始化入口
class SillyBilibiliApp : Application(), ImageLoaderFactory {

    /**
     * 全局统一的图片加载配置：
     * - 本地封面是静态文件，关闭缓存头校验，避免重复回源校验
     * - 内存缓存 25% 堆、磁盘缓存 256MB，封面缓存目录被系统清理后能快速重建
     * - 全局 80ms 短淡入：列表滚动时新图不会长时间占住合成层
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .respectCacheHeaders(false)
            .crossfade(80)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .build()
}
