package com.jediterm.util;

import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.TerminalDisplay;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.TerminalSelection;
import com.jediterm.terminal.emulator.mouse.MouseMode;

import java.awt.*;

/**
 * @author traff
 */
public class BackBufferDisplay implements TerminalDisplay {
  private final TerminalTextBuffer myTerminalTextBuffer;
  private TerminalSelection mySelection = null;

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
  public Dimension requestResize(Dimension pendingResize, RequestOrigin origin, int cursorY, JediTerminal.ResizeHandler resizeHandler) {
    return myTerminalTextBuffer.resize(pendingResize, origin, cursorY, resizeHandler, mySelection);
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
  public void setWindowTitle(String name) {
  }

  @Override
  public void setCurrentPath(String path) {
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
}
