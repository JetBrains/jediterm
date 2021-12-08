package com.jediterm.core.emulator.mouse;

import com.jediterm.core.awtCompat.MouseEvent;

/**
 * @author traff
 */
public interface TerminalMouseListener {
  void mousePressed(int x, int y, MouseEvent event);
  void mouseReleased(int x, int y, MouseEvent event);
  void mouseMoved(int x, int y, MouseEvent event);
  void mouseDragged(int x, int y, MouseEvent event);
  void mouseWheelMoved(int x, int y, MouseEvent event);
}
