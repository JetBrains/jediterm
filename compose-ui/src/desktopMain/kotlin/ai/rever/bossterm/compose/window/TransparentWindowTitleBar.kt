package ai.rever.bossterm.compose.window

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Close button (red)
                TrafficLightButton(
                    color = Color(0xFFFF5F57),
                    onClick = onClose
                )

                // Minimize button (yellow)
                TrafficLightButton(
                    color = Color(0xFFFFBD2E),
                    onClick = onMinimize
                )

                // Fullscreen/Maximize button (green)
                // Click = Fullscreen, Option+Click = Maximize (macOS behavior)
                GreenTrafficLightButton(
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

/**
 * macOS-style traffic light button with hover effect.
 */
@Composable
private fun TrafficLightButton(
    color: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(
                if (isHovered) color else color.copy(alpha = 0.85f)
            )
            .hoverable(interactionSource)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Button is just a colored circle
    }
}

/**
 * Green traffic light button with macOS behavior:
 * - Click = Fullscreen
 * - Option+Click = Maximize (zoom)
 */
@Composable
private fun GreenTrafficLightButton(
    onFullscreen: () -> Unit,
    onMaximize: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val color = Color(0xFF28C840)

    // Track if Option key is pressed
    var isOptionPressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(
                if (isHovered) color else color.copy(alpha = 0.85f)
            )
            .hoverable(interactionSource)
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
        // Button is just a colored circle
    }
}
