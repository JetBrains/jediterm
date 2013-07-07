package com.jediterm.util;

import com.jediterm.terminal.display.BackBuffer;
import com.jediterm.terminal.display.JediTerminal;
import com.jediterm.terminal.display.StyleState;

/**
 * @author traff
 */
public class BackBufferTerminal extends JediTerminal {
  public BackBufferTerminal(BackBuffer backBuffer,
                            StyleState initialStyleState) {
    super(new BackBufferDisplay(backBuffer), backBuffer, initialStyleState);
  }
}
