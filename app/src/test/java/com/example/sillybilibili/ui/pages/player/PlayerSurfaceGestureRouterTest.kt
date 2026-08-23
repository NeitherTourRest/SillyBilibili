package com.example.sillybilibili.ui.pages.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSurfaceGestureRouterTest {

    @Test
    fun `short movement remains a tap and does not select a drag axis`() {
        val router = PlayerSurfaceGestureRouter(touchSlopPx = 12f)

        assertEquals(PlayerSurfaceGestureAxis.NONE, router.dragBy(6f, 4f, isFullscreen = true))
    }

    @Test
    fun `horizontal movement selects seek before vertical movement`() {
        val router = PlayerSurfaceGestureRouter(touchSlopPx = 12f)

        assertEquals(PlayerSurfaceGestureAxis.HORIZONTAL, router.dragBy(18f, 5f, isFullscreen = true))
        assertEquals(PlayerSurfaceGestureAxis.HORIZONTAL, router.axis)
    }

    @Test
    fun `vertical movement only selects swipe switching in fullscreen`() {
        val router = PlayerSurfaceGestureRouter(touchSlopPx = 12f)

        assertEquals(PlayerSurfaceGestureAxis.NONE, router.dragBy(3f, 20f, isFullscreen = false))
        router.reset()
        assertEquals(PlayerSurfaceGestureAxis.VERTICAL, router.dragBy(3f, 20f, isFullscreen = true))
    }
}
