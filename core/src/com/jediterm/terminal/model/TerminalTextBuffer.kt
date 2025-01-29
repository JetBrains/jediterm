package com.jediterm.terminal.model

import com.jediterm.core.compatibility.Point
import com.jediterm.core.util.CellPosition
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.StyledTextConsumer
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.TerminalLine.TextEntry
import com.jediterm.terminal.model.hyperlinks.TextProcessing
import com.jediterm.terminal.util.CharUtils
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
  var width: Int = initialWidth
    private set
  var height: Int = initialHeight
    private set

  var historyLinesStorage: LinesStorage = createHistoryLinesStorage()
    private set
  var screenLinesStorage: LinesStorage = createScreenLinesStorage()
    private set

  @Deprecated("Use historyLinesStorage instead", replaceWith = ReplaceWith("historyLinesStorage"))
  var historyBuffer: LinesBuffer = createLinesBuffer(historyLinesStorage)
    private set

  @Deprecated("Use screenLinesStorage instead", replaceWith = ReplaceWith("screenLinesStorage"))
  var screenBuffer: LinesBuffer = createLinesBuffer(screenLinesStorage)
    private set

  internal val historyLinesStorageOrBackup: LinesStorage
    get() = if (isUsingAlternateBuffer) historyLinesStorageBackup!! else historyLinesStorage

  internal val screenLinesStorageOrBackup: LinesStorage
    get() = if (isUsingAlternateBuffer) screenLinesStorageBackup!! else screenLinesStorage

  val historyLinesCount: Int
    get() = historyLinesStorage.size

  val screenLinesCount: Int
    get() = screenLinesStorage.size

  private val myLock: Lock = ReentrantLock()

  private var historyLinesStorageBackup: LinesStorage? = null
  private var screenLinesStorageBackup: LinesStorage? = null

  private var historyBufferBackup: LinesBuffer? = null
  private var screenBufferBackup: LinesBuffer? = null // to store textBuffer after switching to alternate buffer

  private var alternateBuffer = false

  var isUsingAlternateBuffer: Boolean = false
    private set

  private val listeners: MutableList<TerminalModelListener> = CopyOnWriteArrayList()
  private val historyBufferListeners: MutableList<TerminalHistoryBufferListener> = CopyOnWriteArrayList()
  private val changesMulticaster: TextBufferChangesMulticaster = TextBufferChangesMulticaster()

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

  private fun createLinesBuffer(delegate: LinesStorage): LinesBuffer {
    return LinesBuffer(delegate, textProcessing)
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
        changeWidthOperation.addPointToTrack(selection.end, false)
      }
      changeWidthOperation.run()
      width = newWidth
      height = newHeight
      val newCursor = changeWidthOperation.getTrackedPoint(cursorPoint)
      newCursorX = newCursor.x + 1
      newCursorY = newCursor.y + 1
      if (selection != null) {
        selection.start.setLocation(changeWidthOperation.getTrackedPoint(selection.start))
        selection.end.setLocation(changeWidthOperation.getTrackedPoint(selection.end))
      }
      changesMulticaster.widthResized()
    }

    val oldHeight = height
    if (newHeight < oldHeight) {
      if (!alternateBuffer) {
        val lineDiffCount = oldHeight - newHeight

        // We need to move lines from text buffer to the scroll buffer,
        // but empty bottom lines up to the cursor can be collapsed

        // Number of lines to remove until the new height or cursor if it is located below the new height.
        val maxBottomLinesToRemove = min(lineDiffCount, max(0, oldHeight - oldCursorY))
        // Number of already empty lines on the screen (but not greater than required count to remove)
        val emptyLinesCount = min(maxBottomLinesToRemove, oldHeight - screenLinesStorage.size)
        // Count of lines to remove from the screen buffer (TerminalLine objects are created for those lines)
        val actualLinesToRemove = maxBottomLinesToRemove - emptyLinesCount
        // Total count of already empty lines and removed empty lines
        val emptyLinesDeleted = emptyLinesCount + removeBottomEmptyLines(actualLinesToRemove)

        val screenLinesToMove = lineDiffCount - emptyLinesDeleted
        val removedLines = screenLinesStorage.removeFromTop(screenLinesToMove)
        addLinesToHistory(removedLines)
        newCursorY = oldCursorY - screenLinesToMove
        selection?.shiftY(-screenLinesToMove)
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

  private fun fireModelChangeEvent() {
    for (modelListener in listeners) {
      modelListener.modelChanged()
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
    }
    else {
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

        screenBufferBackup = screenBuffer
        historyBufferBackup = historyBuffer
        screenBuffer = createLinesBuffer(screenLinesStorage)
        historyBuffer = createLinesBuffer(historyLinesStorage)
        isUsingAlternateBuffer = true
      }
    }
    else {
      if (isUsingAlternateBuffer) {
        screenLinesStorage = screenLinesStorageBackup!!
        historyLinesStorage = historyLinesStorageBackup!!
        screenLinesStorageBackup = createScreenLinesStorage()
        historyLinesStorageBackup = createHistoryLinesStorage()

        screenBuffer = screenBufferBackup!!
        historyBuffer = historyBufferBackup!!
        screenBufferBackup = createLinesBuffer(screenLinesStorageBackup!!)
        historyBufferBackup = createLinesBuffer(historyLinesStorageBackup!!)
        isUsingAlternateBuffer = false
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

  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(TerminalTextBuffer::class.java)
    private const val USE_CONPTY_COMPATIBLE_RESIZE = true
  }
}