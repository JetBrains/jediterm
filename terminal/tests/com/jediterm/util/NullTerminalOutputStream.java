package com.jediterm.util;

import com.jediterm.terminal.TerminalOutputStream;

/**
 * @author traff
 */
public class NullTerminalOutputStream implements TerminalOutputStream {
  @Override
  public void sendBytes(byte[] response) {
    //nop
  }

  @Override
  public void sendString(String string) {
    //nop
  }
}
