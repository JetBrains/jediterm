package com.jediterm.terminal;

import com.google.common.base.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
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

  public @NotNull Color toAwtColor() {
    if (isIndexed()) {
      throw new IllegalArgumentException("Color is indexed color so a palette is needed");
    }

    return myColor != null ? myColor : Objects.requireNonNull(myColorSupplier).get();
  }

  public int getColorIndex() {
    return myColorIndex;
  }

  public @NotNull String toHexString16() {
    Color color = toAwtColor();

    // (n * 0x101) converts the 8-bit number to 16 bits.
    String red = Strings.padStart(Integer.toHexString(color.getRed() * 0x101), 4, '0');
    String green = Strings.padStart(Integer.toHexString(color.getGreen() * 0x101), 4, '0');
    String blue = Strings.padStart(Integer.toHexString(color.getBlue() * 0x101), 4, '0');
    
    return red + "/" + green + "/" + blue;
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
