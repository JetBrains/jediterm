package com.jediterm.terminal.display;

import com.jediterm.terminal.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author traff
 */
public class SelectionUtil {
  private static final Logger LOG = LoggerFactory.getLogger(SelectionUtil.class);
  
  private static final List<Character> SEPARATORS = new ArrayList<Character>();
  static {
    SEPARATORS.add(' ');
    SEPARATORS.add('\u00A0'); // NO-BREAK SPACE
    SEPARATORS.add('\t');
    SEPARATORS.add('\'');
    SEPARATORS.add('"');
    SEPARATORS.add('$');
    SEPARATORS.add('(');
    SEPARATORS.add(')');
    SEPARATORS.add('[');
    SEPARATORS.add(']');
    SEPARATORS.add('{');
    SEPARATORS.add('}');
    SEPARATORS.add('<');
    SEPARATORS.add('>');
  }

  public static List<Character> getDefaultSeparators() {
    return new ArrayList<Character>(SEPARATORS);
  }
  
  public static Pair<Point, Point> sortPoints(Point a, Point b) {
    if (a.y == b.y) { /* same line */
      return Pair.create(a.x <= b.x ? a : b, a.x > b.x ? a : b);
    }
    else {
      return Pair.create(a.y < b.y ? a : b, a.y > b.y ? a : b);
    }
  }

  public static String getSelectionText(TerminalSelection selection, BackBuffer backBuffer) {
    return getSelectionText(selection.getStart(), selection.getEnd(), backBuffer);
  }
  
  public static String getSelectionText(final Point selectionStart,
                                        final Point selectionEnd,
                                        final BackBuffer backBuffer) {

    Pair<Point, Point> pair = sortPoints(selectionStart, selectionEnd);

    Point top = pair.first;
    top.y = Math.max(top.y, - backBuffer.getScrollBufferLinesCount());
    Point bottom = pair.second;

    final StringBuilder selectionText = new StringBuilder();

    for (int i = top.y; i <= bottom.y; i++) {
      TerminalLine line = backBuffer.getLine(i);
      String text = line.getText();
      if (i == top.y) {
        if (i == bottom.y) {
          selectionText.append(text.substring(Math.min(text.length(), top.x), Math.min(text.length(), bottom.x)));
        } else {
          selectionText.append(text.substring(Math.min(text.length(), top.x)));
        }
      }
      else if (i == bottom.y) {
        selectionText.append(text.substring(0, Math.min(text.length(), bottom.x)));
      }
      else {
        selectionText.append(line.getText());
      }
      if (!line.isWrapped() && i < bottom.y) {
        selectionText.append("\n");
      }
    }

    return selectionText.toString();
  }

  public static Point getPreviousSeparator(Point charCoords, BackBuffer backBuffer) {
    return getPreviousSeparator(charCoords, backBuffer, SEPARATORS);
  }

  public static Point getPreviousSeparator(Point charCoords, BackBuffer backBuffer, @NotNull List<Character> separators) {
    int x = charCoords.x;
    int y = charCoords.y;
    int terminalWidth = backBuffer.getWidth();

    if (separators.contains(backBuffer.getBuffersCharAt(x, y))) {
      return new Point(x, y);
    }

    String line = backBuffer.getLine(y).getText();
    while (x < line.length() && !separators.contains(line.charAt(x))) {
      x--;
      if (x < 0) {
        if (y <= - backBuffer.getScrollBufferLinesCount()) {
          return new Point(0, y);
        }
        y--;
        x = terminalWidth - 1;

        line = backBuffer.getLine(y).getText();
      }
    }

    x++;
    if (x >= terminalWidth) {
      y++;
      x = 0;
    }

    return new Point(x, y);
  }

  public static Point getNextSeparator(Point charCoords, BackBuffer backBuffer) {
    return getNextSeparator(charCoords, backBuffer, SEPARATORS);
  }

  public static Point getNextSeparator(Point charCoords, BackBuffer backBuffer, @NotNull List<Character> separators) {
    int x = charCoords.x;
    int y = charCoords.y;
    int terminalWidth = backBuffer.getWidth();
    int terminalHeight = backBuffer.getHeight();

    if (separators.contains(backBuffer.getBuffersCharAt(x, y))) {
      return new Point(x, y);
    }

    String line = backBuffer.getLine(y).getText();
    while (x < line.length() && !separators.contains(line.charAt(x))) {
      x++;
      if (x >= terminalWidth) {
        if (y >= terminalHeight - 1) {
          return new Point(terminalWidth - 1, terminalHeight - 1);
        }
        y++;
        x = 0;
        
        line = backBuffer.getLine(y).getText();
      }
    }

    x--;
    if (x < 0) {
      y--;
      x = terminalWidth - 1;
    }

    return new Point(x, y);
  }

}
