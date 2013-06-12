package com.jediterm.emulator;

import com.jediterm.emulator.display.CharBuffer;

/**
 * @author traff
 */
public interface StyledTextConsumer {
  /**
   *
   * @param x indicates starting column of the characters
   * @param y indicates row of the characters
   * @param style style of characters
   * @param characters text characters
   * @param startRow number of the first row.
   *                 It can be different for different buffers, e.g. backbuffer starts from 0, textbuffer and scrollbuffer from -count
   */
  void consume(int x, int y, TextStyle style, CharBuffer characters, int startRow);
}
