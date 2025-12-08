package ai.rever.bossterm.compose.splits

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Cursor

/**
 * Visual divider thickness (the visible line).
 */
val DIVIDER_THICKNESS = 1.dp

/**
 * Drag hit area size (invisible overlay for easier grabbing).
 */
private val DRAG_AREA_SIZE = 16.dp

/**
 * The thin visible divider line between split panes.
 * This is what users see - a 1dp line.
 */
@Composable
fun SplitDividerLine(
    orientation: SplitOrientation,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .then(
                when (orientation) {
                    SplitOrientation.HORIZONTAL -> Modifier.fillMaxWidth().height(DIVIDER_THICKNESS)
                    SplitOrientation.VERTICAL -> Modifier.fillMaxHeight().width(DIVIDER_THICKNESS)
                }
            )
            .background(Color(0xFF2D2D2D))
    )
}

/**
 * The invisible drag target overlay.
 * This is positioned absolutely over the divider for a larger hit area.
 *
 * Uses cumulative drag tracking to avoid recomposition issues during drag.
 *
 * @param orientation The orientation of the split
 * @param currentRatio The current split ratio (used to capture start ratio)
 * @param availableSize The available size in pixels for ratio calculation
 * @param minRatio Minimum ratio when resizing (default 0.1 = 10%)
 * @param onRatioChange Called with the new ratio during drag
 * @param size The size of the drag area (default 16dp)
 */
@Composable
fun SplitDragHandle(
    orientation: SplitOrientation,
    currentRatio: Float,
    availableSize: Float,
    onRatioChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    minRatio: Float = 0.1f,
    size: Dp = DRAG_AREA_SIZE
) {
    val cursor = when (orientation) {
        SplitOrientation.HORIZONTAL -> PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR))
        SplitOrientation.VERTICAL -> PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))
    }

    // Track the starting ratio when drag begins
    var startRatio by remember { mutableFloatStateOf(currentRatio) }
    var cumulativeDelta by remember { mutableFloatStateOf(0f) }

    // Calculate max ratio based on min (ensures both panes respect minimum)
    val maxRatio = 1f - minRatio

    Box(
        modifier = modifier
            .then(
                when (orientation) {
                    SplitOrientation.HORIZONTAL -> Modifier.fillMaxWidth().height(size)
                    SplitOrientation.VERTICAL -> Modifier.fillMaxHeight().width(size)
                }
            )
            .alpha(0f) // Invisible
            .pointerHoverIcon(cursor)
            .pointerInput(orientation) {
                detectDragGestures(
                    onDragStart = {
                        // Capture the ratio at drag start
                        startRatio = currentRatio
                        cumulativeDelta = 0f
                    },
                    onDragEnd = {
                        cumulativeDelta = 0f
                    },
                    onDragCancel = {
                        cumulativeDelta = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val delta = when (orientation) {
                            SplitOrientation.HORIZONTAL -> dragAmount.y
                            SplitOrientation.VERTICAL -> dragAmount.x
                        }
                        cumulativeDelta += delta

                        // Calculate new ratio from start ratio + cumulative delta
                        val deltaRatio = cumulativeDelta / availableSize
                        val newRatio = (startRatio + deltaRatio).coerceIn(minRatio, maxRatio)
                        onRatioChange(newRatio)
                    }
                )
            }
    )
}

/**
 * Legacy single-component divider for simpler use cases.
 */
@Composable
fun SplitDivider(
    orientation: SplitOrientation,
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val cursor = when (orientation) {
        SplitOrientation.HORIZONTAL -> PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR))
        SplitOrientation.VERTICAL -> PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))
    }

    Box(
        modifier = modifier
            .then(
                when (orientation) {
                    SplitOrientation.HORIZONTAL -> Modifier.fillMaxWidth().height(DIVIDER_THICKNESS)
                    SplitOrientation.VERTICAL -> Modifier.fillMaxHeight().width(DIVIDER_THICKNESS)
                }
            )
            .background(Color(0xFF2D2D2D))
            .pointerHoverIcon(cursor)
            .pointerInput(orientation) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val delta = when (orientation) {
                        SplitOrientation.HORIZONTAL -> dragAmount.y
                        SplitOrientation.VERTICAL -> dragAmount.x
                    }
                    onDrag(delta)
                }
            }
    )
}
