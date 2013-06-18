package com.jediterm.emulator;

/**
 *  Sends a response from the terminal emulator.
 * 
 * @author traff
 */
public interface TerminalOutputStream {
  void sendBytes(byte[] response);

  void sendString(final String string);
}
