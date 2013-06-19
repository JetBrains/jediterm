package com.jediterm.util;

import com.jediterm.terminal.TextStyle;

import java.awt.*;

/**
 * @author traff
 */
public class TestStyle extends TextStyle {
  public TestStyle(int number, final Color foreground, final Color background) {
    super(foreground, background);
    myNumber = number;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    TestStyle style = (TestStyle)o;

    if (myNumber != style.myNumber) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myNumber;
    return result;
  }
}
