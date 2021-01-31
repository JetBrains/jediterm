package com.jediterm.terminal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Objects;

/**
 * @author traff
 */
public class TerminalColor {
  public static final TerminalColor BLACK = index(0);
  public static final TerminalColor WHITE = index(15);

  private final int myColorIndex;
  private final Color myColor;

  public TerminalColor(int colorIndex) {
    myColorIndex = colorIndex;
    myColor = null;
  }

  public TerminalColor(int r, int g, int b) {
    myColorIndex = -1;
    myColor = new Color(r, g, b);
  }

  public static @NotNull TerminalColor index(int colorIndex) {
    return new TerminalColor(colorIndex);
  }

  public static TerminalColor rgb(int r, int g, int b) {
    return new TerminalColor(r, g, b);
  }

  public boolean isIndexed() {
    return myColorIndex != -1;
  }

  public Color toAwtColor() {
    if (isIndexed()) {
      throw new IllegalArgumentException("Color is indexed color so a palette is needed");
    }

    return myColor;
  }

  public int getColorIndex() {
    return myColorIndex;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TerminalColor that = (TerminalColor) o;
    return myColorIndex == that.myColorIndex && Objects.equals(myColor, that.myColor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myColorIndex, myColor);
  }

  public static @Nullable TerminalColor awt(@Nullable Color color) {
    if (color == null) {
      return null;
    }
    return rgb(color.getRed(), color.getGreen(), color.getBlue());
  }
}
