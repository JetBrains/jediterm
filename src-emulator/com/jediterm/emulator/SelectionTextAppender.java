/**
 *
 */
package com.jediterm.emulator;

import com.jediterm.emulator.display.CharBuffer;
import org.apache.log4j.Logger;

import java.awt.*;

public class SelectionTextAppender implements StyledTextConsumer {
  private static final Logger logger = Logger.getLogger(SelectionTextAppender.class);
  private final StringBuilder mySelection;
  private final StringBuilder myLastLine;
  private final Point myBegin;
  private final Point myEnd;

  boolean first = true;

  public SelectionTextAppender(final Point begin, final Point end) {
    mySelection = new StringBuilder();
    myLastLine = new StringBuilder();
    myEnd = end;
    myBegin = begin;
  }

  public void consume(final int x, final int y, final TextStyle style, final CharBuffer characters) {
    int startPos = characters.getStart();
    int extent = characters.getLen();

    if (y == myEnd.y) {
      extent = Math.min(myEnd.x - x, extent);
    }
    if (y == myBegin.y) {
      final int xAdj = Math.max(0, myBegin.x - x);
      startPos += xAdj;
      extent -= xAdj;
      if (extent < 0) return;
    }
    if (extent < 0) return; // The run is off the left edge of the selection on the first line,
    //  or off the right edge on the last line.
    if (characters.getLen() > 0) {
      if (!first && x == 0) {
        appendLastLineWithTrimming();
        myLastLine.setLength(0);
        myLastLine.append('\n');
      }
      first = false;
      if (startPos < 0) {
        logger.error("Attempt to copy to selection from before start of buffer");
      }
      else if (startPos + extent >= characters.getBuf().length) {
        logger.error("Attempt to copy to selection from after end of buffer");
      }
      else {
        myLastLine.append(characters.getBuf(), startPos, extent);
      }
    }
  }

  public String getText() {
    return mySelection.append(myLastLine).toString();
  }

  private StringBuilder appendLastLineWithTrimming() {
    return mySelection.append(Util.trimTrailing(myLastLine.toString()));
  }
}