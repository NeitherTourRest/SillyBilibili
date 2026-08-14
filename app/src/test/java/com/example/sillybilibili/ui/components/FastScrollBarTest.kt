package com.example.sillybilibili.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FastScrollBarTest {

    @Test
    fun `fast scroll maps the track endpoints to valid first visible indexes`() {
        assertEquals(0, fastScrollTargetIndex(0f, itemCount = 100, visibleItemCount = 8))
        assertEquals(92, fastScrollTargetIndex(1f, itemCount = 100, visibleItemCount = 8))
    }

    @Test
    fun `fast scroll clamps an out of bounds touch position`() {
        assertEquals(0, fastScrollTargetIndex(-0.5f, itemCount = 20, visibleItemCount = 5))
        assertEquals(15, fastScrollTargetIndex(2f, itemCount = 20, visibleItemCount = 5))
    }
}
