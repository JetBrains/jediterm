package com.jediterm.terminal.model;

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

  public TerminalSelection(Point start, Point end) {
    myStart = start;
    myEnd = end;
  }

  public Point getStart() {
    return myStart;
  }

  public Point getEnd() {
    return myEnd;
  }

  public void updateEnd(Point end) {
    myEnd = end;
  }

  public Pair<Point, Point> pointsForRun(int width) {
    Pair<Point, Point> p = SelectionUtil.sortPoints(new Point(myStart), new Point(myEnd));
    p.second.x = Math.min(p.second.x + 1, width);
    return p;
  }

  public boolean contains(Point toTest) {
    return SelectionUtil.sortPoints(myStart, toTest).first == myStart
        && SelectionUtil.sortPoints(toTest, myEnd).second == myEnd;
  }

  public void shiftY(int dy) {
    myStart.y += dy;
    myEnd.y += dy;
  }
}
