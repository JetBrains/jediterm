package com.jediterm.terminal;

import org.jetbrains.annotations.NotNull;

/**
 *  Sends a response from the terminal emulator.
 *
 * @author traff
 */
public interface TerminalOutputStream {
  void sendBytes(byte @NotNull [] response, boolean userInput);

  void sendString(@NotNull String string, boolean userInput);

  /**
   * Sends a response string immediately, bypassing any async write queue.
   * This is needed for device status reports (e.g., OSC 11 query) where the
   * response must be sent before the emulator goes back to blocking on read.
   */
  default void sendStringImmediately(@NotNull String string) {
    sendString(string, false);
  }

  /**
   * Sends response bytes immediately, bypassing any async write queue.
   * This is needed for device attributes responses where the
   * response must be sent before the emulator goes back to blocking on read.
   */
  default void sendBytesImmediately(byte @NotNull [] response) {
    sendBytes(response, false);
  }
}
