package com.jediterm.terminal.display;

import com.jediterm.terminal.util.Pair;

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

  public boolean contains(int x, int y) {
    Pair<Point, Point> p = SelectionUtil.sortPoints(myStart, myEnd);
    if (y == p.first.y) {
      return x >= p.first.x;
    }
    if (y == p.second.y) {
      return x <= p.second.x;
    }
    return p.first.y < y && y < p.second.y;
  }
}
