package com.jediterm;

import java.awt.Dimension;
import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;

public class BackBuffer {
  private static final Logger logger = Logger.getLogger(BackBuffer.class);
  private static final char EMPTY_CHAR = ' '; // (char) 0x0;

  private char[] myBuf;
  private TextStyle[] myStyleBuf;
  private BitSet myDamage;

  StyleState myStyleState;

  private final ScrollBuffer myScrollBuffer;

  private int myWidth;
  private int myHeight;

  private final Lock myLock = new ReentrantLock();

  public BackBuffer(final int width, final int height, StyleState styleState, ScrollBuffer buffer) {
    myStyleState = styleState;
    myScrollBuffer = buffer;
    allocateBuffers(width, height);
  }

  private void allocateBuffers(final int width, final int height) {
    this.myWidth = width;
    this.myHeight = height;

    myBuf = new char[width * height];
    Arrays.fill(myBuf, EMPTY_CHAR);

    myStyleBuf = new TextStyle[width * height];
    Arrays.fill(myStyleBuf, TextStyle.EMPTY);

    myDamage = new BitSet(width * height);
  }

  public Dimension doResize(final Dimension pendingResize, final RequestOrigin origin) {
    final char[] oldBuf = myBuf;
    final TextStyle[] oldStyleBuf = myStyleBuf;
    final int oldHeight = myHeight;
    final int oldWidth = myWidth;
    allocateBuffers(pendingResize.width, pendingResize.height);

    Arrays.fill(myBuf, EMPTY_CHAR);

    // copying lines...
    final int copyWidth = Math.min(oldWidth, myWidth);
    final int copyHeight = Math.min(oldHeight, myHeight);

    final int oldStart = oldHeight - copyHeight;
    final int start = myHeight - copyHeight;

    for (int i = 0; i < copyHeight; i++) {
      System.arraycopy(oldBuf, (oldStart + i) * oldWidth, myBuf, (start + i) * myWidth, copyWidth);
      System.arraycopy(oldStyleBuf, (oldStart + i) * oldWidth, myStyleBuf, (start + i) * myWidth, copyWidth);
    }

    //myScrollBuffer.pumpRuns(0, pendingResize.height, this);

    myDamage.set(0, myWidth * myHeight - 1, true);

    return pendingResize;
  }

  public void clearArea(final int leftX, final int topY, final int rightX, final int bottomY) {
    if (topY > bottomY) {
      logger.error("Attempt to clear upside down area: top:" + topY + " > bottom:" + bottomY);
      return;
    }
    for (int y = topY; y < bottomY; y++) {
      if (y > myHeight - 1 || y < 0) {
        logger.error("attempt to clear line" + y + "\n" +
                     "args were x1:" + leftX + " y1:" + topY + " x2:"
                     + rightX + "y2:" + bottomY);
      }
      else if (leftX > rightX) {
        logger.error("Attempt to clear backwards area: left:" + leftX + " > right:" + rightX);
      }
      else {
        Arrays.fill(myBuf,
                    y * myWidth + leftX,
                    y * myWidth + rightX,
                    EMPTY_CHAR);
        Arrays.fill(myStyleBuf,
                    y * myWidth + leftX,
                    y * myWidth + rightX,
                    TextStyle.EMPTY
        );
        myDamage.set(y * myWidth + leftX,
                     y * myWidth + rightX,
                     true);
      }
    }
  }

  public void drawBytes(final byte[] bytes, final int s, final int len, final int x, final int y) {
    final int adjY = y - 1;
    if (adjY >= myHeight || adjY < 0) {
      if (logger.isDebugEnabled()) {
        StringBuffer sb = new StringBuffer("Attempt to draw line ")
          .append(adjY).append(" at (").append(x).append(",")
          .append(y).append(")");

        CharacterUtils.appendBuf(sb, bytes, s, len);
        logger.debug(sb);
      }
      return;
    }

    for (int i = 0; i < len; i++) {
      final int location = adjY * myWidth + x + i;
      myBuf[location] = (char)bytes[s + i]; // Arraycopy does not convert
      myStyleBuf[location] = myStyleState.getCurrent();
    }
    myDamage.set(adjY * myWidth + x, adjY * myWidth + x + len);
  }

