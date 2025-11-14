package com.jediterm.terminal.model

/**
 * @param maxCapacity maximum number of stored lines; -1 means no restriction
 */
internal class CyclicBufferLinesStorage(private val maxCapacity: Int) : LinesStorage {

  private val lines: ArrayDeque<TerminalLine> = ArrayDeque()

  private val isCapacityLimited: Boolean = maxCapacity >= 0

  override val size: Int
    get() = lines.size

  /** O(1) */
  override fun get(index: Int): TerminalLine {
    if (index < 0) {
      throw IndexOutOfBoundsException("Negative index: $index")
    }

    if (index >= size) {
      repeat(index - size + 1) {
        addToBottom(TerminalLine.createEmpty())
      }
    }

    return lines[index]
  }

  /** O(size) */
  override fun indexOf(line: TerminalLine): Int = lines.indexOf(line)

  /**
   * Amortized 0(1).
   * The worst case is when we need to extend the internal storage of the array deque.
   */
  override fun addToTop(line: TerminalLine) {
    if (isCapacityLimited && lines.size == maxCapacity) {
      return
    }
    lines.addFirst(line)
  }

  /**
   * Amortized 0(1).
   * The worst case is when we need to extend the internal storage of the array deque.
   */
  override fun addToBottom(line: TerminalLine) {
    lines.addLast(line)
    if (isCapacityLimited && lines.size > maxCapacity) {
      lines.removeFirst()
    }
  }

  /** O(1) */
  override fun removeFromTop(): TerminalLine {
    return lines.removeFirst()
  }

  /** O(1) */
  override fun removeFromBottom(): TerminalLine {
    return lines.removeLast()
  }

  /** O(size) */
  override fun clear() = lines.clear()

  override fun iterator(): Iterator<TerminalLine> = lines.iterator()
}
