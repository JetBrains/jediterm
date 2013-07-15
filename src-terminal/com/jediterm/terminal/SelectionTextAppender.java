/**
 *
 */
package com.jediterm.terminal;

import com.jediterm.terminal.display.CharBuffer;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class SelectionTextAppender implements StyledTextConsumer {
  private static final Logger LOG = Logger.getLogger(SelectionTextAppender.class);
  private final StringBuilder mySelection;
  private final Point myBegin;
  private final Point myEnd;

  boolean first = true;

  public SelectionTextAppender(final Point begin, final Point end) {
    mySelection = new StringBuilder();
    myEnd = end;
    myBegin = begin;
  }

  public void consume(final int x, int y, @NotNull final TextStyle style, @NotNull final CharBuffer characters, int startRow) {
    int startPos = characters.getStart();
    int length = characters.getLength();

    if (y < myBegin.y || y > myEnd.y) {
      throw new IllegalStateException("y = " + y + " is not within it's bounds [" + myBegin.y + ", " + myEnd.y + "]");
    }

    if (y == myEnd.y) {
      length = Math.min(myEnd.x - x, length);
    }
    if (y == myBegin.y) {
      final int xAdj = Math.max(0, myBegin.x - x);
      startPos += xAdj;
      length -= xAdj;
    }

    if (length > 0) {
      if (!first && x == 0) {
        mySelection.append('\n');
      }
      first = false;

      startPos = Math.max(0, startPos);
      length = Math.min(characters.getBuf().length - startPos, length);

      mySelection.append(characters.getBuf(), startPos, length);
    }
  }

  public String getText() {
    return mySelection.toString();
  }
}