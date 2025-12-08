package ai.rever.bossterm.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import ai.rever.bossterm.compose.menu.MenuActions
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
    modifier: Modifier = Modifier
) {
    // Settings integration
    val settingsManager = remember { SettingsManager.instance }
    val settings by settingsManager.settings.collectAsState()

    // Load font once and share across all tabs
    val sharedFont = remember {
        loadTerminalFont()
    }

    // Create tab controller with window focus tracking for notifications
    val tabController = remember {
        TabController(
            settings = settings,
            onLastTabClosed = onExit,
            isWindowFocused = isWindowFocused
        )
    }

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
                val workingDir = tabController.getActiveWorkingDirectory()
                tabController.createTab(workingDir = workingDir)
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

    // Wire up split menu actions (updates when active tab changes)
    LaunchedEffect(menuActions, tabController.activeTabIndex) {
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
                // No pending tab, create fresh terminal
                tabController.createTab()
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

    // Tab UI layout
    Column(modifier = modifier.fillMaxSize()) {
        // Tab bar at top (only show when multiple tabs)
        if (tabController.tabs.size > 1) {
            TabBar(
                tabs = tabController.tabs,
                activeTabIndex = tabController.activeTabIndex,
                onTabSelected = { index -> tabController.switchToTab(index) },
                onTabClosed = { index -> tabController.closeTab(index) },
                onNewTab = {
                    val workingDir = tabController.getActiveWorkingDirectory()
                    tabController.createTab(workingDir = workingDir)
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
                    splitState.getFocusedSession()?.workingDirectory?.value
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
                    splitState.getFocusedSession()?.workingDirectory?.value
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
                    val workingDir = tabController.getActiveWorkingDirectory()
                    tabController.createTab(workingDir = workingDir)
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
}

/**
 * Load terminal font with fallback to system monospace.
 */
private fun loadTerminalFont(): FontFamily {
    return try {
        val fontStream = object {}.javaClass.classLoader
            ?.getResourceAsStream("fonts/MesloLGSNF-Regular.ttf")
            ?: return FontFamily.Monospace

        val tempFile = java.io.File.createTempFile("MesloLGSNF", ".ttf")
        tempFile.deleteOnExit()
        fontStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        FontFamily(
            androidx.compose.ui.text.platform.Font(
                file = tempFile,
                weight = FontWeight.Normal
            )
        )
    } catch (e: Exception) {
        System.err.println("Failed to load terminal font: ${e.message}")
        FontFamily.Monospace
    }
}
