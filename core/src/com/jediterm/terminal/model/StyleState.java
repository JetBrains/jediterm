package com.jediterm.terminal.model;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import org.jetbrains.annotations.NotNull;

public class StyleState {
  private volatile TextStyle myCurrentStyle = TextStyle.EMPTY;
  private volatile TextStyle myDefaultStyle = TextStyle.EMPTY;

  public StyleState() {
  }

  public @NotNull TextStyle getCurrent() {
    return myCurrentStyle;
  }

  public void reset() {
    myCurrentStyle = myDefaultStyle;
  }

  public void set(StyleState styleState) {
    setCurrent(styleState.getCurrent());
  }

  public void setDefaultStyle(TextStyle defaultStyle) {
    myDefaultStyle = defaultStyle;
  }

  public TerminalColor getBackground() {
    return getBackground(null);
  }

  public TerminalColor getBackground(TerminalColor color) {
    return color != null ? color : myDefaultStyle.getBackground();
  }

  public TerminalColor getForeground() {
    return getForeground(null);
  }

  public TerminalColor getForeground(TerminalColor color) {
    return color != null ? color : myDefaultStyle.getForeground();
  }

  public void setCurrent(TextStyle current) {
    myCurrentStyle = current;
  }
}
