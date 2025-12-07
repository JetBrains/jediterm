package ai.rever.bossterm.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import ai.rever.bossterm.compose.menu.MenuActions
import ai.rever.bossterm.compose.settings.SettingsManager
import ai.rever.bossterm.compose.tabs.TabBar
import ai.rever.bossterm.compose.tabs.TabController
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

    // Initialize with one tab on first composition
    LaunchedEffect(Unit) {
        if (tabController.tabs.isEmpty()) {
            tabController.createTab()
        }
    }

    // Cleanup all tabs when window is closed
    DisposableEffect(tabController) {
        onDispose {
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
                }
            )
        }

        // Render active terminal tab
        if (tabController.tabs.isNotEmpty()) {
            val activeTab = tabController.tabs[tabController.activeTabIndex]

            // Update window title when active tab's title changes
            LaunchedEffect(activeTab) {
                activeTab.display.windowTitleFlow.collect { newTitle ->
                    if (newTitle.isNotEmpty()) {
                        onWindowTitleChange(newTitle)
                    }
                }
            }

            ProperTerminal(
                tab = activeTab,
                isActiveTab = true,
                sharedFont = sharedFont,
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
                menuActions = menuActions,
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
