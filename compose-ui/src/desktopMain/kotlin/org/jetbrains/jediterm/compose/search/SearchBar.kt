package org.jetbrains.jediterm.compose.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

    // Auto-focus search input when opened
    LaunchedEffect(visible) {
        if (visible) {
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Focus request may fail
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        color = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search input field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        "Find in terminal...",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
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
                    fontSize = 14.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF4A90E2),
                    unfocusedBorderColor = Color(0xFF404040),
                    cursorColor = Color.White,
                    focusedContainerColor = Color(0xFF2B2B2B),
                    unfocusedContainerColor = Color(0xFF2B2B2B)
                )
            )

            // Match counter
            if (searchQuery.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (totalMatches > 0) Color(0xFF2D5F2D) else Color(0xFF5F2D2D)
                ) {
                    Text(
                        text = if (totalMatches > 0) "$currentMatch/$totalMatches" else "No matches",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Navigation buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Previous match button
                IconButton(
                    onClick = onFindPrevious,
                    enabled = totalMatches > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Previous match",
                        tint = if (totalMatches > 0) Color.White else Color.Gray
                    )
                }

                // Next match button
                IconButton(
                    onClick = onFindNext,
                    enabled = totalMatches > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Next match",
                        tint = if (totalMatches > 0) Color.White else Color.Gray
                    )
                }
            }

            // Case sensitive toggle
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (caseSensitive) Color(0xFF4A90E2) else Color(0xFF404040),
                modifier = Modifier
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Checkbox(
                        checked = caseSensitive,
                        onCheckedChange = { onCaseSensitiveToggle() },
                        modifier = Modifier.size(20.dp),
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color.White,
                            uncheckedColor = Color.Gray,
                            checkmarkColor = Color(0xFF1E1E1E)
                        )
                    )
                    Text(
                        "Match case",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }

            // Close button
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close search",
                    tint = Color.White
                )
            }
        }
    }
}
