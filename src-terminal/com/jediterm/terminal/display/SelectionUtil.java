package com.jediterm.terminal.display;

import com.jediterm.terminal.SelectionTextAppender;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author traff
 */
public class SelectionUtil {
  private static final Logger LOG = Logger.getLogger(SelectionUtil.class);
  
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

  public static String getSelectionText(final Point selectionStart,
                                        final Point selectionEnd,
                                        final BackBuffer backBuffer) {
    Point top;
    Point bottom;
    int terminalWidth = backBuffer.getWidth();

    if (selectionStart.y == selectionEnd.y) {                        /* same line */
      top = selectionStart.x < selectionEnd.x ? selectionStart
                                              : selectionEnd;
      bottom = selectionStart.x >= selectionEnd.x ? selectionStart
                                                  : selectionEnd;
    }
    else {
      top = selectionStart.y < selectionEnd.y ? selectionStart
                                              : selectionEnd;
      bottom = selectionStart.y > selectionEnd.y ? selectionStart
                                                 : selectionEnd;
    }

    final StringBuilder selectionText = new StringBuilder();

    if (top.y < 0) {  //add lines from scroll buffer
      final Point scrollEnd = bottom.y >= 0 ? new Point(terminalWidth, -1) : bottom;
      SelectionTextAppender scrollText = new SelectionTextAppender(top, scrollEnd);
      backBuffer.getScrollBuffer().processLines(top.y, scrollEnd.y - top.y + 1,
                                                scrollText);
      selectionText.append(scrollText.getText());
    }

    if (bottom.y >= 0) {
      final Point backBegin = top.y < 0 ? new Point(0, 0) : top;
      SelectionTextAppender selectionTextAppender = new SelectionTextAppender(backBegin, bottom);
      for (int y = backBegin.y; y <= bottom.y; y++) {
        if (backBuffer.checkTextBufferIsValid(y)) {
          backBuffer.processTextBufferLines(y, 1, selectionTextAppender, 0);
        }
        else {
          LOG.error("Text buffer has invalid content");
          backBuffer.processBufferRow(y, selectionTextAppender);
        }
      }

      if (selectionText.length() > 0) {
        selectionText.append("\n");
      }
      selectionText.append(selectionTextAppender.getText());
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
