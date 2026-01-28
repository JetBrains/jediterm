package com.jediterm.terminal.model

import com.jediterm.core.compatibility.Point
import com.jediterm.core.util.CellPosition
import com.jediterm.core.util.TermSize
import kotlin.math.max
import kotlin.math.min

internal fun doResizeTextBuffer(
  buffer: TerminalTextBuffer,
  newTermSize: TermSize,
  oldCursor: CellPosition,
  selection: TerminalSelection?
): TerminalResizeResult {
  return if (buffer.isUsingAlternateBuffer) {
    resizeWhenAlternateBufferActive(buffer, newTermSize, oldCursor, selection)
  }
  else {
    resizeWhenMainBufferActive(buffer, newTermSize, oldCursor, selection)
  }
}

private fun resizeWhenMainBufferActive(
  buffer: TerminalTextBuffer,
  newTermSize: TermSize,
  oldCursor: CellPosition,
  selection: TerminalSelection?
): TerminalResizeResult {
  check(!buffer.isUsingAlternateBuffer) { "resizeWhenMainBufferActive should be called only when main buffer is active" }

  return if (newTermSize.columns != buffer.width) {
    resizeWithReflow(buffer, newTermSize, oldCursor, selection)
  }
  else if (newTermSize.rows < buffer.height) {
    decreaseHeight(buffer, newTermSize.rows, oldCursor, selection)
  }
  else {
    // newTermSize.rows >= buffer.height (height increased)
    // Do not move lines from the scroll buffer to the screen buffer.
    // It is crucial for compatibility with ConPTY.
    TerminalResizeResult(oldCursor)
  }
}

private fun resizeWhenAlternateBufferActive(
  buffer: TerminalTextBuffer,
  newTermSize: TermSize,
  oldCursor: CellPosition,
  selection: TerminalSelection?
): TerminalResizeResult {
  check(buffer.isUsingAlternateBuffer) { "resizeWhenAlternateBufferActive should be called only when alternate buffer is active" }

  return if (buffer.width != newTermSize.columns) {
    resizeWithReflow(buffer, newTermSize, oldCursor, selection)
  }
  else TerminalResizeResult(oldCursor)
}

/**
 * Resizes the **Main** buffer trying to fit the new width and height, trying to preserve the cursor and selection location.
 * The lines are reflown with adding/removing soft wraps.
 */
private fun resizeWithReflow(
  textBuffer: TerminalTextBuffer,
  newSize: TermSize,
  oldCursor: CellPosition,
  selection: TerminalSelection?
): TerminalResizeResult {
  val changeWidthOperation = ChangeWidthOperation(textBuffer, newSize.columns, newSize.rows)
  val cursorPoint = Point(oldCursor.x - 1, oldCursor.y - 1)
  changeWidthOperation.addPointToTrack(cursorPoint, true)
  if (selection != null) {
    changeWidthOperation.addPointToTrack(selection.start, false)
    changeWidthOperation.addPointToTrack(selection.end, false)
  }

  changeWidthOperation.run()

  val newCursor = changeWidthOperation.getTrackedPoint(cursorPoint)
  if (selection != null) {
    selection.start.setLocation(changeWidthOperation.getTrackedPoint(selection.start))
    selection.end.setLocation(changeWidthOperation.getTrackedPoint(selection.end))
  }
  return TerminalResizeResult(CellPosition(newCursor.x + 1, newCursor.y + 1))
}

/**
 * Moves top lines of the screen to the scroll buffer, so the height of the screen becomes [newHeight].
 */
private fun decreaseHeight(
  buffer: TerminalTextBuffer,
  newHeight: Int,
  oldCursor: CellPosition,
  selection: TerminalSelection?,
): TerminalResizeResult {
  val curHeight = buffer.height
  check(newHeight < curHeight) { "decreaseHeight should be called only when newHeight < curHeight" }

  // We need to move lines from the text buffer to the scroll buffer,
  // but empty bottom lines up to the cursor can be collapsed

  val lineDiffCount = curHeight - newHeight
  // Number of lines to remove until the new height or cursor if it is located below the new height.
  val maxBottomLinesToRemove = min(lineDiffCount, max(0, curHeight - oldCursor.y))
  // Number of already empty lines on the screen (but not greater than required count to remove)
  val emptyLinesCount = min(maxBottomLinesToRemove, curHeight - buffer.screenLinesStorage.size)
  // Count of lines to remove from the screen buffer (TerminalLine objects are created for those lines)
  val actualLinesToRemove = maxBottomLinesToRemove - emptyLinesCount
  // Total count of already empty lines and removed empty lines
  val emptyLinesDeleted = emptyLinesCount + buffer.removeBottomEmptyLines(actualLinesToRemove)

  val screenLinesToMove = lineDiffCount - emptyLinesDeleted
  val removedLines = buffer.screenLinesStorage.removeFromTop(screenLinesToMove)
  buffer.addLinesToHistory(removedLines)

  selection?.shiftY(-screenLinesToMove)
  val newCursorY = oldCursor.y - screenLinesToMove
  return TerminalResizeResult(CellPosition(oldCursor.x, newCursorY))
}