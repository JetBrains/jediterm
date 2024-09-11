package com.jediterm.terminal.model

import com.jediterm.core.compatibility.Point
import com.jediterm.core.util.CellPosition
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.StyledTextConsumer
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.TerminalLine.TextEntry
import com.jediterm.terminal.model.hyperlinks.TextProcessing
import com.jediterm.terminal.util.CharUtils
import org.jetbrains.annotations.ApiStatus
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
class TerminalTextBuffer(
  initialWidth: Int,
  initialHeight: Int,
  private val styleState: StyleState,
  private val maxHistoryLinesCount: Int,
  val textProcessing: TextProcessing?
) {
  var width: Int = initialWidth
    private set
  var height: Int = initialHeight
    private set

  var historyBuffer: LinesBuffer = createHistoryBuffer()
    private set
  var screenBuffer: LinesBuffer = createScreenBuffer()
    private set

  internal val historyBufferOrBackup: LinesBuffer
    get() = if (isUsingAlternateBuffer) historyBufferBackup!! else historyBuffer

  internal val screenBufferOrBackup: LinesBuffer
    get() = if (isUsingAlternateBuffer) screenBufferBackup!! else screenBuffer

  val historyLinesCount: Int
    get() = historyBuffer.lineCount

  val screenLinesCount: Int
    get() = screenBuffer.lineCount

  private val myLock: Lock = ReentrantLock()

  private var historyBufferBackup: LinesBuffer? = null
  private var screenBufferBackup: LinesBuffer? = null // to store textBuffer after switching to alternate buffer

  private var alternateBuffer = false

  var isUsingAlternateBuffer: Boolean = false
    private set

  private val listeners: MutableList<TerminalModelListener> = CopyOnWriteArrayList()
  private val typeAheadListeners: MutableList<TerminalModelListener> = CopyOnWriteArrayList()
  private val historyBufferListeners: MutableList<TerminalHistoryBufferListener> = CopyOnWriteArrayList()

  @JvmOverloads
  constructor(width: Int, height: Int, styleState: StyleState, textProcessing: TextProcessing? = null) : this(
    width,
    height,
    styleState,
    LinesBuffer.DEFAULT_MAX_LINES_COUNT,
    textProcessing
  )

  private fun createScreenBuffer(): LinesBuffer {
    return LinesBuffer(-1, textProcessing)
  }

  private fun createHistoryBuffer(): LinesBuffer {
    return LinesBuffer(maxHistoryLinesCount, textProcessing)
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
        val emptyLinesCount = min(maxBottomLinesToRemove, oldHeight - screenBuffer.lineCount)
        // Count of lines to remove from the screen buffer (TerminalLine objects are created for those lines)
        val actualLinesToRemove = maxBottomLinesToRemove - emptyLinesCount
        // Total count of already empty lines and removed empty lines
        val emptyLinesDeleted = emptyLinesCount + screenBuffer.removeBottomEmptyLines(actualLinesToRemove)

        val screenLinesToMove = lineDiffCount - emptyLinesDeleted
        screenBuffer.moveTopLinesTo(screenLinesToMove, historyBuffer)
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
          val historyLinesCount = min(newHeight - oldHeight, historyBuffer.lineCount)
          historyBuffer.moveBottomLinesTo(historyLinesCount, screenBuffer)
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

  fun addHistoryBufferListener(listener: TerminalHistoryBufferListener) {
    historyBufferListeners.add(listener)
  }

  fun removeHistoryBufferListener(listener: TerminalHistoryBufferListener) {
    historyBufferListeners.remove(listener)
  }

  fun addTypeAheadModelListener(listener: TerminalModelListener) {
    typeAheadListeners.add(listener)
  }

  fun removeTypeAheadModelListener(listener: TerminalModelListener) {
    typeAheadListeners.remove(listener)
  }

  private fun fireModelChangeEvent() {
    for (modelListener in listeners) {
      modelListener.modelChanged()
    }
  }

  @ApiStatus.Internal
  fun fireTypeAheadModelChangeEvent() {
    for (modelListener in typeAheadListeners) {
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
      screenBuffer.deleteCharacters(x, y, count, createEmptyStyleWithCurrentColor())
      fireModelChangeEvent()
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
      screenBuffer.insertBlankCharacters(x, y, count, width, createEmptyStyleWithCurrentColor())
      fireModelChangeEvent()
    }
  }

  fun writeString(x: Int, y: Int, str: CharBuffer) {
    writeString(x, y, str, styleState.current)
  }

  fun addLine(line: TerminalLine) {
    screenBuffer.addLines(listOf(line))
    fireModelChangeEvent()
  }

  private fun writeString(x: Int, y: Int, str: CharBuffer, style: TextStyle) {
    screenBuffer.writeString(x, y - 1, str, style)

    fireModelChangeEvent()
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
        historyBuffer.addLines(deletedLines)
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
      return screenBuffer.getLine(index)
    }
    else {
      if (index < -historyLinesCount) {
        LOG.error("Attempt to get line out of bounds: $index < ${-historyLinesCount}")
        return TerminalLine.createEmpty()
      }
      return historyBuffer.getLine(historyLinesCount + index)
    }
  }

  fun getScreenLines(): String {
    myLock.lock()
    try {
      val sb = StringBuilder()
      for (row in 0 until height) {
        val line = StringBuilder(screenBuffer.getLine(row).text)

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
    screenBuffer.processLines(yStart, yCount, consumer)
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
        screenBufferBackup = screenBuffer
        historyBufferBackup = historyBuffer
        screenBuffer = createScreenBuffer()
        historyBuffer = createHistoryBuffer()
        isUsingAlternateBuffer = true
      }
    }
    else {
      if (isUsingAlternateBuffer) {
        screenBuffer = screenBufferBackup!!
        historyBuffer = historyBufferBackup!!
        screenBufferBackup = createScreenBuffer()
        historyBufferBackup = createHistoryBuffer()
        isUsingAlternateBuffer = false
      }
    }
    fireModelChangeEvent()
  }

  fun insertLines(y: Int, count: Int, scrollRegionBottom: Int) {
    screenBuffer.insertLines(y, count, scrollRegionBottom - 1, createFillerEntry())
    fireModelChangeEvent()
  }

  // returns deleted lines
  fun deleteLines(y: Int, count: Int, scrollRegionBottom: Int): List<TerminalLine> {
    val deletedLines = screenBuffer.deleteLines(y, count, scrollRegionBottom - 1, createFillerEntry())
    fireModelChangeEvent()
    return deletedLines
  }

  fun clearLines(startRow: Int, endRow: Int) {
    screenBuffer.clearLines(startRow, endRow, createFillerEntry())
    fireModelChangeEvent()
  }

  fun eraseCharacters(leftX: Int, rightX: Int, y: Int) {
    val style = createEmptyStyleWithCurrentColor()
    if (y >= 0) {
      screenBuffer.clearArea(leftX, y, rightX, y + 1, style)
      fireModelChangeEvent()
      if (textProcessing != null && y < height) {
        textProcessing.processHyperlinks(screenBuffer, getLine(y))
      }
    }
    else {
      LOG.error("Attempt to erase characters in line: $y")
    }
  }

  fun clearScreenAndHistoryBuffers() {
    screenBuffer.clearAll()
    historyBuffer.clearAll()
    fireModelChangeEvent()
  }

  fun clearScreenBuffer() {
    screenBuffer.clearAll()
    fireModelChangeEvent()
  }

  @Deprecated("use {@link #clearScreenAndHistoryBuffers()} instead")
  fun clearAll() {
    screenBuffer.clearAll()
    fireModelChangeEvent()
  }

  /**
   * @param scrollOrigin row where a scrolling window starts, should be in the range [-history_lines_count, 0]
   */
  fun processHistoryAndScreenLines(scrollOrigin: Int, maximalLinesToProcess: Int, consumer: StyledTextConsumer) {
    val linesToProcess = if (maximalLinesToProcess < 0) {
      //Process all lines in this case
      historyBuffer.lineCount + screenBuffer.lineCount
    }
    else maximalLinesToProcess

    val linesFromHistory = min(-scrollOrigin, linesToProcess)

    var y = historyBuffer.lineCount + scrollOrigin
    if (y < 0) { // it seems that lower bound of scrolling can get out of sync with history buffer lines count
      y = 0 // to avoid exception, we start with the first line in this case
    }
    historyBuffer.processLines(y, linesFromHistory, consumer, y)

    if (linesFromHistory < linesToProcess) {
      // we can show lines from screen buffer
      screenBuffer.processLines(0, linesToProcess - linesFromHistory, consumer, -linesFromHistory)
    }
  }

  fun clearHistory() {
    modify {
      val lineCount = historyBuffer.lineCount
      historyBuffer.clearAll()
      if (lineCount > 0) {
        fireHistoryBufferLineCountChanged()
      }
    }
    fireModelChangeEvent()
  }

  fun moveScreenLinesToHistory() {
    modify {
      screenBuffer.removeBottomEmptyLines(screenBuffer.lineCount)
      val movedToHistoryLineCount = screenBuffer.moveTopLinesTo(screenBuffer.lineCount, historyBuffer)
      if (historyBuffer.lineCount > 0) {
        historyBuffer.getLine(historyBuffer.lineCount - 1).isWrapped = false
      }
      if (movedToHistoryLineCount > 0) {
        fireHistoryBufferLineCountChanged()
      }
    }
  }

  private fun fireHistoryBufferLineCountChanged() {
    for (historyBufferListener in historyBufferListeners) {
      historyBufferListener.historyBufferLineCountChanged()
    }
  }

  fun findScreenLineIndex(line: TerminalLine): Int {
    return screenBuffer.findLineIndex(line)
  }

  fun clearTypeAheadPredictions() {
    screenBuffer.clearTypeAheadPredictions()
    historyBuffer.clearTypeAheadPredictions()
    fireModelChangeEvent()
  }

  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(TerminalTextBuffer::class.java)
    private const val USE_CONPTY_COMPATIBLE_RESIZE = true
  }
}