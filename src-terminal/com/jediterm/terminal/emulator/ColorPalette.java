package com.jediterm.terminal.emulator;

import java.awt.*;

/**
 * @author traff
 */
public class ColorPalette {
  @SuppressWarnings("UseJBColor")
  private static final Color[] XTERM_PALETTE = new Color[]{
    new Color(0x000000),
    new Color(0xcd0000),
    new Color(0x00cd00),
    new Color(0xcdcd00),
    new Color(0x1e90ff),
    new Color(0xcd00cd),
    new Color(0x00cdcd),
    new Color(0xe5e5e5),
    new Color(0x4c4c4c),
    new Color(0xff0000),
    new Color(0x00ff00),
    new Color(0xffff00),
    new Color(0x4682b4),
    new Color(0xff00ff),
    new Color(0x00ffff),
    new Color(0xffffff),
  };


  public static Color[] getCurrentColorSettings() {
    return XTERM_PALETTE;
  }
}
