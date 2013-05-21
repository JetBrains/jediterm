package com.jediterm.emulator.display;

import com.jediterm.emulator.*;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BackBuffer implements StyledTextConsumer {
  private static final Logger logger = Logger.getLogger(BackBuffer.class);
  private static final char EMPTY_CHAR = ' '; // (char) 0x0;

  private char[] myBuf;
  private TextStyle[] myStyleBuf;
  private BitSet myDamage;

  private final StyleState myStyleState;
  private final LinesBuffer myScrollBuffer;

  private final LinesBuffer myTextBuffer = new LinesBuffer();

  private int myWidth;
  private int myHeight;

  private final Lock myLock = new ReentrantLock();
  private int myTextLinesCount;

  public BackBuffer(final int width, final int height, StyleState styleState, LinesBuffer scrollBuffer) {
    myStyleState = styleState;
    myScrollBuffer = scrollBuffer;
    myWidth = width;
    myHeight = height;

    allocateBuffers();
  }

  private void allocateBuffers() {
    myBuf = new char[myWidth * myHeight];
    Arrays.fill(myBuf, EMPTY_CHAR);

    myStyleBuf = new TextStyle[myWidth * myHeight];
    Arrays.fill(myStyleBuf, TextStyle.EMPTY);

    myDamage = new BitSet(myWidth * myHeight);
  }

  public Dimension resize(final Dimension pendingResize,
                          final RequestOrigin origin,
                          final int cursorY,
                          BufferedTerminalWriter.ResizeHandler resizeHandler) {
    final char[] oldBuf = myBuf;
    final TextStyle[] oldStyleBuf = myStyleBuf;
    final int oldHeight = myHeight;
    final int oldWidth = myWidth;

    final int newWidth = pendingResize.width;
    final int newHeight = pendingResize.height;
    final int scrollLinesCountOld = myScrollBuffer.getLineCount();
    final int textLinesCountOld = myTextBuffer.getLineCount();

    boolean textBufferUpdated = false;

    if (newHeight < cursorY) {
      //we need to move lines from text buffer to the scroll buffer
      int count = cursorY - newHeight;
      myTextBuffer.moveTopLinesTo(count, myScrollBuffer);
    }
    else if (newHeight > cursorY && myScrollBuffer.getLineCount() > 0) {
      //we need to move lines from scroll buffer to the text buffer
      myScrollBuffer.moveBottomLinesTo(newHeight - cursorY, myTextBuffer);
      textBufferUpdated = true;
    }

    myWidth = newWidth;
    myHeight = newHeight;

    allocateBuffers();

    Arrays.fill(myBuf, EMPTY_CHAR);

    final int copyWidth = Math.min(oldWidth, myWidth);
    final int copyHeight = Math.min(oldHeight, myHeight);

    final int oldStart;
    final int start;


    if (myHeight > oldHeight) {
      oldStart = 0;

      start = Math.min(myHeight - copyHeight, scrollLinesCountOld);
    }
    else {
      oldStart = Math.max(0, cursorY - myHeight);
      start = 0;
    }

    // copying lines...
    for (int i = 0; i < copyHeight; i++) {
      System.arraycopy(oldBuf, (oldStart + i) * oldWidth, myBuf, (start + i) * myWidth, copyWidth);
      System.arraycopy(oldStyleBuf, (oldStart + i) * oldWidth, myStyleBuf, (start + i) * myWidth, copyWidth);
    }

    if (myWidth > oldWidth || textBufferUpdated) {
      //we need to fill new space with data from the text buffer
      myTextLinesCount = myTextBuffer.getLineCount();
      myTextBuffer.processLines(-myTextLinesCount, myTextLinesCount, this);
    }

    if (myTextBuffer.getLineCount() >= myHeight) {
      myTextBuffer.moveTopLinesTo(myTextBuffer.getLineCount() - myHeight, myScrollBuffer);
    }

    int myCursorY = cursorY + (myTextBuffer.getLineCount() - textLinesCountOld);

    myDamage.set(0, myWidth * myHeight - 1, true);

    resizeHandler.sizeUpdated(myWidth, myHeight, myCursorY);

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

  public void drawBytes(final int x, final int y, final char[] bytes, final int start, final int len) {
    final int adjY = y - 1;
    if (adjY >= myHeight || adjY < 0) {
      if (logger.isDebugEnabled()) {
        StringBuffer sb = new StringBuffer("Attempt to draw line ")
          .append(adjY).append(" at (").append(x).append(",")
          .append(y).append(")");

        CharacterUtils.appendBuf(sb, bytes, start, len);
        logger.debug(sb);
      }
      return;
    }

    for (int i = 0; i < len; i++) {
      final int location = adjY * myWidth + x + i;
      myBuf[location] = bytes[start + i]; // Arraycopy does not convert

      myStyleBuf[location] = myStyleState.getCurrent();
    }
    myDamage.set(adjY * myWidth + x, adjY * myWidth + x + len);
  }

  public void drawString(final int x, final int y, final String str) {
    drawString(x, y, str, myStyleState.getCurrent());
  }

  private void drawString(int x, int y, String str, TextStyle style) {
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
      myStyleBuf[location] = style;
    }
    myDamage.set(adjY * myWidth + x, adjY * myWidth + x + str.length());
  }

  public void scrollArea(final int y, final int h, final int dy) {
    myTextBuffer.removeTopLines(y);
    processBufferRows(y - 1, -dy, myScrollBuffer);

    final int lastLine = y + h;
    if (dy > 0) {
      moveLinesDown(y, dy, lastLine);
    }
    else {
      moveLinesUp(y, dy, lastLine);
    }
  }

  private void moveLinesUp(int y, int dy, int lastLine) {
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

  private void moveLinesDown(int y, int dy, int lastLine) {
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
      final StringBuilder sb = new StringBuilder();
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
      final StringBuilder sb = new StringBuilder();
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

  public void processBufferRows(final int startRow, final int height, final StyledTextConsumer consumer) {
    processBufferCells(0, startRow, myWidth, height, consumer);
  }

  public void processBufferCells(final int startCol,
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
      if (location < 0 || location >= myStyleBuf.length) {
        throw new IllegalStateException("Can't pump a char at " + row + "x" + col);
      }
      final TextStyle cellStyle = myStyleBuf[location];
      if (lastStyle == null) {
        //begin line
        lastStyle = cellStyle;
      }
      else if (!cellStyle.equals(lastStyle)) {
        //start of new run
        consumer.consume(beginRun, row, lastStyle, new CharBuffer(myBuf, row * myWidth + beginRun, col - beginRun));
        beginRun = col;
        lastStyle = cellStyle;
      }
    }
    //end row
    if (lastStyle == null) {
      logger.error("Style is null for run supposed to be from " + beginRun + " to " + endCol + "on row " + row);
    }
    else {
      consumer.consume(beginRun, row, lastStyle, new CharBuffer(myBuf, row * myWidth + beginRun, endCol - beginRun));
    }
  }

  /**
   * Cell is a styled block of text
   *
   * @param consumer
   */
  public void processDamagedCells(final StyledTextConsumer consumer) {
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
            lastStyle = null;
          }
          else {
            if (lastStyle == null) {
              //begin a new run
              beginRun = col;
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
    consumer.consume(beginRun, row, lastStyle, new CharBuffer(myBuf, row * myWidth + beginRun, col - beginRun));
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

  public void moveToTextBuffer(int cursorY) {
    if (myTextBuffer.getLineCount()>cursorY) {
      myTextBuffer.removeLines(cursorY - 1, myTextBuffer.getLineCount());
    }
    processBufferRow(cursorY - 1, myTextBuffer);
  }

  @Override
  public void consume(int x, int y, TextStyle style, CharBuffer characters) {
    int len = Math.min(myWidth - x, characters.getLen());

    if (len > 0) {
      drawString(x, y + myTextLinesCount + 1, new String(characters.getBuf(), characters.getStart(), len), style);
    }
  }

  public int getWidth() {
    return myWidth;
  }

  public int getHeight() {
    return myHeight;
  }

  public void clearLines(int startRow, int endRow) {
    myTextBuffer.removeLines(startRow, endRow); //TODO: when we remove lines from the mi possibly we need to insert empty lines
    clearArea(0, startRow, myWidth, endRow);
  }
}
