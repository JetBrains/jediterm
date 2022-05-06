package com.jediterm.core.awtCompat;

import java.util.Objects;

public final class Dimension {
  public int width;
  public int height;

  public Dimension() {
    this(0, 0);
  }

  public Dimension(int width, int height) {
    this.width = width;
    this.height = height;
  }

  public Dimension(Dimension other) {
    width = other.width;
    height = other.height;
  }

  public void setSize(int width, int height) {
    this.width = width;
    this.height = height;
  }

  public Dimension copy() {
    return new Dimension(width, height);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Dimension)) return false;
    Dimension dimension = (Dimension) o;
    return width == dimension.width && height == dimension.height;
  }

  @Override
  public int hashCode() {
    return Objects.hash(width, height);
  }

  public String toString() {
    return getClass().getName() + "[width=" + width + ",height=" + height + "]";
  }

}