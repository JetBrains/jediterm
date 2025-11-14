package com.jediterm.terminal.model;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import org.jetbrains.annotations.NotNull;

public abstract class TerminalLineIntervalHighlighting {
  private final TerminalLine myLine;
  private final int myStartOffset;
  private final int myEndOffset;
  private final TextStyle myStyle;
  private boolean myDisposed = false;

  TerminalLineIntervalHighlighting(@NotNull TerminalLine line, int startOffset, int length, @NotNull TextStyle style) {
    if (startOffset < 0) {
      throw new IllegalArgumentException("Negative startOffset: " + startOffset);
    }
    if (length < 0) {
      throw new IllegalArgumentException("Negative length: " + length);
    }
    myLine = line;
    myStartOffset = startOffset;
    myEndOffset = startOffset + length;
    myStyle = style;
  }

  public @NotNull TerminalLine getLine() {
    return myLine;
  }

  public int getStartOffset() {
    return myStartOffset;
  }

  public int getEndOffset() {
    return myEndOffset;
  }

  public int getLength() {
    return myEndOffset - myStartOffset;
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  public final void dispose() {
    doDispose();
    myDisposed = true;
  }

  protected abstract void doDispose();

  public boolean intersectsWith(int otherStartOffset, int otherEndOffset) {
    return !(myEndOffset <= otherStartOffset || otherEndOffset <= myStartOffset);
  }

  public @NotNull TextStyle mergeWith(@NotNull TextStyle style) {
    TerminalColor foreground = myStyle.getForeground();
    if (foreground == null) {
      foreground = style.getForeground();
    }
    TerminalColor background = myStyle.getBackground();
    if (background == null) {
      background = style.getBackground();
    }
    return new TextStyle(foreground, background);
  }

  @Override
  public String toString() {
    return "startOffset=" + myStartOffset +
      ", endOffset=" + myEndOffset +
      ", disposed=" + myDisposed;
  }
}
