package com.jediterm.terminal.model;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import org.jetbrains.annotations.NotNull;

public class StyleState {
  private TextStyle myCurrentStyle = TextStyle.EMPTY;
  private TextStyle myDefaultStyle = TextStyle.EMPTY;
  
  private TextStyle myMergedStyle = null;

  public StyleState() {
    this(TextStyle.EMPTY);
  }

  public StyleState(TextStyle textStyle) {
    myCurrentStyle = textStyle;
  }

  public TextStyle getCurrent() {
    return TextStyle.getCanonicalStyle(getMergedStyle());
  }

  private static TextStyle merge(@NotNull TextStyle style, @NotNull TextStyle defaultStyle) {
    TextStyle newStyle = style.clone();
    if (newStyle.getBackground() == null && defaultStyle.getBackground() != null) {
      newStyle.setBackground(defaultStyle.getBackground());
    }
    if (newStyle.getForeground() == null && defaultStyle.getForeground() != null) {
      newStyle.setForeground(defaultStyle.getForeground());
    }
    
    return newStyle.readonlyCopy();
  }

  public void reset() {
    myCurrentStyle = myDefaultStyle.clone();
    myMergedStyle = null;
  }

  public void set(StyleState styleState) {
    setCurrent(styleState.getCurrent());
  }

  public void setDefaultStyle(TextStyle defaultStyle) {
    myDefaultStyle = defaultStyle;
    myMergedStyle = null;
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

  public StyleState clone() {
    return new StyleState(myCurrentStyle);
  }

  public void setCurrent(TextStyle current) {
    myCurrentStyle = current;
    myMergedStyle = null;
  }

  public TextStyle getMergedStyle() {
    if (myMergedStyle == null) {
      myMergedStyle = merge(myCurrentStyle, myDefaultStyle);
    }
    return myMergedStyle;
  }
}
