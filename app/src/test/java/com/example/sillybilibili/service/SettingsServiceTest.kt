package com.example.sillybilibili.service

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test

class SettingsServiceTest {

    private val context = mockk<Context>()
    private val prefs = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>()

    private fun setupService(): SettingsService {
        every { context.getSharedPreferences("silly_bilibili_prefs", Context.MODE_PRIVATE) } returns prefs
        every { prefs.getString(any(), any()) } answers {
            secondArg() // return the default value (second arg)
        }
        every { prefs.getBoolean(any(), any()) } answers {
            secondArg<Boolean>() // return the default value
        }
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.clear() } returns editor
        every { editor.apply() } just Runs
        return SettingsService(context)
    }

    // ── scanPath ───────────────────────────────────────────────

    @Test
    fun `scanPath getter returns null by default`() {
        val service = setupService()
        assertNull(service.scanPath)
    }

    @Test
    fun `scanPath getter returns stored value`() {
        val service = setupService()
        every { prefs.getString("scan_path", null) } returns "/sdcard/bilibili"
        assertEquals("/sdcard/bilibili", service.scanPath)
    }

    @Test
    fun `scanPath setter writes to SharedPreferences`() {
        val service = setupService()
        service.scanPath = "/new/path"
        verify { editor.putString("scan_path", "/new/path") }
        verify { editor.apply() }
    }

    // ── outputPath ─────────────────────────────────────────────

    @Test
    fun `outputPath getter returns null by default`() {
        val service = setupService()
        assertNull(service.outputPath)
    }

    @Test
    fun `outputPath setter writes to SharedPreferences`() {
        val service = setupService()
        service.outputPath = "/output/mp4"
        verify { editor.putString("output_path", "/output/mp4") }
    }

    // ── autoScan ───────────────────────────────────────────────

    @Test
    fun `autoScan getter returns true by default`() {
        val service = setupService()
        assertTrue(service.autoScan)
    }

    @Test
    fun `autoScan getter returns stored value`() {
        val service = setupService()
        every { prefs.getBoolean("auto_scan", true) } returns false
        assertFalse(service.autoScan)
    }

    @Test
    fun `autoScan setter writes to SharedPreferences`() {
        val service = setupService()
        service.autoScan = false
        verify { editor.putBoolean("auto_scan", false) }
    }

    // ── clear ──────────────────────────────────────────────────

    @Test
    fun `clear calls editor clear and apply`() {
        val service = setupService()
        service.clear()
        verify { editor.clear() }
        verify { editor.apply() }
    }
}
