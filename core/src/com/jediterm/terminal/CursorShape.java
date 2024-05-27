package com.jediterm.terminal;

/**
 * Cursor shape as described by <a href="https://vt100.net/docs/vt510-rm/DECSCUSR.html">DECSCUSR</a>.
 */
public enum CursorShape {
  BLINK_BLOCK,
  STEADY_BLOCK,
  BLINK_UNDERLINE,
  STEADY_UNDERLINE,
  BLINK_VERTICAL_BAR,
  STEADY_VERTICAL_BAR;

  public boolean isBlinking() {
    return this == BLINK_BLOCK || this == BLINK_UNDERLINE || this == BLINK_VERTICAL_BAR;
  }
}
