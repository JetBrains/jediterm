package org.jetbrains.jediterm.compose.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import com.jediterm.core.compatibility.Point
import com.jediterm.core.input.InputEvent
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.RequestOrigin
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TerminalKeyEncoder
import com.jediterm.terminal.emulator.ColorPaletteImpl
import com.jediterm.terminal.emulator.mouse.MouseButtonCodes
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.SelectionUtil.getNextSeparator
import com.jediterm.terminal.model.SelectionUtil.getPreviousSeparator
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.util.CharUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.jediterm.compose.ConnectionState
import org.jetbrains.jediterm.compose.PreConnectScreen
import org.jetbrains.jediterm.compose.actions.addTabManagementActions
import org.jetbrains.jediterm.compose.actions.createBuiltinActions
import org.jetbrains.jediterm.compose.debug.DebugPanel
import org.jetbrains.jediterm.compose.features.ContextMenuController
import org.jetbrains.jediterm.compose.features.ContextMenuPopup
import org.jetbrains.jediterm.compose.features.showHyperlinkContextMenu
import org.jetbrains.jediterm.compose.features.showTerminalContextMenu
import org.jetbrains.jediterm.compose.hyperlinks.Hyperlink
import org.jetbrains.jediterm.compose.hyperlinks.HyperlinkDetector
import org.jetbrains.jediterm.compose.ime.IMEHandler
import org.jetbrains.jediterm.compose.search.RabinKarpSearch
import org.jetbrains.jediterm.compose.scrollbar.AlwaysVisibleScrollbar
import org.jetbrains.jediterm.compose.scrollbar.computeMatchPositions
import org.jetbrains.jediterm.compose.scrollbar.rememberTerminalScrollbarAdapter
import org.jetbrains.jediterm.compose.search.SearchBar
import org.jetbrains.jediterm.compose.settings.SettingsManager
import org.jetbrains.jediterm.compose.tabs.TerminalTab
import com.jediterm.core.typeahead.TerminalTypeAheadManager
import org.jetbrains.skia.FontMgr
import com.jediterm.terminal.TextStyle as JediTextStyle
import java.awt.event.KeyEvent as JavaKeyEvent

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
private fun mapComposeModifiers(keyEvent: KeyEvent): Int {
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
 *
 * Refactored to support multiple tabs - accepts a TerminalTab with all per-tab state.
 */
@OptIn(
  ExperimentalComposeUiApi::class,
  ExperimentalTextApi::class
)
@Composable
fun ProperTerminal(
  tab: TerminalTab,
  isActiveTab: Boolean,
  sharedFont: FontFamily,
  onTabTitleChange: (String) -> Unit,
  onNewTab: () -> Unit = {},
  onNewPreConnectTab: () -> Unit = {},  // Ctrl+Shift+T: Test pre-connection input
  onCloseTab: () -> Unit = {},
  onNextTab: () -> Unit = {},
  onPreviousTab: () -> Unit = {},
  onSwitchToTab: (Int) -> Unit = {},
  modifier: Modifier = Modifier
) {
  // Extract tab state (no more remember {} blocks - state lives in TerminalTab)
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
  val actionRegistry = remember(isMacOS) {
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
                val (start, end) = selectWordAt(col, bufferRow, textBuffer)
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
                val (start, end) = selectLineAt(col, bufferRow, textBuffer)
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
            val selectedText = extractSelectedText(textBuffer, start, end)
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

        // Create immutable snapshot for lock-free rendering
        // Snapshot cached by Compose - recreated when display triggers redraw OR buffer dimensions change
        // This eliminates the 15ms lock hold that was blocking PTY writers (94% reduction in contention)
        // Adding buffer dimensions as keys ensures snapshot updates after initial resize
        val bufferSnapshot = remember(display.redrawTrigger.value, textBuffer.width, textBuffer.height) {
          textBuffer.createSnapshot()
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
          // Guard against invalid canvas sizes during resize - prevents drawText constraint failures
          if (size.width < cellWidth || size.height < cellHeight) return@Canvas

          // Capture canvas size for auto-scroll bounds detection in pointer event handlers
          canvasSize = size

          // Two-pass rendering to fix z-index issue:
          // Pass 1: Draw all backgrounds first
          // Pass 2: Draw all text on top
          // This prevents backgrounds from overlapping emoji that extend beyond cell boundaries

          // NO LOCK HELD - rendering from immutable snapshot instead
          val height = bufferSnapshot.height
          val width = bufferSnapshot.width

          // Calculate visible bounds - limit rendering to what fits in canvas
          // This prevents crashes when buffer is wider/taller than current canvas during resize
          val visibleCols = (size.width / cellWidth).toInt().coerceAtMost(width)
          val visibleRows = (size.height / cellHeight).toInt().coerceAtMost(height)

          // ===== PASS 1: DRAW ALL BACKGROUNDS =====
          for (row in 0 until visibleRows) {
            val lineIndex = row - scrollOffset
            val line = bufferSnapshot.getLine(lineIndex)

              var col = 0
              while (col < visibleCols) {
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
          for (row in 0 until visibleRows) {
            // Calculate actual line index based on scroll offset
            // scrollOffset = 0 means showing screen lines 0..height-1
            // scrollOffset > 0 means showing some history lines
            val lineIndex = row - scrollOffset
            val line = bufferSnapshot.getLine(lineIndex)

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

              var col = 0  // Character index in buffer
              var visualCol = 0  // Visual column position (accounts for double-width chars)
              while (col < visibleCols) {
                val char = line.charAt(col)
                val style = line.getStyleAt(col)

                // Skip double-width character continuation markers
                // CRITICAL FIX: DWC is a storage artifact, not a separate visual cell
                // Visual width is already accounted for via grapheme.visualWidth or isWcwidthDoubleWidth
                // Incrementing visualCol here would double-count and cause spacing issues with emoji
                if (char == CharUtils.DWC) {
                  col++
                  // Do NOT increment visualCol here
                  continue
                }

                // GRAPHEME CLUSTER HANDLING: Check for ZWJ sequences
                // ZWJ (Zero-Width Joiner) U+200D combines multiple emoji into single visual unit
                // Examples: 👨‍👩‍👧‍👦 (family), 👨‍💻 (man technologist)
                // Problem: line.text contains DWC markers that break ZWJ sequences
                // Solution: Extract clean text by skipping DWC markers

                // Build clean text from current position (no DWC markers)
                val cleanText = buildString {
                  var i = col
                  var count = 0
                  while (i < width && count < 20) {  // Look ahead max 20 chars
                    val c = line.charAt(i)
                    if (c != CharUtils.DWC) {
                      append(c)
                      count++
                    }
                    i++
                  }
                }

                // Check if current character is IMMEDIATELY followed by skin tone modifier
                // Skin tone modifiers: U+1F3FB to U+1F3FF (stored as surrogate pairs)
                fun hasFollowingSkinTone(): Boolean {
                  // Skip past current character (might be surrogate pair + DWC)
                  var checkCol = col
                  val currentChar = line.charAt(checkCol)

                  // If current is high surrogate, skip past the pair
                  if (Character.isHighSurrogate(currentChar)) {
                    checkCol++  // Skip low surrogate
                  }
                  checkCol++  // Move to next position

                  // Skip DWC if present (for double-width chars)
                  if (checkCol < width && line.charAt(checkCol) == CharUtils.DWC) {
                    checkCol++
                  }

                  // Now check if we're at a skin tone modifier
                  if (checkCol < width - 1) {
                    val c1 = line.charAt(checkCol)
                    // Skin tone modifiers are surrogate pairs: high=\uD83C, low=\uDFFB-\uDFFF
                    if (c1 == '\uD83C' && checkCol + 1 < width) {
                      val c2 = line.charAt(checkCol + 1)
                      if (c2.code in 0xDFFB..0xDFFF) {
                        return true
                      }
                    }
                  }

                  return false
                }

                val hasZWJ = cleanText.contains('\u200D')
                val hasSkinTone = hasFollowingSkinTone()

                if (hasZWJ || hasSkinTone) {
                  // Segment clean text into graphemes
                  val graphemes = com.jediterm.terminal.util.GraphemeUtils.segmentIntoGraphemes(cleanText)

                  if (graphemes.isNotEmpty()) {
                    val grapheme = graphemes[0]

                    // Handle if grapheme contains ZWJ or skin tone
                    if (grapheme.hasZWJ || hasSkinTone) {
                      // Flush any pending batch before rendering ZWJ sequence
                      flushBatch()

                      val x = visualCol * cellWidth  // Use visual column for positioning
                      val y = row * cellHeight

                      // Get style for first character of grapheme
                      val isBold = style?.hasOption(JediTextStyle.Option.BOLD) ?: false
                      val isItalic = style?.hasOption(JediTextStyle.Option.ITALIC) ?: false
                      val isInverse = style?.hasOption(JediTextStyle.Option.INVERSE) ?: false
                      val isDim = style?.hasOption(JediTextStyle.Option.DIM) ?: false

                      // Apply color logic
                      val baseFg = style?.foreground?.let { convertTerminalColor(it) } ?: settings.defaultForegroundColor
                      val baseBg = style?.background?.let { convertTerminalColor(it) } ?: settings.defaultBackgroundColor
                      var fgColor = if (isInverse) baseBg else baseFg
                      if (isDim) fgColor = applyDimColor(fgColor)

                      // Use system font (FontFamily.Default) for ZWJ sequences
                      // This enables Apple Color Emoji on macOS which has combined glyphs
                      val textStyle = TextStyle(
                        color = fgColor,
                        fontFamily = FontFamily.Default,  // System font with ZWJ support
                        fontSize = settings.fontSize.sp,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (isItalic) androidx.compose.ui.text.font.FontStyle.Italic
                        else androidx.compose.ui.text.font.FontStyle.Normal
                      )

                      // Render the complete ZWJ sequence as a single unit
                      val measurement = textMeasurer.measure(grapheme.text, textStyle)
                      val glyphWidth = measurement.size.width.toFloat()
                      val glyphHeight = measurement.size.height.toFloat()

                      // Calculate scale to fit in allocated space
                      val allocatedWidth = cellWidth * grapheme.visualWidth.toFloat()
                      val targetHeight = cellHeight * 1.0f

                      val widthScale = if (glyphWidth > 0) allocatedWidth / glyphWidth else 1.0f
                      val heightScale = if (glyphHeight > 0) targetHeight / glyphHeight else 1.0f
                      val scale = minOf(widthScale, heightScale).coerceIn(0.8f, 2.5f)

                      // Center in allocated space
                      val scaledWidth = glyphWidth * scale
                      val scaledHeight = glyphHeight * scale
                      val centerX = x + (allocatedWidth - scaledWidth) / 2f
                      val centerY = y + (cellHeight - scaledHeight) / 2f

                      scale(scaleX = scale, scaleY = scale, pivot = Offset(x, y + cellHeight / 2f)) {
                        drawText(
                          textMeasurer = textMeasurer,
                          text = grapheme.text,
                          topLeft = Offset(x + (centerX - x) / scale, y),
                          style = textStyle
                        )
                      }

                      // Advance by matching grapheme text against buffer cells
                      // We must consume ALL cells (including DWC markers) for this grapheme
                      val graphemeText = grapheme.text
                      var graphemeCharIndex = 0
                      var charsToSkip = 0
                      var i = col

                      while (i < width && graphemeCharIndex < graphemeText.length) {
                        val c = line.charAt(i)

                        if (c == CharUtils.DWC) {
                          // DWC markers are part of grapheme storage but not in grapheme.text
                          charsToSkip++
                          i++
                          continue
                        }

                        // Match this buffer character against grapheme.text
                        val expectedChar = graphemeText[graphemeCharIndex]

                        if (Character.isHighSurrogate(c) && i + 1 < width) {
                          val next = line.charAt(i + 1)
                          if (Character.isLowSurrogate(next)) {
                            // Verify surrogate pair matches
                            if (graphemeCharIndex + 1 < graphemeText.length &&
                              graphemeText[graphemeCharIndex] == c &&
                              graphemeText[graphemeCharIndex + 1] == next) {
                              charsToSkip += 2
                              i += 2
                              graphemeCharIndex += 2
                              continue
                            }
                          }
                        }

                        // Single character match
                        if (expectedChar == c) {
                          charsToSkip++
                          i++
                          graphemeCharIndex++
                        } else {
                          // Mismatch - stop to avoid consuming wrong cells
                          println("[WARN] Grapheme match break: grapheme='${grapheme.text}' expected='$expectedChar'(${expectedChar.code}) got='$c'(${c.code}) at col=$col i=$i")
                          println("[WARN]   charsToSkip=$charsToSkip graphemeCharIndex=$graphemeCharIndex/${graphemeText.length}")
                          println("[WARN]   This will cause desync: advancing col by $charsToSkip but visualCol by ${grapheme.visualWidth}")
                          break
                        }
                      }

                      // Check if we fully matched the grapheme
                      val fullyMatched = graphemeCharIndex == graphemeText.length
                      if (!fullyMatched) {
                        println("[ERROR] Partial grapheme match at row=$row col=$col:")
                        println("[ERROR]   grapheme='${grapheme.text}' visualWidth=${grapheme.visualWidth}")
                        println("[ERROR]   matched $graphemeCharIndex/${graphemeText.length} chars, skipped $charsToSkip buffer cells")
                        println("[ERROR]   Buffer will desync: col+=$charsToSkip visualCol+=${grapheme.visualWidth}")
                      }

                      // After matching grapheme, check if there are DWC markers we need to skip
                      while (i < width && line.charAt(i) == CharUtils.DWC) {
                        charsToSkip++
                        i++
                      }

                      col += charsToSkip
                      // Use grapheme's logical visual width, not buffer cell count
                      // Family emoji takes 8+ buffer cells but only 2 visual columns
                      visualCol += grapheme.visualWidth

                      continue
                    }
                  }
                }

                val x = visualCol * cellWidth  // Use visual column for positioning
                val y = row * cellHeight

                // For surrogate pairs, we need to find the low surrogate FIRST
                // because we need the actual codepoint for wcwidth check
                // Check what's at col+1 and col+2
                val charAtCol1 = if (col + 1 < width) line.charAt(col + 1) else null
                val charAtCol2 = if (col + 2 < width) line.charAt(col + 2) else null

                // Find low surrogate: could be at col+1 (single-width) or col+2 (if col+1 is DWC)
                val lowSurrogate = if (Character.isHighSurrogate(char)) {
                  when {
                    charAtCol1 != null && Character.isLowSurrogate(charAtCol1) -> charAtCol1
                    charAtCol1 == CharUtils.DWC && charAtCol2 != null && Character.isLowSurrogate(charAtCol2) -> charAtCol2
                    else -> null
                  }
                } else null
                val lowSurrogatePos = when (lowSurrogate) {
                  charAtCol1 -> col + 1
                  charAtCol2 -> col + 2
                  else -> -1
                }

                // Calculate actual codepoint (combining surrogates if needed)
                val actualCodePoint = if (lowSurrogate != null && Character.isLowSurrogate(lowSurrogate)) {
                  Character.toCodePoint(char, lowSurrogate)
                } else char.code

                // Check if this is a double-width character according to wcwidth
                // IMPORTANT: Use actualCodePoint, not char.code, for surrogate pairs!
                val wcwidthResult = char != ' ' && char != '\u0000' &&
                  CharUtils.isDoubleWidthCharacter(actualCodePoint, display.ambiguousCharsAreDoubleWidth())

                // Detect actual width from buffer: if col+1 has DWC, it's stored as double-width
                val isWcwidthDoubleWidth = charAtCol1 == CharUtils.DWC || wcwidthResult

                // Determine the text to render - for surrogate pairs, combine high and low surrogates
                val charTextToRender = if (lowSurrogate != null && Character.isLowSurrogate(lowSurrogate)) {
                  "$char$lowSurrogate"
                } else {
                  char.toString()
                }

                // Detect cursive/mathematical characters - need system font but no special scaling
                val isCursiveOrMath = actualCodePoint in 0x1D400..0x1D7FF

                // Detect technical/prompt symbols - need system font, horizontal centering, baseline-aligned
                // These symbols should align with text baseline, not be bounding-box centered like emoji
                val isTechnicalSymbol = actualCodePoint in 0x23E9..0x23FF  // Media/prompt symbols (⏩⏪⏫⏬⏭⏮⏯⏰⏱⏲⏳⏴⏵⏶⏷⏸⏹⏺)

                // For emoji and symbols, we'll render them with slight scaling for better visibility
                // These are Unicode blocks containing symbols that render poorly at normal size
                // IMPORTANT: Use actualCodePoint for surrogate pair emoji!
                val isEmojiOrWideSymbol = when (actualCodePoint) {
                  in 0x2600..0x26FF -> true  // Miscellaneous Symbols (☁️, ☀️, ★, etc.)
                  // Note: Dingbats (0x2700-0x27BF) removed - Nerd Font has monochrome glyphs (✳, ❯, etc.)
                  in 0x1F300..0x1F9FF -> true  // Emoji & Pictographs
                  in 0x1F600..0x1F64F -> true  // Emoticons
                  in 0x1F680..0x1F6FF -> true  // Transport & Map Symbols
                  else -> false
                }

                // Separate rendering width from buffer storage width
                // Emoji (>= 0x1F300) should render in 2 cells even if stored as single-width
                val isDoubleWidth = if (actualCodePoint >= 0x1F300) {
                  true  // Force emoji to render in 2-cell space
                } else {
                  isWcwidthDoubleWidth  // Use buffer storage width
                }

                // Peek ahead to detect emoji + variation selector pairs
                // When found, we'll handle them together with system font
                // For double-width emoji, need to skip DWC marker at col+1 to check col+2
                val nextCharOffset = if (isWcwidthDoubleWidth) 2 else 1
                val nextChar = if (col + nextCharOffset < width) line.charAt(col + nextCharOffset) else null
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
                // Cursive/math/technical chars can't be batched (need system font), emoji need special scaling
                val canBatch = !isDoubleWidth && !isEmojiOrWideSymbol && !isCursiveOrMath && !isTechnicalSymbol &&
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
                    batchStartCol = visualCol  // Use visual column for rendering position
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
                    // For emoji/symbols/cursive, use system font (FontFamily.Default)
                    // to enable proper emoji rendering on macOS (Apple Color Emoji)
                    // MesloLGSNF doesn't have glyphs for emoji or mathematical alphanumerics
                    val fontForChar = if (isEmojiOrWideSymbol || isEmojiWithVariationSelector || isCursiveOrMath) {
                      FontFamily.Default  // System font with emoji/Unicode support
                    } else if (isTechnicalSymbol) {
                      // Use STIX Two Math for monochrome technical symbols (avoids Apple Color Emoji)
                      // Load system font via Skia FontMgr
                      val skiaTypeface = FontMgr.default.matchFamilyStyle("STIX Two Math", org.jetbrains.skia.FontStyle.NORMAL)
                      if (skiaTypeface != null) {
                        FontFamily(androidx.compose.ui.text.platform.Typeface(skiaTypeface))
                      } else {
                        measurementStyle.fontFamily  // Fallback to Nerd Font
                      }
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
                      val measurement = textMeasurer.measure(charTextToRender, textStyle)
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
                            text = charTextToRender,
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
                          text = charTextToRender,
                          topLeft = Offset(x + centeringOffset, y),
                          style = textStyle
                        )
                      }
                    } else if (isEmojiOrWideSymbol) {
                      // For emoji/symbols: measure and scale to fit cell better
                      // If this is emoji+variation selector pair, render both together
                      val textToRender = if (isEmojiWithVariationSelector) {
                        "$charTextToRender$nextChar"  // Render emoji + variation selector together
                      } else {
                        charTextToRender
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
                        // Variation selector doesn't add visual width
                      }
                    } else if (isCursiveOrMath) {
                      // Cursive/math characters: center in cell to prevent overlap
                      val measurement = textMeasurer.measure(charTextToRender, textStyle)
                      val glyphWidth = measurement.size.width.toFloat()
                      val centeringOffset = ((cellWidth - glyphWidth) / 2f).coerceAtLeast(0f)
                      drawText(
                        textMeasurer = textMeasurer,
                        text = charTextToRender,
                        topLeft = Offset(x + centeringOffset, y),
                        style = textStyle
                      )
                    } else if (isTechnicalSymbol) {
                      // Technical symbols: horizontal center + baseline-aligned using STIX Two Math font
                      val measurement = textMeasurer.measure(charTextToRender, textStyle)
                      val glyphWidth = measurement.size.width.toFloat()
                      val glyphBaseline = measurement.firstBaseline  // Symbol's baseline from top
                      val cellBaseline = cellMetrics.third           // Cell's expected baseline from 'W'

                      // Offset Y so the symbol's baseline aligns with the cell's baseline
                      val baselineAlignmentOffset = cellBaseline - glyphBaseline
                      val centeringOffset = ((cellWidth - glyphWidth) / 2f).coerceAtLeast(0f)

                      drawText(
                        textMeasurer = textMeasurer,
                        text = charTextToRender,
                        topLeft = Offset(x + centeringOffset, y + baselineAlignmentOffset),
                        style = textStyle
                      )
                    } else {
                      // Normal single-width rendering
                      drawText(
                        textMeasurer = textMeasurer,
                        text = charTextToRender,
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

                  }
                }  // Close else block

                // If true double-width (wcwidth), skip the next column (contains DWC marker)
                // For emoji/symbols, don't skip - they're single-width in the buffer but render wider
                if (isWcwidthDoubleWidth) {
                  col++  // Skip DWC marker in buffer
                  // No visualCol++ here because we do it below
                }

                col++  // Advance to next character in buffer

                // Skip the low surrogate if we found one
                // For single-width: [high][low] - we've advanced past high, now skip low
                // For double-width: [high][DWC][low] - we've skipped DWC and advanced, now skip low
                if (lowSurrogate != null) {
                  col++  // Skip the low surrogate
                }

                visualCol++  // Advance visual column (1 for single-width, handled by DWC skip for double-width)

                // For double-width characters (rendering width, not buffer width),
                // add 1 more to visualCol since they occupy 2 visual columns
                // This includes emoji forced to double-width even if stored as single-width
                if (isDoubleWidth) {
                  visualCol++  // Double-width takes 2 visual columns
                }
              }

              // Flush any remaining batch at end of line
              flushBatch()
            }

            // ===== PASS 3: DRAW HYPERLINK UNDERLINE =====
            // Draw hyperlink underline if hovered with Ctrl/Cmd modifier
            // This provides standard IDE-like behavior: underline only shows when modifier is pressed
            // Must be done as separate pass since text batching would otherwise skip the underline check
            if (settings.hyperlinkUnderlineOnHover &&
              hoveredHyperlink != null &&
              isModifierPressed) {
              val link = hoveredHyperlink!!
              // Only draw if hyperlink is visible on screen
              if (link.row in 0 until visibleRows) {
                val y = link.row * cellHeight
                val underlineY = y + cellHeight - 1f
                val startX = link.startCol * cellWidth
                val endX = link.endCol * cellWidth
                drawLine(
                  color = settings.hyperlinkColorValue,
                  start = Offset(startX, underlineY),
                  end = Offset(endX, underlineY),
                  strokeWidth = 1f
                )
              }
            }

            // Draw search match highlights (all matches)
            if (searchVisible && searchMatches.isNotEmpty()) {
              val matchLength = searchQuery.length
              searchMatches.forEachIndexed { index, (matchCol, matchRow) ->
                // Convert buffer-relative row to screen row
                val screenRow = matchRow + scrollOffset
                if (screenRow in 0 until visibleRows) {
                  // Use orange for current match, yellow for others
                  val matchColor = if (index == currentMatchIndex) {
                    settings.currentSearchMarkerColorValue.copy(alpha = 0.6f)  // Orange for current
                  } else {
                    settings.searchMarkerColorValue.copy(alpha = 0.4f)  // Yellow for others
                  }

                  // Draw highlight for each character in the match
                  for (charOffset in 0 until matchLength) {
                    val col = matchCol + charOffset
                    if (col in 0 until width) {
                      val x = col * cellWidth
                      val y = screenRow * cellHeight
                      drawRect(
                        color = matchColor,
                        topLeft = Offset(x, y),
                        size = Size(cellWidth, cellHeight)
                      )
                    }
                  }
                }
              }
            }

            // Draw selection highlight (for manual selection, not search)
            if (selectionStart != null && selectionEnd != null && !(searchVisible && searchMatches.isNotEmpty())) {
              val start = selectionStart!!
              val end = selectionEnd!!

              val (startCol, startRow) = start
              val (endCol, endRow) = end

              // Determine first (earlier row) and last (later row) points
              // This is direction-aware: first point is always the one with smaller row
              val (firstCol, firstRow, lastCol, lastRow) = if (startRow <= endRow) {
                listOf(startCol, startRow, endCol, endRow)
              } else {
                listOf(endCol, endRow, startCol, startRow)
              }

              // Use foundPatternColor (yellow) for search results, selectionColor (blue) for manual selection
              val highlightColor = if (searchVisible && searchMatches.isNotEmpty()) {
                settings.foundPatternColorValue.copy(alpha = 0.5f)  // Yellow for search
              } else {
                settings.selectionColorValue.copy(alpha = 0.3f)     // Blue for selection
              }

              // Draw selection highlight rectangles
              // Selection coords are buffer-relative, convert to screen coords for rendering
              for (bufferRow in firstRow..lastRow) {
                val screenRow = bufferRow + scrollOffset  // Convert buffer to screen row
                if (screenRow in 0 until visibleRows) {  // Check if visible on screen
                  // For single-line selection, use min/max columns
                  // For multi-line: first row from firstCol to end, middle rows full, last row from 0 to lastCol
                  val (colStart, colEnd) = if (firstRow == lastRow) {
                    // Single line: use min/max columns
                    minOf(firstCol, lastCol) to maxOf(firstCol, lastCol)
                  } else {
                    // Multi-line: direction-aware columns
                    when (bufferRow) {
                      firstRow -> firstCol to (width - 1)  // First row: from start col to end
                      lastRow -> 0 to lastCol              // Last row: from 0 to end col
                      else -> 0 to (width - 1)             // Middle rows: full line
                    }
                  }

                  for (col in colStart..colEnd) {
                    if (col in 0 until width) {
                      val x = col * cellWidth
                      val y = screenRow * cellHeight  // Use screen row for Y position
                      drawRect(
                        color = highlightColor,
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
          // No unlock needed - snapshot was created outside Canvas, lock already released

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
 * Select word at the given character coordinates using SelectionUtil.
 * Returns the selection as a pair of (start, end) coordinates.
 *
 * Note: SelectionUtil functions (getPreviousSeparator/getNextSeparator) expect TerminalTextBuffer
 * and may handle locking internally. We removed explicit lock/unlock here to avoid redundant locking.
 * Future enhancement: Create snapshot-compatible versions of SelectionUtil functions.
 */
private fun selectWordAt(
  col: Int,
  row: Int,
  textBuffer: TerminalTextBuffer
): Pair<Pair<Int, Int>, Pair<Int, Int>> {
  // Convert Pair<Int, Int> to Point for SelectionUtil
  val clickPoint = Point(col, row)

  // Get word boundaries using SelectionUtil
  // SelectionUtil may handle its own locking internally
  val startPoint = getPreviousSeparator(clickPoint, textBuffer)
  val endPoint = getNextSeparator(clickPoint, textBuffer)

  // Convert Point back to Pair<Int, Int>
  return Pair(Pair(startPoint.x, startPoint.y), Pair(endPoint.x, endPoint.y))
}

/**
 * Select entire logical line at the given character coordinates.
 * Handles wrapped lines by walking backwards and forwards through isWrapped property.
 * Returns the selection as a pair of (start, end) coordinates.
 */
private fun selectLineAt(
  col: Int,
  row: Int,
  textBuffer: TerminalTextBuffer
): Pair<Pair<Int, Int>, Pair<Int, Int>> {
  // Create immutable snapshot (fast, <1ms with lock, then lock released)
  // This allows PTY writers to continue during line selection calculation
  val snapshot = textBuffer.createSnapshot()

  var startLine = row
  var endLine = row

  // Walk backwards through wrapped lines to find logical line start
  while (startLine > -snapshot.historyLinesCount) {
    val prevLine = snapshot.getLine(startLine - 1)
    if (prevLine.isWrapped) {
      startLine--
    } else {
      break
    }
  }

  // Walk forwards through wrapped lines to find logical line end
  while (endLine < snapshot.height - 1) {
    val currentLine = snapshot.getLine(endLine)
    if (currentLine.isWrapped) {
      endLine++
    } else {
      break
    }
  }

  // Select from start of first line to end of last line
  return Pair(Pair(0, startLine), Pair(snapshot.width - 1, endLine))
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

  // Determine first (earlier row) and last (later row) points
  // This is direction-aware: first point is always the one with smaller row
  val (firstCol, firstRow, lastCol, lastRow) = if (startRow <= endRow) {
    listOf(startCol, startRow, endCol, endRow)
  } else {
    listOf(endCol, endRow, startCol, startRow)
  }

  // Use snapshot for lock-free text extraction
  val snapshot = textBuffer.createSnapshot()
  val result = StringBuilder()

  for (row in firstRow..lastRow) {
    val line = snapshot.getLine(row)

    // For single-line selection, use min/max columns
    // For multi-line: first row from firstCol to end, middle rows full, last row from 0 to lastCol
    val (colStart, colEnd) = if (firstRow == lastRow) {
      // Single line: use min/max columns
      minOf(firstCol, lastCol) to maxOf(firstCol, lastCol)
    } else {
      // Multi-line: direction-aware columns
      when (row) {
        firstRow -> firstCol to (snapshot.width - 1)  // First row: from start col to end
        lastRow -> 0 to lastCol                        // Last row: from 0 to end col
        else -> 0 to (snapshot.width - 1)              // Middle rows: full line
      }
    }

    for (col in colStart..colEnd) {
      if (col < snapshot.width) {
        val char = line.charAt(col)
        // Skip DWC markers
        if (char != CharUtils.DWC) {
          result.append(char)
        }
      }
    }

    // Add newline between rows (except after last row)
    if (row < lastRow) {
      result.append('\n')
    }
  }

  return result.toString()
}

// filterEnvironmentVariables function moved to TabController.kt

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
