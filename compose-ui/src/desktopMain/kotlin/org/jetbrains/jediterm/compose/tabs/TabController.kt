package org.jetbrains.jediterm.compose.tabs

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.emulator.JediEmulator
import com.jediterm.terminal.model.JediTerminal
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import kotlinx.coroutines.*
import org.jetbrains.jediterm.compose.ComposeTerminalDisplay
import org.jetbrains.jediterm.compose.ConnectionState
import org.jetbrains.jediterm.compose.PlatformServices
import org.jetbrains.jediterm.compose.demo.BlockingTerminalDataStream
import org.jetbrains.jediterm.compose.features.ContextMenuController
import org.jetbrains.jediterm.compose.getPlatformServices
import org.jetbrains.jediterm.compose.ime.IMEState
import org.jetbrains.jediterm.compose.settings.TerminalSettings

/**
 * Controller for managing multiple terminal tabs.
 *
 * This class is responsible for the lifecycle of terminal tabs, including:
 * - Creating new tabs with full terminal initialization
 * - Closing tabs with proper resource cleanup
 * - Switching between tabs
 * - Tracking working directories for tab inheritance
 *
 * Architecture:
 * - Each tab has independent PTY process, terminal state, and UI state
 * - Tabs run background jobs even when not visible
 * - Active tab receives keyboard focus and immediate updates
 * - Background tabs pause UI updates (performance optimization)
 */
