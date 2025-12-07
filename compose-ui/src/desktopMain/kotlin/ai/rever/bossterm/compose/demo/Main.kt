package ai.rever.bossterm.compose.demo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import ai.rever.bossterm.compose.TabbedTerminal
import java.util.UUID

/**
 * Represents a single terminal window with its own state.
 */
data class TerminalWindow(
    val id: String = UUID.randomUUID().toString(),
    val title: MutableState<String> = mutableStateOf("BossTerm")
)

/**
 * Global window manager for multi-window support.
 */
object WindowManager {
    private val _windows = mutableStateListOf<TerminalWindow>()
    val windows: List<TerminalWindow> get() = _windows

    fun createWindow(): TerminalWindow {
        val window = TerminalWindow()
        _windows.add(window)
        return window
    }

    fun closeWindow(id: String) {
        _windows.removeAll { it.id == id }
    }

    fun hasWindows(): Boolean = _windows.isNotEmpty()
}

fun main() = application {
    // Create initial window if none exist
    if (WindowManager.windows.isEmpty()) {
        WindowManager.createWindow()
    }

    // Render all windows
    for (window in WindowManager.windows) {
        key(window.id) {
            Window(
                onCloseRequest = {
                    WindowManager.closeWindow(window.id)
                    if (!WindowManager.hasWindows()) {
                        exitApplication()
                    }
                },
                title = window.title.value
            ) {
                TabbedTerminal(
                    onExit = {
                        WindowManager.closeWindow(window.id)
                        if (!WindowManager.hasWindows()) {
                            exitApplication()
                        }
                    },
                    onWindowTitleChange = { newTitle ->
                        window.title.value = newTitle
                    },
                    onNewWindow = {
                        WindowManager.createWindow()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
