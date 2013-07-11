package com.jediterm.terminal.display;

import java.awt.*;

/**
 * @author traff
 */
public class TerminalSelection {
  private final Point myStart;

  private Point myEnd;

  public TerminalSelection(Point start) {
    myStart = start;
  }

  public Point getStart() {
    return myStart;
  }

  public Point getEnd() {
    return myEnd;
  }

  public void updateEnd(Point end, int width) {
    myEnd = end;
    myEnd.x = Math.min(myEnd.x + 1, width);
  }
}
