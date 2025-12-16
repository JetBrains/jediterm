package ai.rever.bossterm.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import ai.rever.bossterm.compose.menu.MenuActions
import ai.rever.bossterm.compose.util.loadTerminalFont
import ai.rever.bossterm.compose.settings.SettingsManager
import ai.rever.bossterm.compose.splits.NavigationDirection
import ai.rever.bossterm.compose.splits.SplitContainer
import ai.rever.bossterm.compose.splits.SplitOrientation
import ai.rever.bossterm.compose.splits.SplitViewState
import ai.rever.bossterm.compose.demo.WindowManager
import ai.rever.bossterm.compose.tabs.TabBar
import ai.rever.bossterm.compose.tabs.TabController
import ai.rever.bossterm.compose.tabs.TerminalTab
import ai.rever.bossterm.compose.ui.ProperTerminal

/**
 * Terminal composable with multi-tab support.
 *
 * This provides a complete tabbed terminal experience with:
 * - Multiple tabs per window
 * - Tab bar with close buttons
 * - Keyboard shortcuts for tab management
 * - Working directory inheritance for new tabs
 * - Command completion notifications (when window unfocused)
 *
 * Basic usage:
 * ```kotlin
 * TabbedTerminal(
 *     onExit = { exitApplication() }
 * )
 * ```
 *
 * With callbacks:
 * ```kotlin
 * TabbedTerminal(
 *     onExit = { exitApplication() },
 *     onWindowTitleChange = { title -> window.title = title },
 *     onNewWindow = { WindowManager.createWindow() }
 * )
 * ```
 *
 * @param onExit Called when the last tab is closed
 * @param onWindowTitleChange Called when active tab's title changes (for window title bar)
 * @param onNewWindow Called when user requests a new window (Cmd/Ctrl+N)
 * @param menuActions Optional menu action callbacks for wiring up menu bar
 * @param isWindowFocused Lambda returning whether this window is currently focused (for notifications)
 * @param initialCommand Optional command to run in the first terminal tab after startup
 * @param modifier Compose modifier for the terminal container
 */
