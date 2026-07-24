package com.example.sillybilibili.ui.pages.home

import org.junit.Assert.*
import org.junit.Test

class FilterStateTest {

    @Test
    fun `FilterState default is inactive`() {
        val state = FilterState()
        assertFalse(state.isActive)
    }

    @Test
    fun `FilterState active when quality set`() {
        val state = FilterState(quality = "1080P")
        assertTrue(state.isActive)
    }

    @Test
    fun `FilterState active when orientation set`() {
        val state = FilterState(orientation = Orientation.PORTRAIT)
        assertTrue(state.isActive)
    }

    @Test
    fun `FilterState active when durationRange set`() {
        val state = FilterState(durationRange = DurationRange.UNDER_1MIN)
        assertTrue(state.isActive)
    }

    @Test
    fun `FilterState active when sizeRange set`() {
        val state = FilterState(sizeRange = SizeRange.UNDER_10MB)
        assertTrue(state.isActive)
    }

    @Test
    fun `FilterState active when timeRange set`() {
        val state = FilterState(timeRange = TimeRange.TODAY)
        assertTrue(state.isActive)
    }

    @Test
    fun `FilterState active when hasCover set`() {
        val state = FilterState(hasCover = true)
        assertTrue(state.isActive)
    }

    @Test
    fun `DurationRange maxMs null means unlimited`() {
        assertNull(DurationRange.OVER_30MIN.maxMs)
    }

    @Test
    fun `SizeRange maxBytes null means unlimited`() {
        assertNull(SizeRange.OVER_100MB.maxBytes)
    }

    @Test
    fun `TimeRange minusMs defines lookback window`() {
        assertEquals(86_400_000L, TimeRange.TODAY.minusMs)
        assertEquals(604_800_000L, TimeRange.WEEK.minusMs)
        assertEquals(2_592_000_000L, TimeRange.MONTH.minusMs)
    }

    @Test
    fun `Orientation values are distinct`() {
        assertNotEquals(Orientation.LANDSCAPE, Orientation.PORTRAIT)
    }

    @Test
    fun `FilterState copy preserves equality`() {
        val original = FilterState(quality = "720P", hasCover = false)
        val copy = original.copy()
        assertEquals(original, copy)
        assertTrue(original == copy)
    }

    @Test
    fun `FilterState copy with changes is not equal`() {
        val original = FilterState(quality = "720P")
        val modified = original.copy(quality = "1080P")
        assertNotEquals(original, modified)
    }
}
