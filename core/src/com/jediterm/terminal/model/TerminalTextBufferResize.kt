package com.jediterm.terminal.model

import com.jediterm.core.compatibility.Point
import com.jediterm.core.util.CellPosition
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.model.TerminalLine.TextEntry
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
    // If the width is changed - we need to reflow the lines to fit the new width.
    resizeWithReflow(buffer, newTermSize, oldCursor, selection)
  }
  else if (newTermSize.rows < buffer.height) {
    // If only height is decreased - we need to move lines from the screen buffer to the scroll buffer.
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

  // If the alternate buffer is active, resize it by just truncating the part out of new bounds.
  // The running application should rewrite the screen in a moment.
  return truncateToSize(buffer, newTermSize, oldCursor, selection)
}

/**
 * Resizes the **active** buffer trying to fit the new width and height, trying to preserve the cursor and selection location.
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
 * Moves top lines of the **active** buffer screen to the scroll buffer, so the height of the screen becomes [newHeight].
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

/**
 * Truncates the **active** buffer to the new size by removing all rows and columns that are out of bounds.
 * The cursor and selection are moved to the nearest valid position within the new bounds.
 *
 * This approach is not really the correct way of the text buffer resize, because data out of new bounds is just lost.
 * So, it shouldn't be used for the main buffer resize.
 * But it is the best way from the UX point of view for the alternate buffer resize.
 * It ensures that there is no blinking when applications redraw the screen after resize.
 */
private fun truncateToSize(
  buffer: TerminalTextBuffer,
  newSize: TermSize,
  oldCursor: CellPosition,
  selection: TerminalSelection?
): TerminalResizeResult {
  if (newSize.rows >= buffer.height && newSize.columns >= buffer.width) {
    // If the size is increased, the buffer can be left as is.
    return TerminalResizeResult(oldCursor)
  }

  val screenLines = buffer.screenLinesStorage
  val newLinesSize = min(newSize.rows, screenLines.size)
  val newLines = ArrayList<TerminalLine>(newLinesSize)
  for (ind in 0 until newLinesSize) {
    val line = screenLines[ind]
    val newLine = line.truncateToLen(newSize.columns)
    newLines.add(newLine)
  }

  screenLines.clear()
  screenLines.addAllToBottom(newLines)

  if (selection != null) {
    selection.start.coerceIn(newSize)
    selection.end.coerceIn(newSize)
  }

  val newCursor = CellPosition(oldCursor.x.coerceIn(1, newSize.columns), oldCursor.y.coerceIn(1, newSize.rows))
  return TerminalResizeResult(newCursor)
}

private fun TerminalLine.truncateToLen(newLength: Int): TerminalLine {
  if (length() <= newLength) {
    return this
  }

  val newLine = TerminalLine.createEmpty()
  forEachEntry { entry ->
    if (newLine.length() + entry.length <= newLength) {
      newLine.appendEntry(entry)
    }
    else {
      val sizeLeft = newLength - newLine.length()
      if (sizeLeft > 0) {
        val newEntry = TextEntry(entry.style, entry.text.subBuffer(0, sizeLeft))
        newLine.appendEntry(newEntry)
      }
      return@forEachEntry
    }
  }

  return newLine
}

private fun Point.coerceIn(size: TermSize) {
  x = x.coerceIn(0, size.columns - 1)
  y = y.coerceIn(0, size.rows - 1)
}