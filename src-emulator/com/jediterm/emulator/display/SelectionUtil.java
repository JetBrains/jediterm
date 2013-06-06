package com.jediterm.emulator.display;

import com.jediterm.emulator.SelectionTextAppender;

import java.awt.*;

/**
 * @author traff
 */
public class SelectionUtil {
  public static String getSelectionText(Point selectionStart,
                                        Point selectionEnd,
                                        LinesBuffer scrollBuffer, BackBuffer backBuffer) {
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

    if (top.y < 0) {
      final Point scrollEnd = bottom.y >= 0 ? new Point(terminalWidth, -1) : bottom;
      SelectionTextAppender scrollText = new SelectionTextAppender(top, scrollEnd);
      scrollBuffer.processLines(top.y, scrollEnd.y - top.y,
                                scrollText);
      selectionText.append(scrollText.getText());
    }

    if (bottom.y >= 0) {
      final Point backBegin = top.y < 0 ? new Point(0, 0) : top;
      SelectionTextAppender bufferText = new SelectionTextAppender(backBegin, bottom);
      backBuffer.processBufferCells(0, backBegin.y, terminalWidth, bottom.y - backBegin.y + 1,
                                    bufferText);
      if (selectionText.length() > 0) {
        selectionText.append("\n");
      }
      selectionText.append(bufferText.getText());
    }
    return selectionText.toString();
  }
}
