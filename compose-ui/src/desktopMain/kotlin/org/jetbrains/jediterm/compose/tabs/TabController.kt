package org.jetbrains.jediterm.compose.tabs

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.emulator.JediEmulator
import com.jediterm.terminal.model.JediTerminal
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalApplicationTitleListener
import com.jediterm.terminal.model.TerminalTextBuffer
import kotlinx.coroutines.*
import org.jetbrains.jediterm.compose.ComposeQuestioner
import org.jetbrains.jediterm.compose.ComposeTerminalDisplay
import org.jetbrains.jediterm.compose.ConnectionState
import org.jetbrains.jediterm.compose.PlatformServices
import org.jetbrains.jediterm.compose.debug.ChunkSource
import org.jetbrains.jediterm.compose.demo.BlockingTerminalDataStream
import org.jetbrains.jediterm.compose.features.ContextMenuController
import org.jetbrains.jediterm.compose.getPlatformServices
import org.jetbrains.jediterm.compose.ime.IMEState
import org.jetbrains.jediterm.compose.osc.WorkingDirectoryOSCListener
import org.jetbrains.jediterm.compose.settings.TerminalSettings
import org.jetbrains.jediterm.compose.typeahead.ComposeTypeAheadModel
import org.jetbrains.jediterm.compose.typeahead.CoroutineDebouncer
import com.jediterm.core.typeahead.TerminalTypeAheadManager
import com.jediterm.core.typeahead.TypeAheadTerminalModel
import com.jediterm.terminal.util.GraphemeBoundaryUtils
import java.io.EOFException

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
     * Registered session lifecycle listeners.
     * Thread-safe: uses CopyOnWriteArrayList for safe iteration during modification.
     */
    private val sessionListeners = java.util.concurrent.CopyOnWriteArrayList<TerminalSessionListener>()

    /**
     * Add a session lifecycle listener.
     *
     * @param listener The listener to add
     */
    fun addSessionListener(listener: TerminalSessionListener) {
        sessionListeners.add(listener)
    }

    /**
     * Remove a session lifecycle listener.
     *
     * @param listener The listener to remove
     */
    fun removeSessionListener(listener: TerminalSessionListener) {
        sessionListeners.remove(listener)
    }

    /**
     * Notify all listeners that a session was created.
     */
    private fun notifySessionCreated(session: TerminalTab) {
        sessionListeners.forEach { listener ->
            try {
                listener.onSessionCreated(session)
            } catch (e: Exception) {
                println("WARN: Session listener threw exception in onSessionCreated: ${e.message}")
            }
        }
    }

    /**
     * Notify all listeners that a session was closed.
     */
    private fun notifySessionClosed(session: TerminalTab) {
        sessionListeners.forEach { listener ->
            try {
                listener.onSessionClosed(session)
            } catch (e: Exception) {
                println("WARN: Session listener threw exception in onSessionClosed: ${e.message}")
            }
        }
    }

    /**
     * Notify all listeners that all sessions have been closed.
     */
    private fun notifyAllSessionsClosed() {
        sessionListeners.forEach { listener ->
            try {
                listener.onAllSessionsClosed()
            } catch (e: Exception) {
                println("WARN: Session listener threw exception in onAllSessionsClosed: ${e.message}")
            }
        }
    }

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
     * @param onProcessExit Callback invoked when shell process exits (before auto-closing tab)
     * @return The newly created TerminalTab
     */
    fun createTab(
        workingDir: String? = null,
        command: String = System.getenv("SHELL") ?: "/bin/bash",
        arguments: List<String> = emptyList(),
        onProcessExit: (() -> Unit)? = null
    ): TerminalTab {
        tabCounter++

        // Initialize terminal components
        val styleState = StyleState()
        val textBuffer = TerminalTextBuffer(80, 24, styleState, settings.bufferMaxLines)
        val display = ComposeTerminalDisplay()
        val terminal = JediTerminal(display, textBuffer, styleState)

        // CRITICAL: Register ModelListener to trigger redraws when buffer content changes
        // This is how the Swing TerminalPanel gets notified - without this, the display
        // never knows when to redraw after new text is written to the buffer!
        textBuffer.addModelListener(object : com.jediterm.terminal.model.TerminalModelListener {
            override fun modelChanged() {
                display.requestImmediateRedraw()
            }
        })

        // Configure character encoding mode (ISO-8859-1 enables GR mapping, UTF-8 disables it)
        terminal.setCharacterEncoding(settings.characterEncoding)

        val dataStream = BlockingTerminalDataStream()

        // Create working directory state
        val workingDirectoryState = mutableStateOf<String?>(workingDir)

        // Register OSC 7 listener for working directory tracking (Phase 4)
        val oscListener = WorkingDirectoryOSCListener(workingDirectoryState)
        terminal.addCustomCommandListener(oscListener)

        // Register window title listener for reactive updates (OSC 0/1/2 sequences)
        terminal.addApplicationTitleListener(object : TerminalApplicationTitleListener {
            override fun onApplicationTitleChanged(newApplicationTitle: String) {
                display.windowTitle = newApplicationTitle
            }

            override fun onApplicationIconTitleChanged(newIconTitle: String) {
                display.iconTitle = newIconTitle
            }
        })

        // Create emulator with terminal
        val emulator = JediEmulator(dataStream, terminal)

        // Create debug collector if enabled (before tab creation to avoid circular dependency)
        val debugCollector = if (settings.debugModeEnabled) {
            org.jetbrains.jediterm.compose.debug.DebugDataCollector(
                tab = null,  // Will be set after tab creation
                maxChunks = settings.debugMaxChunks,
                maxSnapshots = settings.debugMaxSnapshots
            )
        } else {
            null
        }

        // Create type-ahead model and manager if enabled
        val typeAheadModel = if (settings.typeAheadEnabled) {
            ComposeTypeAheadModel(
                terminal = terminal,
                textBuffer = textBuffer,
                display = display,
                settings = settings
            ).also { model ->
                // Detect shell type for word boundary calculation (bash vs zsh)
                val shellType = TypeAheadTerminalModel.commandLineToShellType((listOf(command) + arguments).toMutableList())
                model.setShellType(shellType)
            }
        } else {
            null
        }

        // Create coroutine scope for type-ahead (will be shared with tab scope)
        val tabCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        val typeAheadManager = typeAheadModel?.let { model ->
            TerminalTypeAheadManager(model).also { manager ->
                // Set up coroutine-based debouncer for clearing stale predictions
                val debouncer = CoroutineDebouncer(
                    action = manager::debounce,
                    delayNanos = TerminalTypeAheadManager.MAX_TERMINAL_DELAY,
                    scope = tabCoroutineScope
                )
                manager.setClearPredictionsDebouncer(debouncer)
            }
        }

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
            workingDirectory = workingDirectoryState,
            connectionState = mutableStateOf(ConnectionState.Initializing),
            onProcessExit = onProcessExit,
            coroutineScope = tabCoroutineScope,
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
            hoveredHyperlink = mutableStateOf(null),
            debugEnabled = mutableStateOf(settings.debugModeEnabled),
            debugCollector = debugCollector,
            typeAheadModel = typeAheadModel,
            typeAheadManager = typeAheadManager
        )

        // Complete debug collector initialization
        debugCollector?.let { collector ->
            // Set the tab reference now that tab is created
            collector.setTab(tab)

            // Hook into data stream for PTY output capture
            dataStream.debugCallback = { data ->
                collector.recordChunk(data, ChunkSource.PTY_OUTPUT)
            }
        }

        // Connect type-ahead manager to PTY arrival notifications
        typeAheadManager?.let { manager ->
            dataStream.onTerminalStateChanged = {
                manager.onTerminalStateChanged()
            }
        }

        // Initialize the terminal session (spawn PTY, start coroutines)
        initializeTerminalSession(tab, workingDir, command, arguments)

        // Add to tabs list
        tabs.add(tab)

        // Notify listeners about new session
        notifySessionCreated(tab)

        // Switch to newly created tab
        switchToTab(tabs.size - 1)

        return tab
    }

    /**
     * Pre-connection configuration collected from user input.
     */
    data class PreConnectConfig(
        val command: String,
        val arguments: List<String> = emptyList(),
        val workingDir: String? = null,
        val environment: Map<String, String> = emptyMap()
    )

    /**
     * Create a new terminal tab with pre-connection user prompts.
     *
     * This method allows interactive setup before the PTY is spawned, useful for:
     * - SSH connections requiring passwords or 2FA
     * - Custom host/port selection
     * - Environment variable configuration
     *
     * The preConnectHandler receives a ComposeQuestioner that can prompt for input.
     * The handler returns either a PreConnectConfig to proceed, or null to cancel.
     *
     * Example:
     * ```kotlin
     * tabController.createTabWithPreConnect { questioner ->
     *     // Use dropdown for connection type selection
     *     val connectionType = questioner.questionSelection(
     *         prompt = "Select connection type:",
     *         options = listOf(
     *             ConnectionState.SelectOption("ssh", "SSH"),
     *             ConnectionState.SelectOption("local", "Local Shell")
     *         )
     *     ) ?: return@createTabWithPreConnect null  // Cancelled
     *
     *     val host = questioner.questionVisible("Enter SSH host:", "localhost")
     *     val password = questioner.questionHidden("Enter password:")
     *     if (password == null) return@createTabWithPreConnect null
     *
     *     questioner.showMessage("Connecting to $host...")
     *     PreConnectConfig(
     *         command = "ssh",
     *         arguments = listOf("-l", "user", host)
     *     )
     * }
     * ```
     *
     * @param onProcessExit Optional callback invoked when shell process exits
     * @param preConnectHandler Suspend function to gather configuration via user prompts
     * @return The newly created TerminalTab, or null if user cancelled
     */
    @Suppress("DEPRECATION")
    fun createTabWithPreConnect(
        onProcessExit: (() -> Unit)? = null,
        preConnectHandler: suspend (ComposeQuestioner) -> PreConnectConfig?
    ): TerminalTab {
        tabCounter++

        // Initialize terminal components (same as createTab)
        val styleState = StyleState()
        val textBuffer = TerminalTextBuffer(80, 24, styleState, settings.bufferMaxLines)
        val display = ComposeTerminalDisplay()
        val terminal = JediTerminal(display, textBuffer, styleState)

        textBuffer.addModelListener(object : com.jediterm.terminal.model.TerminalModelListener {
            override fun modelChanged() {
                display.requestImmediateRedraw()
            }
        })

        terminal.setCharacterEncoding(settings.characterEncoding)

        val dataStream = BlockingTerminalDataStream()
        val workingDirectoryState = mutableStateOf<String?>(null)

        val oscListener = WorkingDirectoryOSCListener(workingDirectoryState)
        terminal.addCustomCommandListener(oscListener)

        terminal.addApplicationTitleListener(object : TerminalApplicationTitleListener {
            override fun onApplicationTitleChanged(newApplicationTitle: String) {
                display.windowTitle = newApplicationTitle
            }

            override fun onApplicationIconTitleChanged(newIconTitle: String) {
                display.iconTitle = newIconTitle
            }
        })

        val emulator = JediEmulator(dataStream, terminal)

        val debugCollector = if (settings.debugModeEnabled) {
            org.jetbrains.jediterm.compose.debug.DebugDataCollector(
                tab = null,
                maxChunks = settings.debugMaxChunks,
                maxSnapshots = settings.debugMaxSnapshots
            )
        } else {
            null
        }

        val tabCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        // Create tab with Initializing state
        val tab = TerminalTab(
            id = java.util.UUID.randomUUID().toString(),
            title = mutableStateOf("Shell $tabCounter"),
            terminal = terminal,
            textBuffer = textBuffer,
            display = display,
            dataStream = dataStream,
            emulator = emulator,
            processHandle = mutableStateOf(null),
            workingDirectory = workingDirectoryState,
            connectionState = mutableStateOf(ConnectionState.Initializing),
            onProcessExit = onProcessExit,
            coroutineScope = tabCoroutineScope,
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
            hoveredHyperlink = mutableStateOf(null),
            debugEnabled = mutableStateOf(settings.debugModeEnabled),
            debugCollector = debugCollector,
            typeAheadModel = null,  // Type-ahead configured after preConnect
            typeAheadManager = null
        )

        debugCollector?.let { collector ->
            collector.setTab(tab)
            dataStream.debugCallback = { data ->
                collector.recordChunk(data, ChunkSource.PTY_OUTPUT)
            }
        }

        // Add to tabs list
        tabs.add(tab)

        // Notify listeners about new session
        notifySessionCreated(tab)

        switchToTab(tabs.size - 1)

        // Run pre-connection handler in coroutine
        tab.coroutineScope.launch(Dispatchers.IO) {
            try {
                // Create questioner that updates tab's connection state
                val questioner = ComposeQuestioner { newState ->
                    tab.connectionState.value = newState
                }

                // Get configuration from user (may prompt for input)
                val config = preConnectHandler(questioner)

                if (config == null) {
                    // User cancelled - close tab
                    withContext(Dispatchers.Main) {
                        val tabIndex = tabs.indexOf(tab)
                        if (tabIndex != -1) {
                            closeTab(tabIndex)
                        }
                    }
                    return@launch
                }

                // Update working directory from config
                if (config.workingDir != null) {
                    workingDirectoryState.value = config.workingDir
                }

                // Initialize terminal session with collected config
                initializeTerminalSessionWithConfig(tab, config)

            } catch (e: Exception) {
                tab.connectionState.value = ConnectionState.Error(
                    message = "Pre-connection setup failed: ${e.message ?: "Unknown error"}",
                    cause = e
                )
            }
        }

        return tab
    }

    /**
     * Initialize terminal session with pre-collected configuration.
     */
    private suspend fun initializeTerminalSessionWithConfig(
        tab: TerminalTab,
        config: PreConnectConfig
    ) {
        try {
            val services = getPlatformServices()

            // Set TERM environment variables for TUI compatibility
            val terminalEnvironment = buildMap {
                putAll(filterEnvironmentVariables(System.getenv()))
                put("TERM", "xterm-256color")
                put("COLORTERM", "truecolor")
                put("TERM_PROGRAM", "JediTerm")
                put("TERM_FEATURES", "T2:M:H:Ts0:Ts1:Ts2:Sc0:Sc1:Sc2:B:U:Aw")
                putAll(config.environment)
            }

            val processConfig = PlatformServices.ProcessService.ProcessConfig(
                command = config.command,
                arguments = config.arguments,
                environment = terminalEnvironment,
                workingDirectory = config.workingDir ?: System.getProperty("user.home")
            )

            val handle = services.getProcessService().spawnProcess(processConfig)

            if (handle == null) {
                tab.connectionState.value = ConnectionState.Error(
                    message = "Failed to spawn process",
                    cause = null
                )
                return
            }

            tab.processHandle.value = handle
            tab.connectionState.value = ConnectionState.Connected(handle)

            // Connect terminal output to PTY
            tab.terminal.setTerminalOutput(ProcessTerminalOutput(handle, tab))

            // Configure type-ahead if enabled
            if (settings.typeAheadEnabled) {
                val typeAheadModel = ComposeTypeAheadModel(
                    terminal = tab.terminal,
                    textBuffer = tab.textBuffer,
                    display = tab.display,
                    settings = settings
                ).also { model ->
                    val shellType = TypeAheadTerminalModel.commandLineToShellType(
                        (listOf(config.command) + config.arguments).toMutableList()
                    )
                    model.setShellType(shellType)
                }

                val typeAheadManager = TerminalTypeAheadManager(typeAheadModel).also { manager ->
                    val debouncer = CoroutineDebouncer(
                        action = manager::debounce,
                        delayNanos = TerminalTypeAheadManager.MAX_TERMINAL_DELAY,
                        scope = tab.coroutineScope
                    )
                    manager.setClearPredictionsDebouncer(debouncer)
                }

                tab.dataStream.onTerminalStateChanged = {
                    typeAheadManager.onTerminalStateChanged()
                }
            }

            // Start emulator processing coroutine
            tab.coroutineScope.launch(Dispatchers.Default) {
                try {
                    while (handle.isAlive()) {
                        try {
                            tab.emulator.processChar(tab.dataStream.char, tab.terminal)
                        } catch (_: EOFException) {
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
            tab.coroutineScope.launch(Dispatchers.IO) {
                val maxChunkSize = 64 * 1024

                while (handle.isAlive()) {
                    val output = handle.read()
                    if (output != null) {
                        val processedOutput = if (output.length > maxChunkSize) {
                            val safeBoundary = GraphemeBoundaryUtils.findLastCompleteGraphemeBoundary(output, maxChunkSize)
                            output.substring(0, safeBoundary)
                        } else {
                            output
                        }
                        tab.dataStream.append(processedOutput)
                    }
                }
                tab.dataStream.close()
            }

            // Start debug state capture coroutine if enabled
            tab.debugCollector?.let { collector ->
                tab.coroutineScope.launch(Dispatchers.IO) {
                    try {
                        while (handle.isAlive() && isActive) {
                            delay(settings.debugCaptureInterval)
                            collector.captureState()
                        }
                    } catch (e: Exception) {
                        println("DEBUG: State capture coroutine stopped: ${e.message}")
                    }
                }
            }

            // Monitor process exit
            handle.waitFor()
            println("INFO: Shell process exited for tab: ${tab.title.value}")

            withContext(Dispatchers.Main) {
                tab.onProcessExit?.invoke()
            }

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
                    put("TERM_FEATURES", "T2:M:H:Ts0:Ts1:Ts2:Sc0:Sc1:Sc2:B:U:Aw")
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
                tab.terminal.setTerminalOutput(ProcessTerminalOutput(handle, tab))

                // Start emulator processing coroutine
                // Note: Initial prompt will display via ModelListener → requestImmediateRedraw()
                // when buffer content changes. No need for premature redraw here.
                launch(Dispatchers.Default) {
                    try {
                        while (handle.isAlive()) {
                            try {
                                tab.emulator.processChar(tab.dataStream.char, tab.terminal)
                                // Note: Redraws are triggered by scrollArea() when buffer changes
                                // No need for explicit requestRedraw() here - it causes redundant requests
                                // scrollArea() now uses smart priority (IMMEDIATE for interactive, debounced for bulk)
                            } catch (_: EOFException) {
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
                                // Find the last complete grapheme boundary before maxChunkSize
                                // to avoid splitting emoji, surrogate pairs, or ZWJ sequences
                                val safeBoundary = GraphemeBoundaryUtils.findLastCompleteGraphemeBoundary(output, maxChunkSize)

                                val truncatedLength = output.length - safeBoundary
                                println("WARNING: Process output chunk (${output.length} chars) exceeds limit, " +
                                        "truncating at grapheme boundary (safe: $safeBoundary chars, " +
                                        "buffering $truncatedLength chars for next chunk)")

                                output.substring(0, safeBoundary)
                            } else {
                                output
                            }

                            tab.dataStream.append(processedOutput)
                        }
                    }
                    tab.dataStream.close()
                }

                // Start debug state capture coroutine if enabled
                tab.debugCollector?.let { collector ->
                    launch(Dispatchers.IO) {
                        try {
                            while (handle.isAlive() && isActive) {
                                delay(settings.debugCaptureInterval)
                                collector.captureState()
                            }
                        } catch (e: Exception) {
                            println("DEBUG: State capture coroutine stopped: ${e.message}")
                        }
                    }
                }

                // Monitor process exit
                handle.waitFor()  // Blocks until process exits
                println("INFO: Shell process exited for tab: ${tab.title.value}")

                // Call onProcessExit callback for custom cleanup/logging (if provided)
                withContext(Dispatchers.Main) {
                    tab.onProcessExit?.invoke()
                }

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
    @OptIn(DelicateCoroutinesApi::class)
    fun closeTab(index: Int) {
        if (index < 0 || index >= tabs.size) return

        val tab = tabs[index]

        // Hold reference to process before tab disposal to prevent GC during kill()
        val processToKill = tab.processHandle.value

        // Clean up resources (cancels coroutines only, process kill handled below)
        tab.dispose()

        // Remove from list
        tabs.removeAt(index)

        // Notify listeners about session closure (after removal so tab count is accurate)
        notifySessionClosed(tab)

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
            // Notify listeners that all sessions are closed
            notifyAllSessionsClosed()
            // Last tab closed - exit application (legacy callback)
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
     * Also records emulator-generated output in debug mode.
     */
    private class ProcessTerminalOutput(
        private val processHandle: PlatformServices.ProcessService.ProcessHandle,
        private val tab: TerminalTab
    ) : com.jediterm.terminal.TerminalOutputStream {
        override fun sendBytes(response: ByteArray, userInput: Boolean) {
            // Record emulator-generated responses in debug mode
            if (!userInput) {
                tab.debugCollector?.recordChunk(
                    String(response, Charsets.UTF_8),
                    org.jetbrains.jediterm.compose.debug.ChunkSource.EMULATOR_GENERATED
                )
            }

            kotlinx.coroutines.runBlocking {
                processHandle.write(String(response, Charsets.UTF_8))
            }
        }

        override fun sendString(string: String, userInput: Boolean) {
            // Record emulator-generated responses in debug mode
            if (!userInput) {
                tab.debugCollector?.recordChunk(
                    string,
                    org.jetbrains.jediterm.compose.debug.ChunkSource.EMULATOR_GENERATED
                )
            }

            kotlinx.coroutines.runBlocking {
                processHandle.write(string)
            }
        }
    }
}
