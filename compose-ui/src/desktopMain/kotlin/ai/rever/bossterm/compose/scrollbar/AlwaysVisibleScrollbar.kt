package ai.rever.bossterm.compose.scrollbar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.v2.ScrollbarAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val AUTO_HIDE_DELAY_MS = 1500L  // Hide after 1.5 seconds of inactivity

/**
 * Custom scrollbar with auto-hide behavior.
 * Shows when scrolling, hovered, or dragging; hides smoothly after inactivity.
 *
 * @param adapter ScrollbarAdapter that provides scroll position information
 * @param redrawTrigger State that changes when terminal content updates (for buffer changes)
 * @param modifier Modifier to be applied to the scrollbar container
 * @param thickness Width of the scrollbar in Dp
 * @param thumbColor Color of the scrollbar thumb
 * @param trackColor Color of the scrollbar track background
 * @param minThumbHeight Minimum height of the thumb in Dp
 * @param matchPositions Normalized [0, 1] positions of search matches for scrollbar markers
 * @param currentMatchIndex Index of the current match (-1 if none)
 * @param matchMarkerColor Color for regular match markers (default: yellow)
 * @param currentMatchMarkerColor Color for current match marker (default: orange)
 * @param onMatchClicked Callback when a match marker is clicked (receives match index)
 */
