package com.jediterm.terminal.emulator.mouse;

import com.jediterm.core.input.MouseEvent;
import com.jediterm.core.input.MouseWheelEvent;
import org.jetbrains.annotations.NotNull;

public interface TerminalMouseListener {
  void mousePressed(int x, int y, @NotNull MouseEvent event);
  void mouseReleased(int x, int y, @NotNull MouseEvent event);
  void mouseMoved(int x, int y, @NotNull MouseEvent event);
  void mouseDragged(int x, int y, @NotNull MouseEvent event);
  void mouseWheelMoved(int x, int y, @NotNull MouseWheelEvent event);
}
