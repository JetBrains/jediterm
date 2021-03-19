package com.jediterm.terminal.model;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import org.jetbrains.annotations.NotNull;

public abstract class TerminalLineIntervalHighlighting {
  private final int myStartOffset;
  private final int myEndOffset;
  private final TextStyle myStyle;
  private boolean myDisposed = false;

  public TerminalLineIntervalHighlighting(int startOffset, int length, @NotNull TextStyle style) {
    myStartOffset = startOffset;
    myEndOffset = startOffset + length;
    myStyle = style;
  }

  public int getStartOffset() {
    return myStartOffset;
  }

  public int getEndOffset() {
    return myEndOffset;
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
    TerminalColor background = myStyle.getForeground();
    if (background == null) {
      background = myStyle.getBackground();
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
