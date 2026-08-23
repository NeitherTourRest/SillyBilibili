package com.example.sillybilibili.ui.pages.player

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class PlayerSurfaceTouchListenerTest {

    @Test
    fun `single tap delivered by the native player surface invokes controls callback`() {
        var tapCount = 0
        val context = RuntimeEnvironment.getApplication()
        val listener = PlayerSurfaceTouchListener(context) {
            callbacks(onTap = { tapCount++ })
        }
        val view = View(context)
        val start = SystemClock.uptimeMillis()

        listener.onTouch(view, motionEvent(start, start, MotionEvent.ACTION_DOWN))
        listener.onTouch(view, motionEvent(start, start + 40, MotionEvent.ACTION_UP))
        shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)

        assertEquals(1, tapCount)
    }

    private fun motionEvent(downTime: Long, eventTime: Long, action: Int): MotionEvent =
        MotionEvent.obtain(downTime, eventTime, action, 160f, 160f, 0)

    private fun callbacks(onTap: () -> Unit = {}): PlayerSurfaceGestureCallbacks =
        PlayerSurfaceGestureCallbacks(
            isFullscreen = false,
            onTap = onTap,
            onDoubleTap = {},
            onHorizontalStart = {},
            onHorizontalDrag = {},
            onHorizontalEnd = {},
            onHorizontalCancel = {},
            onVerticalStart = {},
            onVerticalDrag = {},
            onVerticalEnd = {},
            onVerticalCancel = {}
        )
}