  public void drawString(final String str, final int x, final int y) {
    final int adjY = y - 1;
    if (adjY >= myHeight || adjY < 0) {
      if (logger.isDebugEnabled()) {
        logger.debug("Attempt to draw line out of bounds: " + adjY + " at (" + x + "," + y + ")");
      }
      return;
    }
    str.getChars(0, str.length(), myBuf, adjY * myWidth + x);
    for (int i = 0; i < str.length(); i++) {
      final int location = adjY * myWidth + x + i;
      myStyleBuf[location] = myStyleState.getCurrent();
    }
    myDamage.set(adjY * myWidth + x, adjY * myWidth + x + str.length());
  }

  public void scrollArea(final int y, final int h, final int dy) {
    final int lastLine = y + h;
    if (dy > 0)
    // Moving lines down
    {
      for (int line = lastLine - dy; line >= y; line--) {
        if (line < 0) {
          logger.error("Attempt to scroll line from above top of screen:" + line);
          continue;
        }
        if (line + dy + 1 > myHeight) {
          logger.error("Attempt to scroll line off bottom of screen:" + (line + dy));
          continue;
        }
        System.arraycopy(myBuf, line * myWidth, myBuf, (line + dy) * myWidth, myWidth);
        System.arraycopy(myStyleBuf, line * myWidth, myStyleBuf, (line + dy) * myWidth, myWidth);
        Util.bitsetCopy(myDamage, line * myWidth, myDamage, (line + dy) * myWidth, myWidth);
        //damage.set( (line + dy) * width, (line + dy + 1) * width );
      }
    }
    else
    // Moving lines up
    {
      for (int line = y + dy + 1; line < lastLine; line++) {
        if (line > myHeight - 1) {
          logger.error("Attempt to scroll line from below bottom of screen:" + line);
          continue;
        }
        if (line + dy < 0) {
          logger.error("Attempt to scroll to line off top of screen" + (line + dy));
          continue;
        }

        System.arraycopy(myBuf, line * myWidth, myBuf, (line + dy) * myWidth, myWidth);
        System.arraycopy(myStyleBuf, line * myWidth, myStyleBuf, (line + dy) * myWidth, myWidth);
        Util.bitsetCopy(myDamage, line * myWidth, myDamage, (line + dy) * myWidth, myWidth);
        //damage.set( (line + dy) * width, (line + dy + 1) * width );
      }
    }
  }

  public String getStyleLines() {
    myLock.lock();
    try {
      final StringBuilder sb = new StringBuilder();
      for (int row = 0; row < myHeight; row++) {
        for (int col = 0; col < myWidth; col++) {
          final TextStyle style = myStyleBuf[row * myWidth + col];
          int styleNum = style == null ? styleNum = 0 : style.getNumber();
          sb.append(String.format("%03d ", styleNum));
        }
        sb.append("\n");
      }
      return sb.toString();
    }
    finally {
      myLock.unlock();
    }
  }

  public String getLines() {
    myLock.lock();
    try {
      final StringBuffer sb = new StringBuffer();
      for (int row = 0; row < myHeight; row++) {
        sb.append(myBuf, row * myWidth, myWidth);
        sb.append('\n');
      }
      return sb.toString();
    }
    finally {
      myLock.unlock();
    }
  }

  public String getDamageLines() {
    myLock.lock();
    try {
      final StringBuffer sb = new StringBuffer();
      for (int row = 0; row < myHeight; row++) {
        for (int col = 0; col < myWidth; col++) {
          boolean isDamaged = myDamage.get(row * myWidth + col);
          sb.append(isDamaged ? 'X' : '-');
        }
        sb.append("\n");
      }
      return sb.toString();
    }
    finally {
      myLock.unlock();
    }
  }

  public void resetDamage() {
    myLock.lock();
    try {
      myDamage.clear();
    }
    finally {
      myLock.unlock();
    }
  }

