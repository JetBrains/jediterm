package com.jediterm.util;

import com.jediterm.emulator.display.BackBuffer;
import com.jediterm.emulator.display.BufferedDisplayTerminal;
import com.jediterm.emulator.display.StyleState;

/**
 * @author traff
 */
public class BackBufferTerminal extends BufferedDisplayTerminal {
  public BackBufferTerminal(BackBuffer backBuffer,
                            StyleState initialStyleState) {
    super(new BackBufferDisplay(backBuffer), backBuffer, initialStyleState);
  }
}
