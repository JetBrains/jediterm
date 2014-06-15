package com.jediterm.terminal.model;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jediterm.terminal.CharacterUtils;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.StyledTextConsumer;
import com.jediterm.terminal.StyledTextConsumerAdapter;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.util.Pair;
import com.jediterm.terminal.model.TerminalLine.TextEntry;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.*;
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

  private java.util.List<TerminalModelListener> myListeners = Lists.newArrayList();

  public TerminalTextBuffer(final int width, final int height, @NotNull StyleState styleState) {
    myStyleState = styleState;
    myWidth = width;
    myHeight = height;

    myScreenBuffer = new LinesBuffer();
  }

  public Dimension resize(@NotNull final Dimension pendingResize,
                          @NotNull final RequestOrigin origin,
                          final int cursorY,
                          @NotNull JediTerminal.ResizeHandler resizeHandler,
                          @Nullable TerminalSelection mySelection) {

    final int newWidth = pendingResize.width;
    final int newHeight = pendingResize.height;
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

    if (myScreenBuffer.getLineCount() > myHeight) {
      myScreenBuffer.moveTopLinesTo(myScreenBuffer.getLineCount() - myHeight, myHistoryBuffer);
    }

    int myCursorY = cursorY + (myScreenBuffer.getLineCount() - textLinesCountOld);

    resizeHandler.sizeUpdated(myWidth, myHeight, myCursorY);


    fireModelChangeEvent();

    return pendingResize;
  }

  public void addModelListener(TerminalModelListener listener) {
    myListeners.add(listener);
  }

  public void removeModelListener(TerminalModelListener listener) {
    myListeners.remove(listener);
  }

  private void fireModelChangeEvent() {
    for (TerminalModelListener modelListener: myListeners) {
      modelListener.modelChanged();
    }
  }

  private TextStyle createEmptyStyleWithCurrentColor() {
    return myStyleState.getCurrent().createEmptyWithColors();
  }

  private TextEntry createFillerEntry() {
    return new TextEntry(createEmptyStyleWithCurrentColor(), new CharBuffer(CharacterUtils.NUL_CHAR, myWidth));
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
      myScreenBuffer.deleteCharacters(x, y, count, createEmptyStyleWithCurrentColor());

      fireModelChangeEvent();
    }
  }

  public void insertBlankCharacters(final int x, final int y, final int count) {
    if (y > myHeight - 1 || y < 0) {
      LOG.error("attempt to insert blank chars in line " + y + "\n" +
              "args were x:" + x + " count:" + count);
    } else if (count < 0) {
      LOG.error("Attempt to insert negative blank chars number: count:" + count);
    } else if (count > 0) { //nothing to do
      myScreenBuffer.insertBlankCharacters(x, y, count, myWidth, createEmptyStyleWithCurrentColor());

      fireModelChangeEvent();
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

    fireModelChangeEvent();
  }

  public void writeString(final int x, final int y, @NotNull final String str) {
    writeString(x, y, str, myStyleState.getCurrent());
  }

  private void writeString(int x, int y, @NotNull String str, @NotNull TextStyle style) {
    myScreenBuffer.writeString(x, y - 1, str, style);

    fireModelChangeEvent();
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

      fireModelChangeEvent();
    }
  }

  public String getStyleLines() {
    final Map<Integer, Integer> hashMap = Maps.newHashMap();
    myLock.lock();
    try {
      final StringBuilder sb = new StringBuilder();
      myScreenBuffer.processLines(0, myHeight, new StyledTextConsumerAdapter() {
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

  public void processScreenLines(final int yStart, final int yCount, @NotNull final StyledTextConsumer consumer) {
    myScreenBuffer.processLines(yStart, yCount, consumer);
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
    return getLine(y).charAt(x);
  }

  public TextStyle getStyleAt(int x, int y) {
    return getLine(y).getStyleAt(x);
  }

  public Pair<Character, TextStyle> getStyledCharAt(int x, int y) {
    synchronized (myScreenBuffer) {
      TerminalLine line = getLine(y);
      return new Pair<Character, TextStyle>(line.charAt(x), line.getStyleAt(x));
    }
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
      }
    } else {
      if (myUsingAlternateBuffer) {
        myScreenBuffer = myScreenBufferBackup;
        myHistoryBuffer = myHistoryBufferBackup;
        myUsingAlternateBuffer = false;
        myScreenBufferBackup = new LinesBuffer();
        myHistoryBufferBackup = new LinesBuffer();
      }
    }
    fireModelChangeEvent();
  }

  public LinesBuffer getHistoryBuffer() {
    return myHistoryBuffer;
  }

  public void insertLines(int y, int count, int scrollRegionBottom) {
    myScreenBuffer.insertLines(y, count, scrollRegionBottom - 1, createFillerEntry());

    fireModelChangeEvent();
  }

  // returns deleted lines
  public LinesBuffer deleteLines(int y, int count, int scrollRegionBottom) {
    LinesBuffer linesBuffer = myScreenBuffer.deleteLines(y, count, scrollRegionBottom - 1, createFillerEntry());
    fireModelChangeEvent();
    return linesBuffer;
  }

  public void clearLines(int startRow, int endRow) {
    myScreenBuffer.clearLines(startRow, endRow, createFillerEntry());
    fireModelChangeEvent();
  }

  public void eraseCharacters(int leftX, int rightX, int y) {
    TextStyle style = createEmptyStyleWithCurrentColor();
    if (y >= 0) {
      myScreenBuffer.clearArea(leftX, y, rightX, y + 1, style);
      fireModelChangeEvent();
    } else {
      LOG.error("Attempt to erase characters in line: " + y);
    }
  }

  public void clearAll() {
    myScreenBuffer.clearAll();
    fireModelChangeEvent();
  }

  public void processHistoryAndScreenLines(int scrollOrigin, StyledTextConsumer consumer) {
    int linesFromHistory = Math.min(-scrollOrigin, myHeight);
    myHistoryBuffer.processLines(myHistoryBuffer.getLineCount() + scrollOrigin, linesFromHistory, consumer, myHistoryBuffer.getLineCount() + scrollOrigin);
    if (myHeight - linesFromHistory + 1 > 0) {
      myScreenBuffer.processLines(0, myHeight - linesFromHistory, consumer, -linesFromHistory);
    }
  }
}
