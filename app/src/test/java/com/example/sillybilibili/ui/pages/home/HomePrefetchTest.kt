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

    @Test
    fun `page only switches when the user reaches the final cards`() {
        assertFalse(shouldAdvanceHomePage(lastVisibleIndex = 37, loadedItemCount = 40, hasMoreData = true))
        assertTrue(shouldAdvanceHomePage(lastVisibleIndex = 38, loadedItemCount = 40, hasMoreData = true))
    }

    @Test
    fun `page never switches when there is no following page`() {
        assertFalse(shouldAdvanceHomePage(lastVisibleIndex = 39, loadedItemCount = 40, hasMoreData = false))
    }
}
