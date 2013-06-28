package com.jediterm.util;

import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.TerminalDisplay;
import com.jediterm.terminal.display.BackBuffer;
import com.jediterm.terminal.display.BufferedDisplayTerminal;

import java.awt.*;

/**
 * @author traff
 */
public class BackBufferDisplay implements TerminalDisplay {
  private final BackBuffer myBackBuffer;

  public BackBufferDisplay(BackBuffer backBuffer) {
    myBackBuffer = backBuffer;
  }

  @Override
  public int getRowCount() {
    return myBackBuffer.getHeight();
  }

  @Override
  public int getColumnCount() {
    return myBackBuffer.getWidth();
  }

  @Override
  public void setCursor(int x, int y) {

  }

  @Override
  public void beep() {

  }

  @Override
  public Dimension requestResize(Dimension pendingResize, RequestOrigin origin, int cursorY, BufferedDisplayTerminal.ResizeHandler resizeHandler) {
    return myBackBuffer.resize(pendingResize, origin, cursorY, resizeHandler);
  }

  @Override
  public void scrollArea(int y, int h, int dy) {
  }

  @Override
  public void setCursorVisible(boolean shouldDrawCursor) {
  }

  @Override
  public void setScrollingEnabled(boolean enabled) {
  }

  @Override
  public void setBlinkingCursor(boolean enabled) {
    
  }
}
