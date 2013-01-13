/**
 *
 */
package com.jediterm;

import java.awt.Point;

import org.apache.log4j.Logger;

public class SelectionTextAppender implements StyledTextConsumer {
  private static final Logger logger = Logger.getLogger(SelectionTextAppender.class);
  private final StringBuilder selection;
  private final Point begin;
  private final Point end;

  boolean first = true;

  public SelectionTextAppender(final StringBuilder selectionText, final Point begin, final Point end) {
    this.selection = selectionText;
    this.end = end;
    this.begin = begin;
  }

  public void consume(final int x, final int y, final TextStyle style, final CharBuffer characters) {
    int startPos = characters.getStart();
    int extent = characters.getLen();

    if (y == end.y) {
      extent = Math.min(end.x - x, extent);
    }
    if (y == begin.y) {
      final int xAdj = Math.max(0, begin.x - x);
      startPos += xAdj;
      extent -= xAdj;
      if (extent < 0) return;
    }
    if (extent < 0) return; // The run is off the left edge of the selection on the first line,
    //  or off the right edge on the last line.
    if (characters.getLen() > 0) {
      if (!first && x == 0) selection.append('\n');
      first = false;
      if (startPos < 0) {
        logger.error("Attempt to copy to selection from before start of buffer");
      }
      else if (startPos + extent >= characters.getBuf().length) {
        logger.error("Attempt to copy to selection from after end of buffer");
      }
      else {
        selection.append(characters.getBuf(), startPos, extent);
      }
    }
  }
}