package com.example.sillybilibili.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

private data class VisibleItem(val index: Int, val top: Int, val bottom: Int)

/**
 * 拖拽连续选择（多选模式下的类 Google Photos 体验，列表视图）：
 * 手指按住任意卡片后开始拖动，本修饰符不消费任何事件，因此列表滚动照常工作；
 * 指针扫过的卡片与起始卡片之间的区间会被连续选中，滚动时选区跟随指针。
 * 仅当按下位置落在某张卡片上且位移超过 touch slop 后才激活，单点点击不受影响。
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.rangeSelectDrag(
    enabled: Boolean,
    listState: LazyListState,
    onRangeSelect: (anchorIndex: Int, currentIndex: Int) -> Unit
): Modifier = rangeSelectDragImpl(
    enabled = enabled,
    key = listState,
    items = { listState.layoutInfo.visibleItemsInfo.map { VisibleItem(it.index, it.offset, it.offset + it.size) } },
    onRangeSelect = onRangeSelect
)

/** 拖拽连续选择（宫格视图），行为与列表视图一致。 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.rangeSelectDrag(
    enabled: Boolean,
    gridState: LazyGridState,
    onRangeSelect: (anchorIndex: Int, currentIndex: Int) -> Unit
): Modifier = rangeSelectDragImpl(
    enabled = enabled,
    key = gridState,
    items = { gridState.layoutInfo.visibleItemsInfo.map { VisibleItem(it.index, it.offset.y, it.offset.y + it.size.height) } },
    onRangeSelect = onRangeSelect
)

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.rangeSelectDragImpl(
    enabled: Boolean,
    key: Any,
    items: () -> List<VisibleItem>,
    onRangeSelect: (anchorIndex: Int, currentIndex: Int) -> Unit
): Modifier {
    if (!enabled) return this
    return this.pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val anchorIndex = items().firstOrNull {
                down.position.y >= it.top && down.position.y < it.bottom
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
                        val current = items().firstOrNull {
                            change.position.y >= it.top && change.position.y < it.bottom
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