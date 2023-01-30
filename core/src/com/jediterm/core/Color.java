package com.jediterm.core;

import org.jetbrains.annotations.NotNull;

public final class Color {
  private final int value;

  public Color(int r, int g, int b) {
    this(r, g, b, 255);
  }

  public Color(int r, int g, int b, int a) {
    value = ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
  }

  public Color(int rgb) {
    this(rgb, false);
  }

  public Color(int rgba, boolean hasAlpha) {
    if (hasAlpha) {
      value = rgba;
    } else {
      value = 0xff000000 | rgba;
    }
  }

  public int getRed() {
    return (value >> 16) & 0xFF;
  }

  public int getGreen() {
    return (value >> 8) & 0xFF;
  }

  public int getBlue() {
    return value & 0xFF;
  }

  public int getAlpha() {
    return (value >> 24) & 0xff;
  }

  public int getRGB() {
    return value;
  }

  /**
   * Returns <a href="https://linux.die.net/man/3/xparsecolor">XParseColor</a> representation of TerminalColor
   */
  public @NotNull String toXParseColor() {
    return "rgb:" + toHexString16();
  }

  private @NotNull String toHexString16() {
    // (n * 0x101) converts the 8-bit number to 16 bits.
    String red = padStart(Integer.toHexString(getRed() * 0x101), 4, '0');
    String green = padStart(Integer.toHexString(getGreen() * 0x101), 4, '0');
    String blue = padStart(Integer.toHexString(getBlue() * 0x101), 4, '0');

    return red + "/" + green + "/" + blue;
  }

  @SuppressWarnings("SameParameterValue")
  private @NotNull String padStart(@NotNull String str, int totalLength, char ch) {
    return Character.toString(ch).repeat(Math.max(0, totalLength - str.length())) + str;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof Color && ((Color)o).value == value;
  }

  @Override
  public int hashCode() {
    return value;
  }

  public String toString() {
    return getClass().getName() + "[r=" + getRed() + ",g=" + getGreen() + ",b=" + getBlue() + ", alpha=" + getAlpha() + "]";
  }
}
