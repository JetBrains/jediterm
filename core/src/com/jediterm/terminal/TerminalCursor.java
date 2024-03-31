package com.jediterm.terminal;

import com.jediterm.core.util.CellPosition;
import org.jetbrains.annotations.NotNull;

public interface TerminalCursor {
  void cursorPosition(int x, int y);

  void cursorUp(int countY);

  void cursorDown(int dY);

  void cursorForward(int dX);

  void cursorBackward(int dX);

  void cursorShape(@NotNull CursorShape shape);

  int getCursorX();

  int getCursorY();

  @NotNull CellPosition getCursorPosition();
}