class TabController(
    private val settings: TerminalSettings,
    private val onLastTabClosed: () -> Unit
) {
    /**
     * List of all terminal tabs (observable, triggers recomposition).
     */
    val tabs: SnapshotStateList<TerminalTab> = mutableStateListOf()

    /**
     * Index of the currently active tab (0-based).
     */
    var activeTabIndex by mutableStateOf(0)
        private set

    /**
     * Counter for generating unique tab titles (Shell 1, Shell 2, etc.).
     */
    private var tabCounter = 0

    /**
     * Get the currently active tab, or null if no tabs exist.
     */
    val activeTab: TerminalTab?
        get() = tabs.getOrNull(activeTabIndex)

    /**
     * Create a new terminal tab with optional working directory.
     *
     * @param workingDir Working directory to start the shell in (inherits from active tab if null)
     * @param command Shell command to execute (default: $SHELL or /bin/bash)
     * @param arguments Command-line arguments for the shell (default: empty)
     * @return The newly created TerminalTab
     */
    fun createTab(
        workingDir: String? = null,
        command: String = System.getenv("SHELL") ?: "/bin/bash",
        arguments: List<String> = emptyList()
    ): TerminalTab {
        tabCounter++

        // Initialize terminal components
        val styleState = StyleState()
        val textBuffer = TerminalTextBuffer(80, 24, styleState, settings.bufferMaxLines)
        val display = ComposeTerminalDisplay()
        val terminal = JediTerminal(display, textBuffer, styleState)
        val dataStream = BlockingTerminalDataStream()
        val emulator = JediEmulator(dataStream, terminal)

        // Create tab with all state
        val tab = TerminalTab(
            id = java.util.UUID.randomUUID().toString(),
            title = mutableStateOf("Shell $tabCounter"),
            terminal = terminal,
            textBuffer = textBuffer,
            display = display,
            dataStream = dataStream,
            emulator = emulator,
            processHandle = mutableStateOf(null),
            workingDirectory = mutableStateOf(workingDir),
            connectionState = mutableStateOf(ConnectionState.Initializing),
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            isFocused = mutableStateOf(false),
            scrollOffset = mutableStateOf(0),
            searchVisible = mutableStateOf(false),
            searchQuery = mutableStateOf(""),
            searchMatches = mutableStateOf(emptyList()),
            currentSearchMatchIndex = mutableStateOf(-1),
            selectionStart = mutableStateOf(null),
            selectionEnd = mutableStateOf(null),
            selectionClipboard = mutableStateOf(null),
            imeState = IMEState(),
            contextMenuController = ContextMenuController(),
            hyperlinks = mutableStateOf(emptyList()),
            hoveredHyperlink = mutableStateOf(null)
        )

        // Initialize the terminal session (spawn PTY, start coroutines)
        initializeTerminalSession(tab, workingDir, command, arguments)

        // Add to tabs list
        tabs.add(tab)

        // Switch to newly created tab
        switchToTab(tabs.size - 1)

        return tab
    }

    /**
     * Initialize a terminal session for a tab.
     * This spawns the PTY process and starts background coroutines for:
     * - Emulator processing (reads from dataStream, processes escape sequences)
     * - PTY output reading (reads from process, feeds dataStream)
     * - Process exit monitoring (auto-closes tab when shell exits)
     *
     * @param tab The tab to initialize
     * @param workingDir Working directory for the shell
     * @param command Shell command to execute
     * @param arguments Command-line arguments
     */
    private fun initializeTerminalSession(
        tab: TerminalTab,
        workingDir: String?,
        command: String,
        arguments: List<String>
    ) {
        tab.coroutineScope.launch(Dispatchers.IO) {
            try {
                val services = getPlatformServices()

                // Set TERM environment variables for TUI compatibility
                val terminalEnvironment = buildMap {
                    putAll(filterEnvironmentVariables(System.getenv()))
                    put("TERM", "xterm-256color")
                    put("COLORTERM", "truecolor")
                    put("TERM_PROGRAM", "JediTerm")
                }

                val config = PlatformServices.ProcessService.ProcessConfig(
                    command = command,
                    arguments = arguments,
                    environment = terminalEnvironment,
                    workingDirectory = workingDir ?: System.getProperty("user.home")
                )

                val handle = services.getProcessService().spawnProcess(config)

                if (handle == null) {
                    tab.connectionState.value = ConnectionState.Error(
                        message = "Failed to spawn process",
                        cause = null
                    )
                    return@launch
                }

                tab.processHandle.value = handle
                tab.connectionState.value = ConnectionState.Connected(handle)

                // Connect terminal output to PTY for bidirectional communication
                tab.terminal.setTerminalOutput(ProcessTerminalOutput(handle))

                // Start emulator processing coroutine
                launch(Dispatchers.Default) {
                    try {
                        while (handle.isAlive()) {
                            try {
                                tab.emulator.processChar(tab.dataStream.char, tab.terminal)
                                if (tab.isVisible.value) {
                                    tab.display.requestRedraw()
                                }
                            } catch (e: java.io.EOFException) {
                                break
                            } catch (e: Exception) {
                                if (e !is com.jediterm.terminal.TerminalDataStream.EOF) {
                                    println("WARNING: Error processing terminal output: ${e.message}")
                                }
                                break
                            }
                        }
                    } finally {
                        tab.dataStream.close()
                    }
                }

                // Read PTY output in background
                launch(Dispatchers.IO) {
                    val maxChunkSize = 64 * 1024

                    while (handle.isAlive()) {
                        val output = handle.read()
                        if (output != null) {
                            val processedOutput = if (output.length > maxChunkSize) {
                                println("WARNING: Process output chunk (${output.length} chars) exceeds limit, truncating")
                                output.substring(0, maxChunkSize)
                            } else {
                                output
                            }

                            tab.dataStream.append(processedOutput)
                        }
                    }
                    tab.dataStream.close()
                }

                // Monitor process exit
                handle.waitFor()  // Blocks until process exits
                println("INFO: Shell process exited for tab: ${tab.title.value}")

                // Auto-close tab when shell exits (as per user requirements)
                withContext(Dispatchers.Main) {
                    val tabIndex = tabs.indexOf(tab)
                    if (tabIndex != -1) {
                        closeTab(tabIndex)
                    }
                }

            } catch (e: Exception) {
                tab.connectionState.value = ConnectionState.Error(
                    message = "Terminal initialization failed: ${e.message ?: "Unknown error"}",
                    cause = e
                )
                println("ERROR: Terminal initialization failed for tab ${tab.title.value}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Close a tab by index.
     * - Cancels all coroutines
     * - Terminates PTY process
     * - Removes from tabs list
     * - Switches to adjacent tab or closes application if last tab
     *
     * @param index Index of the tab to close
     */
    fun closeTab(index: Int) {
        if (index < 0 || index >= tabs.size) return

        val tab = tabs[index]

        // Hold reference to process before tab disposal to prevent GC during kill()
        val processToKill = tab.processHandle.value

        // Clean up resources (cancels coroutines only, process kill handled below)
        tab.dispose()

        // Remove from list
        tabs.removeAt(index)

        // Kill process asynchronously with guaranteed reference
        // This prevents theoretical GC issue where tab might be GC'd before kill() completes
        if (processToKill != null) {
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    processToKill.kill()
                } catch (e: Exception) {
                    println("WARN: Error killing process: ${e.message}")
                }
            }
        }

        // Handle tab switching
        if (tabs.isEmpty()) {
            // Last tab closed - exit application
            onLastTabClosed()
        } else {
            // Adjust active tab index
            if (activeTabIndex >= tabs.size) {
                // Active tab was the last one, move to new last tab
                switchToTab(tabs.size - 1)
            } else if (activeTabIndex > index) {
                // Active tab is after the closed tab, decrement index
                activeTabIndex--
            } else if (activeTabIndex == index) {
                // Closed the active tab, switch to the same index (which is now the next tab)
                switchToTab(minOf(index, tabs.size - 1))
            }
        }
    }

    /**
     * Switch to a specific tab by index.
     *
     * @param index Index of the tab to switch to (0-based)
     */
    fun switchToTab(index: Int) {
        if (index < 0 || index >= tabs.size || index == activeTabIndex) return

        // Hide previous tab
        activeTab?.onHidden()

        // Switch active index
        activeTabIndex = index

        // Show new tab
        activeTab?.onVisible()
    }

    /**
     * Switch to the next tab (wraps around to first tab).
     */
    fun nextTab() {
        if (tabs.isEmpty()) return
        switchToTab((activeTabIndex + 1) % tabs.size)
    }

    /**
     * Switch to the previous tab (wraps around to last tab).
     */
    fun previousTab() {
        if (tabs.isEmpty()) return
        switchToTab((activeTabIndex - 1 + tabs.size) % tabs.size)
    }

    /**
     * Get the working directory of the currently active tab.
     * Returns null if no working directory is tracked (OSC 7 not received yet).
     */
    fun getActiveWorkingDirectory(): String? {
        return activeTab?.workingDirectory?.value
    }

    /**
     * Filter environment variables to remove potentially problematic ones.
     * (e.g., parent terminal's TERM variables that shouldn't be inherited)
     */
    private fun filterEnvironmentVariables(env: Map<String, String>): Map<String, String> {
        return env.filterKeys { key ->
            !key.startsWith("ITERM_") &&
            !key.startsWith("KITTY_") &&
            key != "TERM_SESSION_ID"
        }
    }

    /**
     * Routes terminal responses back to the PTY process.
     */
    private class ProcessTerminalOutput(
        private val processHandle: PlatformServices.ProcessService.ProcessHandle
    ) : com.jediterm.terminal.TerminalOutputStream {
        override fun sendBytes(response: ByteArray, userInput: Boolean) {
            kotlinx.coroutines.runBlocking {
                processHandle.write(String(response, Charsets.UTF_8))
            }
        }

        override fun sendString(string: String, userInput: Boolean) {
            kotlinx.coroutines.runBlocking {
                processHandle.write(string)
            }
        }
    }
}
