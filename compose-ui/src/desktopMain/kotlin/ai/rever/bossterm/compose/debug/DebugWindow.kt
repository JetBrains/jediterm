package ai.rever.bossterm.compose.debug

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import ai.rever.bossterm.terminal.model.TerminalTextBuffer

/**
 * Debug tools window - displays terminal debugging tools in a separate window.
 */
@Composable
fun DebugWindow(
    visible: Boolean,
    collector: DebugDataCollector?,
    textBuffer: TerminalTextBuffer?,
    onClose: () -> Unit
) {
    if (!visible) return

    DialogWindow(
        onCloseRequest = onClose,
        title = "Debug Tools - Terminal Inspector",
        resizable = true,
        state = rememberDialogState(size = DpSize(1000.dp, 700.dp))
    ) {
        DebugPanelContent(
            collector = collector,
            textBuffer = textBuffer,
            onClose = onClose
        )
    }
}
