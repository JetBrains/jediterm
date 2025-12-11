package ai.rever.bossterm.compose

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ai.rever.bossterm.compose.terminal.BlockingTerminalDataStream
import ai.rever.bossterm.compose.ui.ProperTerminal
import ai.rever.bossterm.compose.util.loadTerminalFont
import ai.rever.bossterm.compose.features.ContextMenuController
import ai.rever.bossterm.compose.ime.IMEState
import ai.rever.bossterm.compose.settings.SettingsLoader
import ai.rever.bossterm.compose.settings.TerminalSettings
import ai.rever.bossterm.compose.tabs.TerminalTab
import ai.rever.bossterm.terminal.emulator.BossEmulator
import ai.rever.bossterm.terminal.model.BossTerminal
import ai.rever.bossterm.terminal.model.StyleState
import ai.rever.bossterm.terminal.model.TerminalTextBuffer

/**
 * Simplified Terminal composable for external integration.
 *
 * This provides a clean API for embedding a terminal in any Compose Desktop application,
 * abstracting away the complexity of session management, settings, and process lifecycle.
 *
 * Basic usage:
 * ```kotlin
 * EmbeddableTerminal()  // Uses default settings from ~/.bossterm/settings.json
 * ```
 *
 * Custom settings path:
 * ```kotlin
 * EmbeddableTerminal(settingsPath = "/path/to/my-settings.json")
 * ```
 *
 * Custom font (via settings):
 * ```kotlin
 * EmbeddableTerminal(settings = TerminalSettings(fontName = "JetBrains Mono"))
 * ```
 *
 * With callbacks:
 * ```kotlin
 * EmbeddableTerminal(
 *     onOutput = { output -> println("Output: $output") },
 *     onTitleChange = { title -> window.title = title },
 *     onExit = { code -> println("Shell exited: $code") },
 *     onReady = { println("Terminal ready!") }
 * )
 * ```
 *
 * Programmatic control with session preservation:
 * ```kotlin
 * // Session survives when EmbeddableTerminal leaves composition
 * val state = rememberEmbeddableTerminalState(autoDispose = false)
 *
 * if (showTerminal) {
 *     EmbeddableTerminal(state = state)
 * }
 * // Terminal process keeps running even when hidden!
 *
 * // Don't forget to dispose when truly done:
 * DisposableEffect(Unit) {
 *     onDispose { state.dispose() }
 * }
 * ```
 *
 * @param state Optional EmbeddableTerminalState for programmatic control and session preservation
 * @param settingsPath Path to custom settings JSON file. If null, uses ~/.bossterm/settings.json
 * @param settings Direct TerminalSettings object. Overrides settingsPath if provided.
 * @param command Shell command to run. Defaults to $SHELL or /bin/zsh
 * @param workingDirectory Initial working directory. Defaults to user home
 * @param environment Additional environment variables to set
 * @param onOutput Callback invoked when terminal produces output
 * @param onTitleChange Callback invoked when terminal title changes (OSC 0/1/2)
 * @param onExit Callback invoked when shell process exits with exit code
 * @param onReady Callback invoked when terminal is ready (process started)
 * @param modifier Compose modifier for the terminal container
 */
@Composable
fun EmbeddableTerminal(
    state: EmbeddableTerminalState? = null,
    settingsPath: String? = null,
    settings: TerminalSettings? = null,
    command: String? = null,
    workingDirectory: String? = null,
    environment: Map<String, String>? = null,
    onOutput: ((String) -> Unit)? = null,
    onTitleChange: ((String) -> Unit)? = null,
    onExit: ((Int) -> Unit)? = null,
    onReady: (() -> Unit)? = null,
    onNewWindow: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Use provided state or create auto-disposing one
    val effectiveState = state ?: rememberEmbeddableTerminalState(autoDispose = true)

    // Resolve settings: direct > path > default
    val resolvedSettings = remember(settings, settingsPath) {
        SettingsLoader.resolveSettings(settings, settingsPath)
    }

    // Effective shell command
    val effectiveCommand = command ?: System.getenv("SHELL") ?: "/bin/zsh"

    // Load font from settings.fontName or use default bundled font
    val terminalFont = remember(resolvedSettings.fontName) {
        loadTerminalFont(resolvedSettings.fontName)
    }

    // Initialize session if not already done (session lives in state, not composable)
    LaunchedEffect(effectiveState, resolvedSettings, effectiveCommand) {
        if (effectiveState.session == null) {
            effectiveState.initializeSession(
                settings = resolvedSettings,
                command = effectiveCommand,
                workingDirectory = workingDirectory,
                environment = environment,
                onOutput = onOutput,
                onExit = onExit
            )
        }
    }

    // Get current session (may be null during initialization)
    val session = effectiveState.session

    // Wire up title change callback
    LaunchedEffect(session, onTitleChange) {
        if (session != null && onTitleChange != null) {
            session.display.windowTitleFlow.collectLatest { title ->
                if (title.isNotEmpty()) {
                    onTitleChange(title)
                }
            }
        }
    }

    // Fire onReady when connected
    val connectionState = session?.connectionState?.value
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected && onReady != null) {
            onReady()
        }
    }

    // Render terminal if session exists
    if (session != null) {
        ProperTerminal(
            tab = session,
            isActiveTab = true,
            sharedFont = terminalFont,
            onTabTitleChange = { onTitleChange?.invoke(it) },
            onNewWindow = onNewWindow ?: {},
            modifier = modifier
        )
    }
}

