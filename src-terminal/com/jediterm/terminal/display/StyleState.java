package com.jediterm.terminal.display;

import com.jediterm.terminal.TextStyle;

import java.awt.*;

public class StyleState {
  private TextStyle myCurrentStyle = TextStyle.EMPTY;
  private TextStyle myDefaultStyle = TextStyle.EMPTY;

  public StyleState() {
    this(TextStyle.EMPTY);
  }

  public StyleState(TextStyle textStyle) {
    myCurrentStyle = textStyle;
  }

  public TextStyle getCurrent() {
    return TextStyle.getCanonicalStyle(merge(myCurrentStyle, myDefaultStyle));
  }

  private static TextStyle merge(TextStyle style, TextStyle defaultStyle) {
    TextStyle newStyle = style;
    if (newStyle.getBackground() == null && defaultStyle.getBackground() != null) {
      newStyle = newStyle.setBackground(defaultStyle.getBackground());
    }
    if (newStyle.getForeground() == null && defaultStyle.getForeground() != null) {
      newStyle = newStyle.setForeground(defaultStyle.getForeground());
    }
    
    return newStyle;
  }

  public void setCurrentBackground(final Color bg) {
    myCurrentStyle = myCurrentStyle.setBackground(bg);
  }

  public void setCurrentForeground(final Color fg) {
    myCurrentStyle = myCurrentStyle.setForeground(fg);
  }

  public void setOption(TextStyle.Option opt, boolean val) {
    myCurrentStyle = myCurrentStyle.setOption(opt, val);
  }

  public void reset() {
    myCurrentStyle = new TextStyle();
  }

  public void set(StyleState styleState) {
    myCurrentStyle = styleState.getCurrent();
  }

  public void setDefaultStyle(TextStyle defaultStyle) {
    myDefaultStyle = defaultStyle;
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
}