  public void processBufferRuns(final int startRow, final int height, final StyledTextConsumer consumer) {
    processBufferRuns(0, startRow, myWidth, height, consumer);
  }

  public void processBufferRuns(final int startCol,
                                 final int startRow,
                                 final int width,
                                 final int height,
                                 final StyledTextConsumer consumer) {

    final int endRow = startRow + height;
    final int endCol = startCol + width;

    myLock.lock();
    try {
      for (int row = startRow; row < endRow; row++) {
        processBufferRow(row, startCol, endCol, consumer);
      }
    }
    finally {
      myLock.unlock();
    }
  }

  public void processBufferRow(int row, StyledTextConsumer consumer) {
    processBufferRow(row, 0, myWidth, consumer);
  }

  public void processBufferRow(int row, int startCol, int endCol, StyledTextConsumer consumer) {
    TextStyle lastStyle = null;
    int beginRun = startCol;
    for (int col = startCol; col < endCol; col++) {
      final int location = row * myWidth + col;
      if (location < 0 || location > myStyleBuf.length) {
        throw new IllegalStateException("Can't pump a char at " + row + "x" + col);
      }
      final TextStyle cellStyle = myStyleBuf[location];
      if (lastStyle == null) {
        //begin line
        lastStyle = cellStyle;
      }
      else if (!cellStyle.equals(lastStyle)) {
        //start of new run
        consumer.consume(beginRun, row, lastStyle, new BufferCharacters(myBuf, row * myWidth + beginRun, col - beginRun));
        beginRun = col;
        lastStyle = cellStyle;
      }
    }
    //end row
    if (lastStyle == null) {
      logger.error("Style is null for run supposed to be from " + beginRun + " to " + endCol + "on row " + row);
    }
    else {
      consumer.consume(beginRun, row, lastStyle, new BufferCharacters(myBuf, row * myWidth + beginRun, endCol - beginRun));
    }
  }

  /**
   * Run is a styled block of text
   *
   * @param consumer
   */
  public void processDamagedRuns(final StyledTextConsumer consumer) {
    final int startRow = 0;
    final int endRow = myHeight;
    final int startCol = 0;
    final int endCol = myWidth;
    myLock.lock();
    try {
      for (int row = startRow; row < endRow; row++) {

        TextStyle lastStyle = null;
        int beginRun = startCol;
        for (int col = startCol; col < endCol; col++) {
          final int location = row * myWidth + col;
          if (location < 0 || location > myStyleBuf.length) {
            logger.error("Requested out of bounds runs: pumpFromDamage");
            continue;
          }
          final TextStyle cellStyle = myStyleBuf[location];
          final boolean isDamaged = myDamage.get(location);
          if (!isDamaged) {
            if (lastStyle != null) { //flush previous run
              flushStyledText(consumer, row, lastStyle, beginRun, col);
            }
            beginRun = col;
            lastStyle = null;
          }
          else {
            if (lastStyle == null) {
              //begin a new run
              lastStyle = cellStyle;
            }
            else if (!cellStyle.equals(lastStyle)) {
              //flush prev run and start of a new one
              flushStyledText(consumer, row, lastStyle, beginRun, col);
              beginRun = col;
              lastStyle = cellStyle;
            }
          }
        }
        //flush the last run
        if (lastStyle != null) {
          flushStyledText(consumer, row, lastStyle, beginRun, endCol);
        }
      }
    }
    finally {
      myLock.unlock();
    }
  }

  private void flushStyledText(StyledTextConsumer consumer, int row, TextStyle lastStyle, int beginRun, int col) {
    consumer.consume(beginRun, row, lastStyle, new BufferCharacters(myBuf, row * myWidth + beginRun, col - beginRun));
  }

  public boolean hasDamage() {
    return myDamage.nextSetBit(0) != -1;
  }

  public void lock() {
    myLock.lock();
  }

  public void unlock() {
    myLock.unlock();
  }

  public boolean tryLock() {
    return myLock.tryLock();
  }
}
