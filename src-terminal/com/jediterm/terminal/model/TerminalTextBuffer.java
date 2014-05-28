package com.jediterm.terminal.model;

import com.google.common.collect.Maps;
import com.jediterm.terminal.CharacterUtils;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.StyledTextConsumer;
import com.jediterm.terminal.TextStyle;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Buffer for storing styled text data.
 * Stores only text that fit into one screen XxY, but has scrollBuffer to save history lines and screenBuffer to restore
 * screen after resize. ScrollBuffer stores all lines before the first line currently shown on the screen. TextBuffer
 * stores lines that are shown currently on the screen and they have there(in TextBuffer) their initial length (even if
 * it doesn't fit to screen width).
 * <p/>
 * Also handles screen damage (TODO: write about it).
 */
public class TerminalTextBuffer {
  private static final Logger LOG = Logger.getLogger(TerminalTextBuffer.class);

  private BitSet myDamage;

  @NotNull
  private final StyleState myStyleState;

  private LinesBuffer myHistoryBuffer = new LinesBuffer();

  private LinesBuffer myScreenBuffer = new LinesBuffer();

  private int myWidth;
  private int myHeight;

  private final Lock myLock = new ReentrantLock();

  private LinesBuffer myHistoryBufferBackup;
  private LinesBuffer myScreenBufferBackup; // to store textBuffer after switching to alternate buffer

  private boolean myAlternateBuffer = false;

  private boolean myUsingAlternateBuffer = false;

  public TerminalTextBuffer(final int width, final int height, @NotNull StyleState styleState) {
    myStyleState = styleState;
    myWidth = width;
    myHeight = height;

    myScreenBuffer = new LinesBuffer();

    myDamage = new BitSet(myWidth * myHeight);
  }

  public Dimension resize(@NotNull final Dimension pendingResize,
                          @NotNull final RequestOrigin origin,
                          final int cursorY,
                          @NotNull JediTerminal.ResizeHandler resizeHandler,
                          @Nullable TerminalSelection mySelection) {
    final int oldHeight = myHeight;
    final int oldWidth = myWidth;

    final int newWidth = pendingResize.width;
    final int newHeight = pendingResize.height;
    final int scrollLinesCountOld = myHistoryBuffer.getLineCount();
    final int textLinesCountOld = myScreenBuffer.getLineCount();


    if (newHeight < cursorY) {
      //we need to move lines from text buffer to the scroll buffer
      int count = cursorY - newHeight;
      if (!myAlternateBuffer) {
        myScreenBuffer.moveTopLinesTo(count, myHistoryBuffer);
      }
      if (mySelection != null) {
        mySelection.shiftY(-count);
      }
    } else if (newHeight > cursorY && myHistoryBuffer.getLineCount() > 0) {
      //we need to move lines from scroll buffer to the text buffer
      if (!myAlternateBuffer) {
        myHistoryBuffer.moveBottomLinesTo(newHeight - cursorY, myScreenBuffer);
      }
      if (mySelection != null) {
        mySelection.shiftY(newHeight - cursorY);
      }
    }

    myWidth = newWidth;
    myHeight = newHeight;

    myDamage = new BitSet(myWidth * myHeight);

    if (myScreenBuffer.getLineCount() > myHeight) {
      myScreenBuffer.moveTopLinesTo(myScreenBuffer.getLineCount() - myHeight, myHistoryBuffer);
    }

    int myCursorY = cursorY + (myScreenBuffer.getLineCount() - textLinesCountOld);

    setDamage();

    resizeHandler.sizeUpdated(myWidth, myHeight, myCursorY);

    return pendingResize;
  }

  private void setDamage() {
    myDamage.set(0, myWidth * myHeight - 1, true);
  }

  private TextStyle createEmptyStyleWithCurrentColor() {
    return myStyleState.getCurrent().createEmptyWithColors();
  }

  public void deleteCharacters(final int x, final int y, final int count) {
    if (y > myHeight - 1 || y < 0) {
      LOG.error("attempt to delete in line " + y + "\n" +
              "args were x:" + x + " count:" + count);
    } else if (count < 0) {
      LOG.error("Attempt to delete negative chars number: count:" + count);
    } else if (count == 0) { //nothing to do
      return;
    } else {
      int to = y * myWidth + x;
      int from = to + count;
      int remain = myWidth - x - count;
      LOG.debug("About to delete " + count + " chars on line " + y + ", starting from " + x +
              " (from : " + from + " to : " + to + " remain : " + remain + ")");

      myScreenBuffer.deleteCharacters(x, y, count);

      myDamage.set(to, (y + 1) * myWidth, true);
    }
  }

  public void insertBlankCharacters(final int x, final int y, final int count) {
    if (y > myHeight - 1 || y < 0) {
      LOG.error("attempt to insert blank chars in line " + y + "\n" +
              "args were x:" + x + " count:" + count);
    } else if (count < 0) {
      LOG.error("Attempt to insert negative blank chars number: count:" + count);
    } else if (count > 0) { //nothing to do
      int from = y * myWidth + x;

      myScreenBuffer.insertBlankCharacters(x, y, count, myWidth);

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

    myScreenBuffer.writeString(x, adjY, new String(bytes, start, len), style); //TODO: make write bytes method

    myDamage.set(adjY * myWidth + x, adjY * myWidth + x + len);
  }

  public void writeString(final int x, final int y, @NotNull final String str) {
    writeString(x, y, str, myStyleState.getCurrent());
  }

  private void writeString(int x, int y, @NotNull String str, @NotNull TextStyle style) {
    myScreenBuffer.writeString(x, y - 1, str, style);
  }

  public void scrollArea(final int scrollRegionTop, final int dy, int scrollRegionBottom) {
    if (dy == 0) {
      return;
    }
    if (dy > 0) {
      insertLines(scrollRegionTop - 1, dy, scrollRegionBottom);
    } else {
      LinesBuffer removed = deleteLines(scrollRegionTop - 1, -dy, scrollRegionBottom);
      if (scrollRegionTop == 1) {
        removed.moveTopLinesTo(removed.getLineCount(), myHistoryBuffer);
      }
    }
  }

  public String getStyleLines() {
    final Map<Integer, Integer> hashMap = Maps.newHashMap();
    myLock.lock();
    try {
      final StringBuilder sb = new StringBuilder();
      myScreenBuffer.processLines(0, myHeight, new StyledTextConsumer() {
        int count = 0;

        @Override
        public void consume(int x, int y, @NotNull TextStyle style, @NotNull CharBuffer characters, int startRow) {
          if (x == 0) {
            sb.append("\n");
          }
          int styleNum = style.getId();
          if (!hashMap.containsKey(styleNum)) {
            hashMap.put(styleNum, count++);
          }
          sb.append(String.format("%02d ", hashMap.get(styleNum)));

        }
      });
      return sb.toString();
    } finally {
      myLock.unlock();
    }
  }

  /**
   * Returns terminal lines. Negative indexes are for history buffer. Non-negative for screen buffer.
   *
   * @param index index of line
   * @return history lines for index<0, screen line for index>=0
   */
  public TerminalLine getLine(int index) {
    if (index >= 0) {
      if (index >= getHeight()) {
        LOG.error("Attempt to get line out of bounds: " + index + " >= " + getHeight());
        return TerminalLine.createEmpty();
      }
      return myScreenBuffer.getLine(index);
    } else {
      if (index < -getHistoryLinesCount()) {
        LOG.error("Attempt to get line out of bounds: " + index + " < " + -getHistoryLinesCount());
        return TerminalLine.createEmpty();
      }
      return myHistoryBuffer.getLine(getHistoryLinesCount() + index);
    }
  }

  public String getScreenLines() {
    myLock.lock();
    try {
      final StringBuilder sb = new StringBuilder();
      for (int row = 0; row < myHeight; row++) {
        StringBuilder line = new StringBuilder(myScreenBuffer.getLine(row).getText());

        for (int i = line.length(); i < myWidth; i++) {
          line.append(' ');
        }
        if (line.length() > myWidth) {
          line.setLength(myWidth);
        }

        sb.append(line);
        sb.append('\n');
      }
      return sb.toString();
    } finally {
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
    } finally {
      myLock.unlock();
    }
  }

  public void resetDamage() {
    myLock.lock();
    try {
      myDamage.clear();
    } finally {
      myLock.unlock();
    }
  }

  public void processScreenLines(final int yStart, final int yCount, @NotNull final StyledTextConsumer consumer) {
    myScreenBuffer.processLines(yStart - getScreenLinesCount(), Math.min(yCount, myScreenBuffer.getLineCount()), consumer);
  }

  public void processBufferRows(final int startRow, final int height, final StyledTextConsumer consumer) {
    processScreenLine(startRow, height, consumer);
  }

  public void processScreenLine(final int startRow,
                                final int height,
                                final StyledTextConsumer consumer) {

    final int endRow = startRow + height;

    myLock.lock();
    try {
      for (int row = startRow; row < endRow; row++) {
        processScreenLine(row, consumer);
      }
    } finally {
      myLock.unlock();
    }
  }


  public void processScreenLine(int line, StyledTextConsumer consumer) {
    myScreenBuffer.processLines(line, 1, consumer);
  }

  /**
   * Cell is a styled block of text
   *
   * @param consumer
   */
  public void processDamagedCells(final StyledTextConsumer consumer) {
    final int startRow = 0;
    final int endRow = myHeight;
    processStyledText(consumer, startRow, endRow);
  }

  private void processStyledText(StyledTextConsumer consumer, int startRow, int endRow) {
    for (int row = startRow; row < endRow; row++) {
      TerminalLine line = myScreenBuffer.getLine(row);
      Iterator<TerminalLine.TextEntry> iterator = line.entriesIterator();
      int x = 0;
      while (iterator.hasNext()) {
        TerminalLine.TextEntry te = iterator.next();
        consumer.consume(x, row, te.getStyle(), te.getText(), startRow);
        x += te.getLength();
      }
    }
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

  public int getWidth() {
    return myWidth;
  }

  public int getHeight() {
    return myHeight;
  }

  public int getHistoryLinesCount() {
    return myHistoryBuffer.getLineCount();
  }

  public int getScreenLinesCount() {
    return myScreenBuffer.getLineCount();
  }

  public char getBuffersCharAt(int x, int y) {
    String lineText = getLine(y).getText();
    return x < lineText.length() ? lineText.charAt(x) : CharacterUtils.EMPTY_CHAR;
  }

  public TextStyle getStyleAt(int x, int y) {
    TerminalLine line = myScreenBuffer.getLine(y);
    return line.getStyleAt(x);
  }

  public void useAlternateBuffer(boolean enabled) {
    myAlternateBuffer = enabled;
    if (enabled) {
      if (!myUsingAlternateBuffer) {
        myScreenBufferBackup = myScreenBuffer;
        myHistoryBufferBackup = myHistoryBuffer;
        myScreenBuffer = new LinesBuffer();
        myHistoryBuffer = new LinesBuffer();
        myUsingAlternateBuffer = true;
        setDamage();
      }
    } else {
      if (myUsingAlternateBuffer) {
        myScreenBuffer = myScreenBufferBackup;
        myHistoryBuffer = myHistoryBufferBackup;
        myUsingAlternateBuffer = false;
        myScreenBufferBackup = new LinesBuffer();
        myHistoryBufferBackup = new LinesBuffer();
        setDamage();
      }
    }
  }

  public LinesBuffer getHistoryBuffer() {
    return myHistoryBuffer;
  }

  public void insertLines(int y, int count, int scrollRegionBottom) {
    myScreenBuffer.insertLines(y, count, scrollRegionBottom - 1);
  }

  // returns deleted lines
  public LinesBuffer deleteLines(int y, int count, int scrollRegionBottom) {
    return myScreenBuffer.deleteLines(y, count, scrollRegionBottom - 1);
  }

  public void clearLines(int startRow, int endRow) {
    myScreenBuffer.clearLines(startRow, endRow);
  }


  public void eraseCharacters(int leftX, int rightX, int y) {
    TextStyle style = createEmptyStyleWithCurrentColor();
    if (y >= 0) {
      myScreenBuffer.clearArea(leftX, y, rightX, y + 1, style);
    } else {
      LOG.error("Attempt to erase characters in line: " + y);
    }
  }

  public void clearAll() {
    myScreenBuffer.clearAll();
  }

  public void processHistoryAndScreenLines(int scrollOrigin, StyledTextConsumer consumer) {
    int linesFromHistory = Math.min(-scrollOrigin, myHeight);
    myHistoryBuffer.processLines(myHistoryBuffer.getLineCount() + scrollOrigin, linesFromHistory, consumer, myHistoryBuffer.getLineCount() + scrollOrigin);
    if (myHeight - linesFromHistory + 1 > 0) {
      myScreenBuffer.processLines(0, myHeight - linesFromHistory + 1, consumer, -linesFromHistory+1);
    }
  }
}
