package com.jediterm.util;

import com.jediterm.core.CursorShape;
import com.jediterm.core.RequestOrigin;
import com.jediterm.core.TerminalColor;
import com.jediterm.core.awtCompat.Dimension;
import com.jediterm.core.TerminalDisplay;
import com.jediterm.core.model.TerminalTextBuffer;
import com.jediterm.core.model.JediTerminal;
import com.jediterm.core.model.TerminalSelection;
import com.jediterm.core.emulator.mouse.MouseMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public class BackBufferDisplay implements TerminalDisplay {
  private final TerminalTextBuffer myTerminalTextBuffer;
  private TerminalSelection mySelection = null;
  private String myWindowTitle;
  private @Nullable TerminalColor myForegroundColor;
  private @Nullable TerminalColor myBackgroundColor;

  public BackBufferDisplay(TerminalTextBuffer terminalTextBuffer) {
    myTerminalTextBuffer = terminalTextBuffer;
  }

  @Override
  public int getRowCount() {
    return myTerminalTextBuffer.getHeight();
  }

  @Override
  public int getColumnCount() {
    return myTerminalTextBuffer.getWidth();
  }

  @Override
  public void setCursor(int x, int y) {
  }

  @Override
  public void setCursorShape(CursorShape shape) {
  }

  @Override
  public void beep() {
  }

  @Override
  public void requestResize(@NotNull Dimension newWinSize, RequestOrigin origin, int cursorX, int cursorY, JediTerminal.ResizeHandler resizeHandler) {
    myTerminalTextBuffer.resize(newWinSize, origin, cursorX, cursorY, resizeHandler, mySelection);
  }

  @Override
  public void scrollArea(int scrollRegionTop, int scrollRegionSize, int dy) {
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

  @Override
  public void setWindowTitle(String title) {
    myWindowTitle = title;
  }

  public @Nullable String getWindowTitle() {
    return myWindowTitle;
  }

  public TerminalSelection getSelection() {
    return mySelection;
  }

  @Override
  public boolean ambiguousCharsAreDoubleWidth() {
    return false;
  }

  public void setSelection(TerminalSelection mySelection) {
    this.mySelection = mySelection;
  }

  @Override
  public void terminalMouseModeSet(MouseMode mode) {
  }

  @Override
  public @Nullable TerminalColor getWindowForeground() {
    return myForegroundColor;
  }

  public void setWindowForeground(@Nullable TerminalColor foregroundColor) {
    myForegroundColor = foregroundColor;
  }

  @Override
  public @Nullable TerminalColor getWindowBackground() {
    return myBackgroundColor;
  }

  public void setWindowBackground(@Nullable TerminalColor backgroundColor) {
    myBackgroundColor = backgroundColor;
  }
}
