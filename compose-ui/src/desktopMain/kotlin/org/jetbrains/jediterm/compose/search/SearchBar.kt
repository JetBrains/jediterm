package org.jetbrains.jediterm.compose.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compact search bar positioned in top-right corner (VS Code style).
 */
@Composable
fun SearchBar(
    visible: Boolean,
    searchQuery: String,
    currentMatch: Int,
    totalMatches: Int,
    caseSensitive: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onFindNext: () -> Unit,
    onFindPrevious: () -> Unit,
    onCaseSensitiveToggle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val focusRequester = remember { FocusRequester() }

    // Request focus when search bar becomes visible
    LaunchedEffect(visible) {
        if (visible) {
            focusRequester.requestFocus()
        }
    }

    // Wrap in a Box that consumes ALL pointer events to prevent pass-through
    Box(
        modifier = modifier
            .widthIn(max = 320.dp)
            .padding(4.dp)
            // Block pointer events from passing through to elements below
            // We handle this at Main pass - children (buttons) process first, then we consume
            // to prevent propagation to terminal below
            .pointerInput(visible) {  // Key on visible to maintain stable coroutine
                awaitPointerEventScope {
                    while (true) {
                        // Main pass: children process first, then we consume remaining events
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        event.changes.forEach { change ->
                            // Only consume if not already consumed by a child (button)
                            if (!change.isConsumed) {
                                change.consume()
                            }
                        }
                    }
                }
            }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                // Secondary event absorption - backup for rapid clicks
                .pointerInput(visible) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            event.changes.forEach { change ->
                                if (!change.isConsumed) {
                                    change.consume()
                                }
                            }
                        }
                    }
                },
            color = Color(0xFF252526),
            shape = RoundedCornerShape(4.dp),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Search input field - compact
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .background(Color(0xFF3C3C3C), RoundedCornerShape(2.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when {
                                    event.key == Key.Enter && event.isShiftPressed -> {
                                        onFindPrevious()
                                        true
                                    }
                                    event.key == Key.Enter -> {
                                        onFindNext()
                                        true
                                    }
                                    event.key == Key.Escape -> {
                                        onClose()
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 13.sp
                    ),
                    cursorBrush = SolidColor(Color.White),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                "Find",
                                color = Color(0xFF808080),
                                fontSize = 13.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }

            // Match counter - compact
            if (searchQuery.isNotEmpty()) {
                Text(
                    text = if (totalMatches > 0) "$currentMatch/$totalMatches" else "0/0",
                    color = if (totalMatches > 0) Color(0xFFCCCCCC) else Color(0xFFFF6B6B),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Navigation and action buttons - slightly larger for easier clicking
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Previous match
                IconButton(
                    onClick = onFindPrevious,
                    enabled = totalMatches > 0,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Previous",
                        tint = if (totalMatches > 0) Color(0xFFCCCCCC) else Color(0xFF606060),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Next match
                IconButton(
                    onClick = onFindNext,
                    enabled = totalMatches > 0,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Next",
                        tint = if (totalMatches > 0) Color(0xFFCCCCCC) else Color(0xFF606060),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Case sensitive toggle - compact icon button
                IconButton(
                    onClick = onCaseSensitiveToggle,
                    modifier = Modifier.size(28.dp)
                ) {
                    Text(
                        "Aa",
                        color = if (caseSensitive) Color(0xFF4A90E2) else Color(0xFF808080),
                        fontSize = 12.sp,
                        fontWeight = if (caseSensitive) FontWeight.Bold else FontWeight.Normal
                    )
                }

                // Close button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFFCCCCCC),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        }
    }
}
