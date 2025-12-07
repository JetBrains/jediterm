package ai.rever.bossterm.compose.debug

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import ai.rever.bossterm.terminal.model.TerminalTextBuffer

/**
 * Debug tools window (non-modal, allows terminal interaction).
 */
@Composable
fun DebugWindow(
    visible: Boolean,
    collector: DebugDataCollector?,
    textBuffer: TerminalTextBuffer?,
    onClose: () -> Unit
) {
    if (!visible) return

    Window(
        onCloseRequest = onClose,
        title = "Debug Tools - Terminal Inspector",
        resizable = true,
        alwaysOnTop = false,
        state = rememberWindowState(size = DpSize(1000.dp, 700.dp))
    ) {
        DebugPanelContent(
            collector = collector,
            textBuffer = textBuffer,
            onClose = onClose
        )
    }
}
