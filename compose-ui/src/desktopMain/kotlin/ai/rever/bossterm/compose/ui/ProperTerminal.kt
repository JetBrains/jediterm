package ai.rever.bossterm.compose.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.rever.bossterm.core.util.TermSize
import ai.rever.bossterm.terminal.CursorShape
import ai.rever.bossterm.terminal.RequestOrigin
import ai.rever.bossterm.terminal.TerminalKeyEncoder
import ai.rever.bossterm.terminal.emulator.mouse.MouseButtonCodes
import ai.rever.bossterm.terminal.emulator.mouse.MouseMode
import ai.rever.bossterm.terminal.model.TerminalTextBuffer
import ai.rever.bossterm.terminal.util.CharUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ai.rever.bossterm.compose.ConnectionState
import ai.rever.bossterm.compose.PreConnectScreen
import ai.rever.bossterm.compose.actions.addTabManagementActions
import ai.rever.bossterm.compose.actions.createBuiltinActions
import ai.rever.bossterm.compose.debug.DebugPanel
import ai.rever.bossterm.compose.features.ContextMenuController
import ai.rever.bossterm.compose.features.ContextMenuPopup
import ai.rever.bossterm.compose.features.showHyperlinkContextMenu
import ai.rever.bossterm.compose.features.showTerminalContextMenu
import ai.rever.bossterm.compose.hyperlinks.Hyperlink
import ai.rever.bossterm.compose.hyperlinks.HyperlinkDetector
import ai.rever.bossterm.compose.SelectionMode
import ai.rever.bossterm.compose.ime.IMEHandler
import ai.rever.bossterm.compose.search.RabinKarpSearch
import ai.rever.bossterm.compose.scrollbar.AlwaysVisibleScrollbar
import ai.rever.bossterm.compose.scrollbar.computeMatchPositions
import ai.rever.bossterm.compose.scrollbar.rememberTerminalScrollbarAdapter
import ai.rever.bossterm.compose.search.SearchBar
import ai.rever.bossterm.compose.rendering.RenderingContext
import ai.rever.bossterm.compose.rendering.TerminalCanvasRenderer
import ai.rever.bossterm.compose.selection.SelectionEngine
import ai.rever.bossterm.compose.settings.SettingsManager
import ai.rever.bossterm.compose.TerminalSession
import ai.rever.bossterm.compose.util.ColorUtils
import ai.rever.bossterm.compose.util.KeyMappingUtils
import ai.rever.bossterm.compose.input.createComposeMouseEvent
import ai.rever.bossterm.compose.input.createComposeMouseWheelEvent
import ai.rever.bossterm.compose.input.createMouseEvent
import ai.rever.bossterm.compose.input.toMouseModifierFlags
import ai.rever.bossterm.compose.input.isShiftPressed
import ai.rever.bossterm.compose.input.isAltPressed
import ai.rever.bossterm.core.typeahead.TerminalTypeAheadManager
import org.jetbrains.skia.FontMgr
import ai.rever.bossterm.terminal.TextStyle as BossTextStyle
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import java.io.File
/**
 * Proper terminal implementation using BossTerm's emulator.
 * This uses the real BossTerminal, BossEmulator, and TerminalTextBuffer from the core module.
 *
 * Refactored to support multiple tabs - accepts a TerminalTab with all per-tab state.
 */
