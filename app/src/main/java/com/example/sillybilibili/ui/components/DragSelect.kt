package com.example.sillybilibili.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 拖拽连续选择（多选模式下的类 Google Photos 体验）：
 * 手指按住任意卡片后开始拖动，本修饰符不消费任何事件，因此列表滚动照常工作；
 * 指针扫过的卡片与起始卡片之间的区间会被连续选中，滚动时选区跟随指针。
 * 仅当按下位置落在某张卡片上且位移超过 touch slop 后才激活，单点点击不受影响。
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.rangeSelectDrag(
    enabled: Boolean,
    listState: LazyListState,
    onRangeSelect: (anchorIndex: Int, currentIndex: Int) -> Unit
): Modifier {
    if (!enabled) return this
    return this.pointerInput(listState) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val anchorIndex = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                down.position.y >= it.offset && down.position.y < it.offset + it.size
            }?.index ?: -1
            var active = anchorIndex >= 0
            var lastCurrent = -1
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (active) {
                    val moved = (change.position - down.position).getDistance()
                    if (moved > viewConfiguration.touchSlop) {
                        val current = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                            change.position.y >= it.offset && change.position.y < it.offset + it.size
                        }?.index ?: anchorIndex
                        if (current != lastCurrent) {
                            lastCurrent = current
                            onRangeSelect(anchorIndex, current)
                        }
                    }
                }
            }
        }
    }
}