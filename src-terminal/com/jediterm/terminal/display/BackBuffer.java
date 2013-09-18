package com.jediterm.terminal.display;

import com.google.common.collect.Maps;
import com.jediterm.terminal.*;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Buffer for storing styled text data.
 * Stores only text that fit into one screen XxY, but has scrollBuffer to save history lines and textBuffer to restore
 * screen after resize. ScrollBuffer stores all lines before the first line currently shown on the screen. TextBuffer
 * stores lines that are shown currently on the screen and they have there(in TextBuffer) their initial length (even if
 * it doesn't fit to screen width).
 * <p/>
 * Also handles screen damage (TODO: write about it).
 */
public class BackBuffer implements StyledTextConsumer {
  private static final Logger LOG = Logger.getLogger(BackBuffer.class);

  private static final char EMPTY_CHAR = ' '; // (char) 0x0;

  private char[] myBuf;
  private TextStyle[] myStyleBuf;
  private BitSet myDamage;

  @NotNull
  private final StyleState myStyleState;

  private LinesBuffer myScrollBuffer = new LinesBuffer();

  private LinesBuffer myTextBuffer = new LinesBuffer();

  private int myWidth;
  private int myHeight;

  private final Lock myLock = new ReentrantLock();

  private LinesBuffer myTextBufferBackup; // to store textBuffer after switching to alternate buffer
  private LinesBuffer myScrollBufferBackup;
  private boolean myAlternateBuffer = false;

  private boolean myUsingAlternateBuffer = false;

  public BackBuffer(final int width, final int height, @NotNull StyleState styleState) {
    myStyleState = styleState;
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

  public Dimension resize(@NotNull final Dimension pendingResize,
                          @NotNull final RequestOrigin origin,
                          final int cursorY,
                          @NotNull JediTerminal.ResizeHandler resizeHandler, 
                          @Nullable TerminalSelection mySelection) {
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
      if (!myAlternateBuffer) {
        myTextBuffer.moveTopLinesTo(count, myScrollBuffer);
      }
      if (mySelection != null) {
        mySelection.shiftY(-count);
      }
    }
    else if (newHeight > cursorY && myScrollBuffer.getLineCount() > 0) {
      //we need to move lines from scroll buffer to the text buffer
      if (!myAlternateBuffer) {
        myScrollBuffer.moveBottomLinesTo(newHeight - cursorY, myTextBuffer);
        textBufferUpdated = true;
      }
      if (mySelection != null) {
        mySelection.shiftY(newHeight - cursorY);
      }
    }

    myWidth = newWidth;
    myHeight = newHeight;

    allocateBuffers();

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

    if (!myAlternateBuffer && (myWidth > oldWidth || textBufferUpdated)) {
      //we need to fill new space with data from the text buffer
      resetFromTextBuffer();
    }

    if (myTextBuffer.getLineCount() >= myHeight) {
      myTextBuffer.moveTopLinesTo(myTextBuffer.getLineCount() - myHeight, myScrollBuffer);
    }

    int myCursorY = cursorY + (myTextBuffer.getLineCount() - textLinesCountOld);

    myDamage.set(0, myWidth * myHeight - 1, true);

    resizeHandler.sizeUpdated(myWidth, myHeight, myCursorY);

    return pendingResize;
  }

  private void resetFromTextBuffer() {
    clearArea();
    myTextBuffer.processLines(0, getTextBufferLinesCount(), this, 0);
  }

  private void clearArea() {
    clearArea(0, 0, myWidth, myHeight);
  }

  private void clearArea(final int leftX, final int topY, final int rightX, final int bottomY) {
    clearArea(leftX, topY, rightX, bottomY, TextStyle.EMPTY);
  }

  private void clearArea(final int leftX, final int topY, final int rightX, final int bottomY, @NotNull TextStyle textStyle) {
    if (topY > bottomY) {
      LOG.error("Attempt to clear upside down area: top:" + topY + " > bottom:" + bottomY);
      return;
    }
    for (int y = topY; y < bottomY; y++) {
      if (y > myHeight - 1 || y < 0) {
        LOG.error("attempt to clear line " + y + "\n" +
                  "args were x1:" + leftX + " y1:" + topY + " x2:"
                  + rightX + " y2:" + bottomY);
      }
      else if (leftX > rightX) {
        LOG.error("Attempt to clear backwards area: left:" + leftX + " > right:" + rightX);
      }
      else {
        Arrays.fill(myBuf,
                    y * myWidth + leftX,
                    y * myWidth + rightX,
                    EMPTY_CHAR);

        Arrays.fill(myStyleBuf,
                    y * myWidth + leftX,
                    y * myWidth + rightX,
                    textStyle
        );
        myDamage.set(y * myWidth + leftX,
                     y * myWidth + rightX,
                     true);
      }
    }
  }

  private TextStyle createEmptyStyleWithCurrentColor() {
    return myStyleState.getCurrent().createEmptyWithColors();
  }

  public void deleteCharacters(final int x, final int y, final int count) {
    if (y > myHeight - 1 || y < 0) {
      LOG.error("attempt to delete in line " + y + "\n" +
                "args were x:" + x + " count:" + count);
    }
    else if (count < 0) {
      LOG.error("Attempt to delete negative chars number: count:" + count);
    }
    else if (count == 0) { //nothing to do
      return;
    }
    else {
      int to = y * myWidth + x;
      int from = to + count;
      int remain = myWidth - x - count;
      LOG.debug("About to delete " + count + " chars on line " + y + ", starting from " + x +
                " (from : " + from + " to : " + to + " remain : " + remain + ")");
      System.arraycopy(myBuf, from, myBuf, to, remain);
      Arrays.fill(myBuf, to + remain, (y + 1) * myWidth, EMPTY_CHAR);
      System.arraycopy(myStyleBuf, from, myStyleBuf, to, remain);
      Arrays.fill(myStyleBuf, to + remain, (y + 1) * myWidth, createEmptyStyleWithCurrentColor());

      myTextBuffer.deleteCharacters(x, y, count);

      myDamage.set(to, (y + 1) * myWidth, true);
    }
  }

  public void insertBlankCharacters(final int x, final int y, final int count) {
    if (y > myHeight - 1 || y < 0) {
      LOG.error("attempt to insert blank chars in line " + y + "\n" +
                "args were x:" + x + " count:" + count);
    }
    else if (count < 0) {
      LOG.error("Attempt to insert negative blank chars number: count:" + count);
    }
    else if (count == 0) { //nothing to do
      return;
    }
    else {
      int from = y * myWidth + x;
      int to = from + count;
      int remain = myWidth - x - count;
      LOG.debug("About to insert " + count + " blank chars on line " + y + ", starting from " + x +
                " (from : " + from + " to : " + to + " remain : " + remain + ")");
      System.arraycopy(myBuf, from, myBuf, to, remain);
      Arrays.fill(myBuf, from, to, EMPTY_CHAR);
      System.arraycopy(myStyleBuf, from, myStyleBuf, to, remain);
      Arrays.fill(myStyleBuf, from, to, TextStyle.EMPTY);

      myTextBuffer.insertBlankCharacters(x, y, count, myWidth);

      myDamage.set(from, (y + 1) * myWidth, true);
    }
  }

  public void writeBytes(final int x, final int y, final char[] bytes, final int start, final int len) {
    final int adjY = y - 1;
    if (adjY >= myHeight || adjY < 0) {
      if (LOG.isDebugEnabled()) {
        StringBuilder sb = new StringBuilder("Attempt to draw line ")
          .append(adjY).append(" at (").append(x).append(",")
          .append(y).append(")");

        CharacterUtils.appendBuf(sb, bytes, start, len);
        LOG.debug(sb);
      }
      return;
    }

    TextStyle style = myStyleState.getCurrent();
    for (int i = 0; i < len; i++) {
      final int location = adjY * myWidth + x + i;
      myBuf[location] = bytes[start + i]; // Arraycopy does not convert

      myStyleBuf[location] = style;
    }

    myTextBuffer.writeString(x, adjY, new String(bytes, start, len), style); //TODO: make write bytes method

    myDamage.set(adjY * myWidth + x, adjY * myWidth + x + len);
  }

  public void writeString(final int x, final int y, @NotNull final String str) {
    writeString(x, y, str, myStyleState.getCurrent());
  }

  private void writeString(int x, int y, @NotNull String str, @NotNull TextStyle style) {
    if (writeToBackBuffer(x, y, str, style)) return;

    myTextBuffer.writeString(x, y - 1, str, style);
  }

  private boolean writeToBackBuffer(int x, int y, @NotNull String str, @NotNull TextStyle style) {
    final int adjY = y - 1;
    if (adjY >= myHeight || adjY < 0) {
      LOG.debug("Attempt to draw line out of bounds: " + adjY + " at (" + x + "," + y + ")");
      return true;
    }
    str.getChars(0, str.length(), myBuf, adjY * myWidth + x);
    for (int i = 0; i < str.length(); i++) {
      final int location = adjY * myWidth + x + i;
      myStyleBuf[location] = style;
    }
    myDamage.set(adjY * myWidth + x, adjY * myWidth + x + str.length());
    return false;
  }


  public void scrollArea(final int scrollRegionTop, final int dy, int scrollRegionBottom) {
    if (dy == 0) {
      return;
    }
    if (dy > 0) {
      insertLines(scrollRegionTop - 1, dy, scrollRegionBottom);
    }
    else {
      LinesBuffer removed = deleteLines(scrollRegionTop - 1, -dy, scrollRegionBottom);
      if (scrollRegionTop == 1) {
        removed.moveTopLinesTo(removed.getLineCount(), myScrollBuffer);
      }
    }
  }

  private void moveLinesUp(int y, int dy, int lastLine) {
    if (dy >= 0) {
      LOG.error("dy should be negative");
    }
    for (int line = y; line < lastLine; line++) {
      if (line >= myHeight) {
        // this is not necessary an error; simply skip it
        LOG.debug("Attempt to scroll line from below bottom of screen: " + line);
        continue;
      }
      if (line + dy < 0) {
        // this is not necessary an error; simply skip it
        LOG.debug("Attempt to scroll to line off top of screen: " + (line + dy));
        continue;
      }

      System.arraycopy(myBuf, line * myWidth, myBuf, (line + dy) * myWidth, myWidth);
      System.arraycopy(myStyleBuf, line * myWidth, myStyleBuf, (line + dy) * myWidth, myWidth);
      myDamage.set((line + dy) * myWidth, (line + dy + 1) * myWidth);
    }
  }

  private void moveLinesDown(int y, int dy, int lastLine) {
    if (dy <= 0) {
      LOG.error("dy should be positive");
    }
    for (int line = lastLine - dy; line >= y; line--) {
      if (line < 0) {
        // this is not necessary an error; simply skip it
        LOG.debug("Attempt to scroll line from above top of screen: " + line);
        continue;
      }
      if (line + dy + 1 > myHeight) {
        // this is not necessary an error; simply skip it
        LOG.debug("Attempt to scroll line off bottom of screen: " + (line + dy));
        continue;
      }

      System.arraycopy(myBuf, line * myWidth, myBuf, (line + dy) * myWidth, myWidth);
      System.arraycopy(myStyleBuf, line * myWidth, myStyleBuf, (line + dy) * myWidth, myWidth);
      myDamage.set((line + dy) * myWidth, (line + dy + 1) * myWidth);
    }
  }

  public String getStyleLines() {
    int count = 0;
    Map<Integer, Integer> hashMap = Maps.newHashMap();
    myLock.lock();
    try {
      final StringBuilder sb = new StringBuilder();
      for (int row = 0; row < myHeight; row++) {
        for (int col = 0; col < myWidth; col++) {
          final TextStyle style = myStyleBuf[row * myWidth + col];
          int styleNum = style == null ? 0 : style.getId();
          if (!hashMap.containsKey(styleNum)) {
            hashMap.put(styleNum, count++);
          }
          sb.append(String.format("%02d ", hashMap.get(styleNum)));
        }
        sb.append("\n");
      }
      return sb.toString();
    }
    finally {
      myLock.unlock();
    }
  }

  public TerminalLine getLine(int index) {
    if (index >= 0) {
      if (index >= getHeight()) {
        LOG.error("Attempt to get line out of bounds: " + index + " >= " + getHeight());
        return TerminalLine.createEmpty();
      }
      return myTextBuffer.getLine(index);
    }
    else {
      if (index < - myScrollBuffer.getLineCount()) {
        LOG.error("Attempt to get line out of bounds: " + index + " < " + -myScrollBuffer.getLineCount());
        return TerminalLine.createEmpty();
      }
      return myScrollBuffer.getLine(getScrollBufferLinesCount() + index);
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

  public void processTextBufferLines(final int yStart, final int yCount, @NotNull final StyledTextConsumer consumer, int startRow) {
    myTextBuffer.processLines(yStart - startRow, Math.min(yCount, myTextBuffer.getLineCount()), consumer, startRow);
  }

  public void processTextBufferLines(final int yStart, final int yCount, @NotNull final StyledTextConsumer consumer) {
    myTextBuffer.processLines(yStart - getTextBufferLinesCount(), Math.min(yCount, myTextBuffer.getLineCount()), consumer);
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
        consumer.consume(beginRun, row, lastStyle, new CharBuffer(myBuf, row * myWidth + beginRun, col - beginRun), 0);
        beginRun = col;
        lastStyle = cellStyle;
      }
    }
    //end row

    if (endCol == startCol) { // no run occurred : retrieve text style
      final int location = row * myWidth + startCol;
      if (location < 0 || location >= myStyleBuf.length) {
        throw new IllegalStateException("Can't pump a char at " + row + "x" + startCol);
      }
      lastStyle = myStyleBuf[location];
    }

    if (lastStyle == null) {
      LOG.error("Style is null for run supposed to be from " + beginRun + " to " + endCol + " on row " + row);
    }
    else {
      consumer.consume(beginRun, row, lastStyle, new CharBuffer(myBuf, row * myWidth + beginRun, endCol - beginRun), 0);
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
            LOG.error("Requested out of bounds runs: pumpFromDamage");
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
    consumer.consume(beginRun, row, lastStyle, new CharBuffer(myBuf, row * myWidth + beginRun, col - beginRun), 0);
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

  private String getLineTrimTrailing(int row) {
    StringBuilder sb = new StringBuilder();
    sb.append(myBuf, row * myWidth, myWidth);
    return Util.trimTrailing(sb.toString());
  }

  @Override
  public void consume(int x, int y, @NotNull TextStyle style, @NotNull CharBuffer characters, int startRow) {
    int len = Math.min(myWidth - x, characters.getLength());

    if (len > 0) {
      writeToBackBuffer(x, y - startRow + 1, new String(characters.getBuf(), characters.getStart(), len), style);
    }
  }

  public int getWidth() {
    return myWidth;
  }

  public int getHeight() {
    return myHeight;
  }

  public int getScrollBufferLinesCount() {
    return myScrollBuffer.getLineCount();
  }

  public int getTextBufferLinesCount() {
    return myTextBuffer.getLineCount();
  }

  public String getTextBufferLines() {
    return myTextBuffer.getLines();
  }

  public boolean checkTextBufferIsValid(int row) {
    return myTextBuffer.getLineText(row).startsWith(getLineTrimTrailing(row));//in a row back buffer is always a prefix of text buffer
  }

  public char getCharAt(int x, int y) {
    return myBuf[x + myWidth * y];
  }

  public char getBuffersCharAt(int x, int y) {
    String lineText = getLine(y).getText();
    return x < lineText.length() ? lineText.charAt(x) : EMPTY_CHAR;
  }

  public TextStyle getStyleAt(int x, int y) {
    return myStyleBuf[x + myWidth * y];
  }

  public void useAlternateBuffer(boolean enabled) {
    myAlternateBuffer = enabled;
    if (enabled) {
      if (!myUsingAlternateBuffer) {
        myTextBufferBackup = myTextBuffer;
        myScrollBufferBackup = myScrollBuffer;
        myTextBuffer = new LinesBuffer();
        myScrollBuffer = new LinesBuffer();
        clearArea();
        myUsingAlternateBuffer = true;
      }
    }
    else {
      if (myUsingAlternateBuffer) {
        myTextBuffer = myTextBufferBackup;
        myScrollBuffer = myScrollBufferBackup;
        resetFromTextBuffer();
        myUsingAlternateBuffer = false;
      }
    }
  }

  public LinesBuffer getScrollBuffer() {
    return myScrollBuffer;
  }

  public void insertLines(int y, int count, int scrollRegionBottom) {
    moveLinesDown(y, count, scrollRegionBottom - 1);
    clearArea(0, y, myWidth, Math.min(y + count, scrollRegionBottom));
    myTextBuffer.insertLines(y, count, scrollRegionBottom - 1);
  }

  // returns deleted lines
  public LinesBuffer deleteLines(int y, int count, int scrollRegionBottom) {
    moveLinesUp(y + count, -count, scrollRegionBottom);
    clearArea(0, Math.max(y, scrollRegionBottom - count), myWidth, scrollRegionBottom);

    return myTextBuffer.deleteLines(y, count, scrollRegionBottom - 1);
  }

  public void clearLines(int startRow, int endRow) {
    TextStyle style = createEmptyStyleWithCurrentColor();

    myTextBuffer.clearLines(startRow, endRow);
    clearArea(0, startRow, myWidth, endRow, style);
  }


  public void eraseCharacters(int leftX, int rightX, int y) {
    TextStyle style = createEmptyStyleWithCurrentColor();
    if (y >= 0) {
      clearArea(leftX, y, rightX, y + 1, style);
      myTextBuffer.clearArea(leftX, y, rightX, y + 1, style);
    }
    else {
      LOG.error("Attempt to erase characters in line: " + y);
    }
  }

  public void clearAll() {
    clearArea();
    myScrollBuffer.clearAll();
  }
}
