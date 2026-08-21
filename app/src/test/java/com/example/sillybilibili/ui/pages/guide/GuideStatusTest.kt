package com.example.sillybilibili.ui.pages.guide

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideStatusTest {
    @Test
    fun `shizuku guide feedback reflects the checked connection state`() {
        assertEquals("已连接 · 扫描页可使用 Shizuku 访问缓存", shizukuGuideStatusText(true))
        assertEquals("未连接 · 可在 Shizuku 中启动服务后重新检查", shizukuGuideStatusText(false))
    }
}
