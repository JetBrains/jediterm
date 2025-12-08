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
import ai.rever.bossterm.compose.cli.CLIInstallDialog
import ai.rever.bossterm.compose.cli.CLIInstaller
import ai.rever.bossterm.compose.menu.MenuActions
import ai.rever.bossterm.compose.notification.NotificationService
import ai.rever.bossterm.compose.settings.SettingsManager
import ai.rever.bossterm.compose.settings.SettingsWindow
import ai.rever.bossterm.compose.update.UpdateBanner
import ai.rever.bossterm.compose.update.UpdateManager
import ai.rever.bossterm.compose.update.UpdateState
import ai.rever.bossterm.compose.window.CustomTitleBar
import ai.rever.bossterm.compose.window.configureWindowTransparency
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import kotlinx.coroutines.launch
import java.awt.GraphicsEnvironment
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.UUID
import androidx.compose.ui.unit.DpSize

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
            // CLI install dialog state
            var showCLIInstallDialog by remember { mutableStateOf(false) }
            var isFirstRun by remember { mutableStateOf(false) }
            var isCLIInstalled by remember { mutableStateOf(CLIInstaller.isInstalled()) }

            // Check for first run (CLI not installed)
            LaunchedEffect(Unit) {
                if (!isCLIInstalled) {
                    // Check if this is the first window (avoid showing on every window)
                    if (WindowManager.windows.firstOrNull()?.id == window.id) {
                        isFirstRun = true
                        showCLIInstallDialog = true
                    }
                }
            }

            // Refresh CLI install status when dialog closes
            LaunchedEffect(showCLIInstallDialog) {
                if (!showCLIInstallDialog) {
                    isCLIInstalled = CLIInstaller.isInstalled()
                }
            }

            // Get settings
            val settingsManagerForWindow = remember { SettingsManager.instance }
            val windowSettings by settingsManagerForWindow.settings.collectAsState()

            // Read native title bar setting at startup (not reactive - requires restart)
            val useNativeTitleBar = remember { settingsManagerForWindow.settings.value.useNativeTitleBar }

            Window(
                onCloseRequest = {
                    WindowManager.closeWindow(window.id)
                    if (!WindowManager.hasWindows()) {
                        exitApplication()
                    }
                },
                state = windowState,
                title = window.title.value,
                undecorated = !useNativeTitleBar,
                transparent = !useNativeTitleBar,
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

                    // Configure window transparency and blur (only for custom title bar mode)
                    if (!useNativeTitleBar) {
                        configureWindowTransparency(
                            window = awtWindow,
                            isTransparent = windowSettings.backgroundOpacity < 1.0f,
                            enableBlur = windowSettings.windowBlur
                        )
                    }

                    onDispose {
                        awtWindow.removeWindowFocusListener(focusListener)
                    }
                }

                // Handle fullscreen expansion for undecorated windows (only needed for custom title bar)
                // Store previous bounds to restore when exiting fullscreen
                var previousBounds by remember { mutableStateOf<java.awt.Rectangle?>(null) }

                if (!useNativeTitleBar) {
                    LaunchedEffect(windowState.placement) {
                        if (windowState.placement == WindowPlacement.Fullscreen) {
                            // Save current bounds before going fullscreen
                            previousBounds = awtWindow.bounds

                            val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
                            val screenDevice = ge.screenDevices.firstOrNull { device ->
                                awtWindow.bounds.intersects(device.defaultConfiguration.bounds)
                            } ?: ge.defaultScreenDevice

                            val screenBounds = screenDevice.defaultConfiguration.bounds
                            val insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(
                                screenDevice.defaultConfiguration
                            )

                            // Set window to fill available screen space (excluding menu bar/dock)
                            awtWindow.setBounds(
                                screenBounds.x + insets.left,
                                screenBounds.y + insets.top,
                                screenBounds.width - insets.left - insets.right,
                                screenBounds.height - insets.top - insets.bottom
                            )
                        } else if (windowState.placement == WindowPlacement.Floating && previousBounds != null) {
                            // Restore previous bounds when exiting fullscreen
                            awtWindow.bounds = previousBounds
                            previousBounds = null
                        }
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
                        Separator()
                        Item(
                            if (isCLIInstalled) "Uninstall Command Line Tool..." else "Install Command Line Tool...",
                            onClick = { showCLIInstallDialog = true }
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

                    Menu("Shell", mnemonic = 'S') {
                        Item(
                            "Split Pane Vertically",
                            onClick = { window.menuActions.onSplitVertical?.invoke() },
                            shortcut = KeyShortcut(Key.D, meta = isMacOS, ctrl = !isMacOS)
                        )
                        Item(
                            "Split Pane Horizontally",
                            onClick = { window.menuActions.onSplitHorizontal?.invoke() },
                            shortcut = KeyShortcut(Key.H, meta = isMacOS, ctrl = !isMacOS, shift = true)
                        )
                        Separator()
                        Item(
                            "Close Split Pane",
                            onClick = { window.menuActions.onClosePane?.invoke() }
                            // No shortcut - conflicts with Close Window. Use Cmd+W when pane is focused.
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

                // Track fullscreen/maximized state for corner radius (only for custom title bar)
                val isFullscreenOrMaximized = windowState.placement == WindowPlacement.Fullscreen ||
                                               windowState.placement == WindowPlacement.Maximized
                val cornerRadius = if (useNativeTitleBar || isFullscreenOrMaximized) 0.dp else 20.dp

                // Load background image if set
                val backgroundImage = remember(windowSettings.backgroundImagePath) {
                    if (windowSettings.backgroundImagePath.isNotEmpty()) {
                        try {
                            val file = java.io.File(windowSettings.backgroundImagePath)
                            if (file.exists()) {
                                androidx.compose.ui.res.loadImageBitmap(file.inputStream())
                            } else null
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }

                // Content area with transparent background (transparency only works with custom title bar)
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(cornerRadius)),
                    color = windowSettings.defaultBackgroundColor.copy(
                        alpha = if (useNativeTitleBar) 1f else windowSettings.backgroundOpacity
                    ),
                    shape = RoundedCornerShape(cornerRadius)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Background layer: either image or faux blur effect
                        if (backgroundImage != null) {
                            // Background image with blur effect
                            androidx.compose.foundation.Image(
                                bitmap = backgroundImage,
                                contentDescription = "Background",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                alpha = windowSettings.backgroundImageOpacity,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (windowSettings.windowBlur) {
                                            Modifier.blur(windowSettings.blurRadius.dp)
                                        } else {
                                            Modifier
                                        }
                                    )
                            )
                        } else if (!useNativeTitleBar && windowSettings.backgroundOpacity < 1.0f && windowSettings.windowBlur) {
                            // Faux blur effect: grey gradient overlay to simulate frosted glass
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                Color.Gray.copy(alpha = 0.3f),
                                                Color.DarkGray.copy(alpha = 0.4f),
                                                Color.Gray.copy(alpha = 0.35f)
                                            )
                                        )
                                    )
                                    .blur(windowSettings.blurRadius.dp)
                            )
                        }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Custom title bar (only when not using native title bar)
                        if (!useNativeTitleBar) {
                            CustomTitleBar(
                                title = window.title.value,
                                windowState = windowState,
                                onClose = {
                                    WindowManager.closeWindow(window.id)
                                    if (!WindowManager.hasWindows()) {
                                        exitApplication()
                                    }
                                },
                                onMinimize = { windowState.isMinimized = true },
                                onFullscreen = {
                                    // Toggle fullscreen
                                    windowState.placement = if (windowState.placement == WindowPlacement.Fullscreen) {
                                        WindowPlacement.Floating
                                    } else {
                                        WindowPlacement.Fullscreen
                                    }
                                },
                                onMaximize = {
                                    // Same as fullscreen for undecorated windows
                                    windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                                        WindowPlacement.Floating
                                    } else {
                                        WindowPlacement.Maximized
                                    }
                                },
                                backgroundColor = windowSettings.defaultBackgroundColor.copy(
                                    alpha = (windowSettings.backgroundOpacity * 1.1f).coerceAtMost(1f)
                                )
                            )
                        }

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
                        onShowSettings = { showSettingsDialog = true },
                        menuActions = window.menuActions,
                        isWindowFocused = { window.isWindowFocused.value },
                        modifier = Modifier.fillMaxSize().weight(1f)
                    )
                    }
                    }
                }

                // Settings dialog
                SettingsWindow(
                    visible = showSettingsDialog,
                    onDismiss = { showSettingsDialog = false },
                    onRestartApp = {
                        // Close this window and create a new one with updated settings
                        showSettingsDialog = false
                        WindowManager.closeWindow(window.id)
                        WindowManager.createWindow()
                    }
                )

                // CLI install dialog
                CLIInstallDialog(
                    visible = showCLIInstallDialog,
                    onDismiss = {
                        showCLIInstallDialog = false
                        isFirstRun = false
                    },
                    isFirstRun = isFirstRun
                )
            }
        }
    }
}