@OptIn(
  ExperimentalComposeUiApi::class,
  ExperimentalTextApi::class,
  ExperimentalFoundationApi::class
)
@Composable
fun ProperTerminal(
  tab: TerminalSession,
  isActiveTab: Boolean,
  sharedFont: FontFamily,
  onTabTitleChange: (String) -> Unit,
  onNewTab: () -> Unit = {},
  onNewPreConnectTab: () -> Unit = {},  // Ctrl+Shift+T: Test pre-connection input
  onCloseTab: () -> Unit = {},
  onNextTab: () -> Unit = {},
  onPreviousTab: () -> Unit = {},
  onSwitchToTab: (Int) -> Unit = {},
  onNewWindow: () -> Unit = {},  // Cmd/Ctrl+N: New window
  modifier: Modifier = Modifier
) {
  // Extract session state (no more remember {} blocks - state lives in TerminalSession)
  val processHandle = tab.processHandle.value
  var connectionState by tab.connectionState
  var isFocused by tab.isFocused
  var scrollOffset by tab.scrollOffset
  val scope = rememberCoroutineScope()
  var hasPerformedInitialResize by remember { mutableStateOf(false) }  // Track initial resize
  var isModifierPressed by remember { mutableStateOf(false) }  // Track Ctrl/Cmd for hyperlink clicks
  val focusRequester = remember { FocusRequester() }
  val textMeasurer = rememberTextMeasurer()
  val clipboardManager = LocalClipboardManager.current

  // Settings integration
  val settingsManager = remember { SettingsManager.instance }
  val settings by settingsManager.settings.collectAsState()

  // Use tab's terminal components
  val terminal = tab.terminal
  val textBuffer = tab.textBuffer
  val display = tab.display

  // Search state from tab
  var searchVisible by tab.searchVisible
  var searchQuery by tab.searchQuery
  var searchCaseSensitive by remember { mutableStateOf(settings.searchCaseSensitive) }
  var searchMatches by tab.searchMatches

  // Debug panel state from tab
  var debugPanelVisible by tab.debugPanelVisible
  val debugCollector = tab.debugCollector
  var currentMatchIndex by tab.currentSearchMatchIndex

  // IME state from tab
  val imeState = tab.imeState

  // Selection state from tab
  var selectionStart by tab.selectionStart
  var selectionEnd by tab.selectionEnd
  var selectionMode by tab.selectionMode

  // X11-style selection clipboard from tab
  var selectionClipboard by tab.selectionClipboard

  // Scroll terminal to show a search match (only scroll if match not already visible)
  fun scrollToMatch(matchRow: Int) {
    val screenHeight = textBuffer.height
    val historySize = textBuffer.historyLinesCount

    // Calculate currently visible rows (in buffer coordinates)
    // scrollOffset=0 means viewing current screen (rows 0 to screenHeight-1)
    // scrollOffset=N means scrolled up N lines into history
    val visibleRowStart = -scrollOffset  // First visible buffer row
    val visibleRowEnd = visibleRowStart + screenHeight - 1  // Last visible buffer row

    // Only scroll if match is not visible
    if (matchRow < visibleRowStart) {
      // Match is above visible area, scroll up to show it (with 2-line margin from top)
      val targetOffset = -matchRow + 2
      scrollOffset = targetOffset.coerceIn(0, historySize)
    } else if (matchRow > visibleRowEnd) {
      // Match is below visible area, scroll down to show it (with 2-line margin from bottom)
      val targetOffset = -(matchRow - screenHeight + 3)
      scrollOffset = targetOffset.coerceIn(0, historySize)
    }
    // If match is already visible, don't scroll

    display.requestImmediateRedraw()
  }

  // Highlight a search match using selection
  fun highlightMatch(matchCol: Int, matchRow: Int, matchLength: Int) {
    // matchRow is already buffer-relative (from search), use directly
    // Selection rendering will convert buffer to screen coords
    selectionStart = Pair(matchCol, matchRow)
    selectionEnd = Pair(matchCol + matchLength - 1, matchRow)

    display.requestImmediateRedraw()
  }

  // Search function using Rabin-Karp algorithm for O(n+m) performance
  fun performSearch() {
    if (searchQuery.isEmpty()) {
      searchMatches = emptyList()
      currentMatchIndex = -1
      return
    }

    // Use snapshot for lock-free search - no blocking during search operation
    val snapshot = terminal.terminalTextBuffer.createSnapshot()

    // Use Rabin-Karp search for O(n+m) average case vs O(n*m) naive indexOf
    val rabinMatches = RabinKarpSearch.searchBuffer(
      snapshot = snapshot,
      pattern = searchQuery,
      ignoreCase = !searchCaseSensitive
    )

    // Convert to the expected Pair<Int, Int> format (column, row)
    val matches = rabinMatches.map { Pair(it.column, it.row) }

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

  // Hyperlink state from tab
  var hoveredHyperlink by tab.hoveredHyperlink
  var cachedHyperlinks by remember { mutableStateOf<Map<Int, List<Hyperlink>>>(emptyMap()) }
  // Track previous hover state for consumer callbacks
  var previousHoveredHyperlink by remember { mutableStateOf<Hyperlink?>(null) }

  // Terminal key encoder for proper escape sequence generation (function keys, modifiers, etc.)
  val keyEncoder = remember { TerminalKeyEncoder() }

  // ProcessTerminalOutput is now defined in TabController
  // No longer needed here since terminal output routing is set up during tab initialization

  // Watch redraw trigger to force recomposition
  // Use predicted cursor position from type-ahead manager if available
  // This makes the cursor respond immediately to keystrokes even on high-latency connections
  // Note: typeAheadManager.cursorX returns 1-based (for Swing), we need 0-based for Compose rendering
  val cursorX = tab.typeAheadManager?.let { it.cursorX - 1 } ?: display.cursorX.value
  val cursorY = display.cursorY.value
  val cursorVisible = display.cursorVisible.value
  val cursorShape = display.cursorShape.value

  // Blink state for SLOW_BLINK and RAPID_BLINK text attributes
  var slowBlinkVisible by remember { mutableStateOf(true) }
  var rapidBlinkVisible by remember { mutableStateOf(true) }

  // Drag state for text selection
  var isDragging by remember { mutableStateOf(false) }
  var dragStartPos by remember { mutableStateOf<Offset?>(null) }  // Track initial mouse position for drag detection

  // Auto-scroll state for drag selection beyond bounds
  var autoScrollJob by remember { mutableStateOf<Job?>(null) }
  var lastDragPosition by remember { mutableStateOf<Offset?>(null) }
  var canvasSize by remember { mutableStateOf(Size.Zero) }

  // Multi-click tracking for double-click (word select) and triple-click (line select)
  var lastClickTime by remember { mutableStateOf(0L) }
  var lastClickPosition by remember { mutableStateOf(Offset.Zero) }
  var clickCount by remember { mutableIntStateOf(0) }

  // Drag threshold: pixels mouse must move before considering it a drag (not just a click)
  val DRAG_THRESHOLD = 5f
  // Multi-click thresholds
  val MULTI_CLICK_TIME_THRESHOLD = 500L  // milliseconds
  val MULTI_CLICK_DISTANCE_THRESHOLD = 10f  // pixels
  // Auto-scroll constants (matching Swing TerminalPanel.java:218)
  val AUTO_SCROLL_SPEED = 0.05f  // Scroll coefficient: faster when further from bounds
  val AUTO_SCROLL_INTERVAL = 50L  // 20 Hz scroll rate when outside bounds

  // Context menu controller
  val contextMenuController = remember { ContextMenuController() }

  // Helper functions for context menu actions
  fun clearBuffer() {
    scope.launch {
      tab.writeUserInput("clear\n")
    }
  }

  fun clearScrollback() {
    val buffer = terminal.terminalTextBuffer
    // clearHistory() is a write operation, still needs lock
    buffer.lock()
    try {
      buffer.clearHistory()
    } finally {
      buffer.unlock()
    }
    display.requestImmediateRedraw()
  }

  fun selectAll() {
    // Use snapshot for lock-free selection bounds calculation
    val snapshot = terminal.terminalTextBuffer.createSnapshot()
    // Select from start of history to end of screen
    selectionStart = Pair(0, -snapshot.historyLinesCount)
    selectionEnd = Pair(snapshot.width - 1, snapshot.height - 1)
    display.requestImmediateRedraw()
  }

  // Auto-scroll function for drag selection beyond canvas bounds
  // Scrolls proportionally to distance from bounds while mouse is held outside
  // Note: cellWidthParam and cellHeightParam are passed because cellWidth/cellHeight are defined later in the composable
  fun startAutoScroll(position: Offset, cellWidthParam: Float, cellHeightParam: Float) {
    autoScrollJob?.cancel()
    autoScrollJob = scope.launch {
      while (isActive && isDragging) {
        val historySize = textBuffer.historyLinesCount
        val height = canvasSize.height

        // Calculate scroll amount based on distance from bounds
        val scrolled = when {
          position.y < 0 -> {
            // Dragging above canvas - scroll up into history
            val scrollDelta = (-position.y * AUTO_SCROLL_SPEED).toInt().coerceAtLeast(1)
            scrollOffset = (scrollOffset + scrollDelta).coerceIn(0, historySize)
            true
          }
          position.y > height -> {
            // Dragging below canvas - scroll down toward current
            val scrollDelta = ((position.y - height) * AUTO_SCROLL_SPEED).toInt().coerceAtLeast(1)
            scrollOffset = (scrollOffset - scrollDelta).coerceIn(0, historySize)
            true
          }
          else -> false  // Back in bounds
        }

        if (!scrolled) break  // Stop auto-scroll when back in bounds

        // Update selection end to track scroll position (using buffer-relative coordinates)
        lastDragPosition?.let { pos ->
          val col = (pos.x / cellWidthParam).toInt().coerceIn(0, textBuffer.width - 1)
          val screenRow = when {
            pos.y < 0 -> 0  // Top of visible area
            pos.y > height -> ((height / cellHeightParam).toInt()).coerceAtMost(textBuffer.height - 1)
            else -> (pos.y / cellHeightParam).toInt()
          }
          val bufferRow = screenRow - scrollOffset  // Convert screen to buffer-relative row
          selectionEnd = Pair(col, bufferRow)
        }

        display.requestImmediateRedraw()
        delay(AUTO_SCROLL_INTERVAL)
      }
    }
  }

  // Detect macOS for keyboard shortcut handling (Cmd vs Ctrl)
  val isMacOS = remember { System.getProperty("os.name").lowercase().contains("mac") }

  // Create action registry with all built-in actions
  // Key on `tab` to ensure paste/write operations target the current tab
  val actionRegistry = remember(isMacOS, tab) {
    val registry = createBuiltinActions(
      selectionStart = object : MutableState<Pair<Int, Int>?> {
        override var value: Pair<Int, Int>?
          get() = selectionStart
          set(value) { selectionStart = value }
        override fun component1() = value
        override fun component2(): (Pair<Int, Int>?) -> Unit = { selectionStart = it }
      },
      selectionEnd = object : MutableState<Pair<Int, Int>?> {
        override var value: Pair<Int, Int>?
          get() = selectionEnd
          set(value) { selectionEnd = value }
        override fun component1() = value
        override fun component2(): (Pair<Int, Int>?) -> Unit = { selectionEnd = it }
      },
      selectionMode = object : MutableState<SelectionMode> {
        override var value: SelectionMode
          get() = selectionMode
          set(value) { selectionMode = value }
        override fun component1() = value
        override fun component2(): (SelectionMode) -> Unit = { selectionMode = it }
      },
      textBuffer = textBuffer,
      clipboardManager = clipboardManager,
      writeUserInput = tab::writeUserInput,
      pasteText = tab::pasteText,
      searchVisible = object : MutableState<Boolean> {
        override var value: Boolean
          get() = searchVisible
          set(value) { searchVisible = value }
        override fun component1() = value
        override fun component2(): (Boolean) -> Unit = { searchVisible = it }
      },
      debugPanelVisible = object : MutableState<Boolean> {
        override var value: Boolean
          get() = debugPanelVisible
          set(value) { debugPanelVisible = value }
        override fun component1() = value
        override fun component2(): (Boolean) -> Unit = { debugPanelVisible = it }
      },
      imeState = imeState,
      display = display,
      scope = scope,
      selectAllCallback = { selectAll() },
      isMacOS = isMacOS
    )

    // Add tab management shortcuts (Phase 5)
    addTabManagementActions(
      registry = registry,
      onNewTab = onNewTab,
      onNewPreConnectTab = onNewPreConnectTab,
      onCloseTab = onCloseTab,
      onNextTab = onNextTab,
      onPreviousTab = onPreviousTab,
      onSwitchToTab = onSwitchToTab,
      onNewWindow = onNewWindow,
      isMacOS = isMacOS
    )

    registry
  }

  // Cursor blink state for BLINK_* cursor shapes
  var cursorBlinkVisible by remember { mutableStateOf(true) }

  // Use shared font loaded once by Main.kt (performance optimization for multiple tabs)
  // Font loading is expensive and should only happen once, not per tab

  val measurementStyle = remember(sharedFont, settings.fontSize) {
    TextStyle(
      fontFamily = sharedFont,
      fontSize = settings.fontSize.sp,
      fontWeight = FontWeight.Normal
    )
  }

  // Cache cell dimensions and baseline offset (calculated once, reused for all rendering)
  // Measure a string of 100 characters to get accurate average advance width
  // This prevents cumulative rounding errors when rendering long lines
  val cellMetrics = remember(measurementStyle) {
    val sampleString = "W".repeat(100)  // 100 characters for precise averaging
    val measurement = textMeasurer.measure(sampleString, measurementStyle)
    val width = measurement.size.width.toFloat() / sampleString.length
    val singleMeasurement = textMeasurer.measure("W", measurementStyle)
    val height = singleMeasurement.size.height.toFloat()
    // Get baseline offset from top of text bounds
    val baseline = singleMeasurement.firstBaseline
    Triple(width, height, baseline)
  }
  val cellWidth = cellMetrics.first
  val baseCellHeight = cellMetrics.second  // Raw height without line spacing

  // Calculate effective line spacing based on settings and alternate buffer state
  val isUsingAlternateBuffer = textBuffer.isUsingAlternateBuffer
  val effectiveLineSpacing = if (settings.disableLineSpacingInAlternateBuffer && isUsingAlternateBuffer) {
    1.0f  // No line spacing in alternate buffer
  } else {
    settings.lineSpacing
  }

  // Apply line spacing to cell height
  val cellHeight = baseCellHeight * effectiveLineSpacing

  // Calculate line spacing gap (extra space added by line spacing)
  val lineSpacingGap = cellHeight - baseCellHeight

  // SLOW_BLINK animation timer (configurable via settings.slowTextBlinkMs)
  // Wrapped with enableTextBlinking master toggle for accessibility
  if (settings.enableTextBlinking) {
    LaunchedEffect(settings.slowTextBlinkMs) {
      while (true) {
        delay(settings.slowTextBlinkMs.toLong())
        slowBlinkVisible = !slowBlinkVisible
      }
    }

    // RAPID_BLINK animation timer (configurable via settings.rapidTextBlinkMs)
    LaunchedEffect(settings.rapidTextBlinkMs) {
      while (true) {
        delay(settings.rapidTextBlinkMs.toLong())
        rapidBlinkVisible = !rapidBlinkVisible
      }
    }
  }

  // Cursor blink animation timer (configurable via settings.caretBlinkMs)
  LaunchedEffect(settings.caretBlinkMs) {
    while (true) {
      delay(settings.caretBlinkMs.toLong())
      cursorBlinkVisible = !cursorBlinkVisible
    }
  }

  // PTY initialization and process monitoring are now handled by TabController
  // ProperTerminal only handles rendering and user interaction

  // Request focus when tab becomes active or changes
  // Use tab.id as key so effect re-triggers when switching between tabs
  LaunchedEffect(tab.id) {
    delay(100)
    focusRequester.requestFocus()
  }

  // Resize PTY when it becomes available
  // This fixes the initial size issue: onGloballyPositioned fires and resizes the terminal buffer,
  // but processHandle is NULL at that point. When the PTY connects, we need to sync its size.
  LaunchedEffect(processHandle) {
    processHandle?.let { handle ->
      handle.resize(textBuffer.width, textBuffer.height)
    }
  }

  // Observe icon title changes from OSC 1 sequence for tab labels (XTerm standard)
  // Icon title (OSC 1) is used for tab labels in modern terminals
  // Window title (OSC 2) is used for the main window title bar
  // Using reactive Flow instead of polling for immediate updates and better resource efficiency
  LaunchedEffect(Unit) {
    display.iconTitleFlow.collect { newTitle ->
      if (newTitle.isNotEmpty()) {
        onTabTitleChange(newTitle)
      }
    }
  }

  // Scrollbar adapter that bridges terminal scroll state with Compose scrollbar
  // Note: historySize and screenHeight lambdas read textBuffer properties without locks.
  // This is acceptable because:
  // 1. Reads are atomic (single int reads)
  // 2. Values change rarely (only on resize or history growth)
  // 3. Worst case: scrollbar shows slightly stale values for 1 frame
  // 4. Creating snapshots here would degrade scrolling performance
  val scrollbarAdapter = rememberTerminalScrollbarAdapter(
    terminalScrollOffset = rememberUpdatedState(scrollOffset),
    historySize = {
      textBuffer.historyLinesCount
    },
    screenHeight = { textBuffer.height },
    cellHeight = { cellHeight },
    onScroll = { newOffset ->
      scrollOffset = newOffset
      display.requestImmediateRedraw() // Immediate redraw for responsive scrolling
    }
  )

  /**
   * Mouse reporting decision logic (Issue #20)
   *
   * Determines if mouse event should be forwarded to terminal application (vim, tmux, etc.)
   * or handled locally (selection, scrolling).
   *
   * Key behavior:
   * - Shift+Click ALWAYS forces local action, even when app has mouse mode
   * - Without Shift: respects application's mouse mode settings
   *
   * @return true when mouse event should be forwarded to terminal app
   */
  fun isRemoteMouseAction(shiftPressed: Boolean): Boolean {
    return settings.enableMouseReporting && display.isMouseReporting() && !shiftPressed
  }

  /**
   * Convert pixel position to character cell coordinates (0-based).
   * Clamps coordinates to valid terminal bounds.
   *
   * Note: Reads textBuffer.width/height without locks (acceptable for mouse coordinate conversion).
   * Atomic reads, worst case: mouse maps to slightly wrong cell for 1 frame during resize.
   *
   * @param position Offset in pixels from top-left corner
   * @return Pair of (col, row) in 0-based character coordinates
   */
  fun pixelToCharCoords(position: Offset): Pair<Int, Int> {
    val col = (position.x / cellWidth).toInt().coerceIn(0, textBuffer.width - 1)
    val row = (position.y / cellHeight).toInt().coerceIn(0, textBuffer.height - 1)
    return Pair(col, row)
  }

  /**
   * Drag-and-drop target for file path pasting (like iTerm2).
   * When files are dropped on the terminal, their paths are pasted with shell escaping.
   */
  val dropTarget = remember {
    object : DragAndDropTarget {
      override fun onDrop(event: DragAndDropEvent): Boolean {
        val transferable = event.awtTransferable
        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
          @Suppress("UNCHECKED_CAST")
          val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
          val paths = files.joinToString(" ") { file ->
            escapePathForShell(file.absolutePath)
          }
          if (paths.isNotEmpty()) {
            tab.pasteText(paths)
          }
          return true
        }
        return false
      }
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .onPreviewKeyEvent { keyEvent ->
        // Handle Cmd+F toggle at the top level so it works regardless of which child has focus
        if (keyEvent.type == KeyEventType.KeyDown) {
          val action = actionRegistry.getAction("search")
          if (action != null && action.matchesKeyEvent(keyEvent, isMacOS)) {
            action.execute(keyEvent)
            // When closing search, restore focus to terminal
            if (!searchVisible) {
              scope.launch {
                kotlinx.coroutines.delay(50)
                focusRequester.requestFocus()
              }
            }
            return@onPreviewKeyEvent true
          }
        }
        false
      }
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
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

            // Resize on first render OR when dimensions change (ensures PTY gets correct size on startup)
            if ((!hasPerformedInitialResize || (currentCols != newCols || currentRows != newRows)) && newCols >= 2 && newRows >= 2) {
              val newTermSize = TermSize(newCols, newRows)
              // Resize terminal buffer and notify PTY process (sends SIGWINCH)
              terminal.resize(newTermSize, RequestOrigin.User)
              // Clear type-ahead predictions on resize (terminal state is no longer predictable)
              tab.typeAheadManager?.onResize()
              // Reset scroll to bottom on resize - history size may have changed, making old offset invalid
              // This ensures the user sees the current screen content after resize
              scrollOffset = 0
              // Also notify the process handle if available (must be launched in coroutine)
              scope.launch {
                processHandle?.resize(newCols, newRows)
              }
              // Force redraw with new buffer dimensions (critical for initial size)
              display.requestImmediateRedraw()
              hasPerformedInitialResize = true
            }
          }
        }
        .fillMaxSize()
        .background(settings.defaultBackgroundColor)
        .dragAndDropTarget(
          shouldStartDragAndDrop = { true },
          target = dropTarget
        )
        .onPointerEvent(PointerEventType.Press) { event ->
          val change = event.changes.first()
          // Skip if event was already consumed by an overlay (SearchBar, DebugPanel, etc.)
          if (change.isConsumed) return@onPointerEvent
          if (change.pressed && change.previousPressed.not()) {
            // Check if mouse event should be forwarded to terminal application
            val shiftPressed = event.isShiftPressed()
            if (settings.enableMouseReporting && isRemoteMouseAction(shiftPressed)) {
              // If button is null, skip remote forwarding and fall through to local handling
              // Button can be null for touch events, stylus input, or exotic input devices
              event.button?.let { button ->
                val (col, row) = pixelToCharCoords(change.position)
                val mouseEvent = createComposeMouseEvent(event, button)
                terminal.mousePressed(col, row, mouseEvent)
                change.consume()
                return@onPointerEvent
              } ?: run {
                if (settings.debugModeEnabled) {
                  println("Mouse press with null button at (${change.position.x}, ${change.position.y}) - handling locally")
                }
              }
            }

            // Check for right-click (secondary button)
            if (event.button == PointerButton.Secondary) {
              // Show context menu
              val pos = change.position

              // Check if we're hovering over a hyperlink
              if (hoveredHyperlink != null) {
                val link = hoveredHyperlink!!
                showHyperlinkContextMenu(
                  controller = contextMenuController,
                  x = pos.x,
                  y = pos.y,
                  url = link.url,
                  onOpenLink = { HyperlinkDetector.openUrl(link.url) },
                  onCopyLinkAddress = { clipboardManager.setText(AnnotatedString(link.url)) },
                  hasSelection = selectionStart != null && selectionEnd != null,
                  onCopy = {
                    if (selectionStart != null && selectionEnd != null) {
                      val selectedText = SelectionEngine.extractSelectedText(textBuffer, selectionStart!!, selectionEnd!!, selectionMode)
                      if (selectedText.isNotEmpty()) {
                        clipboardManager.setText(AnnotatedString(selectedText))
                      }
                    }
                  },
                  onPaste = {
                    val text = clipboardManager.getText()?.text
                    if (!text.isNullOrEmpty()) {
                      scope.launch {
                        tab.pasteText(text)
                      }
                    }
                  },
                  onSelectAll = { selectAll() },
                  onClearScreen = { clearBuffer() },
                  onClearScrollback = { clearScrollback() },
                  onFind = { searchVisible = true },
                  onShowDebug = if (debugCollector != null) {
                    { debugPanelVisible = !debugPanelVisible }
                  } else null
                )
              } else {
                showTerminalContextMenu(
                  controller = contextMenuController,
                  x = pos.x,
                  y = pos.y,
                  hasSelection = selectionStart != null && selectionEnd != null,
                  onCopy = {
                    if (selectionStart != null && selectionEnd != null) {
                      val selectedText = SelectionEngine.extractSelectedText(textBuffer, selectionStart!!, selectionEnd!!, selectionMode)
                      if (selectedText.isNotEmpty()) {
                        clipboardManager.setText(AnnotatedString(selectedText))
                      }
                    }
                  },
                  onPaste = {
                    val text = clipboardManager.getText()?.text
                    if (!text.isNullOrEmpty()) {
                      scope.launch {
                        tab.pasteText(text)
                      }
                    }
                  },
                  onSelectAll = { selectAll() },
                  onClearScreen = { clearBuffer() },
                  onClearScrollback = { clearScrollback() },
                  onFind = { searchVisible = true },
                  onShowDebug = if (debugCollector != null) {
                    { debugPanelVisible = !debugPanelVisible }
                  } else null
                )
              }
              change.consume()
              return@onPointerEvent
            }

            // Check for middle-click paste (tertiary button)
            if (event.button == PointerButton.Tertiary && settings.pasteOnMiddleClick) {
              val text = if (settings.emulateX11CopyPaste) {
                // X11 mode: Paste from selection clipboard (middle-click buffer)
                selectionClipboard
              } else {
                // Normal mode: Paste from system clipboard
                clipboardManager.getText()?.text
              }
              if (!text.isNullOrEmpty() && processHandle != null) {
                scope.launch {
                  try {
                    tab.pasteText(text)
                  } catch (e: Exception) {
                    println("ERROR: Failed to paste text via middle-click: ${e.message}")
                    e.printStackTrace()
                  }
                }
                change.consume()
                return@onPointerEvent
              }
            }

            // Check for hyperlink click with Ctrl/Cmd modifier
            // Standard terminal behavior: Ctrl+Click (Windows/Linux) or Cmd+Click (macOS)
            if (hoveredHyperlink != null && isModifierPressed) {
              HyperlinkDetector.openUrl(hoveredHyperlink!!.url)
              change.consume()
              return@onPointerEvent
            }

            // Multi-click detection for word/line selection
            // Track click count based on time and position deltas
            val currentTime = System.currentTimeMillis()
            val currentPosition = change.position
            val timeDelta = currentTime - lastClickTime
            val dx = currentPosition.x - lastClickPosition.x
            val dy = currentPosition.y - lastClickPosition.y
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)

            // Determine if this is a multi-click or a new click sequence
            if (timeDelta < MULTI_CLICK_TIME_THRESHOLD && distance < MULTI_CLICK_DISTANCE_THRESHOLD) {
              clickCount++
            } else {
              clickCount = 1
            }

            lastClickTime = currentTime
            lastClickPosition = currentPosition

            // Track start position but don't create selection yet
            // This allows distinguishing click (no selection) from drag (selection)
            dragStartPos = change.position

            // Branch on click count for different selection modes
            when (clickCount) {
              1 -> {
                // Single click: Clear selection on LEFT-CLICK only (not right-click)
                // Also preserve selection during search navigation
                if (event.button != androidx.compose.ui.input.pointer.PointerButton.Secondary && !searchVisible) {
                  selectionStart = null
                  selectionEnd = null
                }
                isDragging = false
              }
              2 -> {
                // Double-click: Select word at cursor position
                // Convert screen coords to buffer-relative for selection
                val (col, screenRow) = pixelToCharCoords(currentPosition)
                val bufferRow = screenRow - scrollOffset
                val (start, end) = SelectionEngine.selectWordAt(col, bufferRow, textBuffer)
                selectionStart = start
                selectionEnd = end
                isDragging = false

                // Clear search when user manually selects text
                if (searchVisible) {
                  searchVisible = false
                  searchQuery = ""
                  searchMatches = emptyList()
                }
                // Ensure focus is on terminal canvas after click
                focusRequester.requestFocus()
              }
              else -> {
                // Triple-click (or more): Select entire logical line
                // Convert screen coords to buffer-relative for selection
                val (col, screenRow) = pixelToCharCoords(currentPosition)
                val bufferRow = screenRow - scrollOffset
                val (start, end) = SelectionEngine.selectLineAt(col, bufferRow, textBuffer)
                selectionStart = start
                selectionEnd = end
                isDragging = false

                // Clear search when user manually selects text
                if (searchVisible) {
                  searchVisible = false
                  searchQuery = ""
                  searchMatches = emptyList()
                }
                // Ensure focus is on terminal canvas after click
                focusRequester.requestFocus()
              }
            }

            // Phase 2: Immediate redraw for mouse input
            display.requestImmediateRedraw()
          }
        }
        .onPointerEvent(PointerEventType.Move) { event ->
          val change = event.changes.first()
          // Skip if event was already consumed by an overlay
          if (change.isConsumed) return@onPointerEvent
          val pos = change.position
          val startPos = dragStartPos

          // Check if mouse event should be forwarded to terminal application
          val shiftPressed = event.isShiftPressed()
          if (settings.enableMouseReporting && isRemoteMouseAction(shiftPressed)) {
            val (col, row) = pixelToCharCoords(pos)
            if (change.pressed) {
              // Button is held - this is a drag event (BUTTON_MOTION or ALL_MOTION modes)
              event.button?.let { button ->
                val mouseEvent = createComposeMouseEvent(event, button)
                terminal.mouseDragged(col, row, mouseEvent)
              }
            } else if (display.mouseMode.value == MouseMode.MOUSE_REPORTING_ALL_MOTION) {
              // No button pressed - pure move event, only sent in ALL_MOTION mode
              // Check mode before sending to avoid excessive events in other modes
              val mouseEvent = createMouseEvent(MouseButtonCodes.NONE, event.toMouseModifierFlags())
              terminal.mouseMoved(col, row, mouseEvent)
            }
            change.consume()
            return@onPointerEvent
          }

          // Check for hyperlink hover
          val col = (pos.x / cellWidth).toInt()
          val row = (pos.y / cellHeight).toInt() + scrollOffset
          val absoluteRow = row - scrollOffset

          // Get hyperlinks for this row
          val hyperlinksForRow = cachedHyperlinks[absoluteRow]
          hoveredHyperlink = hyperlinksForRow?.firstOrNull { link ->
            link.row == absoluteRow && col >= link.startCol && col < link.endCol
          }

          // Notify hover consumers when hyperlink hover state changes
          if (hoveredHyperlink != previousHoveredHyperlink) {
            // Exit callback for previous hyperlink - notify all consumers
            if (previousHoveredHyperlink != null) {
              tab.hoverConsumers.forEach { it.onMouseExited() }
            }
            // Enter callback for new hyperlink with bounds - notify all consumers
            if (hoveredHyperlink != null) {
              val link = hoveredHyperlink!!
              val bounds = Rect(
                left = link.startCol * cellWidth,
                top = (link.row - scrollOffset) * cellHeight,
                right = link.endCol * cellWidth,
                bottom = (link.row - scrollOffset + 1) * cellHeight
              )
              tab.hoverConsumers.forEach { it.onMouseEntered(bounds, link.url) }
            }
            previousHoveredHyperlink = hoveredHyperlink
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
                // Ensure focus is on terminal canvas after click
                focusRequester.requestFocus()

                // Detect Alt+Drag for block selection mode
                selectionMode = if (event.isAltPressed()) SelectionMode.BLOCK else SelectionMode.NORMAL

                val startCol = (startPos.x / cellWidth).toInt()
                val screenRow = (startPos.y / cellHeight).toInt()
                val bufferRow = screenRow - scrollOffset  // Convert screen to buffer-relative row
                selectionStart = Pair(startCol, bufferRow)
              }

              // Update selection end point as mouse moves
              // Handle out-of-bounds coordinates for auto-scroll
              // Convert to buffer-relative coordinates for consistent selection model
              val col = (pos.x / cellWidth).toInt().coerceIn(0, textBuffer.width - 1)
              val screenRow = when {
                pos.y < 0 -> 0  // Above canvas: first visible row
                pos.y > canvasSize.height -> ((canvasSize.height / cellHeight).toInt()).coerceAtMost(textBuffer.height - 1)
                else -> (pos.y / cellHeight).toInt()
              }
              val bufferRow = screenRow - scrollOffset  // Convert screen to buffer-relative row
              selectionEnd = Pair(col, bufferRow)

              // Track position for auto-scroll updates
              lastDragPosition = pos

              // Start auto-scroll if dragging outside bounds
              if (pos.y < 0 || pos.y > canvasSize.height) {
                if (autoScrollJob?.isActive != true) {
                  startAutoScroll(pos, cellWidth, cellHeight)
                }
              } else {
                // Back in bounds - cancel auto-scroll
                autoScrollJob?.cancel()
              }

              // Phase 2: Immediate redraw during drag
              display.requestImmediateRedraw()
            }
          }
        }
        .onPointerEvent(PointerEventType.Release) { event ->
          val change = event.changes.first()
          // Skip if event was already consumed by an overlay
          if (change.isConsumed) return@onPointerEvent

          // Check if mouse event should be forwarded to terminal application
          val shiftPressed = event.isShiftPressed()
          if (settings.enableMouseReporting && isRemoteMouseAction(shiftPressed)) {
            // If button is null, skip remote forwarding and fall through to local handling
            // Button can be null for touch events, stylus input, or exotic input devices
            event.button?.let { button ->
              val (col, row) = pixelToCharCoords(change.position)
              val mouseEvent = createComposeMouseEvent(event, button)
              terminal.mouseReleased(col, row, mouseEvent)
              change.consume()
              return@onPointerEvent
            } ?: run {
              if (settings.debugModeEnabled) {
                println("Mouse release with null button at (${change.position.x}, ${change.position.y}) - handling locally")
              }
            }
          }

          // If never started dragging (no movement beyond threshold),
          // ensure selection is cleared - this was just a click, not a drag
          // BUT: Don't clear on right-click to allow context menu → Copy
          // ALSO: Don't clear multi-click selections (double-click word, triple-click line)
          if (!isDragging && clickCount == 1 && event.button != androidx.compose.ui.input.pointer.PointerButton.Secondary) {
            selectionStart = null
            selectionEnd = null
          }

          // Copy-on-select: Automatically copy selected text to clipboard
          // Capture values first to avoid race condition (TOCTOU)
          val start = selectionStart
          val end = selectionEnd
          if (settings.copyOnSelect && start != null && end != null) {
            val selectedText = SelectionEngine.extractSelectedText(textBuffer, start, end, selectionMode)
            if (selectedText.isNotEmpty()) {
              if (settings.emulateX11CopyPaste) {
                // X11 mode: Copy to selection clipboard (middle-click buffer)
                // Limit clipboard size to 10MB to prevent memory issues
                if (selectedText.length <= 10_000_000) {
                  selectionClipboard = selectedText
                }
              } else {
                // Normal mode: Copy to system clipboard
                clipboardManager.setText(AnnotatedString(selectedText))
              }
            }
          }

          // Reset drag state and cancel auto-scroll
          isDragging = false
          dragStartPos = null
          autoScrollJob?.cancel()
          autoScrollJob = null
          lastDragPosition = null

          // Phase 2: Immediate redraw on release
          display.requestImmediateRedraw()
        }
        .onPointerEvent(PointerEventType.Scroll) { event ->
          val change = event.changes.first()
          // Skip if event was already consumed by an overlay
          if (change.isConsumed) return@onPointerEvent
          val delta = change.scrollDelta.y
          val shiftPressed = event.isShiftPressed()

          // Forward wheel events to app in alternate buffer (vim, less, etc.) unless Shift held
          if (settings.enableMouseReporting &&
            isRemoteMouseAction(shiftPressed) &&
            textBuffer.isUsingAlternateBuffer) {

            // Only forward if delta is significant (reduces sensitivity)
            // Terminal protocols send discrete events, not continuous deltas
            val absDelta = kotlin.math.abs(delta)
            if (absDelta >= settings.mouseScrollThreshold) {
              val (col, row) = pixelToCharCoords(change.position)
              val wheelEvent = createComposeMouseWheelEvent(event, delta)
              terminal.mouseWheelMoved(col, row, wheelEvent)
            }
            change.consume()
            return@onPointerEvent
          }

          // Local scroll (main buffer or Shift+Wheel override)
          val historySize = textBuffer.historyLinesCount
          scrollOffset = (scrollOffset - delta.toInt()).coerceIn(0, historySize)
        }
        .onPreviewKeyEvent { keyEvent ->
          // Track Ctrl/Cmd key state for hyperlink clicks and hover effects
          when (keyEvent.key) {
            Key.CtrlLeft, Key.CtrlRight, Key.MetaLeft, Key.MetaRight -> {
              val wasPressed = isModifierPressed
              isModifierPressed = keyEvent.type == KeyEventType.KeyDown

              // Request immediate redraw if modifier state changed and hovering over hyperlink
              // This ensures the underline appears/disappears immediately when Cmd/Ctrl is pressed/released
              if (wasPressed != isModifierPressed && hoveredHyperlink != null) {
                display.requestImmediateRedraw()
              }
            }
          }

          // Handle actions in preview (before search bar intercepts)
          // This allows shortcuts like Ctrl+F to work even when search bar is focused
          if (keyEvent.type == KeyEventType.KeyDown) {
            // Only handle search action in preview to intercept before search bar
            val action = actionRegistry.getAction("search")
            if (action != null && action.matchesKeyEvent(keyEvent, isMacOS)) {
              return@onPreviewKeyEvent action.execute(keyEvent)
            }
          }
          false  // Let other events pass through
        }
        .onKeyEvent { keyEvent ->
          // Don't consume keys when search bar is visible - let it handle them
          if (searchVisible) {
            return@onKeyEvent false
          }

          if (keyEvent.type == KeyEventType.KeyDown) {
            // Try to execute action from registry
            val action = actionRegistry.findAction(keyEvent)
            if (action != null && action.enabled()) {
              val consumed = action.execute(keyEvent)
              if (consumed) {
                return@onKeyEvent true
              }
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

            // IME (Input Method Editor) Support - IMPLEMENTED
            // Press Ctrl+Space to toggle IME mode for CJK (Chinese, Japanese, Korean) input.
            // When enabled, an invisible TextField appears at the cursor position to capture
            // IME composition events (e.g., Pinyin for Chinese, Hiragana for Japanese).
            // The composed text is forwarded to the terminal once committed.
            //
            // Implementation: See IMEHandler component (line ~1176) and IMEState (line ~148)
            // Keyboard shortcut: Ctrl+Space to toggle (line ~547)
            // See: ai.rever.bossterm.compose.ime package

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
                val vkCode = KeyMappingUtils.mapComposeKeyToVK(keyEvent.key)
                if (vkCode != null) {
                  val modifiers = KeyMappingUtils.mapComposeModifiers(keyEvent)
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
                // Feed keyboard event to type-ahead manager BEFORE sending to PTY
                // This creates predictions that reduce perceived latency on SSH
                tab.typeAheadManager?.let { manager ->
                  val typeAheadEvents = TerminalTypeAheadManager.TypeAheadEvent.fromString(text)
                  for (event in typeAheadEvents) {
                    manager.onKeyEvent(event)
                  }
                }

                tab.writeUserInput(text)
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
      // Show loading/error screen before connection is established
      if (connectionState !is ConnectionState.Connected) {
        PreConnectScreen(
          state = connectionState,
          onRetry = {
            // Reset to initializing and trigger re-composition
            // The LaunchedEffect will run again automatically
            connectionState = ConnectionState.Initializing
          }
        )
      } else {
        // Only show terminal UI when connected

        // Create incremental snapshot for lock-free rendering with copy-on-write optimization
        // Uses version tracking to reuse unchanged lines (99%+ allocation reduction)
        // Snapshot cached by Compose - recreated when display triggers redraw OR buffer dimensions change
        // This eliminates both lock contention (94%) AND allocation churn (99.5%)
        val bufferSnapshot = remember(display.redrawTrigger.value, textBuffer.width, textBuffer.height) {
          textBuffer.createIncrementalSnapshot()
        }

        Canvas(modifier = Modifier.padding(start = 4.dp, top = 4.dp).fillMaxSize()) {
          // Guard against invalid canvas sizes during resize - prevents drawText constraint failures
          if (size.width < cellWidth || size.height < cellHeight) return@Canvas

          // Capture canvas size for auto-scroll bounds detection in pointer event handlers
          canvasSize = size

          // Calculate visible bounds - limit rendering to what fits in canvas
          val visibleCols = (size.width / cellWidth).toInt().coerceAtMost(bufferSnapshot.width)
          val visibleRows = (size.height / cellHeight).toInt().coerceAtMost(bufferSnapshot.height)

          // Get cursor color from terminal (OSC 12)
          val customCursorColor = terminal.cursorColor
          val baseCursorColor = if (customCursorColor != null) {
            Color(customCursorColor.red, customCursorColor.green, customCursorColor.blue)
          } else null

          // Build rendering context with all state
          val renderingContext = RenderingContext(
            bufferSnapshot = bufferSnapshot,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            baseCellHeight = baseCellHeight,
            cellBaseline = cellMetrics.third,
            scrollOffset = scrollOffset,
            visibleCols = visibleCols,
            visibleRows = visibleRows,
            textMeasurer = textMeasurer,
            measurementFontFamily = sharedFont,
            fontSize = settings.fontSize,
            settings = settings,
            ambiguousCharsAreDoubleWidth = display.ambiguousCharsAreDoubleWidth(),
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            selectionMode = selectionMode,
            searchVisible = searchVisible,
            searchQuery = searchQuery,
            searchMatches = searchMatches,
            currentMatchIndex = currentMatchIndex,
            cursorX = cursorX,
            cursorY = cursorY,
            cursorVisible = cursorVisible,
            cursorBlinkVisible = cursorBlinkVisible,
            cursorShape = cursorShape,
            cursorColor = baseCursorColor,
            isFocused = isFocused,
            hoveredHyperlink = hoveredHyperlink,
            isModifierPressed = isModifierPressed,
            slowBlinkVisible = slowBlinkVisible,
            rapidBlinkVisible = rapidBlinkVisible
          )

          // Render terminal using extracted renderer - returns detected hyperlinks
          with(TerminalCanvasRenderer) {
            val detectedHyperlinks = renderTerminal(renderingContext)
            cachedHyperlinks = detectedHyperlinks
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
              tab.writeUserInput(text)
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
          modifier = Modifier.align(Alignment.TopEnd)
        )

        // Debug panel UI (bottom overlay)
        DebugPanel(
          visible = debugPanelVisible,
          collector = debugCollector,
          textBuffer = textBuffer,
          onClose = { debugPanelVisible = false },
          modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Restore focus to terminal when debug panel closes
        LaunchedEffect(debugPanelVisible) {
          if (!debugPanelVisible) {
            // Panel just closed - restore focus to terminal
            kotlinx.coroutines.delay(50)  // Let DebugPanel unmount first
            focusRequester.requestFocus()
          }
        }
      }

      // Vertical scrollbar on the right side - Always visible custom scrollbar
      if (settings.showScrollbar) {
        // Compute match positions for scrollbar markers
        val matchPositions = remember(searchMatches, textBuffer.historyLinesCount, textBuffer.height) {
          if (searchVisible && settings.showSearchMarkersInScrollbar) {
            computeMatchPositions(
              matches = searchMatches,
              historyLinesCount = textBuffer.historyLinesCount,
              screenHeight = textBuffer.height
            )
          } else {
            emptyList()
          }
        }

        AlwaysVisibleScrollbar(
          adapter = scrollbarAdapter,
          redrawTrigger = display.redrawTrigger,
          modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight(),
          thickness = settings.scrollbarWidth.dp,
          thumbColor = Color.White,
          trackColor = Color.White.copy(alpha = 0.12f),
          minThumbHeight = 32.dp,
          matchPositions = matchPositions,
          currentMatchIndex = currentMatchIndex,
          matchMarkerColor = settings.searchMarkerColorValue,
          currentMatchMarkerColor = settings.currentSearchMarkerColorValue,
          onMatchClicked = { matchIndex ->
            if (matchIndex in searchMatches.indices) {
              currentMatchIndex = matchIndex
              val (col, row) = searchMatches[matchIndex]
              scrollToMatch(row)
              highlightMatch(col, row, searchQuery.length)
            }
          }
        )
      }
    } // end Box

    // Context menu popup
    ContextMenuPopup(controller = contextMenuController)

    DisposableEffect(Unit) {
      onDispose {
        // Notify hover consumers when terminal is disposed
        if (previousHoveredHyperlink != null) {
          tab.hoverConsumers.forEach { it.onMouseExited() }
        }
        scope.launch {
          processHandle?.kill()
        }
      }
    }
  } // end else (Connected state)
}

/**
 * Escape a file path for safe shell usage.
 * Handles spaces, quotes, and other special characters.
 * Uses single quotes with escaped internal single quotes (iTerm2 style).
 */
private fun escapePathForShell(path: String): String {
  // Characters that need quoting in shell
  val needsQuoting = path.contains(' ') ||
    path.contains('\'') ||
    path.contains('"') ||
    path.contains('\\') ||
    path.contains('$') ||
    path.contains('`') ||
    path.contains('!') ||
    path.contains('*') ||
    path.contains('?') ||
    path.contains('[') ||
    path.contains(']') ||
    path.contains('(') ||
    path.contains(')') ||
    path.contains('{') ||
    path.contains('}') ||
    path.contains('&') ||
    path.contains(';') ||
    path.contains('<') ||
    path.contains('>') ||
    path.contains('|')

  return if (needsQuoting) {
    // Wrap in single quotes and escape any internal single quotes
    // 'foo'bar' becomes 'foo'\''bar'
    "'" + path.replace("'", "'\\''") + "'"
  } else {
    path
  }
}