/**
 * State holder for controlling an EmbeddableTerminal programmatically.
 *
 * The session lifecycle is owned by this state, not by the EmbeddableTerminal composable.
 * This means the terminal process survives when EmbeddableTerminal leaves the composition tree.
 *
 * Usage patterns:
 *
 * 1. Auto-dispose (default): Session disposed when state is forgotten
 * ```kotlin
 * val state = rememberEmbeddableTerminalState()  // autoDispose = true
 * EmbeddableTerminal(state = state)
 * ```
 *
 * 2. Manual lifecycle: Session preserved across navigation
 * ```kotlin
 * val state = rememberEmbeddableTerminalState(autoDispose = false)
 * // Must call state.dispose() when done!
 * ```
 *
 * 3. App-level state: Session outlives composition entirely
 * ```kotlin
 * // At app level (outside @Composable)
 * val terminalState = EmbeddableTerminalState()
 *
 * @Composable fun App() {
 *     EmbeddableTerminal(state = terminalState)
 *     DisposableEffect(Unit) { onDispose { terminalState.dispose() } }
 * }
 * ```
 */
class EmbeddableTerminalState {
    internal var session: TerminalTab? by mutableStateOf(null)
    private var initialized = false

    /**
     * Whether the terminal is connected to a shell process.
     */
    val isConnected: Boolean
        get() = session?.connectionState?.value is ConnectionState.Connected

    /**
     * Whether the terminal is currently initializing.
     */
    val isInitializing: Boolean
        get() = session?.connectionState?.value is ConnectionState.Initializing

    /**
     * Whether the session has been disposed.
     */
    val isDisposed: Boolean
        get() = session == null && initialized

    /**
     * Current scroll offset in lines from the bottom.
     */
    val scrollOffset: Int
        get() = session?.scrollOffset?.value ?: 0

    /**
     * Initialize the terminal session. Called automatically by EmbeddableTerminal.
     * Only initializes once; subsequent calls are no-ops.
     */
    internal fun initializeSession(
        settings: TerminalSettings,
        command: String,
        workingDirectory: String?,
        environment: Map<String, String>?,
        onOutput: ((String) -> Unit)?,
        onExit: ((Int) -> Unit)?
    ) {
        if (initialized) return
        initialized = true

        // Create session
        session = createTerminalSession(settings, onOutput)

        // Start process in session's coroutine scope
        session?.coroutineScope?.launch {
            initializeProcess(
                session = session!!,
                command = command,
                workingDirectory = workingDirectory,
                environment = environment,
                onExit = onExit
            )
        }
    }

    /**
     * Dispose the terminal session and kill the process.
     * After disposal, this state can be reused by calling EmbeddableTerminal again.
     */
    fun dispose() {
        session?.dispose()
        session = null
        initialized = false
    }

    /**
     * Send text input to the terminal.
     * Use "\n" for enter key.
     *
     * @param text Text to send to the shell
     */
    fun write(text: String) {
        session?.writeUserInput(text)
    }

    /**
     * Paste text to the terminal with proper bracketed paste mode handling.
     *
     * @param text Text to paste
     */
    fun paste(text: String) {
        session?.pasteText(text)
    }

    /**
     * Scroll to the bottom of the terminal (most recent output).
     */
    fun scrollToBottom() {
        session?.scrollOffset?.value = 0
    }

    /**
     * Scroll by a number of lines.
     *
     * @param lines Number of lines to scroll (positive = up into history, negative = down)
     */
    fun scrollBy(lines: Int) {
        session?.let { s ->
            val maxScroll = s.textBuffer.historyLinesCount
            val newOffset = (s.scrollOffset.value + lines).coerceIn(0, maxScroll)
            s.scrollOffset.value = newOffset
        }
    }

    /**
     * Clear the terminal screen.
     * Sends Ctrl+L (form feed) to trigger shell clear.
     */
    fun clear() {
        session?.writeUserInput("\u000C") // Ctrl+L
    }

    /**
     * Clear selection if any.
     */
    fun clearSelection() {
        session?.selectionStart?.value = null
        session?.selectionEnd?.value = null
    }

    /**
     * Toggle search bar visibility.
     */
    fun toggleSearch() {
        session?.let { s ->
            s.searchVisible.value = !s.searchVisible.value
        }
    }

    /**
     * Set search query and perform search.
     *
     * @param query Search query string
     */
    fun search(query: String) {
        session?.let { s ->
            s.searchQuery.value = query
            s.searchVisible.value = true
        }
    }
}

