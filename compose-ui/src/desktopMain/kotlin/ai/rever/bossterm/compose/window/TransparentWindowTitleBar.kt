package ai.rever.bossterm.compose.window

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState

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
            // macOS-style traffic lights (close, minimize, maximize)
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

                // Maximize button (green)
                TrafficLightButton(
                    color = Color(0xFF28C840),
                    onClick = onMaximize
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
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Button is just a colored circle
    }
}