@Composable
fun TabbedTerminal(
    onExit: () -> Unit,
    onWindowTitleChange: (String) -> Unit = {},
    onNewWindow: () -> Unit = {},
    onShowSettings: () -> Unit = {},
    menuActions: MenuActions? = null,
    isWindowFocused: () -> Boolean = { true },
    initialCommand: String? = null,
    modifier: Modifier = Modifier
) {
    // Settings integration
    val settingsManager = remember { SettingsManager.instance }
    val settings by settingsManager.settings.collectAsState()

    // Load font once and share across all tabs (supports custom font via settings)
    val sharedFont = remember(settings.fontName) {
        loadTerminalFont(settings.fontName)
    }

    // Create tab controller with window focus tracking for notifications
    val tabController = remember {
        TabController(
            settings = settings,
            onLastTabClosed = onExit,
            isWindowFocused = isWindowFocused
        )
    }

    // Track window focus state reactively for overlay
    val isWindowFocusedState by remember { derivedStateOf { isWindowFocused() } }

    // Track SplitViewState per tab (tab.id -> SplitViewState)
    val splitStates = remember { mutableStateMapOf<String, SplitViewState>() }

    // Helper function to get or create SplitViewState for a tab
    fun getOrCreateSplitState(tab: TerminalTab): SplitViewState {
        return splitStates.getOrPut(tab.id) {
            SplitViewState(initialSession = tab)
        }
    }

    // Helper function to create a new session for splitting
    fun createSessionForSplit(splitState: SplitViewState, paneId: String): TerminalSession {
        val workingDir = splitState.getFocusedSession()?.workingDirectory?.value
        return tabController.createSessionForSplit(
            workingDir = workingDir,
            onProcessExit = {
                // Auto-close the pane when shell exits
                splitState.closePane(paneId)
            }
        )
    }

    // Wire up menu actions for tab management
    LaunchedEffect(menuActions, tabController) {
        menuActions?.apply {
            onNewTab = {
                // New tabs always start in home directory (no working dir inheritance)
                // Use initial command from settings if configured
                tabController.createTab(initialCommand = settings.initialCommand.ifEmpty { null })
            }
            onCloseTab = {
                tabController.closeTab(tabController.activeTabIndex)
            }
            onNextTab = {
                tabController.nextTab()
            }
            onPreviousTab = {
                tabController.previousTab()
            }
        }
    }

    // Wire up split menu actions (updates when active tab changes or tabs are added)
    LaunchedEffect(menuActions, tabController.activeTabIndex, tabController.tabs.size) {
        if (tabController.tabs.isEmpty()) return@LaunchedEffect
        val activeTab = tabController.tabs.getOrNull(tabController.activeTabIndex) ?: return@LaunchedEffect
        val splitState = splitStates.getOrPut(activeTab.id) { SplitViewState(initialSession = activeTab) }

        menuActions?.apply {
            onSplitVertical = {
                val workingDir = splitState.getFocusedSession()?.workingDirectory?.value
                var newSessionRef: TerminalSession? = null
                val newSession = tabController.createSessionForSplit(
                    workingDir = workingDir,
                    onProcessExit = {
                        newSessionRef?.let { session ->
                            splitState.getAllPanes()
                                .find { it.session === session }
                                ?.let { pane -> splitState.closePane(pane.id) }
                        }
                    }
                )
                newSessionRef = newSession
                splitState.splitFocusedPane(SplitOrientation.VERTICAL, newSession)
            }
            onSplitHorizontal = {
                val workingDir = splitState.getFocusedSession()?.workingDirectory?.value
                var newSessionRef: TerminalSession? = null
                val newSession = tabController.createSessionForSplit(
                    workingDir = workingDir,
                    onProcessExit = {
                        newSessionRef?.let { session ->
                            splitState.getAllPanes()
                                .find { it.session === session }
                                ?.let { pane -> splitState.closePane(pane.id) }
                        }
                    }
                )
                newSessionRef = newSession
                splitState.splitFocusedPane(SplitOrientation.HORIZONTAL, newSession)
            }
            onClosePane = {
                if (splitState.isSinglePane) {
                    tabController.closeTab(tabController.activeTabIndex)
                } else {
                    splitState.closeFocusedPane()
                }
            }
        }
    }

    // Initialize with one tab on first composition
    // Check for pending tab transfer from another window first
    LaunchedEffect(Unit) {
        if (tabController.tabs.isEmpty()) {
            val pendingTab = WindowManager.pendingTabForNewWindow
            val pendingSplitState = WindowManager.pendingSplitStateForNewWindow
            if (pendingTab != null) {
                // Clear pending state
                WindowManager.pendingTabForNewWindow = null
                WindowManager.pendingSplitStateForNewWindow = null
                // Add the transferred tab
                tabController.createTabFromExistingSession(pendingTab)
                // Restore split state if present
                if (pendingSplitState != null) {
                    splitStates[pendingTab.id] = pendingSplitState
                }
            } else {
                // No pending tab, create fresh terminal with optional initial command
                // Priority: parameter > settings > none
                val effectiveInitialCommand = initialCommand ?: settings.initialCommand.ifEmpty { null }
                tabController.createTab(initialCommand = effectiveInitialCommand)
            }
        }
    }

    // Cleanup split states when tabs are closed
    LaunchedEffect(tabController.tabs.size) {
        val currentTabIds = tabController.tabs.map { it.id }.toSet()
        splitStates.keys.removeAll { it !in currentTabIds }
    }

    // Cleanup all tabs when window is closed
    DisposableEffect(tabController) {
        onDispose {
            // Dispose all split states
            splitStates.values.forEach { it.dispose() }
            splitStates.clear()
            tabController.disposeAll()
        }
    }

    // Tab UI layout with focus overlay support
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Tab bar at top (only show when multiple tabs)
            if (tabController.tabs.size > 1) {
            TabBar(
                tabs = tabController.tabs,
                activeTabIndex = tabController.activeTabIndex,
                onTabSelected = { index -> tabController.switchToTab(index) },
                onTabClosed = { index -> tabController.closeTab(index) },
                onNewTab = {
                    // New tabs always start in home directory (no working dir inheritance)
                    // Use initial command from settings if configured
                    tabController.createTab(initialCommand = settings.initialCommand.ifEmpty { null })
                },
                onTabMoveToNewWindow = { index ->
                    // Get tab first to access its ID for split state lookup
                    val tab = tabController.tabs.getOrNull(index) ?: return@TabBar
                    // Extract split state before extracting tab (preserves entire split layout)
                    val splitState = splitStates.remove(tab.id)
                    // Extract tab without disposing (preserves PTY session)
                    val extractedTab = tabController.extractTab(index) ?: return@TabBar
                    // Create new window and transfer both tab and split state
                    WindowManager.createWindowWithTab(extractedTab, splitState)
                }
            )
        }

        // Render active terminal tab with split support
        if (tabController.tabs.isNotEmpty()) {
            val activeTab = tabController.tabs[tabController.activeTabIndex]
            val splitState = getOrCreateSplitState(activeTab)

            // Update window title when active tab's title changes
            LaunchedEffect(activeTab) {
                activeTab.display.windowTitleFlow.collect { newTitle ->
                    if (newTitle.isNotEmpty()) {
                        onWindowTitleChange(newTitle)
                    }
                }
            }

            // Split operation handlers
            val onSplitHorizontal: () -> Unit = {
                // Only inherit working directory if setting is enabled
                val workingDir = if (settings.splitInheritWorkingDirectory) {
                    val session = splitState.getFocusedSession()
                    // First try OSC 7 tracked directory, then fall back to querying process
                    session?.workingDirectory?.value
                        ?: session?.processHandle?.value?.getWorkingDirectory()
                } else null
                var newSessionRef: TerminalSession? = null
                val newSession = tabController.createSessionForSplit(
                    workingDir = workingDir,
                    onProcessExit = {
                        // Auto-close the pane when process exits
                        newSessionRef?.let { session ->
                            splitState.getAllPanes()
                                .find { it.session === session }
                                ?.let { pane -> splitState.closePane(pane.id) }
                        }
                    }
                )
                newSessionRef = newSession
                splitState.splitFocusedPane(SplitOrientation.HORIZONTAL, newSession, settings.splitDefaultRatio)
            }

            val onSplitVertical: () -> Unit = {
                // Only inherit working directory if setting is enabled
                val workingDir = if (settings.splitInheritWorkingDirectory) {
                    val session = splitState.getFocusedSession()
                    // First try OSC 7 tracked directory, then fall back to querying process
                    session?.workingDirectory?.value
                        ?: session?.processHandle?.value?.getWorkingDirectory()
                } else null
                var newSessionRef: TerminalSession? = null
                val newSession = tabController.createSessionForSplit(
                    workingDir = workingDir,
                    onProcessExit = {
                        // Auto-close the pane when process exits
                        newSessionRef?.let { session ->
                            splitState.getAllPanes()
                                .find { it.session === session }
                                ?.let { pane -> splitState.closePane(pane.id) }
                        }
                    }
                )
                newSessionRef = newSession
                splitState.splitFocusedPane(SplitOrientation.VERTICAL, newSession, settings.splitDefaultRatio)
            }

            val onClosePane: () -> Unit = {
                if (splitState.isSinglePane) {
                    // Last pane - close the tab
                    tabController.closeTab(tabController.activeTabIndex)
                } else {
                    // Close just this pane
                    splitState.closeFocusedPane()
                }
            }

            val onNavigatePane: (NavigationDirection) -> Unit = { direction ->
                splitState.navigateFocus(direction)
            }

            SplitContainer(
                splitState = splitState,
                sharedFont = sharedFont,
                isActiveTab = true,
                onTabTitleChange = { newTitle ->
                    activeTab.title.value = newTitle
                },
                onNewTab = {
                    // New tabs always start in home directory (no working dir inheritance)
                    // Use initial command from settings if configured
                    tabController.createTab(initialCommand = settings.initialCommand.ifEmpty { null })
                },
                onCloseTab = {
                    tabController.closeTab(tabController.activeTabIndex)
                },
                onNextTab = {
                    tabController.nextTab()
                },
                onPreviousTab = {
                    tabController.previousTab()
                },
                onSwitchToTab = { index ->
                    if (index in tabController.tabs.indices) {
                        tabController.switchToTab(index)
                    }
                },
                onNewWindow = onNewWindow,
                onShowSettings = onShowSettings,
                onSplitHorizontal = onSplitHorizontal,
                onSplitVertical = onSplitVertical,
                onClosePane = onClosePane,
                onNavigatePane = onNavigatePane,
                onNavigateNextPane = { splitState.navigateToNextPane() },
                onNavigatePreviousPane = { splitState.navigateToPreviousPane() },
                onMoveToNewTab = if (!splitState.isSinglePane) {
                    {
                        // Extract the session from the split and move it to a new tab
                        val session = splitState.extractFocusedPaneSession()
                        if (session != null) {
                            tabController.createTabFromExistingSession(session)
                        }
                    }
                } else null,  // Don't show option if only one pane (nothing to move)
                menuActions = menuActions,
                // Split pane settings
                splitFocusBorderEnabled = settings.splitFocusBorderEnabled,
                splitFocusBorderColor = settings.splitFocusBorderColorValue,
                splitMinimumSize = settings.splitMinimumSize,
                modifier = Modifier.fillMaxSize()
            )
        }
        }

        // Semi-transparent overlay when window loses focus
        if (!isWindowFocusedState && settings.showUnfocusedOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.15f))
            )
        }
    }
}