@Composable
fun AlwaysVisibleScrollbar(
    adapter: ScrollbarAdapter,
    redrawTrigger: State<Int>,
    modifier: Modifier = Modifier,
    thickness: Dp = 10.dp,
    thumbColor: Color = Color.White,
    trackColor: Color = Color.White.copy(alpha = 0.12f),
    minThumbHeight: Dp = 32.dp,
    matchPositions: List<Float> = emptyList(),
    currentMatchIndex: Int = -1,
    matchMarkerColor: Color = Color(0xFFFFFF00),
    currentMatchMarkerColor: Color = Color(0xFFFF6600),
    onMatchClicked: ((Int) -> Unit)? = null,
    userScrollTrigger: State<Int> = mutableStateOf(0)
) {
    var containerHeight by remember { mutableStateOf(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val scope = rememberCoroutineScope()

    // Auto-hide state tracking
    var isVisible by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragStartScrollOffset by remember { mutableStateOf(0.0) }
    var accumulatedDrag by remember { mutableStateOf(0.0) }

    // Read scroll state directly for zero-lag scroll updates
    // Reading redrawTrigger.value ensures we recompose when terminal content changes (new lines added)
    val scrollOffset = adapter.scrollOffset
    // v2 API: maxScrollOffset = contentSize - viewportSize
    val maxScroll = if (containerHeight > 0f && redrawTrigger.value >= 0) {
        (adapter.contentSize - adapter.viewportSize).coerceAtLeast(0.0)
    } else {
        0.0
    }

    // Detect user-initiated scroll activity and show scrollbar
    // Only triggers on manual scroll (mouse wheel), not on content updates
    LaunchedEffect(userScrollTrigger.value) {
        if (userScrollTrigger.value > 0) {
            isVisible = true
        }
    }

    // Auto-hide timer: hide after inactivity threshold
    LaunchedEffect(isVisible, isHovered, isDragging) {
        if (isVisible && !isHovered && !isDragging) {
            delay(AUTO_HIDE_DELAY_MS)
            // Recheck conditions after delay (might have changed)
            if (!isHovered && !isDragging) {
                isVisible = false
            }
        }
    }

    // Calculate target alpha based on visibility state
    val shouldShow = isVisible || isHovered || isDragging
    val targetAlpha = if (shouldShow) 1f else 0f
    val scrollbarAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 300)
    )

    // Calculate thumb opacity based on hover state (extra brightness when hovered)
    val thumbAlpha = if (isHovered) 1.0f else 0.8f

    // Always render container Box for size measurement
    Box(
        modifier = modifier
            .width(thickness)
            .fillMaxHeight()
            .onSizeChanged { containerHeight = it.height.toFloat() }
            .alpha(scrollbarAlpha)
    ) {
        // Only render visible scrollbar when there's content to scroll
        if (containerHeight > 0f && maxScroll > 0.0) {
            // Calculate thumb dimensions
            val thumbHeightPx = run {
                val visibleRatio = containerHeight / (containerHeight + maxScroll)
                max(
                    with(LocalDensity.current) { minThumbHeight.toPx() }.toDouble(),
                    containerHeight * visibleRatio
                )
            }
            val thumbOffsetPx = run {
                val scrollableHeight = containerHeight - thumbHeightPx
                (scrollOffset / maxScroll) * scrollableHeight
            }

            // Track - handles all pointer events (tap and drag)
            Box(
                modifier = Modifier
                    .width(thickness)
                    .fillMaxHeight()
                    .background(Color.Transparent, shape = RoundedCornerShape(4.dp))
                    .hoverable(interactionSource)
                    // Drag gesture for scrolling - works anywhere on track
                    .pointerInput(maxScroll, containerHeight, thumbHeightPx) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                isDragging = true
                                isVisible = true
                                dragStartScrollOffset = adapter.scrollOffset
                                accumulatedDrag = 0.0
                            },
                            onDragEnd = {
                                isDragging = false
                                accumulatedDrag = 0.0
                            },
                            onDragCancel = {
                                isDragging = false
                                accumulatedDrag = 0.0
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()

                                if (maxScroll > 0.0 && containerHeight > 0f) {
                                    accumulatedDrag += dragAmount.y
                                    val scrollableHeight = containerHeight - thumbHeightPx
                                    if (scrollableHeight > 0.0) {
                                        // Convert drag pixels to scroll offset
                                        val dragRatio = accumulatedDrag / scrollableHeight
                                        val newScrollOffset = (dragStartScrollOffset + dragRatio * maxScroll).coerceIn(0.0, maxScroll)

                                        scope.launch {
                                            adapter.scrollTo(newScrollOffset)
                                        }
                                    }
                                }
                            }
                        )
                    }
                    // Tap gesture for click-to-position
                    .pointerInput(matchPositions, onMatchClicked) {
                        detectTapGestures { offset ->
                            if (containerHeight > 0f) {
                                val clickedPosition = offset.y / containerHeight

                                // Check if clicking on a match marker (with click-to-jump enabled)
                                if (matchPositions.isNotEmpty() && onMatchClicked != null) {
                                    // Find closest match within a tolerance (5% of track height)
                                    val tolerance = 0.05f
                                    val closestMatchIndex = matchPositions.indices.minByOrNull {
                                        abs(matchPositions[it] - clickedPosition)
                                    }
                                    if (closestMatchIndex != null &&
                                        abs(matchPositions[closestMatchIndex] - clickedPosition) <= tolerance) {
                                        onMatchClicked(closestMatchIndex)
                                        return@detectTapGestures
                                    }
                                }

                                // Default: scroll to clicked position
                                if (maxScroll > 0.0) {
                                    val targetScroll = clickedPosition * maxScroll
                                    scope.launch {
                                        adapter.scrollTo(targetScroll)
                                    }
                                }
                            }
                        }
                    }
            ) {
                // Draw match markers on the track
                if (matchPositions.isNotEmpty()) {
                    val density = LocalDensity.current
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val markerHeightPx = max(
                            with(density) { 2.dp.toPx() },
                            size.height / matchPositions.size.coerceAtLeast(1)
                        ).coerceAtMost(with(density) { 4.dp.toPx() }) // Cap at 4dp for aesthetics

                        matchPositions.forEachIndexed { index, position ->
                            val y = position * size.height
                            val color = if (index == currentMatchIndex) {
                                currentMatchMarkerColor
                            } else {
                                matchMarkerColor
                            }
                            drawRect(
                                color = color,
                                topLeft = Offset(0f, y - markerHeightPx / 2),
                                size = Size(size.width, markerHeightPx)
                            )
                        }
                    }
                }

                // Thumb (visual only - track handles all pointer events)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, thumbOffsetPx.toInt()) }
                        .width(thickness)
                        .height(with(LocalDensity.current) { thumbHeightPx.toFloat().toDp() })
                        .padding(2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(thumbColor.copy(alpha = thumbAlpha))
                )
            }
        }
    }
}
