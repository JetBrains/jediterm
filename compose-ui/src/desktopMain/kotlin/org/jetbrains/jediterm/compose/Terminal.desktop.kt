package org.jetbrains.jediterm.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jediterm.compose.demo.SimpleTerminal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
actual fun Terminal(
    state: TerminalState,
    controller: TerminalController,
    modifier: Modifier,
    onTitleChange: ((String?) -> Unit)?,
    onBell: (() -> Unit)?
) {
    // For now, just use SimpleTerminal
    SimpleTerminal(modifier = modifier)
}

@Composable
actual fun Terminal(
    command: String,
    arguments: List<String>,
    environment: Map<String, String>,
    config: TerminalState.TerminalConfig,
    modifier: Modifier,
    onTitleChange: ((String?) -> Unit)?,
    onBell: (() -> Unit)?,
    onProcessExit: ((Int) -> Unit)?
) {
    SimpleTerminal(
        command = command,
        arguments = arguments,
        modifier = modifier
    )
}

// Stub implementations for state and controller
private class StubTerminalState : TerminalState {
    override val config = MutableStateFlow(TerminalState.TerminalConfig())
    override val theme = MutableStateFlow(TerminalState.TerminalTheme())
    override val dimensions = MutableStateFlow(Pair(80, 24))
    override val isFocused = MutableStateFlow(false)
    override val scrollPosition = MutableStateFlow(0)
    override val maxScrollPosition = MutableStateFlow(0)
    override val hasSelection = MutableStateFlow(false)
    override val isConnected = MutableStateFlow(false)
    override val title = MutableStateFlow<String?>(null)

    override fun updateConfig(config: TerminalState.TerminalConfig) {}
    override fun updateTheme(theme: TerminalState.TerminalTheme) {}
    override fun setDimensions(columns: Int, rows: Int) {}
    override fun setFocused(focused: Boolean) {}
    override fun scrollTo(position: Int) {}
    override fun scrollBy(delta: Int) {}
    override fun scrollToBottom() {}
}

private class StubTerminalController(override val state: TerminalState) : TerminalController {
    override suspend fun connect(command: String, arguments: List<String>, environment: Map<String, String>) {}
    override suspend fun disconnect() {}
    override suspend fun sendText(text: String) {}
    override suspend fun sendBytes(bytes: ByteArray) {}
    override suspend fun copySelection(): String? = null
    override suspend fun paste() {}
    override fun selectAll() {}
    override fun clearSelection() {}
    override fun clearScreen() {}
    override fun reset() {}
    override fun dispose() {}
}

actual fun rememberTerminalState(config: TerminalState.TerminalConfig): TerminalState {
    return StubTerminalState()
}

actual fun rememberTerminalController(state: TerminalState): TerminalController {
    return StubTerminalController(state)
}
