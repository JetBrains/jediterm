package com.jediterm.terminal;

import java.awt.*;

/**
 * @author traff
 */
public class TerminalColor {
  public static final TerminalColor BLACK = index(0);
  public static final TerminalColor WHITE = index(15);
  
  private int myColorIndex;
  private int myR;
  private int myG;
  private int myB;

  public TerminalColor(int index) {
    myColorIndex = index;
    myR = -1;
    myG = -1;
    myB = -1;
  }
  
  public TerminalColor(int r, int g, int b) {
    myR = r;
    myG = g;
    myB = b;
  }

  public static TerminalColor index(int index) {
    return new TerminalColor(index);
  }
  
  public static TerminalColor rgb(int r, int g, int b) {
    return new TerminalColor(r, g, b);
  }

  public boolean isIndexed() {
    return myColorIndex != -1;
  }
  
  public Color toAwtColor() {
    if (isIndexed()) {
      throw new IllegalArgumentException("Color is indexed color so a palette is needed");
    }
    
    return new Color(myR, myG, myB);
  }

  public int getIndex() {
    return myColorIndex;
  }
}
