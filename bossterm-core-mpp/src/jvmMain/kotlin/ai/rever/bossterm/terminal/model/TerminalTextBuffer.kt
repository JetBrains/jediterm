package ai.rever.bossterm.terminal.model

import ai.rever.bossterm.core.compatibility.Point
import ai.rever.bossterm.core.util.CellPosition
import ai.rever.bossterm.core.util.TermSize
import ai.rever.bossterm.terminal.StyledTextConsumer
import ai.rever.bossterm.terminal.TextStyle
import ai.rever.bossterm.terminal.model.TerminalLine.TextEntry
import ai.rever.bossterm.terminal.model.hyperlinks.TextProcessing
import ai.rever.bossterm.terminal.model.image.ImageCell
import ai.rever.bossterm.terminal.model.pool.IncrementalSnapshotBuilder
import ai.rever.bossterm.terminal.model.pool.VersionedBufferSnapshot
import ai.rever.bossterm.terminal.util.CharUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.max
import kotlin.math.min

/**
 * Buffer for storing styled text data.
 * Stores only text that fit into one screen XxY, but has scrollBuffer to save history lines and screenBuffer to restore
 * screen after resize. ScrollBuffer stores all lines before the first line currently shown on the screen. TextBuffer
 * stores lines that are shown currently on the screen, and they have there (in TextBuffer) their initial length (even if
 * it doesn't fit to screen width).
 */
