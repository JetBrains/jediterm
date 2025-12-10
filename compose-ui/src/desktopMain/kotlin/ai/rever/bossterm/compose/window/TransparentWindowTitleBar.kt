package ai.rever.bossterm.compose.window

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import java.awt.event.InputEvent

/**
 * Custom title bar for undecorated windows using WindowDraggableArea.
 * Provides macOS-style traffic light buttons and drag-to-move functionality.
 *
 * Must be called within a WindowScope (inside Window composable).
 */
@Composable
fun WindowScope.CustomTitleBar(
    title: String,
    windowState: WindowState,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onFullscreen: () -> Unit,
    onMaximize: () -> Unit,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    WindowDraggableArea(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // macOS-style traffic lights (close, minimize, maximize/fullscreen)
            // Track group hover state - all icons show when any button is hovered
            val groupInteractionSource = remember { MutableInteractionSource() }
            val isGroupHovered by groupInteractionSource.collectIsHoveredAsState()

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),  // ~3.5px margin between 12px buttons
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.hoverable(interactionSource = groupInteractionSource)
            ) {
                // Close button (red)
                CloseButton(
                    isGroupHovered = isGroupHovered,
                    onClick = onClose
                )

                // Minimize button (yellow)
                MinimizeButton(
                    isGroupHovered = isGroupHovered,
                    onClick = onMinimize
                )

                // Fullscreen/Maximize button (green)
                // Click = Fullscreen, Option+Click = Maximize (macOS behavior)
                FullscreenButton(
                    isGroupHovered = isGroupHovered,
                    onFullscreen = onFullscreen,
                    onMaximize = onMaximize
                )
            }

            // Title in center
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Spacer to balance the traffic lights
            Spacer(modifier = Modifier.width(72.dp))
        }
    }
}

// macOS traffic light colors (from official specs)
private object TrafficLightColors {
    // Close button (red) - with transparency for glass effect
    val closeDefault = Color(0xFFFF6159)
    val closeHover = Color(0xFFBF4942)
    val closeIcon = Color(0xFF4D0000)
    val closeBorder = Color(0x33000000)

    // Minimize button (yellow)
    val minimizeDefault = Color(0xFFFFBD2E)
    val minimizeHover = Color(0xFFBF8E22)
    val minimizeIcon = Color(0xFF995700)
    val minimizeBorder = Color(0x33000000)

    // Maximize button (green)
    val maximizeDefault = Color(0xFF28C941)
    val maximizeHover = Color(0xFF1D9730)
    val maximizeIcon = Color(0xFF006500)
    val maximizeBorder = Color(0x33000000)
}

/**
 * Close button (red) with × icon on hover.
 * Icon: two 8px × 1px lines at 45° angles
 */
@Composable
private fun CloseButton(
    isGroupHovered: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isGroupHovered) TrafficLightColors.closeHover else TrafficLightColors.closeDefault

    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(0.5.dp, TrafficLightColors.closeBorder, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        if (isGroupHovered) {
            Canvas(modifier = Modifier.size(8.dp)) {
                val strokeWidth = 1.dp.toPx()
                // Draw × icon - two diagonal lines
                drawLine(
                    color = TrafficLightColors.closeIcon,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = TrafficLightColors.closeIcon,
                    start = Offset(size.width, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * Minimize button (yellow) with − icon on hover.
 * Icon: one 8px × 1px horizontal line
 */
@Composable
private fun MinimizeButton(
    isGroupHovered: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isGroupHovered) TrafficLightColors.minimizeHover else TrafficLightColors.minimizeDefault

    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(0.5.dp, TrafficLightColors.minimizeBorder, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        if (isGroupHovered) {
            Canvas(modifier = Modifier.size(8.dp)) {
                val strokeWidth = 1.dp.toPx()
                // Draw − icon - horizontal line
                drawLine(
                    color = TrafficLightColors.minimizeIcon,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * Fullscreen button (green) with diagonal arrows icon on hover.
 * - Click = Fullscreen
 * - Option+Click = Maximize (zoom)
 * Icon: two triangular arrows pointing to opposite corners
 */
@Composable
private fun FullscreenButton(
    isGroupHovered: Boolean,
    onFullscreen: () -> Unit,
    onMaximize: () -> Unit
) {
    val bgColor = if (isGroupHovered) TrafficLightColors.maximizeHover else TrafficLightColors.maximizeDefault

    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(0.5.dp, TrafficLightColors.maximizeBorder, CircleShape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                            // Check for Option/Alt key
                            val nativeEvent = event.nativeEvent
                            val hasOptionModifier = if (nativeEvent is java.awt.event.MouseEvent) {
                                (nativeEvent.modifiersEx and InputEvent.ALT_DOWN_MASK) != 0
                            } else {
                                false
                            }

                            if (hasOptionModifier) {
                                onMaximize()
                            } else {
                                onFullscreen()
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isGroupHovered) {
            Canvas(modifier = Modifier.size(8.dp)) {
                val strokeWidth = 1.dp.toPx()
                val iconColor = TrafficLightColors.maximizeIcon

                // Draw two small triangular arrows pointing to opposite corners
                // Top-right arrow (↗)
                val trX = size.width
                val trY = 0f
                // Arrow stem
                drawLine(
                    color = iconColor,
                    start = Offset(size.width * 0.35f, size.height * 0.65f),
                    end = Offset(trX, trY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                // Arrow head - horizontal part
                drawLine(
                    color = iconColor,
                    start = Offset(trX, trY),
                    end = Offset(trX - size.width * 0.35f, trY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                // Arrow head - vertical part
                drawLine(
                    color = iconColor,
                    start = Offset(trX, trY),
                    end = Offset(trX, trY + size.height * 0.35f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                // Bottom-left arrow (↙)
                val blX = 0f
                val blY = size.height
                // Arrow stem
                drawLine(
                    color = iconColor,
                    start = Offset(size.width * 0.65f, size.height * 0.35f),
                    end = Offset(blX, blY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                // Arrow head - horizontal part
                drawLine(
                    color = iconColor,
                    start = Offset(blX, blY),
                    end = Offset(blX + size.width * 0.35f, blY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                // Arrow head - vertical part
                drawLine(
                    color = iconColor,
                    start = Offset(blX, blY),
                    end = Offset(blX, blY - size.height * 0.35f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
