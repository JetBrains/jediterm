package com.jediterm.core.input;

final class Event {
  public static final int SHIFT_MASK          = 1;
  public static final int ALT_MASK            = 1 << 3;
  public static final int CTRL_MASK           = 1 << 1;
  public static final int META_MASK           = 1 << 2;
}