@Suppress("DEPRECATION", "removal")
class TerminalTextBuffer internal constructor(
  initialWidth: Int,
  initialHeight: Int,
  private val styleState: StyleState,
  private val maxHistoryLinesCount: Int,
  internal val textProcessing: TextProcessing?
) {
  // Public accessor for Java interop
  fun getTextProcessing(): TextProcessing? = textProcessing
  var width: Int = initialWidth
    private set
  var height: Int = initialHeight
    private set

  var historyLinesStorage: LinesStorage = createHistoryLinesStorage()
    private set
  var screenLinesStorage: LinesStorage = createScreenLinesStorage()
    private set


  internal val historyLinesStorageOrBackup: LinesStorage
    get() = if (isUsingAlternateBuffer) {
      historyLinesStorageBackup ?: historyLinesStorage
    } else {
      historyLinesStorage
    }

  internal val screenLinesStorageOrBackup: LinesStorage
    get() = if (isUsingAlternateBuffer) {
      screenLinesStorageBackup ?: screenLinesStorage
    } else {
      screenLinesStorage
    }

  // Public accessors for Java interop
  fun getHistoryLinesStorageOrBackup(): LinesStorage = historyLinesStorageOrBackup
  fun getScreenLinesStorageOrBackup(): LinesStorage = screenLinesStorageOrBackup

  val historyLinesCount: Int
    get() = historyLinesStorage.size

  val screenLinesCount: Int
    get() = screenLinesStorage.size

  private val myLock: Lock = ReentrantLock()

  /**
   * Incremental snapshot builder for optimized copy-on-write snapshots.
   * Reduces allocation from ~430KB/frame to <10KB/frame by reusing unchanged lines.
   */
  private val snapshotBuilder = IncrementalSnapshotBuilder()

  private var historyLinesStorageBackup: LinesStorage? = null
  private var screenLinesStorageBackup: LinesStorage? = null

  private var alternateBuffer = false

  var isUsingAlternateBuffer: Boolean = false
    private set

  private val listeners: MutableList<TerminalModelListener> = CopyOnWriteArrayList()
  private val historyBufferListeners: MutableList<TerminalHistoryBufferListener> = CopyOnWriteArrayList()
  private val changesMulticaster: TextBufferChangesMulticaster = TextBufferChangesMulticaster()

  // Callback for notifying when lines move between screen and history during resize
  private var resizeCallback: BufferResizeCallback? = null

  fun setResizeCallback(callback: BufferResizeCallback?) {
    resizeCallback = callback
  }

  // ===== BATCH CHANGE TRACKING =====
  // Suppresses intermediate modelChanged events during rapid sequences like clear+write
  @Volatile
  private var batchDepth: Int = 0
  @Volatile
  private var batchHasChanges: Boolean = false

  @JvmOverloads
  constructor(width: Int, height: Int, styleState: StyleState, maxHistoryLinesCount: Int = LinesStorage.DEFAULT_MAX_LINES_COUNT) : this(
    width,
    height,
    styleState,
    maxHistoryLinesCount,
    textProcessing = null
  )

  private fun createScreenLinesStorage(): LinesStorage {
    return CyclicBufferLinesStorage(-1)
  }

  private fun createHistoryLinesStorage(): LinesStorage {
    return CyclicBufferLinesStorage(maxHistoryLinesCount)
  }


  fun resize(newTermSize: TermSize, oldCursor: CellPosition, selection: TerminalSelection?): TerminalResizeResult {
    val newWidth = newTermSize.columns
    val newHeight = newTermSize.rows
    var newCursorX = oldCursor.x
    var newCursorY = oldCursor.y
    val oldCursorY = oldCursor.y

    if (width != newWidth) {
      val changeWidthOperation = ChangeWidthOperation(this, newWidth, newHeight)
      val cursorPoint = Point(oldCursor.x - 1, oldCursor.y - 1)
      changeWidthOperation.addPointToTrack(cursorPoint, true)
      if (selection != null) {
        changeWidthOperation.addPointToTrack(selection.start, false)
        val selectionEnd = selection.end
        if (selectionEnd != null) {
          changeWidthOperation.addPointToTrack(selectionEnd, false)
        }
      }
      // Track image anchor rows through the reflow
      val anchorRows = resizeCallback?.getAnchorRowsToTrack() ?: emptySet()
      for (row in anchorRows) {
        changeWidthOperation.addAnchorRowToTrack(row)
      }
      changeWidthOperation.run()
      width = newWidth
      height = newHeight
      val newCursor = changeWidthOperation.getTrackedPoint(cursorPoint)
      newCursorX = newCursor.x + 1
      newCursorY = newCursor.y + 1
      if (selection != null) {
        selection.start.setLocation(changeWidthOperation.getTrackedPoint(selection.start))
        val selectionEnd = selection.end
        if (selectionEnd != null) {
          selectionEnd.setLocation(changeWidthOperation.getTrackedPoint(selectionEnd))
        }
      }
      // Notify callback with anchor mapping so images can be repositioned
      if (anchorRows.isNotEmpty()) {
        resizeCallback?.onWidthChanged(changeWidthOperation.getAnchorMapping())
      }
      changesMulticaster.widthResized()
    }

    val oldHeight = height
    if (newHeight < oldHeight) {
      if (!alternateBuffer) {
        val lineDiffCount = oldHeight - newHeight

        // Keep cursor in view: cursor must end up at row < newHeight
        // If cursor is currently at row Y, we need cursor to be at row min(Y, newHeight-1)

        // Lines we MUST remove from top to keep cursor in view
        val linesToRemoveFromTop = max(0, oldCursorY - newHeight + 1)
        // Remaining lines to remove come from bottom
        val linesToRemoveFromBottom = lineDiffCount - linesToRemoveFromTop

        // Remove from bottom first (preserves content at/above cursor)
        if (linesToRemoveFromBottom > 0) {
          // First account for virtual empty lines (lines beyond screenLinesStorage.size)
          val emptyLinesCount = min(linesToRemoveFromBottom, oldHeight - screenLinesStorage.size)
          val nonEmptyToRemove = linesToRemoveFromBottom - emptyLinesCount
          if (nonEmptyToRemove > 0) {
            // Try to remove empty lines from bottom of storage first
            val actualEmptyRemoved = removeBottomEmptyLines(nonEmptyToRemove)
            val stillNeedToRemove = nonEmptyToRemove - actualEmptyRemoved
            if (stillNeedToRemove > 0) {
              // Remove non-empty lines from bottom (discarded, not added to history)
              screenLinesStorage.removeFromBottom(stillNeedToRemove)
            }
          }
        }

        // Remove from top only if cursor would be off-screen
        if (linesToRemoveFromTop > 0) {
          val removedLines = screenLinesStorage.removeFromTop(linesToRemoveFromTop)
          addLinesToHistory(removedLines)
          newCursorY = oldCursorY - linesToRemoveFromTop
          selection?.shiftY(-linesToRemoveFromTop)
          // Notify callback so image anchors can be adjusted
          resizeCallback?.onLinesMovedToHistory(linesToRemoveFromTop)
        }
        // else: cursor stays at same position, no adjustment needed
      }
      else {
        newCursorY = oldCursorY
      }
    }
    else if (newHeight > oldHeight) {
      if (USE_CONPTY_COMPATIBLE_RESIZE) {
        // do not move lines from scroll buffer to the screen buffer
        newCursorY = oldCursorY
      }
      else {
        if (!alternateBuffer) {
          //we need to move lines from scroll buffer to the text buffer
          val historyLinesCount = min(newHeight - oldHeight, historyLinesStorage.size)
          val removedLines = historyLinesStorage.removeFromBottom(historyLinesCount)
          screenLinesStorage.addAllToTop(removedLines)
          newCursorY = oldCursorY + historyLinesCount
          selection?.shiftY(historyLinesCount)
          // Notify callback so image anchors can be adjusted
          if (historyLinesCount > 0) {
            resizeCallback?.onLinesRestoredFromHistory(historyLinesCount)
          }
        }
        else {
          newCursorY = oldCursorY
        }
      }
    }

    width = newWidth
    height = newHeight

    fireModelChangeEvent()
    return TerminalResizeResult(CellPosition(newCursorX, newCursorY))
  }

  fun addModelListener(listener: TerminalModelListener) {
    listeners.add(listener)
  }

  fun removeModelListener(listener: TerminalModelListener) {
    listeners.remove(listener)
  }

  fun addChangesListener(listener: TextBufferChangesListener) {
    changesMulticaster.addListener(listener)
  }

  fun removeChangesListener(listener: TextBufferChangesListener) {
    changesMulticaster.removeListener(listener)
  }

  fun addHistoryBufferListener(listener: TerminalHistoryBufferListener) {
    historyBufferListeners.add(listener)
  }

  fun removeHistoryBufferListener(listener: TerminalHistoryBufferListener) {
    historyBufferListeners.remove(listener)
  }

  /**
   * Begin a batch of operations. Model change events are suppressed until endBatch() is called.
   * Batches can be nested - only the outermost endBatch() fires the event.
   *
   * Use this to group related operations (e.g., clear line + write text) into an atomic update.
   */
  fun beginBatch() {
    batchDepth++
  }

  /**
   * End a batch of operations. If this is the outermost batch and changes occurred,
   * fires a single modelChanged event.
   */
  fun endBatch() {
    if (batchDepth > 0) {
      batchDepth--
      if (batchDepth == 0 && batchHasChanges) {
        batchHasChanges = false
        for (modelListener in listeners) {
          modelListener.modelChanged()
        }
      }
    }
  }

  /**
   * Execute a block of operations as an atomic batch.
   * Suppresses intermediate model change events, firing only once at the end.
   */
  inline fun <T> batch(block: () -> T): T {
    beginBatch()
    try {
      return block()
    } finally {
      endBatch()
    }
  }

  private fun fireModelChangeEvent() {
    if (batchDepth > 0) {
      // Inside a batch - just mark that changes occurred
      batchHasChanges = true
    } else {
      // Not batched - fire immediately
      for (modelListener in listeners) {
        modelListener.modelChanged()
      }
    }
  }

  private fun createEmptyStyleWithCurrentColor(): TextStyle {
    return styleState.current.createEmptyWithColors()
  }

  private fun createFillerEntry(): TextEntry {
    return TextEntry(createEmptyStyleWithCurrentColor(), CharBuffer(CharUtils.NUL_CHAR, width))
  }

  fun deleteCharacters(x: Int, y: Int, count: Int) {
    if (y > height - 1 || y < 0) {
      LOG.error("Attempt to delete in line $y args were x: $x count: $count")
    }
    else if (count < 0) {
      LOG.error("Attempt to delete negative chars number: count: $count")
    }
    else if (count > 0) {
      screenLinesStorage[y].deleteCharacters(x, count, createEmptyStyleWithCurrentColor())
      fireModelChangeEvent()
      changesMulticaster.linesChanged(fromIndex = y)
    }
  }

  fun insertBlankCharacters(x: Int, y: Int, count: Int) {
    if (y > height - 1 || y < 0) {
      LOG.error("Attempt to insert blank chars in line $y args were x: $x count: $count")
    }
    else if (count < 0) {
      LOG.error("Attempt to insert negative blank chars number: count:$count")
    }
    else if (count > 0) { // nothing to do
      screenLinesStorage[y].insertBlankCharacters(x, count, width, createEmptyStyleWithCurrentColor())
      fireModelChangeEvent()
      changesMulticaster.linesChanged(fromIndex = y)
    }
  }

  fun writeString(x: Int, y: Int, str: CharBuffer) {
    writeString(x, y, str, styleState.current)
  }

  fun addLine(line: TerminalLine) {
    screenLinesStorage.addToBottom(line)
    fireModelChangeEvent()
    changesMulticaster.linesChanged(fromIndex = screenLinesStorage.size - 1)
  }

  private fun writeString(x: Int, y: Int, str: CharBuffer, style: TextStyle) {
    val line = screenLinesStorage[y - 1]
    line.writeString(x, str, style)

    textProcessing?.processHyperlinks(screenLinesStorage, line)
    fireModelChangeEvent()
    changesMulticaster.linesChanged(fromIndex = y - 1)
  }

  /**
   * Write image cells spanning multiple rows.
   * Called from BossTerminal.processInlineImage() when using cell-based image rendering.
   *
   * @param startRow Screen row (0-indexed) for top of image
   * @param startCol Column for left edge of image
   * @param imageId ID of image in ImageDataCache
   * @param cellWidth Width in cells
   * @param cellHeight Height in cells
   */
  fun writeImageCells(
    startRow: Int,
    startCol: Int,
    imageId: Long,
    cellWidth: Int,
    cellHeight: Int,
    cellYOffset: Int = 0  // Skip first N rows of image (for images partially in history)
  ) {
    myLock.lock()
    try {
      // Ensure startRow is valid
      if (startRow < 0 || startRow >= screenLinesStorage.size) {
        LOG.warn("writeImageCells: Invalid startRow={}, screenSize={}", startRow, screenLinesStorage.size)
        return
      }

      // Write image cells - each cell knows its position in the image grid
      // cellYOffset allows skipping the first N rows (when image top is in history)
      var cellsWritten = 0
      var rowsWritten = 0
      for (cellY in cellYOffset until cellHeight) {
        val row = startRow + (cellY - cellYOffset)  // Buffer row to write to
        if (row >= screenLinesStorage.size) {
          LOG.debug(
            "writeImageCells: Image overflow - row {} >= screenSize {}, wrote cells {}-{} of {} rows. " +
            "Remaining rows will use placement-based rendering.",
            row, screenLinesStorage.size, cellYOffset, cellY - 1, cellHeight
          )
          break
        }

        val line = screenLinesStorage[row]
        for (cellX in 0 until cellWidth) {
          val col = startCol + cellX
          if (col < width) {
            // cellY is the actual position in the image grid (not adjusted for offset)
            line.setImageCell(col, ImageCell(imageId, cellX, cellY, cellWidth, cellHeight))
            cellsWritten++
          }
        }
        rowsWritten++
      }
      LOG.debug("writeImageCells: wrote {} cells across {} rows (imageId={}, {}x{} cells, startRow={}, cellYOffset={})",
        cellsWritten, rowsWritten, imageId, cellWidth, cellHeight, startRow, cellYOffset)

      fireModelChangeEvent()
      changesMulticaster.linesChanged(fromIndex = startRow)
    } finally {
      myLock.unlock()
    }
  }

  /**
   * Write a single row of image cells.
   * Used when writing image rows one at a time with scrolling in between.
   */
  fun writeImageCellRow(
    row: Int,
    startCol: Int,
    imageId: Long,
    cellY: Int,
    cellWidth: Int,
    cellHeight: Int
  ) {
    myLock.lock()
    try {
      if (row < 0 || row >= screenLinesStorage.size) return

      val line = screenLinesStorage[row]
      for (cellX in 0 until cellWidth) {
        val col = startCol + cellX
        if (col < width) {
          line.setImageCell(col, ImageCell(imageId, cellX, cellY, cellWidth, cellHeight))
        }
      }
    } finally {
      myLock.unlock()
    }
  }

  fun scrollArea(scrollRegionTop: Int, dy: Int, scrollRegionBottom: Int) {
    if (dy == 0) {
      return
    }
    if (dy > 0) {
      insertLines(scrollRegionTop - 1, dy, scrollRegionBottom)
    }
    else {
      val deletedLines = deleteLines(scrollRegionTop - 1, -dy, scrollRegionBottom)
      if (scrollRegionTop == 1) {
        addLinesToHistory(deletedLines)
      }
      fireModelChangeEvent()
    }
  }

  /**
   * Negative indexes are for history buffer. Non-negative for screen buffer.
   *
   * @param index index of line
   * @return history lines for index<0, screen line for index>=0
   */
  fun getLine(index: Int): TerminalLine {
    if (index >= 0) {
      if (index >= height) {
        LOG.error("Attempt to get line out of bounds: $index >= $height")
        return TerminalLine.createEmpty()
      }
      val sizeBefore = screenLinesStorage.size
      val line = screenLinesStorage[index]
      if (index >= sizeBefore) {
        // Lines Storage creates lines up to the requested index if there were no lines.
        // So we need to report it in this case.
        changesMulticaster.linesChanged(index)
      }
      return line
    } else {
      if (index < -historyLinesCount) {
        LOG.error("Attempt to get line out of bounds: $index < ${-historyLinesCount}")
        return TerminalLine.createEmpty()
      }
      return historyLinesStorage[historyLinesCount + index]
    }
  }

  /**
   * Negative indexes are for history buffer. Non-negative for screen buffer.
   */
  fun setLineWrapped(index: Int, isWrapped: Boolean) {
    getLine(index).isWrapped = isWrapped
    changesMulticaster.linesChanged(fromIndex = index)
  }

  fun getScreenLines(): String {
    myLock.lock()
    try {
      val sb = StringBuilder()
      for (row in 0 until height) {
        val line = StringBuilder(screenLinesStorage[row].text)

        for (i in line.length until width) {
          line.append(' ')
        }
        if (line.length > width) {
          line.setLength(width)
        }

        sb.append(line)
        sb.append('\n')
      }
      return sb.toString()
    }
    finally {
      myLock.unlock()
    }
  }

  fun processScreenLines(yStart: Int, yCount: Int, consumer: StyledTextConsumer) {
    screenLinesStorage.processLines(yStart, yCount, consumer)
  }

  fun lock() {
    myLock.lock()
  }

  fun unlock() {
    myLock.unlock()
  }

  fun modify(runnable: Runnable) {
    myLock.lock()
    try {
      runnable.run()
    }
    finally {
      myLock.unlock()
    }
  }

  fun modify(runnable: () -> Unit) {
    modify(Runnable(runnable))
  }

  fun tryLock(): Boolean {
    return myLock.tryLock()
  }

  fun getBuffersCharAt(x: Int, y: Int): Char {
    return getLine(y).charAt(x)
  }

  fun getStyleAt(x: Int, y: Int): TextStyle? {
    return getLine(y).getStyleAt(x)
  }

  fun getStyledCharAt(x: Int, y: Int): Pair<Char, TextStyle?> {
    val line = getLine(y)
    return Pair(line.charAt(x), line.getStyleAt(x))
  }

  fun getCharAt(x: Int, y: Int): Char {
    return getLine(y).charAt(x)
  }

  fun useAlternateBuffer(enabled: Boolean) {
    alternateBuffer = enabled
    if (enabled) {
      if (!isUsingAlternateBuffer) {
        screenLinesStorageBackup = screenLinesStorage
        historyLinesStorageBackup = historyLinesStorage
        screenLinesStorage = createScreenLinesStorage()
        historyLinesStorage = createHistoryLinesStorage()

        isUsingAlternateBuffer = true
      }
    }
    else {
      if (isUsingAlternateBuffer) {
        val screenBackup = screenLinesStorageBackup
        val historyBackup = historyLinesStorageBackup

        if (screenBackup != null && historyBackup != null) {
          screenLinesStorage = screenBackup
          historyLinesStorage = historyBackup
          screenLinesStorageBackup = null
          historyLinesStorageBackup = null
          isUsingAlternateBuffer = false
        }
      }
    }
    fireModelChangeEvent()
  }

  fun insertLines(y: Int, count: Int, scrollRegionBottom: Int) {
    screenLinesStorage.insertLines(y, count, scrollRegionBottom - 1, createFillerEntry())
    fireModelChangeEvent()
    changesMulticaster.linesChanged(fromIndex = y)
  }

  // returns deleted lines
  fun deleteLines(y: Int, count: Int, scrollRegionBottom: Int): List<TerminalLine> {
    val deletedLines = screenLinesStorage.deleteLines(y, count, scrollRegionBottom - 1, createFillerEntry())
    fireModelChangeEvent()
    changesMulticaster.linesChanged(fromIndex = y)
    return deletedLines
  }

  fun clearLines(startRow: Int, endRow: Int) {
    val filler = createFillerEntry()
    for (ind in startRow..endRow) {
      screenLinesStorage[ind].clear(filler)
      setLineWrapped(ind, false)
    }
    fireModelChangeEvent()
    changesMulticaster.linesChanged(fromIndex = startRow)
  }

  fun eraseCharacters(leftX: Int, rightX: Int, y: Int) {
    val style = createEmptyStyleWithCurrentColor()
    if (y >= 0) {
      screenLinesStorage[y].clearArea(leftX, rightX, style)
      fireModelChangeEvent()
      changesMulticaster.linesChanged(fromIndex = y)
      if (textProcessing != null && y < height) {
        textProcessing.processHyperlinks(screenLinesStorage, getLine(y))
      }
    }
    else {
      LOG.error("Attempt to erase characters in line: $y")
    }
  }

  fun selectiveEraseCharacters(leftX: Int, rightX: Int, y: Int) {
    val style = createEmptyStyleWithCurrentColor()
    if (y >= 0) {
      screenLinesStorage[y].selectiveClearArea(leftX, rightX, style)
      fireModelChangeEvent()
      changesMulticaster.linesChanged(fromIndex = y)
      if (textProcessing != null && y < height) {
        textProcessing.processHyperlinks(screenLinesStorage, getLine(y))
      }
    }
    else {
      LOG.error("Attempt to selectively erase characters in line: $y")
    }
  }

  fun clearScreenAndHistoryBuffers() {
    screenLinesStorage.clear()
    historyLinesStorage.clear()
    fireModelChangeEvent()
    changesMulticaster.historyCleared()
    changesMulticaster.linesChanged(fromIndex = 0)
  }

  fun clearScreenBuffer() {
    screenLinesStorage.clear()
    fireModelChangeEvent()
    changesMulticaster.linesChanged(fromIndex = 0)
  }

  /**
   * @param scrollOrigin row where a scrolling window starts, should be in the range [-history_lines_count, 0]
   */
  fun processHistoryAndScreenLines(scrollOrigin: Int, maximalLinesToProcess: Int, consumer: StyledTextConsumer) {
    val linesToProcess = if (maximalLinesToProcess < 0) {
      //Process all lines in this case
      historyLinesStorage.size + screenLinesStorage.size
    }
    else maximalLinesToProcess

    val linesFromHistory = min(-scrollOrigin, linesToProcess)

    var y = historyLinesStorage.size + scrollOrigin
    if (y < 0) { // it seems that lower bound of scrolling can get out of sync with history buffer lines count
      y = 0 // to avoid exception, we start with the first line in this case
    }
    historyLinesStorage.processLines(y, linesFromHistory, consumer, y)

    if (linesFromHistory < linesToProcess) {
      // we can show lines from screen buffer
      screenLinesStorage.processLines(0, linesToProcess - linesFromHistory, consumer, -linesFromHistory)
    }
  }

  fun clearHistory() {
    modify {
      val lineCount = historyLinesStorage.size
      historyLinesStorage.clear()
      if (lineCount > 0) {
        fireHistoryBufferLineCountChanged()
      }
      fireModelChangeEvent()
      changesMulticaster.historyCleared()
      changesMulticaster.linesChanged(fromIndex = 0)
    }
  }

  fun moveScreenLinesToHistory() {
    modify {
      removeBottomEmptyLines(screenLinesStorage.size)
      val removedScreenLines = screenLinesStorage.removeFromTop(screenLinesStorage.size)
      addLinesToHistory(removedScreenLines)
      if (historyLinesStorage.size > 0) {
        setLineWrapped(-1, false)
      }
      if (removedScreenLines.isNotEmpty()) {
        fireHistoryBufferLineCountChanged()
      }
    }
  }

  private fun removeBottomEmptyLines(maxCount: Int): Int {
    val removedLinesCount = screenLinesStorage.removeBottomEmptyLines(maxCount)
    if (removedLinesCount > 0) {
      changesMulticaster.linesChanged(fromIndex = screenLinesStorage.size)
    }
    return removedLinesCount
  }

  private fun addLinesToHistory(linesToAdd: List<TerminalLine>) {
    // If size of the buffer exceeds the limit in a result of adding new lines,
    // collect the lines we have to discard.
    val linesToDiscard = if (historyLinesStorage.size + linesToAdd.size > maxHistoryLinesCount) {
      val discardedLinesCount = historyLinesStorage.size + linesToAdd.size - maxHistoryLinesCount
      if (discardedLinesCount == 1) {
        val line = if (historyLinesStorage.size > 0) historyLinesStorage[0] else linesToAdd[0]
        listOf(line)
      }
      else {
        val linesToDiscard = ArrayList<TerminalLine>(discardedLinesCount)
        val countOfLinesFromHistory = min(discardedLinesCount, historyLinesStorage.size)
        for (ind in 0 until countOfLinesFromHistory) {
          linesToDiscard.add(historyLinesStorage[ind])
        }
        if (countOfLinesFromHistory < discardedLinesCount) {
          linesToDiscard.addAll(linesToAdd.subList(0, discardedLinesCount - countOfLinesFromHistory))
        }
        linesToDiscard
      }
    }
    else emptyList()

    historyLinesStorage.addAllToBottom(linesToAdd)

    if (linesToDiscard.isNotEmpty()) {
      changesMulticaster.linesDiscardedFromHistory(linesToDiscard)
    }
  }

  private fun fireHistoryBufferLineCountChanged() {
    for (historyBufferListener in historyBufferListeners) {
      historyBufferListener.historyBufferLineCountChanged()
    }
  }

  fun findScreenLineIndex(line: TerminalLine): Int {
    return screenLinesStorage.indexOf(line)
  }

  fun clearTypeAheadPredictions() {
    clearTypeAheadPredictions(screenLinesStorage)
    clearTypeAheadPredictions(historyLinesStorage)
    fireModelChangeEvent()
  }

  private fun clearTypeAheadPredictions(storage: LinesStorage) {
    for (line in storage) {
      line.myTypeAheadLine = null
    }
  }

  /**
   * Create an immutable snapshot of the current buffer state for lock-free rendering.
   *
   * This operation is designed to be FAST (<1ms typical) to minimize lock hold time.
   * Only copies line references, not individual characters, making it very efficient.
   *
   * **Performance** (for typical 80x24 screen + scrollback):
   * - Lock held for: ~0.5-1ms (shallow copy of line list)
   * - Memory allocated: ~200KB (temporary, GC-friendly)
   * - Thread contention eliminated during rendering (15ms → <1ms)
   *
   * **Usage**: Render from snapshot instead of holding lock during entire render cycle.
   * This eliminates the 15ms lock hold that was blocking PTY writers and causing UI freezes.
   *
   * @return immutable snapshot suitable for lock-free rendering
   */
  fun createSnapshot(): BufferSnapshot {
    myLock.lock()
    try {
      // Shallow copy of screen lines (TerminalLine objects are effectively immutable for reading)
      val screenLinesCopy = (0 until screenLinesStorage.size).map { index ->
        screenLinesStorage[index].copy()
      }

      // Shallow copy of history lines
      val historyLinesCopy = (0 until historyLinesStorage.size).map { index ->
        historyLinesStorage[index].copy()
      }

      return BufferSnapshot(
        screenLines = screenLinesCopy,
        historyLines = historyLinesCopy,
        width = width,
        height = height,
        historyLinesCount = historyLinesStorage.size,
        isUsingAlternateBuffer = isUsingAlternateBuffer
      )
    } finally {
      myLock.unlock()
    }
  }

  /**
   * Create an optimized incremental snapshot using copy-on-write semantics.
   *
   * **PREFERRED FOR RENDERING** - Use this instead of createSnapshot() for rendering.
   *
   * This method uses version tracking on each line to minimize allocations:
   * - Lines that haven't changed since the previous snapshot are reused (zero-copy)
   * - Only modified lines are copied
   * - Expected 99%+ reduction in allocations for typical terminal use
   *
   * **Performance** (for typical 80x24 screen + 1000 history lines):
   * - First call: Full copy (~430KB allocation)
   * - Subsequent calls: Only changed lines (~1-10KB allocation typical)
   * - Lock held for: <1ms
   *
   * **Usage**:
   * ```kotlin
   * val snapshot = textBuffer.createIncrementalSnapshot()
   * for (row in 0 until snapshot.height) {
   *     val line = snapshot.getLine(row)
   *     // ... render line ...
   * }
   * ```
   *
   * @return Immutable versioned snapshot optimized for repeated calls
   */
  fun createIncrementalSnapshot(): VersionedBufferSnapshot {
    myLock.lock()
    try {
      return snapshotBuilder.createSnapshot(
        screenLinesStorage,
        historyLinesStorage,
        width,
        height,
        isUsingAlternateBuffer
      )
    } finally {
      myLock.unlock()
    }
  }

  /**
   * Get statistics from the incremental snapshot builder.
   * Useful for monitoring optimization effectiveness.
   */
  fun getSnapshotBuilderStats() = snapshotBuilder.getStats()

  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(TerminalTextBuffer::class.java)
    private const val USE_CONPTY_COMPATIBLE_RESIZE = true
  }
}

