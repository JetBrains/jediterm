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
   * Sends a response string synchronously. Used for device status reports
   * where the response must be written before returning.
   * <p>
   * The default implementation delegates to {@link #sendString(String, boolean)},
   * which may be asynchronous. Implementations that use async write queues
   * should override this method to ensure synchronous completion.
   */
  default void sendStringImmediately(@NotNull String string) {
    sendString(string, false);
  }

  /**
   * Sends response bytes synchronously. Used for device attributes
   * where the response must be written before returning.
   * <p>
   * The default implementation delegates to {@link #sendBytes(byte[], boolean)},
   * which may be asynchronous. Implementations that use async write queues
   * should override this method to ensure synchronous completion.
   */
  default void sendBytesImmediately(byte @NotNull [] response) {
    sendBytes(response, false);
  }
}
