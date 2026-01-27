package com.jediterm.terminal.model

import com.jediterm.core.compatibility.Point
import com.jediterm.core.util.CellPosition
import com.jediterm.core.util.TermSize
import kotlin.math.max
import kotlin.math.min

private const val USE_CONPTY_COMPATIBLE_RESIZE = true

internal fun doResizeTextBuffer(
  buffer: TerminalTextBuffer,
  newTermSize: TermSize,
  oldCursor: CellPosition,
  selection: TerminalSelection?
): TerminalResizeResult {
  val newWidth = newTermSize.columns
  val newHeight = newTermSize.rows
  var newCursorX = oldCursor.x
  var newCursorY = oldCursor.y
  val oldCursorY = oldCursor.y
  val oldHeight = buffer.height

  if (buffer.width != newWidth) {
    val changeWidthOperation = ChangeWidthOperation(buffer, newWidth, newHeight)
    val cursorPoint = Point(oldCursor.x - 1, oldCursor.y - 1)
    changeWidthOperation.addPointToTrack(cursorPoint, true)
    if (selection != null) {
      changeWidthOperation.addPointToTrack(selection.start, false)
      changeWidthOperation.addPointToTrack(selection.end, false)
    }
    changeWidthOperation.run()
    val newCursor = changeWidthOperation.getTrackedPoint(cursorPoint)
    newCursorX = newCursor.x + 1
    newCursorY = newCursor.y + 1
    if (selection != null) {
      selection.start.setLocation(changeWidthOperation.getTrackedPoint(selection.start))
      selection.end.setLocation(changeWidthOperation.getTrackedPoint(selection.end))
    }
  }
  else if (newHeight < oldHeight) {
    if (!buffer.isUsingAlternateBuffer) {
      val lineDiffCount = oldHeight - newHeight

      // We need to move lines from text buffer to the scroll buffer,
      // but empty bottom lines up to the cursor can be collapsed

      // Number of lines to remove until the new height or cursor if it is located below the new height.
      val maxBottomLinesToRemove = min(lineDiffCount, max(0, oldHeight - oldCursorY))
      // Number of already empty lines on the screen (but not greater than required count to remove)
      val emptyLinesCount = min(maxBottomLinesToRemove, oldHeight - buffer.screenLinesStorage.size)
      // Count of lines to remove from the screen buffer (TerminalLine objects are created for those lines)
      val actualLinesToRemove = maxBottomLinesToRemove - emptyLinesCount
      // Total count of already empty lines and removed empty lines
      val emptyLinesDeleted = emptyLinesCount + buffer.removeBottomEmptyLines(actualLinesToRemove)

      val screenLinesToMove = lineDiffCount - emptyLinesDeleted
      val removedLines = buffer.screenLinesStorage.removeFromTop(screenLinesToMove)
      buffer.addLinesToHistory(removedLines)
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
      if (!buffer.isUsingAlternateBuffer) {
        //we need to move lines from scroll buffer to the text buffer
        val historyLinesCount = min(newHeight - oldHeight, buffer.historyLinesStorage.size)
        val removedLines = buffer.historyLinesStorage.removeFromBottom(historyLinesCount)
        buffer.screenLinesStorage.addAllToTop(removedLines)
        newCursorY = oldCursorY + historyLinesCount
        selection?.shiftY(historyLinesCount)
      }
      else {
        newCursorY = oldCursorY
      }
    }
  }

  return TerminalResizeResult(CellPosition(newCursorX, newCursorY))
}