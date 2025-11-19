package org.jetbrains.jediterm.compose.scrollbar

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ScrollbarAdapter
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Custom scrollbar that is always visible (no auto-hide behavior).
 * Designed to replace Compose Desktop's VerticalScrollbar which hides when not hovered.
 *
 * @param adapter ScrollbarAdapter that provides scroll position information
 * @param modifier Modifier to be applied to the scrollbar container
 * @param thickness Width of the scrollbar in Dp
 * @param thumbColor Color of the scrollbar thumb
 * @param trackColor Color of the scrollbar track background
 * @param minThumbHeight Minimum height of the thumb in Dp
 */
@Composable
fun AlwaysVisibleScrollbar(
    adapter: ScrollbarAdapter,
    modifier: Modifier = Modifier,
    thickness: Dp = 12.dp,
    thumbColor: Color = Color.White,
    trackColor: Color = Color.White.copy(alpha = 0.12f),
    minThumbHeight: Dp = 32.dp
) {
    var containerHeight by remember { mutableStateOf(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val scope = rememberCoroutineScope()

    // State to force recomposition when scroll position changes
    var scrollUpdateTrigger by remember { mutableStateOf(0) }

    // Observe scroll changes reactively (only when values actually change)
    LaunchedEffect(adapter, containerHeight) {
        snapshotFlow {
            val containerSize = containerHeight.toInt()
            if (containerSize > 0) {
                adapter.scrollOffset to adapter.maxScrollOffset(containerSize)
            } else {
                0.0 to 0.0
            }
        }.collect { (scrollOffset, maxScroll) ->
            // Trigger recomposition only when scroll state actually changes
            scrollUpdateTrigger++
        }
    }

    // Calculate thumb opacity based on hover state
    val thumbAlpha = if (isHovered) 1.0f else 0.8f

    // Calculate current scroll state
    val containerSize = containerHeight.toInt()
    val maxScroll = if (containerSize > 0) adapter.maxScrollOffset(containerSize) else 0f

    // Always render container Box for size measurement
    Box(
        modifier = modifier
            .width(thickness)
            .fillMaxHeight()
            .onSizeChanged { containerHeight = it.height.toFloat() }
    ) {
        // Only render visible scrollbar when there's content to scroll
        if (scrollUpdateTrigger >= 0 && containerHeight > 0f && maxScroll > 0f) {
            // Track background
            Box(
                modifier = Modifier
                    .width(thickness)
                    .fillMaxHeight()
                    .background(Color.Transparent, shape = RoundedCornerShape(4.dp))
                    .hoverable(interactionSource)
                    .pointerInput(Unit) {
                        // Handle clicks on track to jump to position
                        detectTapGestures { offset ->
                            if (maxScroll > 0f && containerHeight > 0f) {
                                val targetScroll = (offset.y / containerHeight) * maxScroll
                                scope.launch {
                                    adapter.scrollTo(containerSize, targetScroll)
                                }
                            }
                        }
                    }
            )

            // Thumb
            val scrollOffset = adapter.scrollOffset
            val thumbHeightPx = run {
                val visibleRatio = containerHeight / (containerHeight + maxScroll)
                max(
                    with(LocalDensity.current) { minThumbHeight.toPx() },
                    containerHeight * visibleRatio
                )
            }
            val thumbOffsetPx = run {
                val scrollableHeight = containerHeight - thumbHeightPx
                (scrollOffset / maxScroll) * scrollableHeight
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                    .width(thickness)
                    .height(with(LocalDensity.current) { thumbHeightPx.toDp() })
                    .padding(2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(thumbColor.copy(alpha = thumbAlpha))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()

                                if (maxScroll > 0f && containerHeight > 0f) {
                                    val currentThumbOffset = thumbOffsetPx + dragAmount.y
                                    val scrollableHeight = containerHeight - thumbHeightPx
                                    if (scrollableHeight > 0f) {
                                        val scrollRatio = currentThumbOffset / scrollableHeight
                                        val newScrollOffset = (scrollRatio * maxScroll).coerceIn(0f, maxScroll)

                                        scope.launch {
                                            adapter.scrollTo(containerSize, newScrollOffset)
                                        }
                                    }
                                }
                            }
                        )
                    }
            )
        }
    }
}
