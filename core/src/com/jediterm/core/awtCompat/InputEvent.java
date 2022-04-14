package com.jediterm.core.awtCompat;

public abstract class InputEvent {
  public static final int SHIFT_MASK = 1 << 0;
  public static final int CTRL_MASK = 1 << 1;
  public static final int META_MASK = 1 << 2;
  public static final int ALT_MASK = 1 << 3;
}
