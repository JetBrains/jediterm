package com.jediterm.terminal;

import java.awt.*;

/**
 *  Sends a response from the terminal emulator.
 * 
 * @author traff
 */
public interface TerminalOutputStream {
  /**
   * @deprecated use {@link #sendBytes(byte[], boolean)} instead
   */
  @Deprecated
  void sendBytes(byte[] response);

  /**
   * @deprecated use {@link #sendString(String, boolean)} instead
   */
  @Deprecated
  void sendString(final String string);

  default void sendBytes(byte[] response, boolean userInput) {
    sendBytes(response);
  }
  default void sendString(String string, boolean userInput) {
    sendString(string);
  }
}
