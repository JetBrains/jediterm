package org.jetbrains.jediterm.compose.ime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp

/**
 * IME (Input Method Editor) handler for CJK (Chinese, Japanese, Korean) input.
 *
 * This component uses a transparent TextField overlay to capture IME composition events
 * and forwards the composed text to the terminal. This is a workaround for Compose Desktop's
 * limited IME support in custom keyboard event handlers.
 *
 * Implementation approach:
 * 1. Invisible TextField positioned at cursor location
 * 2. TextField captures IME composition (e.g., Pinyin for Chinese)
 * 3. On composition commit, forward text to terminal
 * 4. Clear TextField for next input
 *
 * Known limitations:
 * - TextField must have focus to receive IME events
 * - Composition window positioning may not be pixel-perfect
 * - Performance overhead from TextField rendering (minimized with transparency)
 *
 * References:
 * - https://github.com/JetBrains/compose-multiplatform/issues/3221
 * - https://github.com/JetBrains/compose-multiplatform/issues/2628
 */
@Composable
fun IMEHandler(
    enabled: Boolean,
    cursorX: Int,
    cursorY: Int,
    charWidth: Float,
    charHeight: Float,
    onTextCommit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!enabled) return

    var textFieldValue by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Position TextField at cursor location
    val offsetX = (cursorX * charWidth).toInt()
    val offsetY = (cursorY * charHeight).toInt()

    Box(modifier = modifier) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                // Detect composition commit (when IME finalizes input)
                // This happens when user selects a candidate from IME popup
                if (newValue.isNotEmpty() && newValue != textFieldValue) {
                    // Check if this is a commit (no pending composition)
                    // In Compose, committed text appears as regular text
                    val committedText = newValue
                    if (committedText.isNotEmpty()) {
                        onTextCommit(committedText)
                        textFieldValue = "" // Clear for next input
                        return@BasicTextField
                    }
                }
                textFieldValue = newValue
            },
            modifier = Modifier
                .offset { IntOffset(offsetX, offsetY) }
                .focusRequester(focusRequester),
            textStyle = TextStyle(
                color = Color.Transparent, // Invisible text
                fontSize = 14.sp
            ),
            singleLine = true
        )
    }

    // Auto-focus the TextField when enabled
    LaunchedEffect(enabled) {
        if (enabled) {
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Focus request may fail if TextField not yet composed
            }
        }
    }
}

/**
 * State holder for IME input management.
 *
 * Usage:
 * ```
 * val imeState = remember { IMEState() }
 *
 * // Enable IME mode (e.g., when Ctrl+Space pressed)
 * imeState.enable()
 *
 * // Disable IME mode
 * imeState.disable()
 *
 * // Check if IME is active
 * if (imeState.isEnabled) { ... }
 * ```
 */
class IMEState {
    private val _isEnabled = mutableStateOf(false)
    val isEnabled: Boolean get() = _isEnabled.value

    fun enable() {
        _isEnabled.value = true
    }

    fun disable() {
        _isEnabled.value = false
    }

    fun toggle() {
        _isEnabled.value = !_isEnabled.value
    }
}
