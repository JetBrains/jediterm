package org.jetbrains.jediterm.compose.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.jetbrains.jediterm.compose.settings.SettingsManager
import org.jetbrains.jediterm.compose.tabs.TabBar
import org.jetbrains.jediterm.compose.tabs.TabController

fun main() = application {
    // Window title state that will be updated by active tab's window title
    val windowTitleState = remember { mutableStateOf("JediTerm Compose - Multiple Terminal Tabs") }

    Window(
        onCloseRequest = ::exitApplication,
        title = windowTitleState.value
    ) {
        TerminalApp(
            onExit = ::exitApplication,
            onWindowTitleChange = { newTitle ->
                windowTitleState.value = newTitle
            }
        )
    }
}

@Composable
fun TerminalApp(
    onExit: () -> Unit,
    onWindowTitleChange: (String) -> Unit = {}
) {
    // Settings integration
    val settingsManager = remember { SettingsManager.instance }
    val settings by settingsManager.settings.collectAsState()

    // Load MesloLGS NF (Nerd Font) once and share across all tabs
    // This is expensive (~2.5MB font file) so we only want to do it once
    val nerdFont = remember {
        try {
            println("INFO: Loading shared MesloLGSNF font for all tabs...")
            val fontStream = object {}.javaClass.classLoader?.getResourceAsStream("fonts/MesloLGSNF-Regular.ttf")
                ?: throw IllegalStateException("Font resource not found: fonts/MesloLGSNF-Regular.ttf")

            // Create temp file from InputStream (Skiko classloader workaround)
            val tempFile = java.io.File.createTempFile("MesloLGSNF", ".ttf")
            tempFile.deleteOnExit()
            fontStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            println("INFO: Loaded shared MesloLGSNF font from: ${tempFile.absolutePath}")
            FontFamily(
                androidx.compose.ui.text.platform.Font(
                    file = tempFile,
                    weight = FontWeight.Normal
                )
            )
        } catch (e: Exception) {
            println("ERROR: Failed to load shared MesloLGSNF font: ${e.message}")
            e.printStackTrace()
            FontFamily.Monospace  // Fallback to system monospace
        }
    }

    // Create tab controller
    val tabController = remember {
        TabController(
            settings = settings,
            onLastTabClosed = onExit
        )
    }

    // Initialize with one tab on first composition
    LaunchedEffect(Unit) {
        if (tabController.tabs.isEmpty()) {
            println("INFO: Creating initial terminal tab...")
            // Note: onProcessExit is now handled by TabController.initializeTerminalSession
            // TabController auto-closes tabs when shell exits, callback is optional for custom logic
            tabController.createTab()
        }
    }

    // Tab UI layout
    Column(modifier = Modifier.fillMaxSize()) {
        // Tab bar at top
        TabBar(
            tabs = tabController.tabs,
            activeTabIndex = tabController.activeTabIndex,
            onTabSelected = { index -> tabController.switchToTab(index) },
            onTabClosed = { index -> tabController.closeTab(index) },
            onNewTab = {
                // Inherit working directory from active tab
                val workingDir = tabController.getActiveWorkingDirectory()
                // TabController handles auto-close on exit, onProcessExit callback is optional
                tabController.createTab(workingDir = workingDir)
            }
        )

        // Render active terminal tab
        if (tabController.tabs.isNotEmpty()) {
            val activeTab = tabController.tabs[tabController.activeTabIndex]

            // Observe active tab's window title (OSC 2) and update main window title bar
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
                sharedFont = nerdFont,
                onTabTitleChange = { newTitle ->
                    activeTab.title.value = newTitle
                },
                onNewTab = {
                    // Inherit working directory from active tab (Phase 5)
                    val workingDir = tabController.getActiveWorkingDirectory()
                    tabController.createTab(workingDir = workingDir)
                },
                onCloseTab = {
                    // Close current tab (Phase 5)
                    tabController.closeTab(tabController.activeTabIndex)
                },
                onNextTab = {
                    // Switch to next tab (Phase 5)
                    tabController.nextTab()
                },
                onPreviousTab = {
                    // Switch to previous tab (Phase 5)
                    tabController.previousTab()
                },
                onSwitchToTab = { index ->
                    // Switch to specific tab by index (Phase 5)
                    if (index in tabController.tabs.indices) {
                        tabController.switchToTab(index)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
