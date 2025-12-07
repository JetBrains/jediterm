package ai.rever.bossterm.compose.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ai.rever.bossterm.compose.TabbedTerminal
import ai.rever.bossterm.compose.menu.MenuActions
import ai.rever.bossterm.compose.update.UpdateBanner
import ai.rever.bossterm.compose.update.UpdateManager
import ai.rever.bossterm.compose.update.UpdateState
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Represents a single terminal window with its own state.
 */
data class TerminalWindow(
    val id: String = UUID.randomUUID().toString(),
    val title: MutableState<String> = mutableStateOf("BossTerm"),
    val menuActions: MenuActions = MenuActions()
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

    // Detect platform
    val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

    // Render all windows
    for (window in WindowManager.windows) {
        key(window.id) {
            val windowState = rememberWindowState()
            Window(
                onCloseRequest = {
                    WindowManager.closeWindow(window.id)
                    if (!WindowManager.hasWindows()) {
                        exitApplication()
                    }
                },
                state = windowState,
                title = window.title.value
            ) {
                // Update manager state
                val updateManager = remember { UpdateManager.instance }
                val updateState by updateManager.updateState.collectAsState()
                val scope = rememberCoroutineScope()

                // Check for updates on first window launch
                var hasCheckedForUpdates by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    if (!hasCheckedForUpdates) {
                        hasCheckedForUpdates = true
                        if (updateManager.shouldCheckForUpdates()) {
                            updateManager.checkForUpdates()
                        }
                    }
                }

                // Menu bar
                MenuBar {
                    // macOS: App menu with Check for Updates (first menu becomes app menu)
                    if (isMacOS) {
                        Menu("BossTerm") {
                            Item(
                                "Check for Updates...",
                                onClick = {
                                    scope.launch {
                                        updateManager.checkForUpdates()
                                    }
                                }
                            )
                        }
                    }

                    Menu("File", mnemonic = 'F') {
                        Item(
                            "New Tab",
                            onClick = { window.menuActions.onNewTab?.invoke() },
                            shortcut = KeyShortcut(Key.T, meta = isMacOS, ctrl = !isMacOS)
                        )
                        Item(
                            "New Window",
                            onClick = { WindowManager.createWindow() },
                            shortcut = KeyShortcut(Key.N, meta = isMacOS, ctrl = !isMacOS)
                        )
                        Separator()
                        Item(
                            "Close Tab",
                            onClick = { window.menuActions.onCloseTab?.invoke() },
                            shortcut = KeyShortcut(Key.W, meta = isMacOS, ctrl = !isMacOS)
                        )
                        Item(
                            "Close Window",
                            onClick = {
                                WindowManager.closeWindow(window.id)
                                if (!WindowManager.hasWindows()) {
                                    exitApplication()
                                }
                            },
                            shortcut = KeyShortcut(Key.W, meta = isMacOS, ctrl = !isMacOS, shift = true)
                        )
                    }

                    Menu("Edit", mnemonic = 'E') {
                        Item(
                            "Copy",
                            onClick = { window.menuActions.onCopy?.invoke() },
                            shortcut = KeyShortcut(Key.C, meta = isMacOS, ctrl = !isMacOS)
                        )
                        Item(
                            "Paste",
                            onClick = { window.menuActions.onPaste?.invoke() },
                            shortcut = KeyShortcut(Key.V, meta = isMacOS, ctrl = !isMacOS)
                        )
                        Separator()
                        Item(
                            "Select All",
                            onClick = { window.menuActions.onSelectAll?.invoke() },
                            shortcut = KeyShortcut(Key.A, meta = isMacOS, ctrl = !isMacOS)
                        )
                        Item(
                            "Clear",
                            onClick = { window.menuActions.onClear?.invoke() },
                            shortcut = KeyShortcut(Key.K, meta = isMacOS, ctrl = !isMacOS)
                        )
                        Separator()
                        Item(
                            "Find...",
                            onClick = { window.menuActions.onFind?.invoke() },
                            shortcut = KeyShortcut(Key.F, meta = isMacOS, ctrl = !isMacOS)
                        )
                    }

                    Menu("View", mnemonic = 'V') {
                        Item(
                            "Toggle Debug Panel",
                            onClick = { window.menuActions.onToggleDebug?.invoke() },
                            shortcut = KeyShortcut(Key.D, meta = isMacOS, ctrl = !isMacOS, shift = true)
                        )
                    }

                    Menu("Window", mnemonic = 'W') {
                        Item(
                            "Minimize",
                            onClick = { windowState.isMinimized = true },
                            shortcut = KeyShortcut(Key.M, meta = isMacOS, ctrl = !isMacOS)
                        )
                        Separator()
                        Item(
                            "Next Tab",
                            onClick = { window.menuActions.onNextTab?.invoke() },
                            shortcut = KeyShortcut(Key.Tab, ctrl = true)
                        )
                        Item(
                            "Previous Tab",
                            onClick = { window.menuActions.onPreviousTab?.invoke() },
                            shortcut = KeyShortcut(Key.Tab, ctrl = true, shift = true)
                        )
                    }

                    // Non-macOS: Help menu with Check for Updates
                    if (!isMacOS) {
                        Menu("Help", mnemonic = 'H') {
                            Item(
                                "Check for Updates...",
                                onClick = {
                                    scope.launch {
                                        updateManager.checkForUpdates()
                                    }
                                }
                            )
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Update banner (shows when update is available)
                    UpdateBanner(
                        updateState = updateState,
                        onCheckForUpdates = {
                            scope.launch {
                                updateManager.checkForUpdates()
                            }
                        },
                        onDownloadUpdate = { updateInfo ->
                            scope.launch {
                                updateManager.downloadUpdate(updateInfo)
                            }
                        },
                        onInstallUpdate = { downloadPath ->
                            scope.launch {
                                updateManager.installUpdate(downloadPath)
                            }
                        },
                        onDismiss = {
                            updateManager.resetState()
                        }
                    )

                    // Terminal content
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
                        menuActions = window.menuActions,
                        modifier = Modifier.fillMaxSize().weight(1f)
                    )
                }
            }
        }
    }
}
