package com.jediterm.terminal.emulator;

import com.jediterm.terminal.TerminalColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author traff
 */
public abstract class ColorPalette {

  public @NotNull Color getForeground(@NotNull TerminalColor color) {
    if (color.isIndexed()) {
      int colorIndex = color.getColorIndex();
      assertColorIndexIsLessThan16(colorIndex);
      return getForegroundByColorIndex(colorIndex);
    }
    return color.toAwtColor();
  }

  protected abstract @NotNull Color getForegroundByColorIndex(int colorIndex);

  public @NotNull Color getBackground(@NotNull TerminalColor color) {
    if (color.isIndexed()) {
      int colorIndex = color.getColorIndex();
      assertColorIndexIsLessThan16(colorIndex);
      return getBackgroundByColorIndex(colorIndex);
    }
    return color.toAwtColor();
  }

  protected abstract @NotNull Color getBackgroundByColorIndex(int colorIndex);

  private void assertColorIndexIsLessThan16(int colorIndex) {
    if (colorIndex < 0 || colorIndex >= 16) {
      throw new AssertionError("Color index is out of bounds [0,15]: " + colorIndex);
    }
  }

  public static @Nullable TerminalColor getIndexedTerminalColor(int colorIndex) {
    return colorIndex < 16 ? TerminalColor.index(colorIndex) : getXTerm256(colorIndex);
  }

  private static @Nullable TerminalColor getXTerm256(int colorIndex) {
    return colorIndex < 256 ? COL_RES_256[colorIndex - 16] : null;
  }

  //The code below is translation of xterm's 256colres.pl
  private static final TerminalColor[] COL_RES_256 = new TerminalColor[240];

  static {
    // colors 16-231 are a 6x6x6 color cube
    for (int red = 0; red < 6; red++) {
      for (int green = 0; green < 6; green++) {
        for (int blue = 0; blue < 6; blue++) {
          COL_RES_256[36 * red + 6 * green + blue] = new TerminalColor(getCubeColorValue(red),
                                                                       getCubeColorValue(green),
                                                                       getCubeColorValue(blue));
        }
      }
    }

    // colors 232-255 are a grayscale ramp, intentionally leaving out
    // black and white
    for (int gray = 0; gray < 24; gray++) {
      int level = 10 * gray + 8;
      COL_RES_256[216 + gray] = new TerminalColor(level, level, level);
    }
  }

  private static int getCubeColorValue(int value) {
    return value == 0 ? 0 : (40 * value + 55);
  }
}
