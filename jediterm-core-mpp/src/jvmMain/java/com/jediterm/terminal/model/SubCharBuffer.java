package com.jediterm.terminal.model;

import org.jetbrains.annotations.NotNull;

public class SubCharBuffer extends CharBuffer {
  private final CharBuffer myParent;
  private final int myOffset;

  public SubCharBuffer(@NotNull CharBuffer parent, int offset, int length) {
    super(parent.getBuf(), parent.getStart() + offset, length);
    myParent = parent;
    myOffset = offset;
  }

  public @NotNull CharBuffer getParent() {
    return myParent;
  }

  public int getOffset() {
    return myOffset;
  }
}
