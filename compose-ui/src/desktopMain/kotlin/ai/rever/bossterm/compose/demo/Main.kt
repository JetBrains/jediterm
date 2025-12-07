package ai.rever.bossterm.compose.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ai.rever.bossterm.compose.TabbedTerminal
import ai.rever.bossterm.compose.menu.MenuActions
import ai.rever.bossterm.compose.notification.NotificationService
import ai.rever.bossterm.compose.settings.SettingsManager
import ai.rever.bossterm.compose.settings.SettingsWindow
import ai.rever.bossterm.compose.update.UpdateBanner
import ai.rever.bossterm.compose.update.UpdateManager
import ai.rever.bossterm.compose.update.UpdateState
import kotlinx.coroutines.launch
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.UUID

/**
 * Represents a single terminal window with its own state.
 */
data class TerminalWindow(
    val id: String = UUID.randomUUID().toString(),
    val title: MutableState<String> = mutableStateOf("BossTerm"),
    val menuActions: MenuActions = MenuActions(),
    val isWindowFocused: MutableState<Boolean> = mutableStateOf(true)
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
            // Settings dialog state (declared before Window for onPreviewKeyEvent access)
            var showSettingsDialog by remember { mutableStateOf(false) }

            Window(
                onCloseRequest = {
                    WindowManager.closeWindow(window.id)
                    if (!WindowManager.hasWindows()) {
                        exitApplication()
                    }
                },
                state = windowState,
                title = window.title.value,
                onPreviewKeyEvent = { keyEvent ->
                    // Handle Cmd+, (macOS) or Ctrl+, (other) for Settings
                    if (keyEvent.type == KeyEventType.KeyDown &&
                        keyEvent.key == Key.Comma &&
                        ((isMacOS && keyEvent.isMetaPressed) || (!isMacOS && keyEvent.isCtrlPressed))
                    ) {
                        showSettingsDialog = true
                        true // Consume event
                    } else {
                        false
                    }
                }
            ) {
                // Update manager state
                val updateManager = remember { UpdateManager.instance }
                val updateState by updateManager.updateState.collectAsState()
                val scope = rememberCoroutineScope()

                // Track window focus for command completion notifications
                val awtWindow = this.window
                DisposableEffect(awtWindow) {
                    val focusListener = object : WindowAdapter() {
                        override fun windowGainedFocus(e: WindowEvent?) {
                            window.isWindowFocused.value = true
                        }
                        override fun windowLostFocus(e: WindowEvent?) {
                            window.isWindowFocused.value = false
                        }
                    }
                    awtWindow.addWindowFocusListener(focusListener)
                    // Set initial focus state
                    window.isWindowFocused.value = awtWindow.isFocused

                    onDispose {
                        awtWindow.removeWindowFocusListener(focusListener)
                    }
                }

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

                // Request notification permission on first launch
                val settingsManager = remember { SettingsManager.instance }
                LaunchedEffect(Unit) {
                    val currentSettings = settingsManager.settings.value
                    if (!currentSettings.notificationPermissionRequested) {
                        // Send welcome notification to trigger macOS permission dialog
                        NotificationService.showNotification(
                            title = "BossTerm",
                            message = "Notifications enabled! You'll be notified when long-running commands complete.",
                            withSound = false
                        )
                        // Mark as requested so we don't show again
                        settingsManager.updateSettings(
                            currentSettings.copy(notificationPermissionRequested = true)
                        )
                    }
                }

                // Menu bar
                MenuBar {
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
                        Separator()
                        Item(
                            "Settings...",
                            onClick = { showSettingsDialog = true },
                            shortcut = KeyShortcut(Key.Comma, meta = isMacOS, ctrl = !isMacOS)
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
                        isWindowFocused = { window.isWindowFocused.value },
                        modifier = Modifier.fillMaxSize().weight(1f)
                    )
                }

                // Settings dialog
                SettingsWindow(
                    visible = showSettingsDialog,
                    onDismiss = { showSettingsDialog = false }
                )
            }
        }
    }
}