/**
 * Immutable snapshot of terminal buffer state for lock-free rendering.
 *
 * Thread-safe by design - all data is defensive copies. Snapshots are short-lived
 * (single frame) and GC-friendly.
 *
 * **Architecture**: Eliminates 15ms lock holds during rendering by copying buffer state
 * in <1ms, then rendering from snapshot without holding lock. This reduces lock
 * contention by 94% and eliminates UI freezing during streaming output (e.g., Claude).
 */
data class BufferSnapshot(
  val screenLines: List<TerminalLine>,
  val historyLines: List<TerminalLine>,
  val width: Int,
  val height: Int,
  val historyLinesCount: Int,
  val isUsingAlternateBuffer: Boolean
) {
  /**
   * Get line by index using same semantics as TerminalTextBuffer.getLine().
   * Negative indices access history buffer, non-negative access screen buffer.
   *
   * @param index line index (negative for history, non-negative for screen)
   * @return terminal line at the specified index, or empty line if out of bounds
   */
  fun getLine(index: Int): TerminalLine {
    return if (index >= 0) {
      screenLines.getOrNull(index) ?: TerminalLine.createEmpty()
    } else {
      val historyIndex = historyLinesCount + index
      historyLines.getOrNull(historyIndex) ?: TerminalLine.createEmpty()
    }
  }
}

/**
 * Callback interface for buffer resize events.
 * Used to notify when lines move between screen and history during resize.
 */
interface BufferResizeCallback {
  /**
   * Called when lines are moved from screen buffer to history during height shrink.
   * Image anchors should be adjusted: anchorRow -= lineCount
   */
  fun onLinesMovedToHistory(lineCount: Int)

  /**
   * Called when lines are restored from history to screen buffer during height expand.
   * Image anchors should be adjusted: anchorRow += lineCount
   */
  fun onLinesRestoredFromHistory(lineCount: Int)

  /**
   * Called before width change to get anchor rows that need tracking through reflow.
   * @return Set of screen-relative anchor rows (0-indexed) to track
   */
  fun getAnchorRowsToTrack(): Set<Int>

  /**
   * Called when terminal width changes and lines are reflowed.
   * Image anchors need to be remapped using the provided mapping.
   *
   * @param anchorMapping Map of old anchor row -> new anchor row (screen-relative, 0-indexed)
   */
  fun onWidthChanged(anchorMapping: Map<Int, Int>)
}