package com.jediterm.terminal.model

import org.jetbrains.annotations.ApiStatus

/**
 * Allows listening for more detailed events about text buffer content changes.
 */
@ApiStatus.Experimental
interface TextBufferChangesListener {
  /**
   * Line at [fromIndex] and probably some lines after it were changed.
   *
   * @param fromIndex line index in the Text Buffer. Positive for the screen lines, negative for the history lines.
   * So, 0 line is the first line on the screen, -1 line is the last line in the history.
   */
  fun linesChanged(fromIndex: Int) {}

  /**
   * History buffer capacity was exceeded, so the Text Buffer had to discard some lines from the start of the history.
   *
   * @param lines discarded lines.
   */
  fun linesDiscardedFromHistory(lines: List<TerminalLine>) {}

  /**
   * All lines were removed from the history buffer.
   * For example, in a result of `clear` (ED - Erase in Display), or RIS (Reset to the Initial State) escape sequence.
   */
  fun historyCleared() {}

  /**
   * Text Buffer width was changed.
   */
  fun widthResized() {}
}