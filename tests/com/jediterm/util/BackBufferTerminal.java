package com.jediterm.util;

import com.jediterm.terminal.display.BackBuffer;
import com.jediterm.terminal.display.BufferedDisplayTerminal;
import com.jediterm.terminal.display.StyleState;

/**
 * @author traff
 */
public class BackBufferTerminal extends BufferedDisplayTerminal {
  public BackBufferTerminal(BackBuffer backBuffer,
                            StyleState initialStyleState) {
    super(new BackBufferDisplay(backBuffer), backBuffer, initialStyleState);
  }
}
