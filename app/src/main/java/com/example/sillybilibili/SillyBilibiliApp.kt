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
// @HiltAndroidApp = Hilt 注解，标记这是 Hilt 依赖注入的根入口
// 加了这行，Hilt 才会在整个 App 中生效
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp  // Hilt 依赖注入的初始化入口
class SillyBilibiliApp : Application()
