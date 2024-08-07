package com.jediterm.terminal.model

import kotlin.math.min

/**
 * Base interface for storing terminal lines.
 * For the first line of the storage, we use the term top, for the last line - bottom.
 * Supports adding/removing lines from the top and bottom.
 * Also accessing lines by index.
 */
internal interface LinesStorage : Iterable<TerminalLine> {
  /** Count of the available lines in the storage */
  val size: Int

  /** [index] must be in the range [0, size) */
  operator fun get(index: Int): TerminalLine

  /** @return -1 if there is no such line */
  fun indexOf(line: TerminalLine): Int

  /**
   * Adds a new line to the start of the storage.
   * If the implementation limits the max capacity of the storage,
   * and storage is full, then the line should not be added.
   */
  fun addToTop(line: TerminalLine)

  /**
   * Adds a new line to the end of the storage.
   * If the implementation limits the max capacity of the storage,
   * and storage is full, the first line from the top should be removed.
   */
  fun addToBottom(line: TerminalLine)

  /**
   * Removes a single line from the start of the storage.
   * @throws NoSuchElementException if storage is empty.
   */
  fun removeFromTop(): TerminalLine

  /**
   * Removes a single line from the end of the storage.
   * @throws NoSuchElementException if storage is empty.
   */
  fun removeFromBottom(): TerminalLine

  fun clear()
}

internal fun LinesStorage.addAllToTop(lines: List<TerminalLine>) {
  for (ind in lines.lastIndex downTo 0) {
    addToTop(lines[ind])
  }
}

internal fun LinesStorage.addAllToBottom(lines: List<TerminalLine>) {
  for (line in lines) {
    addToBottom(line)
  }
}

internal fun LinesStorage.removeFromTop(count: Int): List<TerminalLine> {
  if (count < 0) {
    throw IllegalArgumentException("Count must be >= 0")
  }
  if (count == 0) {
    return emptyList()
  }
  val countToRemove = min(count, size)
  val result = ArrayList<TerminalLine>(countToRemove)
  repeat(countToRemove) {
    result.add(removeFromTop())
  }
  return result
}

internal fun LinesStorage.removeFromBottom(count: Int): List<TerminalLine> {
  if (count < 0) {
    throw IllegalArgumentException("Count must be >= 0")
  }
  if (count == 0) {
    return emptyList()
  }
  val countToRemove = min(count, size)
  val result = ArrayList<TerminalLine>(countToRemove)
  repeat(countToRemove) {
    result.add(removeFromBottom())
  }
  result.reverse()
  return result
}
