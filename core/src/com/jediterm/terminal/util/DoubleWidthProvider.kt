package com.jediterm.terminal.util

/**
 * Decides whether a Unicode code point occupies two terminal cells.
 *
 * Implementations must be deterministic and side effect free.
 */
internal interface DoubleWidthProvider {

  /**
   * @param codePoint Unicode code point (not a UTF-16 `char`); callers must combine surrogate pairs first.
   * @param areAmbiguousCharactersDoubleWidth how to treat East-Asian "ambiguous width" code points
   *        (e.g. box-drawing, Greek, Cyrillic); typically driven by a user setting for CJK locales.
   */
  fun isDoubleWidth(codePoint: Int, areAmbiguousCharactersDoubleWidth: Boolean): Boolean

  val name: String
}
