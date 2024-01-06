package com.jediterm.terminal.model;

import com.jediterm.core.compatibility.Point;
import kotlin.Pair;
import org.jetbrains.annotations.Nullable;

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
    p.getSecond().x = Math.min(p.getSecond().x + 1, width);
    return p;
  }

  public boolean contains(Point toTest) {
    return intersects(toTest.x, toTest.y, 1);
  }

  public void shiftY(int dy) {
    myStart.y += dy;
    myEnd.y += dy;
  }

  public boolean intersects(int x, int row, int length) {
    return null != intersect(x, row, length);
  }

  @Nullable
  public Pair<Integer, Integer> intersect(int x, int row, int length) {
    int newX = x;
    int newLength;

    Pair<Point, Point> p = SelectionUtil.sortPoints(new Point(myStart), new Point(myEnd));

    if (p.getFirst().y == row) {
      newX = Math.max(x, p.getFirst().x);
    }

    if (p.getSecond().y == row) {
      newLength = Math.min(p.getSecond().x, x + length - 1) - newX + 1;
    } else {
      newLength = length - newX + x;
    }

    if (newLength<=0 || row < p.getFirst().y || row > p.getSecond().y) {
      return null;
    } else
      return new Pair<>(newX, newLength);
  }

  @Override
  public String toString() {
    return "[x=" + myStart.x + ",y=" + myStart.y + "]" + " -> [x=" + myEnd.x + ",y=" + myEnd.y + "]";
  }

}
