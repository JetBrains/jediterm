package com.jediterm.emulator;

import java.io.IOException;

/**
 * @author traff
 */
public interface TerminalDataStream {
  char getChar() throws IOException;

  void pushChar(char c) throws IOException;

  CharacterUtils.CharArraySlice advanceThroughASCII(int maxChars) throws IOException;

  void sendBytes(byte[] response) throws IOException;

  void pushBackBuffer(char[] bytes, int i) throws IOException;

  class EOF extends IOException {
    public EOF() {
      super("EOF: There is no more data or connection is lost");
    }
  }
}
