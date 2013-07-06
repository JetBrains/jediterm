package com.jediterm.terminal.display;

import com.jediterm.terminal.TextStyle;

import java.awt.*;

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

  private static TextStyle merge(TextStyle style, TextStyle defaultStyle) {
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
    myCurrentStyle = new TextStyle();
  }

  public void set(StyleState styleState) {
    setCurrent(styleState.getCurrent());
  }

  public void setDefaultStyle(TextStyle defaultStyle) {
    myDefaultStyle = defaultStyle;
    myMergedStyle = null;
  }

  public Color getBackground(Color color) {
    return color != null ? color : myDefaultStyle.getBackground();
  }

  public Color getForeground(Color color) {
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
