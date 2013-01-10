package com.jediterm;

import java.awt.Color;

public class StyleState {
  private TermStyle myCurrentStyle = TermStyle.EMPTY;
  private TermStyle myDefaultStyle = TermStyle.EMPTY;

  public StyleState() {
    myCurrentStyle = TermStyle.EMPTY;
  }

  public TermStyle getCurrent() {
    return TermStyle.getCanonicalStyle(merge(myCurrentStyle, myDefaultStyle));
  }

  private static TermStyle merge(TermStyle style, TermStyle defaultStyle) {
    TermStyle newStyle = style;
    if (newStyle.getBackground() == null) {
      newStyle = newStyle.setBackground(defaultStyle.getBackground());
    }
    if (newStyle.getForeground() == null) {
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

  public void setOption(TermStyle.Option opt, boolean val) {
    myCurrentStyle = myCurrentStyle.setOption(opt, val);
  }

  public void reset() {
    myCurrentStyle = new TermStyle();
  }

  public void set(StyleState styleState) {
    myCurrentStyle = styleState.getCurrent();
  }

  public void setDefaultStyle(TermStyle defaultStyle) {
    myDefaultStyle = defaultStyle;
  }

  public Color getBackground(Color color) {
    return color != null ? color : myDefaultStyle.getBackground();
  }

  public Color getForeground(Color color) {
    return color != null ? color : myDefaultStyle.getForeground();
  }
}
