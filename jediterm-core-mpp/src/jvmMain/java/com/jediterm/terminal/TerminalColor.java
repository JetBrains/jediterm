package com.jediterm.terminal;

import com.jediterm.core.Color;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * @author traff
 */
public class TerminalColor {
  public static final TerminalColor BLACK = index(0);
  public static final TerminalColor WHITE = index(15);

  private final int myColorIndex;
  private final Color myColor;
  private final Supplier<Color> myColorSupplier;

  public TerminalColor(int colorIndex) {
    this(colorIndex, null, null);
  }

  public TerminalColor(int r, int g, int b) {
    this(-1, new Color(r, g, b), null);
  }

  public TerminalColor(@NotNull Supplier<Color> colorSupplier) {
    this(-1, null, colorSupplier);
  }

  private TerminalColor(int colorIndex, @Nullable Color color, @Nullable Supplier<Color> colorSupplier) {
    if (colorIndex != -1) {
      assert color == null;
      assert colorSupplier == null;
    }
    else if (color != null) {
      assert colorSupplier == null;
    }
    else {
      assert colorSupplier != null;
    }
    myColorIndex = colorIndex;
    myColor = color;
    myColorSupplier = colorSupplier;
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

  public @NotNull Color toColor() {
    if (isIndexed()) {
      throw new IllegalArgumentException("Color is indexed color so a palette is needed");
    }

    return myColor != null ? myColor : Objects.requireNonNull(myColorSupplier).get();
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

  public static @Nullable TerminalColor fromColor(@Nullable Color color) {
    if (color == null) {
      return null;
    }
    return rgb(color.getRed(), color.getGreen(), color.getBlue());
  }
}
