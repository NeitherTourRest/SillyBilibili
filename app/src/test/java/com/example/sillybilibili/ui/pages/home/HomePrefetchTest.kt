package com.example.sillybilibili.ui.pages.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePrefetchTest {
    @Test
    fun `large library loads the next batch before the user reaches the end`() {
        assertTrue(shouldPrefetchHomePage(lastVisibleIndex = 36, loadedItemCount = 48, hasMoreData = true))
    }

    @Test
    fun `large library does not request another batch while far from the end`() {
        assertFalse(shouldPrefetchHomePage(lastVisibleIndex = 35, loadedItemCount = 48, hasMoreData = true))
    }

    @Test
    fun `completed library never requests a further batch`() {
        assertFalse(shouldPrefetchHomePage(lastVisibleIndex = 47, loadedItemCount = 48, hasMoreData = false))
    }
}
