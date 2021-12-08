package com.jediterm.core;

import com.jediterm.core.emulator.mouse.MouseMode;
import com.jediterm.core.awtCompat.Dimension;
import com.jediterm.core.model.JediTerminal;
import com.jediterm.core.model.TerminalSelection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TerminalDisplay {
  // Size information
  int getRowCount();

  int getColumnCount();

  void setCursor(int x, int y);

  void setCursorShape(CursorShape shape);

  void beep();

  void requestResize(@NotNull Dimension newWinSize, RequestOrigin origin, int cursorX, int cursorY,
                     JediTerminal.ResizeHandler resizeHandler);

  void scrollArea(final int scrollRegionTop, final int scrollRegionSize, int dy);

  void setCursorVisible(boolean shouldDrawCursor);

  void setScrollingEnabled(boolean enabled);

  void setBlinkingCursor(boolean enabled);

  String getWindowTitle();

  void setWindowTitle(String name);

  void terminalMouseModeSet(MouseMode mode);

  TerminalSelection getSelection();
  
  boolean ambiguousCharsAreDoubleWidth();

  default void setBracketedPasteMode(boolean enabled) {}

  default @Nullable TerminalColor getWindowForeground() {
    return null;
  }

  default @Nullable TerminalColor getWindowBackground() {
    return null;
  }
}
