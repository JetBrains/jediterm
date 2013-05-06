package com.jediterm.emulator;

import com.jediterm.emulator.display.CharBuffer;

/**
 * @author traff
 */
public interface StyledTextConsumer {
  void consume(int x, int y, TextStyle style, CharBuffer characters);
}
