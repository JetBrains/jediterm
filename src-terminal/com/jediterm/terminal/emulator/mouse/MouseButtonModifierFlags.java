package com.jediterm.terminal.emulator.mouse;

/**
 * @author traff
 */
public class MouseButtonModifierFlags {
  // keyboard modifier flag
  //  4 - shit
  //  8 - meta
  //  16 - ctrl
  public static final int MOUSE_BUTTON_SHIFT_FLAG = 4;
  public static final int MOUSE_BUTTON_META_FLAG = 8;
  public static final int MOUSE_BUTTON_CTRL_FLAG = 16;

  // scroll flag
  //  64 - this is scroll event
  public static final int MOUSE_BUTTON_SCROLL_FLAG = 64;

  // for SGR 1006 style, internal use only 
  //  128 - mouse button is released
  public static final int MOUSE_BUTTON_SGR_RELEASE_FLAG = 128;
}
