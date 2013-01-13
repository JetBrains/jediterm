package com.jediterm;

/**
 * @author traff
 */
public interface StyledTextConsumer {
  void consume(int x, int y, TextStyle style, CharBuffer characters);
}