/**
 * Remember an EmbeddableTerminalState for controlling an EmbeddableTerminal composable.
 *
 * @param autoDispose If true (default), the session is disposed when this state is forgotten
 *                    (i.e., when the composable that called this leaves composition).
 *                    If false, you must manually call state.dispose() when done.
 * @return EmbeddableTerminalState instance that persists across recompositions
 */
@Composable
fun rememberEmbeddableTerminalState(autoDispose: Boolean = true): EmbeddableTerminalState {
    val state = remember { EmbeddableTerminalState() }

    if (autoDispose) {
        DisposableEffect(state) {
            onDispose { state.dispose() }
        }
    }

    return state
}

/**
 * Create a terminal session with all required components.
 */
private fun createTerminalSession(
    settings: TerminalSettings,
    onOutput: ((String) -> Unit)?
): TerminalTab {
    val styleState = StyleState()
    val textBuffer = TerminalTextBuffer(80, 24, styleState, settings.bufferMaxLines)
    val display = ComposeTerminalDisplay()
    val terminal = BossTerminal(display, textBuffer, styleState)

    // Register buffer listener for redraws
    textBuffer.addModelListener(object : ai.rever.bossterm.terminal.model.TerminalModelListener {
        override fun modelChanged() {
            display.requestImmediateRedraw()
        }
    })

    // Configure encoding
    terminal.setCharacterEncoding(settings.characterEncoding)

    val dataStream = BlockingTerminalDataStream()

    // Hook output callback
    if (onOutput != null) {
        dataStream.debugCallback = { data ->
            onOutput(data)
        }
    }

    // Wire up chunk batching to prevent intermediate state flickering
    // When a PTY chunk is received (e.g., \r\033[KText), all operations are batched
    // so the clear and write are treated as a single atomic update
    dataStream.onChunkStart = {
        textBuffer.beginBatch()
    }
    dataStream.onChunkEnd = {
        textBuffer.endBatch()
    }

    val emulator = BossEmulator(dataStream, terminal)
    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    return TerminalTab(
        title = mutableStateOf("Terminal"),
        terminal = terminal,
        textBuffer = textBuffer,
        display = display,
        dataStream = dataStream,
        emulator = emulator,
        processHandle = mutableStateOf(null),
        workingDirectory = mutableStateOf(null),
        connectionState = mutableStateOf(ConnectionState.Initializing),
        coroutineScope = coroutineScope,
        isFocused = mutableStateOf(true),
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
}

/**
 * Initialize PTY process for the session.
 */
private suspend fun initializeProcess(
    session: TerminalTab,
    command: String,
    workingDirectory: String?,
    environment: Map<String, String>?,
    onExit: ((Int) -> Unit)?
) {
    try {
        val services = getPlatformServices()

        // Determine shell arguments (login shell)
        val args = if (command.endsWith("/zsh") || command.endsWith("/bash") ||
            command == "zsh" || command == "bash") {
            listOf("-l")
        } else {
            emptyList()
        }

        // Build environment
        val terminalEnvironment = buildMap {
            putAll(System.getenv())
            put("TERM", "xterm-256color")
            put("COLORTERM", "truecolor")
            put("TERM_PROGRAM", "BossTerm")
            environment?.let { putAll(it) }
        }

        // Create process config
        val processConfig = PlatformServices.ProcessService.ProcessConfig(
            command = command,
            arguments = args,
            environment = terminalEnvironment,
            workingDirectory = workingDirectory ?: System.getProperty("user.home")
        )

        // Spawn PTY process
        val processHandle = services.getProcessService().spawnProcess(processConfig)

        if (processHandle == null) {
            session.connectionState.value = ConnectionState.Error("Failed to spawn process")
            return
        }

        session.processHandle.value = processHandle
        session.connectionState.value = ConnectionState.Connected(processHandle)

        // Start emulator coroutine
        session.coroutineScope.launch(Dispatchers.Default) {
            try {
                while (processHandle.isAlive()) {
                    try {
                        session.emulator.processChar(session.dataStream.char, session.terminal)
                    } catch (e: java.io.EOFException) {
                        break
                    } catch (e: Exception) {
                        if (e !is ai.rever.bossterm.terminal.TerminalDataStream.EOF) {
                            println("WARNING: Error processing terminal output: ${e.message}")
                        }
                        break
                    }
                }
            } finally {
                session.dataStream.close()
            }
        }

        // Start output reader coroutine
        session.coroutineScope.launch(Dispatchers.IO) {
            while (processHandle.isAlive()) {
                val output = processHandle.read()
                if (output != null) {
                    session.dataStream.append(output)
                }
            }
            session.dataStream.close()
        }

        // Monitor process exit
        session.coroutineScope.launch(Dispatchers.IO) {
            val exitCode = processHandle.waitFor()
            session.connectionState.value = ConnectionState.Error("Process exited with code $exitCode")
            onExit?.invoke(exitCode)
        }

    } catch (e: Exception) {
        session.connectionState.value = ConnectionState.Error(e.message ?: "Failed to start process")
    }
}
