package org.jetbrains.jediterm.compose.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.jediterm.compose.ComposeTerminalDisplay
import org.jetbrains.jediterm.compose.PlatformServices
import org.jetbrains.jediterm.compose.getPlatformServices
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.emulator.JediEmulator
import com.jediterm.terminal.model.JediTerminal
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.RequestOrigin
import com.jediterm.terminal.TextStyle as JediTextStyle
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.util.CharUtils
import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.TerminalKeyEncoder
import com.jediterm.core.input.InputEvent
import java.awt.event.KeyEvent as JavaKeyEvent
import com.jediterm.terminal.emulator.ColorPaletteImpl
import org.jetbrains.jediterm.compose.settings.SettingsManager
import org.jetbrains.jediterm.compose.search.SearchBar
import org.jetbrains.jediterm.compose.hyperlinks.HyperlinkDetector
import org.jetbrains.jediterm.compose.hyperlinks.Hyperlink
import org.jetbrains.jediterm.compose.ime.IMEHandler
import org.jetbrains.jediterm.compose.ime.IMEState
import org.jetbrains.jediterm.compose.features.ContextMenuController
import org.jetbrains.jediterm.compose.features.ContextMenuPopup
import org.jetbrains.jediterm.compose.features.showTerminalContextMenu
import androidx.compose.ui.Alignment

/**
 * Maps Compose Desktop Key constants to Java AWT VK (Virtual Key) codes.
 * Returns null for keys that don't have a direct VK mapping.
 */
private fun mapComposeKeyToVK(key: Key): Int? {
    return when (key) {
        Key.Enter -> JavaKeyEvent.VK_ENTER
        Key.Backspace -> JavaKeyEvent.VK_BACK_SPACE
        Key.Tab -> JavaKeyEvent.VK_TAB
        Key.Escape -> JavaKeyEvent.VK_ESCAPE
        Key.DirectionUp -> JavaKeyEvent.VK_UP
        Key.DirectionDown -> JavaKeyEvent.VK_DOWN
        Key.DirectionLeft -> JavaKeyEvent.VK_LEFT
        Key.DirectionRight -> JavaKeyEvent.VK_RIGHT
        Key.Home -> JavaKeyEvent.VK_HOME
        Key.MoveEnd -> JavaKeyEvent.VK_END
        Key.PageUp -> JavaKeyEvent.VK_PAGE_UP
        Key.PageDown -> JavaKeyEvent.VK_PAGE_DOWN
        Key.Insert -> JavaKeyEvent.VK_INSERT
        Key.Delete -> JavaKeyEvent.VK_DELETE
        Key.F1 -> JavaKeyEvent.VK_F1
        Key.F2 -> JavaKeyEvent.VK_F2
        Key.F3 -> JavaKeyEvent.VK_F3
        Key.F4 -> JavaKeyEvent.VK_F4
        Key.F5 -> JavaKeyEvent.VK_F5
        Key.F6 -> JavaKeyEvent.VK_F6
        Key.F7 -> JavaKeyEvent.VK_F7
        Key.F8 -> JavaKeyEvent.VK_F8
        Key.F9 -> JavaKeyEvent.VK_F9
        Key.F10 -> JavaKeyEvent.VK_F10
        Key.F11 -> JavaKeyEvent.VK_F11
        Key.F12 -> JavaKeyEvent.VK_F12
        else -> null
    }
}

/**
 * Maps Compose Desktop key event modifiers to JediTerm InputEvent modifier masks.
 * Note: Using JediTerm's InputEvent constants (SHIFT_MASK, etc.) not Java AWT's
 * SHIFT_DOWN_MASK, as TerminalKeyEncoder expects the old Event mask values.
 */
private fun mapComposeModifiers(keyEvent: androidx.compose.ui.input.key.KeyEvent): Int {
    var modifiers = 0
    if (keyEvent.isShiftPressed) modifiers = modifiers or InputEvent.SHIFT_MASK
    if (keyEvent.isCtrlPressed) modifiers = modifiers or InputEvent.CTRL_MASK
    if (keyEvent.isAltPressed) modifiers = modifiers or InputEvent.ALT_MASK
    if (keyEvent.isMetaPressed) modifiers = modifiers or InputEvent.META_MASK
    return modifiers
}

/**
 * Proper terminal implementation using JediTerm's emulator.
 * This uses the real JediTerminal, JediEmulator, and TerminalTextBuffer from the core module.
 */
@OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.ui.text.ExperimentalTextApi::class
)
@Composable
fun ProperTerminal(
    command: String = System.getenv("SHELL") ?: "/bin/bash",
    arguments: List<String> = listOf("--login"),
    modifier: Modifier = Modifier
) {
    var processHandle by remember { mutableStateOf<PlatformServices.ProcessService.ProcessHandle?>(null) }
    var isFocused by remember { mutableStateOf(false) }
    var scrollOffset by remember { mutableStateOf(0) }  // 0 = at bottom, positive = scrolled up into history
    val scope = rememberCoroutineScope()
    var resizeJob by remember { mutableStateOf<Job?>(null) }  // For debouncing resize operations
    val focusRequester = remember { FocusRequester() }
    val textMeasurer = rememberTextMeasurer()
    val clipboardManager = LocalClipboardManager.current

    // Settings integration
    val settingsManager = remember { SettingsManager.instance }
    val settings by settingsManager.settings.collectAsState()

    // Create JediTerm components
    val styleState = remember { StyleState() }
    val textBuffer = remember { TerminalTextBuffer(80, 24, styleState) }
    val display = remember { ComposeTerminalDisplay() }
    val terminal = remember { JediTerminal(display, textBuffer, styleState) }

    // Search state
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchCaseSensitive by remember { mutableStateOf(settings.searchCaseSensitive) }
    var searchMatches by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) } // (col, row)
    var currentMatchIndex by remember { mutableStateOf(-1) }

    // IME (Input Method Editor) state for CJK input
    val imeState = remember { IMEState() }

    // Text selection state (needed by search helper functions)
    var selectionStart by remember { mutableStateOf<Pair<Int, Int>?>(null) }  // (col, row)
    var selectionEnd by remember { mutableStateOf<Pair<Int, Int>?>(null) }    // (col, row)

    // Scroll terminal to show a search match
    fun scrollToMatch(matchRow: Int) {
        val screenHeight = textBuffer.height
        val historySize = textBuffer.historyLinesCount

        when {
            matchRow < 0 -> {
                // Match is in history, scroll up to center it
                val targetOffset = -matchRow - (screenHeight / 2)
                scrollOffset = targetOffset.coerceIn(0, historySize)
            }
            matchRow >= screenHeight -> {
                // Match is below screen, scroll to bottom
                scrollOffset = 0
            }
            else -> {
                // Match is in screen buffer, check if visible
                val visibleRowStart = -scrollOffset
                val visibleRowEnd = visibleRowStart + screenHeight

                if (matchRow !in visibleRowStart until visibleRowEnd) {
                    // Not visible, center it
                    val targetOffset = kotlin.math.max(0, -matchRow + (screenHeight / 2))
                    scrollOffset = targetOffset.coerceIn(0, historySize)
                }
            }
        }
        display.requestImmediateRedraw()
    }

    // Highlight a search match using selection
    fun highlightMatch(matchCol: Int, matchRow: Int, matchLength: Int) {
        // Convert buffer coordinates to screen coordinates
        val screenRow = matchRow + scrollOffset

        // Set selection to highlight the match
        selectionStart = Pair(matchCol, screenRow)
        selectionEnd = Pair(matchCol + matchLength - 1, screenRow)

        display.requestImmediateRedraw()
    }

    // Search function
    fun performSearch() {
        if (searchQuery.isEmpty()) {
            searchMatches = emptyList()
            currentMatchIndex = -1
            return
        }

        val matches = mutableListOf<Pair<Int, Int>>()
        val buffer = terminal.terminalTextBuffer
        buffer.lock()
        try {
            val lineCount = buffer.historyLinesCount + buffer.height
            for (row in -buffer.historyLinesCount until buffer.height) {
                val line = buffer.getLine(row)
                if (line != null) {
                    val text = line.text
                    val searchText = if (searchCaseSensitive) searchQuery else searchQuery.lowercase()
                    val lineText = if (searchCaseSensitive) text else text.lowercase()

                    var index = 0
                    while (index >= 0) {
                        index = lineText.indexOf(searchText, index)
                        if (index >= 0) {
                            matches.add(Pair(index, row))
                            index += searchQuery.length
                        }
                    }
                }
            }
        } finally {
            buffer.unlock()
        }

        searchMatches = matches
        currentMatchIndex = if (matches.isNotEmpty()) {
            // Scroll to and highlight the first match
            val (col, row) = matches[0]
            scrollToMatch(row)
            highlightMatch(col, row, searchQuery.length)
            0
        } else -1
    }

    // Trigger search on query change
    LaunchedEffect(searchQuery, searchCaseSensitive) {
        performSearch()
    }

    // Hyperlink state
    var hoveredHyperlink by remember { mutableStateOf<Hyperlink?>(null) }
    var cachedHyperlinks by remember { mutableStateOf<Map<Int, List<Hyperlink>>>(emptyMap()) }

    // Create single long-lived data stream and emulator to preserve state across chunk boundaries
    // This prevents CSI sequences from being truncated when they span multiple output chunks
    val dataStream = remember { BlockingTerminalDataStream() }
    val emulator = remember { JediEmulator(dataStream, terminal) }

    // Terminal key encoder for proper escape sequence generation (function keys, modifiers, etc.)
    val keyEncoder = remember { TerminalKeyEncoder() }

    /**
     * Routes terminal responses (DSR, device attributes, mouse events, etc.) back to the PTY process.
     * This enables bidirectional communication required by TUI applications like vim, less, codex.
     */
    class ProcessTerminalOutput(
        private val processHandle: PlatformServices.ProcessService.ProcessHandle?
    ) : com.jediterm.terminal.TerminalOutputStream {
        override fun sendBytes(response: ByteArray, userInput: Boolean) {
            kotlinx.coroutines.runBlocking {
                processHandle?.write(String(response, Charsets.UTF_8))
            }
        }

        override fun sendString(string: String, userInput: Boolean) {
            kotlinx.coroutines.runBlocking {
                processHandle?.write(string)
            }
        }
    }

    // Watch redraw trigger to force recomposition
    val redrawTrigger = display.redrawTrigger.value
    val cursorX = display.cursorX.value
    val cursorY = display.cursorY.value
    val cursorVisible = display.cursorVisible.value
    val cursorShape = display.cursorShape.value

    // Blink state for SLOW_BLINK and RAPID_BLINK text attributes
    var slowBlinkVisible by remember { mutableStateOf(true) }
    var rapidBlinkVisible by remember { mutableStateOf(true) }

    // Drag state for text selection
    var isDragging by remember { mutableStateOf(false) }
    var dragStartPos by remember { mutableStateOf<Offset?>(null) }  // Track initial mouse position for drag detection

    // Drag threshold: pixels mouse must move before considering it a drag (not just a click)
    val DRAG_THRESHOLD = 5f

    // Context menu controller
    val contextMenuController = remember { ContextMenuController() }

    // Helper functions for context menu actions
    fun clearBuffer() {
        scope.launch {
            processHandle?.write("clear\n")
        }
    }

    fun clearScrollback() {
        val buffer = terminal.terminalTextBuffer
        buffer.lock()
        try {
            buffer.clearHistory()
        } finally {
            buffer.unlock()
        }
        display.requestImmediateRedraw()
    }

    fun selectAll() {
        val buffer = terminal.terminalTextBuffer
        buffer.lock()
        try {
            // Select from start of history to end of screen
            selectionStart = Pair(0, -buffer.historyLinesCount)
            selectionEnd = Pair(buffer.width - 1, buffer.height - 1)
        } finally {
            buffer.unlock()
        }
        display.requestImmediateRedraw()
    }

    // Cursor blink state for BLINK_* cursor shapes
    var cursorBlinkVisible by remember { mutableStateOf(true) }

    // Cache measurement style for performance (avoid recreating on every draw)
    // Load MesloLGS NF (Nerd Font) from resources - includes powerline glyphs and symbols
    // This matches iTerm2's font configuration for proper symbol rendering
    val nerdFont = remember {
        try {
            // Try loading font from classpath resources using File approach
            // This is more reliable than the resource string approach in some Skiko versions
            val fontStream = object {}.javaClass.classLoader?.getResourceAsStream("fonts/MesloLGSNF-Regular.ttf")
                ?: throw IllegalStateException("Font resource not found: fonts/MesloLGSNF-Regular.ttf")

            // Create temp file from InputStream
            val tempFile = java.io.File.createTempFile("MesloLGSNF", ".ttf")
            tempFile.deleteOnExit()
            fontStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            println("INFO: Loaded MesloLGSNF font from: ${tempFile.absolutePath}")
            FontFamily(
                androidx.compose.ui.text.platform.Font(
                    file = tempFile,
                    weight = FontWeight.Normal
                )
            )
        } catch (e: Exception) {
            println("ERROR: Failed to load MesloLGSNF font: ${e.message}")
            e.printStackTrace()
            FontFamily.Monospace  // Fallback to system monospace
        }
    }

    val measurementStyle = remember(nerdFont, settings.fontSize) {
        TextStyle(
            fontFamily = nerdFont,
            fontSize = settings.fontSize.sp,
            fontWeight = FontWeight.Normal
        )
    }

    // Cache cell dimensions and baseline offset (calculated once, reused for all rendering)
    val cellMetrics = remember(measurementStyle) {
        val measurement = textMeasurer.measure("W", measurementStyle)
        val width = measurement.size.width.toFloat()
        val height = measurement.size.height.toFloat()
        // Get baseline offset from top of text bounds
        val baseline = measurement.firstBaseline
        Triple(width, height, baseline)
    }
    val cellWidth = cellMetrics.first
    val cellHeight = cellMetrics.second
    val baselineOffset = cellMetrics.third

    // Auto-scroll to bottom on new output if already at bottom
    LaunchedEffect(redrawTrigger) {
        if (scrollOffset == 0) {
            // Already at bottom, stay there (no action needed)
        }
    }

    // SLOW_BLINK animation timer (500ms intervals)
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            slowBlinkVisible = !slowBlinkVisible
        }
    }

    // RAPID_BLINK animation timer (250ms intervals)
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(250)
            rapidBlinkVisible = !rapidBlinkVisible
        }
    }

    // Cursor blink animation timer (500ms intervals)
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            cursorBlinkVisible = !cursorBlinkVisible
        }
    }

    // Initialize process
    LaunchedEffect(Unit) {
        val services = getPlatformServices()

        // Set proper TERM environment variables for TUI app compatibility (Neovim, vim, less, etc.)
        // TERM=xterm-256color tells apps we support xterm escape sequences and 256 colors
        // COLORTERM=truecolor advertises 24-bit color support
        // TERM_PROGRAM=JediTerm identifies this terminal for app-specific workarounds
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
            workingDirectory = System.getProperty("user.home")
        )

        val handle = services.getProcessService().spawnProcess(config)
        processHandle = handle

        // Connect terminal output to PTY process for bidirectional communication
        // This enables DSR responses, device attribute queries, and other terminal→app messages
        if (handle != null) {
            terminal.setTerminalOutput(ProcessTerminalOutput(handle))
        }

        // Start emulator processing coroutine - runs continuously and blocks on getChar()
        // This allows CSI sequences to span chunk boundaries without being truncated
        launch(Dispatchers.Default) {
            try {
                while (handle != null && handle.isAlive()) {
                    try {
                        emulator.processChar(dataStream.char, terminal)
                        display.requestRedraw()
                    } catch (e: java.io.EOFException) {
                        // Stream closed, exit gracefully
                        break
                    } catch (e: java.io.IOException) {
                        // Only log non-EOF errors
                        if (e !is com.jediterm.terminal.TerminalDataStream.EOF) {
                            println("WARNING: I/O error processing terminal output: ${e.message}")
                        }
                        break
                    } catch (e: Exception) {
                        println("ERROR: Unexpected exception processing terminal output: ${e.message}")
                        e.printStackTrace()
                        break
                    }
                }
            } finally {
                dataStream.close()
            }
        }

        // Read output in background and append to blocking stream
        launch(Dispatchers.IO) {
            val maxChunkSize = 64 * 1024  // 64KB per read operation to prevent memory exhaustion

            while (handle != null && handle.isAlive()) {
                val output = handle.read()
                if (output != null) {
                    // Limit chunk size to prevent memory exhaustion from malicious/huge output
                    val processedOutput = if (output.length > maxChunkSize) {
                        println("WARNING: Process output chunk (${output.length} chars) exceeds $maxChunkSize limit, truncating")
                        output.substring(0, maxChunkSize)
                    } else {
                        output
                    }

                    // Append chunk to blocking stream - emulator will process it atomically
                    dataStream.append(processedOutput)
                }
            }
            // Signal end of stream
            dataStream.close()
        }

        // Request focus after a short delay to ensure window is ready
        kotlinx.coroutines.delay(100)
        focusRequester.requestFocus()
    }

    Box(
            modifier = modifier
            .onGloballyPositioned { coordinates ->
                // Detect window size changes and resize terminal accordingly
                // Note: This fires frequently, but we validate dimensions carefully to prevent crashes
                val newWidth = coordinates.size.width
                val newHeight = coordinates.size.height

                // Ensure we have valid dimensions (minimum 10x10 pixels to prevent crashes)
                if (newWidth >= 10 && newHeight >= 10 && cellWidth > 0f && cellHeight > 0f) {
                    val newCols = (newWidth / cellWidth).toInt().coerceAtLeast(2)
                    val newRows = (newHeight / cellHeight).toInt().coerceAtLeast(2)
                    val currentCols = textBuffer.width
                    val currentRows = textBuffer.height

                    // Only resize if dimensions actually changed AND are reasonable
                    if ((currentCols != newCols || currentRows != newRows) && newCols >= 2 && newRows >= 2) {
                        val newTermSize = TermSize(newCols, newRows)
                        // Resize terminal buffer and notify PTY process (sends SIGWINCH)
                        terminal.resize(newTermSize, RequestOrigin.User)
                        // Also notify the process handle if available (must be launched in coroutine)
                        scope.launch {
                            processHandle?.resize(newCols, newRows)
                        }
                    }
                }
            }
            .fillMaxSize()
            .background(settings.defaultBackgroundColor)
            .onPointerEvent(PointerEventType.Press) { event ->
                val change = event.changes.first()
                if (change.pressed && change.previousPressed.not()) {
                    // Check for right-click (secondary button)
                    if (event.button == androidx.compose.ui.input.pointer.PointerButton.Secondary) {
                        // Show context menu
                        val pos = change.position
                        showTerminalContextMenu(
                            controller = contextMenuController,
                            x = pos.x,
                            y = pos.y,
                            hasSelection = selectionStart != null && selectionEnd != null,
                            onCopy = {
                                if (selectionStart != null && selectionEnd != null) {
                                    val selectedText = extractSelectedText(textBuffer, selectionStart!!, selectionEnd!!)
                                    if (selectedText.isNotEmpty()) {
                                        clipboardManager.setText(AnnotatedString(selectedText))
                                    }
                                }
                            },
                            onPaste = {
                                val text = clipboardManager.getText()?.text
                                if (!text.isNullOrEmpty()) {
                                    scope.launch {
                                        processHandle?.write(text)
                                    }
                                }
                            },
                            onSelectAll = { selectAll() },
                            onClearScreen = { clearBuffer() },
                            onFind = { searchVisible = true }
                        )
                        change.consume()
                        return@onPointerEvent
                    }

                    // Check for hyperlink click
                    // TODO: Add Ctrl/Cmd modifier check when proper API is available
                    // For now, require Ctrl to be checked via keyboard state separately
                    if (hoveredHyperlink != null) {
                        // Open hyperlink (modifier requirement can be added later)
                        HyperlinkDetector.openUrl(hoveredHyperlink!!.url)
                        change.consume()
                        return@onPointerEvent
                    }

                    // Track start position but don't create selection yet
                    // This allows distinguishing click (no selection) from drag (selection)
                    dragStartPos = change.position

                    // Clear selection on LEFT-CLICK only (not right-click)
                    // Also preserve selection during search navigation
                    if (event.button != androidx.compose.ui.input.pointer.PointerButton.Secondary && !searchVisible) {
                        selectionStart = null
                        selectionEnd = null
                    }
                    isDragging = false

                    // Phase 2: Immediate redraw for mouse input
                    display.requestImmediateRedraw()
                }
            }
            .onPointerEvent(PointerEventType.Move) { event ->
                val change = event.changes.first()
                val pos = change.position
                val startPos = dragStartPos

                // Check for hyperlink hover
                val col = (pos.x / cellWidth).toInt()
                val row = (pos.y / cellHeight).toInt() + scrollOffset
                val absoluteRow = row - scrollOffset

                // Get hyperlinks for this row
                val hyperlinksForRow = cachedHyperlinks[absoluteRow]
                hoveredHyperlink = hyperlinksForRow?.firstOrNull { link ->
                    link.row == absoluteRow && col >= link.startCol && col < link.endCol
                }

                if (startPos != null) {
                    // Calculate distance from start position
                    val dx = pos.x - startPos.x
                    val dy = pos.y - startPos.y
                    val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                    // Only start selecting if moved beyond threshold (5 pixels)
                    // This prevents accidental selection on single clicks
                    if (distance > DRAG_THRESHOLD) {
                        if (!isDragging) {
                            // First time crossing threshold - start selection from original position
                            isDragging = true

                            // Clear search when user manually selects text
                            if (searchVisible) {
                                searchVisible = false
                                searchQuery = ""
                                searchMatches = emptyList()
                            }

                            val startCol = (startPos.x / cellWidth).toInt()
                            val startRow = (startPos.y / cellHeight).toInt()
                            selectionStart = Pair(startCol, startRow)
                        }

                        // Update selection end point as mouse moves
                        val col = (pos.x / cellWidth).toInt()
                        val row = (pos.y / cellHeight).toInt()
                        selectionEnd = Pair(col, row)

                        // Phase 2: Immediate redraw during drag
                        display.requestImmediateRedraw()
                    }
                }
            }
            .onPointerEvent(PointerEventType.Release) { event ->
                // If never started dragging (no movement beyond threshold),
                // ensure selection is cleared - this was just a click, not a drag
                // BUT: Don't clear on right-click to allow context menu → Copy
                if (!isDragging && event.button != androidx.compose.ui.input.pointer.PointerButton.Secondary) {
                    selectionStart = null
                    selectionEnd = null
                }

                // Reset drag state
                isDragging = false
                dragStartPos = null

                // Phase 2: Immediate redraw on release
                display.requestImmediateRedraw()
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val delta = event.changes.first().scrollDelta.y
                val historySize = textBuffer.historyLinesCount
                scrollOffset = (scrollOffset - delta.toInt()).coerceIn(0, historySize)
            }
            .onPreviewKeyEvent { keyEvent ->
                // Handle Ctrl+F / Cmd+F for search (intercept before search bar)
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.F) {
                    searchVisible = !searchVisible
                    return@onPreviewKeyEvent true
                }
                false  // Let other events pass through
            }
            .onKeyEvent { keyEvent ->
                // Don't consume keys when search bar is visible - let it handle them
                if (searchVisible) {
                    return@onKeyEvent false
                }

                if (keyEvent.type == KeyEventType.KeyDown) {
                    // Handle Ctrl+Space for IME toggle (CJK input)
                    if (keyEvent.isCtrlPressed && keyEvent.key == Key.Spacebar) {
                        imeState.toggle()
                        return@onKeyEvent true
                    }

                    // Handle Escape key - clear selection if it exists
                    if (keyEvent.key == Key.Escape) {
                        if (selectionStart != null || selectionEnd != null) {
                            // Selection exists - clear it and consume the Escape key
                            selectionStart = null
                            selectionEnd = null
                            display.requestImmediateRedraw()
                            return@onKeyEvent true  // Consume event
                        }
                        // No selection - let Escape pass through to terminal
                    }

                    // Clear selection on any printable key (except Ctrl/Cmd+key combinations)
                    // This matches standard terminal behavior - typing clears selection
                    if (!keyEvent.isCtrlPressed && !keyEvent.isMetaPressed && !keyEvent.isAltPressed) {
                        if (selectionStart != null || selectionEnd != null) {
                            // Don't clear on navigation keys or function keys
                            val isNavigationKey = keyEvent.key in listOf(
                                Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
                                Key.Home, Key.MoveEnd, Key.PageUp, Key.PageDown,
                                Key.F1, Key.F2, Key.F3, Key.F4, Key.F5, Key.F6,
                                Key.F7, Key.F8, Key.F9, Key.F10, Key.F11, Key.F12
                            )
                            if (!isNavigationKey) {
                                // Clear selection before processing the key
                                selectionStart = null
                                selectionEnd = null
                                display.requestImmediateRedraw()
                            }
                        }
                    }

                    // Handle Ctrl+V / Cmd+V for paste
                    if ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.V) {
                        val text = clipboardManager.getText()?.text
                        if (!text.isNullOrEmpty()) {
                            scope.launch {
                                processHandle?.write(text)
                            }
                        }
                        return@onKeyEvent true
                    }

                    // Handle Ctrl+C / Cmd+C for copy (only when selection exists)
                    if ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.C) {
                        if (selectionStart != null && selectionEnd != null) {
                            val selectedText = extractSelectedText(textBuffer, selectionStart!!, selectionEnd!!)
                            if (selectedText.isNotEmpty()) {
                                clipboardManager.setText(AnnotatedString(selectedText))
                                return@onKeyEvent true  // Consume event only if we copied
                            }
                        }
                        // No selection - let Ctrl+C pass through to terminal (for process interrupt)
                        return@onKeyEvent false
                    }

                    // IME (Input Method Editor) Support - IMPLEMENTED
                    // Press Ctrl+Space to toggle IME mode for CJK (Chinese, Japanese, Korean) input.
                    // When enabled, an invisible TextField appears at the cursor position to capture
                    // IME composition events (e.g., Pinyin for Chinese, Hiragana for Japanese).
                    // The composed text is forwarded to the terminal once committed.
                    //
                    // Implementation: See IMEHandler component (line ~1176) and IMEState (line ~148)
                    // Keyboard shortcut: Ctrl+Space to toggle (line ~547)
                    // See: org.jetbrains.jediterm.compose.ime package

                    // Filter out modifier-only keys - they don't produce output
                    if (keyEvent.key in listOf(
                        Key.ShiftLeft, Key.ShiftRight,
                        Key.CtrlLeft, Key.CtrlRight,
                        Key.AltLeft, Key.AltRight,
                        Key.MetaLeft, Key.MetaRight
                    )) {
                        return@onKeyEvent false
                    }

                    scope.launch {
                        val text = run {
                            // Try to map key to VK code and use TerminalKeyEncoder
                            // This handles function keys, navigation keys, and all modifier combinations
                            val vkCode = mapComposeKeyToVK(keyEvent.key)
                            if (vkCode != null) {
                                val modifiers = mapComposeModifiers(keyEvent)
                                val bytes = keyEncoder.getCode(vkCode, modifiers)
                                if (bytes != null) {
                                    return@run String(bytes, Charsets.UTF_8)
                                }
                            }

                            // Fallback: Handle printable characters
                            val code = keyEvent.utf16CodePoint
                            // Filter out invalid characters (0xFFFF) and Unicode special ranges
                            if (code > 0 && code != 0xFFFF && code < 0xFFF0) {
                                code.toChar().toString()
                            } else ""
                        }

                        if (text.isNotEmpty()) {
                            processHandle?.write(text)
                            // Phase 2: Immediate redraw for user input (zero lag)
                            display.requestImmediateRedraw()
                        }
                    }
                    true
                } else false
            }
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Two-pass rendering to fix z-index issue:
            // Pass 1: Draw all backgrounds first
            // Pass 2: Draw all text on top
            // This prevents backgrounds from overlapping emoji that extend beyond cell boundaries

            textBuffer.lock()
            try {
                val height = textBuffer.height
                val width = textBuffer.width

                // ===== PASS 1: DRAW ALL BACKGROUNDS =====
                for (row in 0 until height) {
                    val lineIndex = row - scrollOffset
                    val line = textBuffer.getLine(lineIndex)

                    var col = 0
                    while (col < width) {
                        val char = line.charAt(col)
                        val style = line.getStyleAt(col)

                        // Skip DWC markers
                        if (char == CharUtils.DWC) {
                            col++
                            continue
                        }

                        val x = col * cellWidth
                        val y = row * cellHeight

                        // Check if double-width
                        val isWcwidthDoubleWidth = char != ' ' && char != '\u0000' &&
                                CharUtils.isDoubleWidthCharacter(char.code, display.ambiguousCharsAreDoubleWidth())

                        // TESTING: Commented out variation selector skip to see if it fixes emoji rendering
                        // if (char.code == 0xFE0F || char.code == 0xFE0E) {
                        //     col++
                        //     continue
                        // }

                        // Get attributes
                        val isInverse = style?.hasOption(JediTextStyle.Option.INVERSE) ?: false
                        val isDim = style?.hasOption(JediTextStyle.Option.DIM) ?: false

                        // Apply defaults FIRST, then swap if INVERSE
                        // This ensures INVERSE works correctly even when colors are null
                        val baseFg = style?.foreground?.let { convertTerminalColor(it) } ?: settings.defaultForegroundColor
                        val baseBg = style?.background?.let { convertTerminalColor(it) } ?: settings.defaultBackgroundColor

                        // THEN swap if INVERSE attribute is set
                        var fgColor = if (isInverse) baseBg else baseFg
                        var bgColor = if (isInverse) baseFg else baseBg

                        // Apply DIM to foreground
                        if (isDim) {
                            fgColor = applyDimColor(fgColor)
                        }

                        // Draw background (single or double width)
                        val bgWidth = if (isWcwidthDoubleWidth) cellWidth * 2 else cellWidth
                        drawRect(
                            color = bgColor,
                            topLeft = Offset(x, y),
                            size = Size(bgWidth, cellHeight)
                        )

                        // Skip next column if double-width
                        if (isWcwidthDoubleWidth) {
                            col++
                        }

                        col++
                    }
                }

                // ===== PASS 2: DRAW ALL TEXT =====
                for (row in 0 until height) {
                    // Calculate actual line index based on scroll offset
                    // scrollOffset = 0 means showing screen lines 0..height-1
                    // scrollOffset > 0 means showing some history lines
                    val lineIndex = row - scrollOffset
                    val line = textBuffer.getLine(lineIndex)

                    // Detect hyperlinks in current line
                    val hyperlinks = HyperlinkDetector.detectHyperlinks(line.text, row)
                    // Cache hyperlinks for mouse hover detection
                    if (hyperlinks.isNotEmpty()) {
                        cachedHyperlinks = cachedHyperlinks + (row to hyperlinks)
                    }

                  // Text batching: accumulate consecutive characters with same style
                  val batchText = StringBuilder()
                  var batchStartCol = 0
                  var batchFgColor: Color? = null
                  var batchIsBold = false
                  var batchIsItalic = false
                  var batchIsUnderline = false

                  // Helper function to flush accumulated batch
                  fun flushBatch() {
                      if (batchText.isNotEmpty()) {
                          val x = batchStartCol * cellWidth
                          val y = row * cellHeight

                          val textStyle = TextStyle(
                              color = batchFgColor ?: settings.defaultForegroundColor,
                              fontFamily = measurementStyle.fontFamily,
                              fontSize = settings.fontSize.sp,
                              fontWeight = if (batchIsBold) FontWeight.Bold else FontWeight.Normal,
                              fontStyle = if (batchIsItalic) androidx.compose.ui.text.font.FontStyle.Italic
                                         else androidx.compose.ui.text.font.FontStyle.Normal
                          )

                          drawText(
                              textMeasurer = textMeasurer,
                              text = batchText.toString(),
                              topLeft = Offset(x, y),
                              style = textStyle
                          )

                          // Draw underline for entire batch if needed
                          if (batchIsUnderline) {
                              val underlineY = y + cellHeight - 2f
                              val underlineWidth = batchText.length * cellWidth
                              drawLine(
                                  color = batchFgColor ?: Color.White,
                                  start = Offset(x, underlineY),
                                  end = Offset(x + underlineWidth, underlineY),
                                  strokeWidth = 1f
                              )
                          }

                          batchText.clear()
                      }
                  }

                  var col = 0
                  while (col < width) {
                      val char = line.charAt(col)
                      val style = line.getStyleAt(col)

                      // Skip double-width character continuation markers
                      if (char == CharUtils.DWC) {
                          col++
                          continue
                      }

                      val x = col * cellWidth
                      val y = row * cellHeight

                      // Check if this is a double-width character according to wcwidth
                      val isWcwidthDoubleWidth = char != ' ' && char != '\u0000' &&
                              CharUtils.isDoubleWidthCharacter(char.code, display.ambiguousCharsAreDoubleWidth())

                      // For emoji and symbols, we'll render them with slight scaling for better visibility
                      // These are Unicode blocks containing symbols that render poorly at normal size
                      val isEmojiOrWideSymbol = when (char.code) {
                          in 0x2600..0x26FF -> true  // Miscellaneous Symbols (☁️, ☀️, ★, etc.)
                          in 0x2700..0x27BF -> true  // Dingbats (✂, ✈, ❯, etc.)
                          in 0x1F300..0x1F9FF -> true  // Emoji & Pictographs
                          in 0x1F600..0x1F64F -> true  // Emoticons
                          in 0x1F680..0x1F6FF -> true  // Transport & Map Symbols
                          else -> false
                      }

                      val isDoubleWidth = isWcwidthDoubleWidth

                      // Peek ahead to detect emoji + variation selector pairs
                      // When found, we'll handle them together with system font
                      val nextChar = if (col + 1 < width) line.charAt(col + 1) else null
                      val isEmojiWithVariationSelector = isEmojiOrWideSymbol &&
                          nextChar != null &&
                          (nextChar.code == 0xFE0F || nextChar.code == 0xFE0E)

                      // Skip standalone variation selectors (fallback for non-emoji pairs)
                      // These will be handled as part of emoji+variation pairs above
                      if ((char.code == 0xFE0F || char.code == 0xFE0E) && !isEmojiOrWideSymbol) {
                          col++
                          continue
                      }

                      // Check text attributes (use false if style is null)
                      val isBold = style?.hasOption(JediTextStyle.Option.BOLD) ?: false
                      val isItalic = style?.hasOption(JediTextStyle.Option.ITALIC) ?: false
                      val isInverse = style?.hasOption(JediTextStyle.Option.INVERSE) ?: false
                      val isDim = style?.hasOption(JediTextStyle.Option.DIM) ?: false
                      val isUnderline = style?.hasOption(JediTextStyle.Option.UNDERLINED) ?: false
                      val isHidden = style?.hasOption(JediTextStyle.Option.HIDDEN) ?: false
                      val isSlowBlink = style?.hasOption(JediTextStyle.Option.SLOW_BLINK) ?: false
                      val isRapidBlink = style?.hasOption(JediTextStyle.Option.RAPID_BLINK) ?: false

                      // Apply defaults FIRST, then swap if INVERSE
                      // This ensures INVERSE works correctly even when colors are null
                      val baseFg = style?.foreground?.let { convertTerminalColor(it) } ?: settings.defaultForegroundColor
                      val baseBg = style?.background?.let { convertTerminalColor(it) } ?: settings.defaultBackgroundColor

                      // THEN swap if INVERSE attribute is set
                      var fgColor = if (isInverse) baseBg else baseFg
                      var bgColor = if (isInverse) baseFg else baseBg

                      // Apply DIM to foreground color (reduce brightness to 50%)
                      if (isDim) {
                          fgColor = applyDimColor(fgColor)
                      }

                      // Note: Backgrounds are already drawn in Pass 1

                      // Determine if text should be visible based on blink state
                      val isBlinkVisible = when {
                          isSlowBlink -> slowBlinkVisible
                          isRapidBlink -> rapidBlinkVisible
                          else -> true
                      }

                      // Decide if this character can be batched or needs individual rendering
                      val canBatch = !isDoubleWidth && !isEmojiOrWideSymbol &&
                                     !isHidden && isBlinkVisible &&
                                     char != ' ' && char != '\u0000'

                      // Check if style matches current batch
                      val styleMatches = batchText.isNotEmpty() &&
                                        batchFgColor == fgColor &&
                                        batchIsBold == isBold &&
                                        batchIsItalic == isItalic &&
                                        batchIsUnderline == isUnderline

                      if (canBatch && (batchText.isEmpty() || styleMatches)) {
                          // Add to batch
                          if (batchText.isEmpty()) {
                              batchStartCol = col
                              batchFgColor = fgColor
                              batchIsBold = isBold
                              batchIsItalic = isItalic
                              batchIsUnderline = isUnderline
                          }
                          batchText.append(char)
                      } else {
                          // Flush current batch before rendering this character
                          flushBatch()

                      // Only draw glyph if it's printable (not space or null), not HIDDEN, and visible in blink cycle
                      if (char != ' ' && char != '\u0000' && !isHidden && isBlinkVisible) {
                          // For emoji+variation selector pairs, use system font (FontFamily.Default)
                          // to enable proper emoji rendering on macOS (Apple Color Emoji)
                          // Skia doesn't honor variation selectors, so we must switch fonts
                          val fontForChar = if (isEmojiWithVariationSelector) {
                              FontFamily.Default  // System font with emoji support
                          } else {
                              measurementStyle.fontFamily  // Nerd Font
                          }

                          // Create text style using the appropriate font
                          val textStyle = TextStyle(
                              color = fgColor,
                              fontFamily = fontForChar,
                              fontSize = settings.fontSize.sp,
                              fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                              fontStyle = if (isItalic) androidx.compose.ui.text.font.FontStyle.Italic
                                         else androidx.compose.ui.text.font.FontStyle.Normal
                          )

                          // For double-width characters: hybrid approach
                          // - If font provides proper double-width glyphs (CJK), center them
                          // - If font doesn't (emoji in monospace), scale them to fill space
                          if (isDoubleWidth) {
                              // Measure the actual glyph width at natural font size
                              val measurement = textMeasurer.measure(char.toString(), textStyle)
                              val glyphWidth = measurement.size.width.toFloat()

                              // Calculate available space (2 cells)
                              val allocatedWidth = cellWidth * 2

                              // Decide whether to scale or center based on glyph width
                              // If glyph is less than 1.5 cells, assume font doesn't support DWC properly - scale it
                              // If glyph is >= 1.5 cells, assume it's proper DWC (like CJK) - center it
                              if (glyphWidth < cellWidth * 1.5f) {
                                  // Font doesn't provide proper double-width glyph - scale it
                                  val scaleX = allocatedWidth / glyphWidth.coerceAtLeast(1f)
                                  scale(scaleX = scaleX, scaleY = 1f, pivot = Offset(x, y + cellWidth)) {
                                      drawText(
                                          textMeasurer = textMeasurer,
                                          text = char.toString(),
                                          topLeft = Offset(x, y),
                                          style = textStyle
                                      )
                                  }
                              } else {
                                  // Font provides proper double-width glyph - center it
                                  val emptySpace = (allocatedWidth - glyphWidth).coerceAtLeast(0f)
                                  val centeringOffset = emptySpace / 2f
                                  drawText(
                                      textMeasurer = textMeasurer,
                                      text = char.toString(),
                                      topLeft = Offset(x + centeringOffset, y),
                                      style = textStyle
                                  )
                              }
                          } else if (isEmojiOrWideSymbol) {
                              // For emoji/symbols: measure and scale to fit cell better
                              // If this is emoji+variation selector pair, render both together
                              val textToRender = if (isEmojiWithVariationSelector) {
                                  "$char$nextChar"  // Render emoji + variation selector together
                              } else {
                                  char.toString()
                              }

                              val measurement = textMeasurer.measure(textToRender, textStyle)
                              val glyphWidth = measurement.size.width.toFloat()
                              val glyphHeight = measurement.size.height.toFloat()

                              // Calculate scale based on BOTH dimensions to prevent clipping
                              // Target size: fill entire cell (100% width and height)
                              val targetWidth = cellWidth * 1.0f
                              val targetHeight = cellHeight * 1.0f

                              // Calculate scales for both dimensions
                              val widthScale = if (glyphWidth > 0) targetWidth / glyphWidth else 1.0f
                              val heightScale = if (glyphHeight > 0) targetHeight / glyphHeight else 1.0f

                              // Use minimum scale to ensure emoji fits in BOTH dimensions
                              // Constrain to minimum 100% (no downsizing), maximum 250% (prevent excessive enlargement)
                              val scale = minOf(widthScale, heightScale).coerceIn(1.0f, 2.5f)

                              // Center emoji in cell with calculated scale
                              val scaledWidth = glyphWidth * scale
                              val scaledHeight = glyphHeight * scale
                              val xOffset = (cellWidth - scaledWidth) / 2f
                              val yOffset = (cellHeight - scaledHeight) / 2f

                              scale(scaleX = scale, scaleY = scale, pivot = Offset(x + cellWidth/2, y + cellHeight/2)) {
                                  drawText(
                                      textMeasurer = textMeasurer,
                                      text = textToRender,
                                      topLeft = Offset(x + xOffset, y + yOffset),
                                      style = textStyle
                                  )
                              }

                              // If we rendered emoji+variation selector, skip the variation selector
                              if (isEmojiWithVariationSelector) {
                                  col++  // Skip the variation selector character
                              }
                          } else {
                              // Normal single-width rendering
                              drawText(
                                  textMeasurer = textMeasurer,
                                  text = char.toString(),
                                  topLeft = Offset(x, y),
                                  style = textStyle
                              )
                          }

                          // Draw underline if UNDERLINE attribute is set
                          if (isUnderline) {
                              val underlineY = y + cellHeight - 2f  // 2 pixels from bottom
                              val underlineWidth = if (isDoubleWidth) cellWidth * 2 else cellWidth
                              drawLine(
                                  color = fgColor,
                                  start = Offset(x, underlineY),
                                  end = Offset(x + underlineWidth, underlineY),
                                  strokeWidth = 1f
                              )
                          }

                          // Draw hyperlink underline if hovered
                          if (settings.hyperlinkUnderlineOnHover &&
                              hoveredHyperlink != null &&
                              hoveredHyperlink!!.row == row &&
                              col >= hoveredHyperlink!!.startCol &&
                              col < hoveredHyperlink!!.endCol) {
                              val underlineY = y + cellHeight - 1f
                              drawLine(
                                  color = settings.hyperlinkColorValue,
                                  start = Offset(x, underlineY),
                                  end = Offset(x + cellWidth, underlineY),
                                  strokeWidth = 1f
                              )
                          }
                      }
                      }  // Close else block

                      // If true double-width (wcwidth), skip the next column (contains DWC marker)
                      // For emoji/symbols, don't skip - they're single-width in the buffer but render wider
                      if (isWcwidthDoubleWidth) {
                          col++
                      }

                      col++
                  }

                  // Flush any remaining batch at end of line
                  flushBatch()
                }

                // Draw selection highlight
                if (selectionStart != null && selectionEnd != null) {
                    val start = selectionStart!!
                    val end = selectionEnd!!

                    // Normalize selection to handle backwards dragging
                    val (startCol, startRow) = start
                    val (endCol, endRow) = end

                    val (minRow, maxRow) = if (startRow < endRow) {
                        startRow to endRow
                    } else {
                        endRow to startRow
                    }

                    val (minCol, maxCol) = if (startCol < endCol) {
                        startCol to endCol
                    } else {
                        endCol to startCol
                    }

                    // Draw selection highlight rectangles
                    for (row in minRow..maxRow) {
                        if (row in 0 until height) {
                            val colStart = if (row == minRow) minCol else 0
                            val colEnd = if (row == maxRow) maxCol else (width - 1)

                            for (col in colStart..colEnd) {
                                if (col in 0 until width) {
                                    val x = col * cellWidth
                                    val y = row * cellHeight
                                    drawRect(
                                        color = settings.selectionColorValue.copy(alpha = 0.3f),
                                        topLeft = Offset(x, y),
                                        size = Size(cellWidth, cellHeight)
                                    )
                                }
                            }
                        }
                    }
                }

                // Draw cursor (visible even when unfocused, but dimmed)
                if (cursorVisible) {
                    // Check if cursor should be visible based on blink state
                    val shouldShowCursor = when (cursorShape) {
                        CursorShape.BLINK_BLOCK, CursorShape.BLINK_UNDERLINE, CursorShape.BLINK_VERTICAL_BAR -> cursorBlinkVisible
                        else -> true  // STEADY_* shapes are always visible
                    }

                    if (shouldShowCursor) {
                        val x = cursorX * cellWidth
                        // Adjust cursor Y position: JediTerm reports cursor in 1-indexed coordinates
                        // but our rendering is 0-indexed, so we need to subtract 1
                        val adjustedCursorY = (cursorY - 1).coerceAtLeast(0)
                        val y = adjustedCursorY * cellHeight
                        // Dimmed cursor when unfocused for better UX
                        val cursorAlpha = if (isFocused) 0.7f else 0.3f
                        // Use custom cursor color from OSC 12, or default to white
                        val customCursorColor = terminal.cursorColor
                        val baseCursorColor = if (customCursorColor != null) {
                            Color(customCursorColor.red, customCursorColor.green, customCursorColor.blue)
                        } else {
                            Color.White
                        }
                        val cursorColor = baseCursorColor.copy(alpha = cursorAlpha)

                        when (cursorShape) {
                            CursorShape.BLINK_BLOCK, CursorShape.STEADY_BLOCK, null -> {
                            // Block cursor - fill entire cell
                            drawRect(
                                color = cursorColor,
                                topLeft = Offset(x, y),
                                size = Size(cellWidth, cellHeight)
                            )
                        }
                        CursorShape.BLINK_UNDERLINE, CursorShape.STEADY_UNDERLINE -> {
                            // Underline cursor - draw line at bottom of cell
                            val underlineHeight = cellHeight * 0.2f  // 20% of cell height
                            drawRect(
                                color = cursorColor,
                                topLeft = Offset(x, y + cellHeight - underlineHeight),
                                size = Size(cellWidth, underlineHeight)
                            )
                        }
                        CursorShape.BLINK_VERTICAL_BAR, CursorShape.STEADY_VERTICAL_BAR -> {
                            // Vertical bar cursor - draw thin line on left side
                            val barWidth = cellWidth * 0.15f  // 15% of cell width
                            drawRect(
                                color = cursorColor,
                                topLeft = Offset(x, y),
                                size = Size(barWidth, cellHeight)
                            )
                        }
                    }
                    }
                }
            } finally {
                textBuffer.unlock()
            }

            // Show focus indicator at bottom
            if (!isFocused) {
                drawText(
                    textMeasurer = textMeasurer,
                    text = "[Click to focus terminal]",
                    topLeft = Offset(0f, size.height - 30f),
                    style = TextStyle(
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                )
            }
        }

        // IME (Input Method Editor) handler for CJK input
        // Provides invisible TextField for IME composition (Pinyin, Hiragana, etc.)
        IMEHandler(
            enabled = imeState.isEnabled,
            cursorX = cursorX,
            cursorY = (cursorY - 1).coerceAtLeast(0), // Adjust for 1-indexed cursor
            charWidth = cellWidth,
            charHeight = cellHeight,
            onTextCommit = { text ->
                // Forward composed text to terminal
                scope.launch {
                    processHandle?.write(text)
                }
                // Disable IME after successful input
                imeState.disable()
            }
        )

        // Search bar UI
        SearchBar(
            visible = searchVisible,
            searchQuery = searchQuery,
            currentMatch = currentMatchIndex + 1,
            totalMatches = searchMatches.size,
            caseSensitive = searchCaseSensitive,
            onSearchQueryChange = { searchQuery = it },
            onFindNext = {
                if (searchMatches.isNotEmpty()) {
                    currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size
                    val (col, row) = searchMatches[currentMatchIndex]
                    scrollToMatch(row)
                    highlightMatch(col, row, searchQuery.length)
                }
            },
            onFindPrevious = {
                if (searchMatches.isNotEmpty()) {
                    currentMatchIndex = if (currentMatchIndex <= 0) searchMatches.size - 1 else currentMatchIndex - 1
                    val (col, row) = searchMatches[currentMatchIndex]
                    scrollToMatch(row)
                    highlightMatch(col, row, searchQuery.length)
                }
            },
            onCaseSensitiveToggle = { searchCaseSensitive = !searchCaseSensitive },
            onClose = {
                searchVisible = false
                searchQuery = ""
                searchMatches = emptyList()

                // Clear search highlight
                selectionStart = null
                selectionEnd = null

                // Restore focus to terminal when search closes
                scope.launch {
                    kotlinx.coroutines.delay(50)  // Let SearchBar unmount first
                    focusRequester.requestFocus()
                }
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    // Context menu popup
    ContextMenuPopup(controller = contextMenuController)

    DisposableEffect(Unit) {
        onDispose {
            scope.launch {
                processHandle?.kill()
            }
        }
    }
}

/**
 * Extract selected text from the terminal text buffer.
 * Handles multi-line selection and normalizes coordinates.
 */
private fun extractSelectedText(
    textBuffer: TerminalTextBuffer,
    start: Pair<Int, Int>,
    end: Pair<Int, Int>
): String {
    val (startCol, startRow) = start
    val (endCol, endRow) = end

    // Normalize selection to handle backwards dragging
    val (minRow, maxRow) = if (startRow < endRow) {
        startRow to endRow
    } else {
        endRow to startRow
    }

    val (minCol, maxCol) = if (startRow == endRow) {
        // Same row - compare columns
        if (startCol < endCol) startCol to endCol else endCol to startCol
    } else {
        // Different rows - use natural order
        if (startRow < endRow) startCol to endCol else endCol to startCol
    }

    textBuffer.lock()
    return try {
        val result = StringBuilder()

        for (row in minRow..maxRow) {
            val line = textBuffer.getLine(row) ?: continue

            val colStart = if (row == minRow) minCol else 0
            val colEnd = if (row == maxRow) maxCol else (textBuffer.width - 1)

            for (col in colStart..colEnd) {
                if (col < textBuffer.width) {
                    val char = line.charAt(col)
                    // Skip DWC markers
                    if (char != CharUtils.DWC) {
                        result.append(char)
                    }
                }
            }

            // Add newline between rows (except after last row)
            if (row < maxRow) {
                result.append('\n')
            }
        }

        result.toString()
    } finally {
        textBuffer.unlock()
    }
}

/**
 * Filter environment variables to remove sensitive data before passing to spawned process.
 * Removes variables that likely contain passwords, tokens, API keys, or other credentials.
 */
private fun filterEnvironmentVariables(env: Map<String, String>): Map<String, String> {
    val sensitiveKeywords = listOf(
        "PASSWORD", "TOKEN", "KEY", "SECRET", "CREDENTIAL", "AUTH",
        "API", "PRIVATE", "CERT", "OAUTH"
    )

    return env.filterKeys { key ->
        val upperKey = key.uppercase()
        !sensitiveKeywords.any { upperKey.contains(it) }
    }
}

// Use XTerm color palette for consistency with original JediTerm
private val colorPalette = ColorPaletteImpl.XTERM_PALETTE

/**
 * Convert JediTerm TerminalColor to Compose Color using the official ColorPalette
 */
private fun convertTerminalColor(terminalColor: TerminalColor?): Color {
    if (terminalColor == null) return Color.Black

    // Use ColorPalette for colors 0-15 to support themes, otherwise use toColor()
    val jediColor = if (terminalColor.isIndexed && terminalColor.colorIndex < 16) {
        colorPalette.getForeground(terminalColor)
    } else {
        terminalColor.toColor()
    }

    return Color(
        red = jediColor.red / 255f,
        green = jediColor.green / 255f,
        blue = jediColor.blue / 255f
    )
}

/**
 * Apply DIM attribute by reducing color brightness to 50%
 */
private fun applyDimColor(color: Color): Color {
    return Color(
        red = color.red * 0.5f,
        green = color.green * 0.5f,
        blue = color.blue * 0.5f,
        alpha = color.alpha
    )
}
