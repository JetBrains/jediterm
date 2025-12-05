package ai.rever.bossterm.compose

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ai.rever.bossterm.compose.demo.BlockingTerminalDataStream
import ai.rever.bossterm.compose.demo.ProperTerminal
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
 * Programmatic control:
 * ```kotlin
 * val state = rememberEmbeddableTerminalState()
 *
 * Button(onClick = { state.write("ls -la\n") }) {
 *     Text("Run ls")
 * }
 *
 * EmbeddableTerminal(state = state)
 * ```
 *
 * @param state Optional EmbeddableTerminalState for programmatic control
 * @param settingsPath Path to custom settings JSON file. If null, uses ~/.bossterm/settings.json
 * @param settings Direct TerminalSettings object. Overrides settingsPath if provided
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
    modifier: Modifier = Modifier
) {
    // Resolve settings: direct > path > default
    val resolvedSettings = remember(settings, settingsPath) {
        SettingsLoader.resolveSettings(settings, settingsPath)
    }

    // Effective shell command
    val effectiveCommand = command ?: System.getenv("SHELL") ?: "/bin/zsh"

    // Load font (shared across terminal instances)
    val terminalFont = remember {
        loadTerminalFont()
    }

    // Create internal session
    val session = remember {
        createTerminalSession(
            settings = resolvedSettings,
            onOutput = onOutput
        )
    }

    // Wire up state if provided
    LaunchedEffect(state, session) {
        state?.session = session
    }

    // Wire up title change callback
    LaunchedEffect(session, onTitleChange) {
        if (onTitleChange != null) {
            session.display.windowTitleFlow.collectLatest { title ->
                if (title.isNotEmpty()) {
                    onTitleChange(title)
                }
            }
        }
    }

    // Fire onReady when connected
    val connectionState by session.connectionState
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected && onReady != null) {
            onReady()
        }
    }

    // Initialize PTY process
    LaunchedEffect(session) {
        initializeProcess(
            session = session,
            command = effectiveCommand,
            workingDirectory = workingDirectory,
            environment = environment,
            onExit = onExit
        )
    }

    // Cleanup on dispose
    DisposableEffect(session) {
        onDispose {
            session.dispose()
            state?.session = null
        }
    }

    // Render terminal
    ProperTerminal(
        tab = session,
        isActiveTab = true,
        sharedFont = terminalFont,
        onTabTitleChange = { onTitleChange?.invoke(it) },
        modifier = modifier
    )
}

/**
 * State holder for controlling an EmbeddableTerminal programmatically.
 */
class EmbeddableTerminalState {
    internal var session: TerminalSession? by mutableStateOf(null)

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
     * Current scroll offset in lines from the bottom.
     */
    val scrollOffset: Int
        get() = session?.scrollOffset?.value ?: 0

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
 * @return EmbeddableTerminalState instance that persists across recompositions
 */
@Composable
fun rememberEmbeddableTerminalState(): EmbeddableTerminalState {
    return remember { EmbeddableTerminalState() }
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
