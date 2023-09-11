package com.jediterm.core.compatibility;

import java.util.Objects;

public final class Point {
  public int x;
  public int y;

  public Point() {
    this(0, 0);
  }

  public Point(int x, int y) {
    setLocation(x, y);
  }

  public Point(Point other) {
    setLocation(other);
  }

  public void setLocation(int x, int y) {
    this.x = x;
    this.y = y;
  }

  public void setLocation(Point p) {
    x = p.x;
    y = p.y;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Point)) return false;
    Point point = (Point) o;
    return x == point.x && y == point.y;
  }

  @Override
  public int hashCode() {
    return Objects.hash(x, y);
  }

  public String toString() {
    return "[x=" + x + ",y=" + y + "]";
  }
}
