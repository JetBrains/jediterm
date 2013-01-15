package com.jediterm.util;

import com.jediterm.BackBuffer;
import com.jediterm.RequestOrigin;
import com.jediterm.TerminalDisplay;

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
  public Dimension requestResize(Dimension pendingResize, RequestOrigin origin, int cursorY) {
    return myBackBuffer.resize(pendingResize, origin, cursorY);
  }

  @Override
  public void scrollArea(int y, int h, int dy) {
  }

  @Override
  public void setShouldDrawCursor(boolean shouldDrawCursor) {
  }
}
