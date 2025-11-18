package org.jetbrains.jediterm.compose.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp

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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF2B2B2B))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Find...") },
            modifier = Modifier
                .weight(1f)
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
            singleLine = true
        )

        if (totalMatches > 0) {
            Text("$currentMatch / $totalMatches", color = Color.White)
        }

        Button(onClick = onFindPrevious, enabled = totalMatches > 0) {
            Text("↑")
        }

        Button(onClick = onFindNext, enabled = totalMatches > 0) {
            Text("↓")
        }

        Checkbox(
            checked = caseSensitive,
            onCheckedChange = { onCaseSensitiveToggle() }
        )
        Text("Case", color = Color.White)

        Button(onClick = onClose) {
            Text("✕")
        }
    }
}
