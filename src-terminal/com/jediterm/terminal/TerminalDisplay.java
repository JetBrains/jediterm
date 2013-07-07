package com.jediterm.terminal;

import com.jediterm.terminal.display.JediTerminal;

import java.awt.*;

public interface TerminalDisplay {
  // Size information
  int getRowCount();

  int getColumnCount();

  void setCursor(int x, int y);

  void beep();

  Dimension requestResize(Dimension pendingResize, RequestOrigin origin, int cursorY, JediTerminal.ResizeHandler resizeHandler);

  void scrollArea(final int y, final int h, int dy);

  void setCursorVisible(boolean shouldDrawCursor);

  void setScrollingEnabled(boolean enabled);

  void setBlinkingCursor(boolean enabled);

  void setWindowTitle(String name);
}
