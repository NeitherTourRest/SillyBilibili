package com.example.sillybilibili.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.sillybilibili.ui.theme.CyberVermilion
import com.example.sillybilibili.ui.theme.DarkDivider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A compact fast-scroll handle for long lazy lists. The invisible rail on the right can be
 * dragged even while the handle is hidden; ordinary scrolling only reveals it briefly.
 */
@Composable
fun FastScrollBar(
    listState: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    if (itemCount < 2) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val visibleItems = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    val maxFirstIndex = (itemCount - visibleItems).coerceAtLeast(1)
    val progress = (listState.firstVisibleItemIndex.toFloat() / maxFirstIndex).coerceIn(0f, 1f)
    val thumbFraction = (visibleItems.toFloat() / itemCount).coerceIn(0.08f, 0.34f)
    var trackHeightPx by remember { mutableIntStateOf(0) }
    var railActive by remember { mutableStateOf(false) }
    val thumbHeightPx = (trackHeightPx * thumbFraction)
        .coerceAtLeast(with(density) { 36.dp.toPx() })
        .coerceAtMost(trackHeightPx.toFloat())
    val thumbOffsetPx = (progress * (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)).roundToInt()

    LaunchedEffect(railActive, listState.isScrollInProgress) {
        if (railActive && !listState.isScrollInProgress) {
            delay(900)
            railActive = false
        }
    }

    fun scrollTo(positionY: Float) {
        if (trackHeightPx <= 0) return
        val target = fastScrollTargetIndex(
            positionFraction = positionY / trackHeightPx,
            itemCount = itemCount,
            visibleItemCount = visibleItems
        )
        scope.launch { listState.scrollToItem(target) }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(26.dp)
            .onSizeChanged { trackHeightPx = it.height }
            .pointerInput(itemCount, visibleItems, trackHeightPx) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        railActive = true
                        scrollTo(offset.y)
                    },
                    onVerticalDrag = { pointerChange, _ ->
                        railActive = true
                        scrollTo(pointerChange.position.y)
                    }
                )
            }
    ) {
        AnimatedVisibility(
            visible = listState.isScrollInProgress || railActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(DarkDivider.copy(alpha = 0.48f))
                )
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(0, thumbOffsetPx) }
                        .width(5.dp)
                        .height(with(density) { thumbHeightPx.toDp() })
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(CyberVermilion.copy(alpha = 0.9f))
                )
            }
        }
    }
}

internal fun fastScrollTargetIndex(
    positionFraction: Float,
    itemCount: Int,
    visibleItemCount: Int
): Int {
    val maxFirstIndex = (itemCount - visibleItemCount).coerceAtLeast(0)
    return (positionFraction.coerceIn(0f, 1f) * maxFirstIndex).roundToInt()
}
