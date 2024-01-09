package com.jediterm.terminal.model;

import com.jediterm.core.compatibility.Point;
import com.jediterm.core.util.CellPosition;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.StyledTextConsumer;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.TerminalLine.TextEntry;
import com.jediterm.terminal.model.hyperlinks.TextProcessing;
import com.jediterm.terminal.util.CharUtils;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Buffer for storing styled text data.
 * Stores only text that fit into one screen XxY, but has scrollBuffer to save history lines and screenBuffer to restore
 * screen after resize. ScrollBuffer stores all lines before the first line currently shown on the screen. TextBuffer
 * stores lines that are shown currently on the screen and they have there(in TextBuffer) their initial length (even if
 * it doesn't fit to screen width).
 * <p/>
 */
public class TerminalTextBuffer {
  private static final Logger LOG = LoggerFactory.getLogger(TerminalTextBuffer.class);
  private static final Boolean USE_CONPTY_COMPATIBLE_RESIZE = true;

  @NotNull
  private final StyleState myStyleState;

  private LinesBuffer myHistoryBuffer;

  private LinesBuffer myScreenBuffer;

  private int myWidth;
  private int myHeight;

  private final int myHistoryLinesCount;

  private final Lock myLock = new ReentrantLock();

  private LinesBuffer myHistoryBufferBackup;
  private LinesBuffer myScreenBufferBackup; // to store textBuffer after switching to alternate buffer

  private boolean myAlternateBuffer = false;

  private boolean myUsingAlternateBuffer = false;

  private final List<TerminalModelListener> myListeners = new CopyOnWriteArrayList<>();
  private final List<TerminalModelListener> myTypeAheadListeners = new CopyOnWriteArrayList<>();
  private final List<TerminalHistoryBufferListener> myHistoryBufferListeners = new CopyOnWriteArrayList<>();

  @Nullable
  private final TextProcessing myTextProcessing;

  public TerminalTextBuffer(final int width, final int height, @NotNull StyleState styleState) {
    this(width, height, styleState, null);
  }

  public TerminalTextBuffer(final int width, final int height, @NotNull StyleState styleState, @Nullable TextProcessing textProcessing) {
    this(width, height, styleState, LinesBuffer.DEFAULT_MAX_LINES_COUNT, textProcessing);
  }

  public TerminalTextBuffer(final int width, final int height, @NotNull StyleState styleState, final int historyLinesCount, @Nullable TextProcessing textProcessing) {
    myStyleState = styleState;
    myWidth = width;
    myHeight = height;
    myHistoryLinesCount = historyLinesCount;
    myTextProcessing = textProcessing;

    myScreenBuffer = createScreenBuffer();
    myHistoryBuffer = createHistoryBuffer();
  }

  @NotNull
  private LinesBuffer createScreenBuffer() {
    return new LinesBuffer(-1, myTextProcessing);
  }

  @NotNull
  private LinesBuffer createHistoryBuffer() {
    return new LinesBuffer(myHistoryLinesCount, myTextProcessing);
  }

  @NotNull TerminalResizeResult resize(@NotNull TermSize newTermSize,
                                       @NotNull CellPosition oldCursor,
                                       @Nullable TerminalSelection selection) {
    final int newWidth = newTermSize.getColumns();
    final int newHeight = newTermSize.getRows();
    int newCursorX = oldCursor.getX();
    int newCursorY = oldCursor.getY();
    final int oldCursorY = oldCursor.getY();

    if (myWidth != newWidth) {
      ChangeWidthOperation changeWidthOperation = new ChangeWidthOperation(this, newWidth, newHeight);
      Point cursorPoint = new Point(oldCursor.getX() - 1, oldCursor.getY() - 1);
      changeWidthOperation.addPointToTrack(cursorPoint, true);
      if (selection != null) {
        changeWidthOperation.addPointToTrack(selection.getStart(), false);
        changeWidthOperation.addPointToTrack(selection.getEnd(), false);
      }
      changeWidthOperation.run();
      myWidth = newWidth;
      myHeight = newHeight;
      Point newCursor = changeWidthOperation.getTrackedPoint(cursorPoint);
      newCursorX = newCursor.x + 1;
      newCursorY = newCursor.y + 1;
      if (selection != null) {
        selection.getStart().setLocation(changeWidthOperation.getTrackedPoint(selection.getStart()));
        selection.getEnd().setLocation(changeWidthOperation.getTrackedPoint(selection.getEnd()));
      }
    }

    final int oldHeight = myHeight;
    if (newHeight < oldHeight) {
      if (!myAlternateBuffer) {
        int lineDiffCount = oldHeight - newHeight;
        //we need to move lines from text buffer to the scroll buffer
        //but empty bottom lines up to the cursor can be collapsed
        int maxBottomLinesToRemove = Math.min(lineDiffCount, Math.max(0, oldHeight - oldCursorY));
        int emptyLinesDeleted = myScreenBuffer.removeBottomEmptyLines(oldHeight - 1, maxBottomLinesToRemove);
        int screenLinesToMove = lineDiffCount - emptyLinesDeleted;
        myScreenBuffer.moveTopLinesTo(screenLinesToMove, myHistoryBuffer);
        newCursorY = oldCursorY - screenLinesToMove;
        if (selection != null) {
          selection.shiftY(-screenLinesToMove);
        }
      }
      else {
        newCursorY = oldCursorY;
      }
    } else if (newHeight > oldHeight) {
      if (USE_CONPTY_COMPATIBLE_RESIZE) {
        // do not move lines from scroll buffer to the screen buffer
        newCursorY = oldCursorY;
      }
      else {
        if (!myAlternateBuffer) {
          //we need to move lines from scroll buffer to the text buffer
          int historyLinesCount = Math.min(newHeight - oldHeight, myHistoryBuffer.getLineCount());
          myHistoryBuffer.moveBottomLinesTo(historyLinesCount, myScreenBuffer);
          newCursorY = oldCursorY + historyLinesCount;
          if (selection != null) {
            selection.shiftY(historyLinesCount);
          }
        } else {
          newCursorY = oldCursorY;
        }
      }
    }

    myWidth = newWidth;
    myHeight = newHeight;

    fireModelChangeEvent();
    return new TerminalResizeResult(new CellPosition(newCursorX, newCursorY));
  }

  public void addModelListener(TerminalModelListener listener) {
    myListeners.add(listener);
  }

  public void removeModelListener(TerminalModelListener listener) {
    myListeners.remove(listener);
  }

  public void addHistoryBufferListener(@NotNull TerminalHistoryBufferListener listener) {
    myHistoryBufferListeners.add(listener);
  }

  public void removeHistoryBufferListener(@NotNull TerminalHistoryBufferListener listener) {
    myHistoryBufferListeners.remove(listener);
  }

  public void addTypeAheadModelListener(TerminalModelListener listener) {
    myTypeAheadListeners.add(listener);
  }

  public void removeTypeAheadModelListener(TerminalModelListener listener) {
    myTypeAheadListeners.remove(listener);
  }

  void fireModelChangeEvent() {
    for (TerminalModelListener modelListener : myListeners) {
      modelListener.modelChanged();
    }
  }

  void fireTypeAheadModelChangeEvent() {
    for (TerminalModelListener modelListener : myTypeAheadListeners) {
      modelListener.modelChanged();
    }
  }

  private TextStyle createEmptyStyleWithCurrentColor() {
    return myStyleState.getCurrent().createEmptyWithColors();
  }

  private TextEntry createFillerEntry() {
    return new TextEntry(createEmptyStyleWithCurrentColor(), new CharBuffer(CharUtils.NUL_CHAR, myWidth));
  }

  public void deleteCharacters(final int x, final int y, final int count) {
    if (y > myHeight - 1 || y < 0) {
      LOG.error("attempt to delete in line " + y + "\n" +
              "args were x:" + x + " count:" + count);
    } else if (count < 0) {
      LOG.error("Attempt to delete negative chars number: count:" + count);
    } else if (count > 0) {
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

  public void writeString(final int x, final int y, @NotNull final CharBuffer str) {
    writeString(x, y, str, myStyleState.getCurrent());
  }

  public void addLine(@NotNull final TerminalLine line) {
    myScreenBuffer.addLines(List.of(line));

    fireModelChangeEvent();
  }

  private void writeString(int x, int y, @NotNull CharBuffer str, @NotNull TextStyle style) {
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

  /**
   * Returns terminal lines. Negative indexes are for history buffer. Non-negative for screen buffer.
   *
   * @param index index of line
   * @return history lines for index<0, screen line for index>=0
   */
  public @NotNull TerminalLine getLine(int index) {
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

  public void modify(@NotNull Runnable runnable) {
    myLock.lock();
    try {
      runnable.run();
    }
    finally {
      myLock.unlock();
    }
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

  public @NotNull Pair<Character, TextStyle> getStyledCharAt(int x, int y) {
    TerminalLine line = getLine(y);
    return new Pair<>(line.charAt(x), line.getStyleAt(x));
  }

  public char getCharAt(int x, int y) {
    return getLine(y).charAt(x);
  }

  public boolean isUsingAlternateBuffer() {
    return myUsingAlternateBuffer;
  }

  public void useAlternateBuffer(boolean enabled) {
    myAlternateBuffer = enabled;
    if (enabled) {
      if (!myUsingAlternateBuffer) {
        myScreenBufferBackup = myScreenBuffer;
        myHistoryBufferBackup = myHistoryBuffer;
        myScreenBuffer = createScreenBuffer();
        myHistoryBuffer = createHistoryBuffer();
        myUsingAlternateBuffer = true;
      }
    } else {
      if (myUsingAlternateBuffer) {
        myScreenBuffer = myScreenBufferBackup;
        myHistoryBuffer = myHistoryBufferBackup;
        myScreenBufferBackup = createScreenBuffer();
        myHistoryBufferBackup = createHistoryBuffer();
        myUsingAlternateBuffer = false;
      }
    }
    fireModelChangeEvent();
  }

  public LinesBuffer getHistoryBuffer() {
    return myHistoryBuffer;
  }

  public @NotNull LinesBuffer getScreenBuffer() {
    return myScreenBuffer;
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
      if (myTextProcessing != null && y < getHeight()) {
        myTextProcessing.processHyperlinks(myScreenBuffer, getLine(y));
      }
    } else {
      LOG.error("Attempt to erase characters in line: " + y);
    }
  }

  public void clearScreenAndHistoryBuffers() {
    myScreenBuffer.clearAll();
    myHistoryBuffer.clearAll();
    fireModelChangeEvent();
  }

  public void clearScreenBuffer() {
    myScreenBuffer.clearAll();
    fireModelChangeEvent();
  }

  /**
   * @deprecated use {@link #clearScreenAndHistoryBuffers()} instead
   */
  @Deprecated
  public void clearAll() {
    myScreenBuffer.clearAll();
    fireModelChangeEvent();
  }

  /**
   * @param scrollOrigin row where a scrolling window starts, should be in the range [-history_lines_count, 0]
   */
  public void processHistoryAndScreenLines(int scrollOrigin, int maximalLinesToProcess, StyledTextConsumer consumer) {
    if (maximalLinesToProcess<0) {
      //Process all lines in this case
      
      maximalLinesToProcess = myHistoryBuffer.getLineCount() + myScreenBuffer.getLineCount();
    }

    int linesFromHistory = Math.min(-scrollOrigin, maximalLinesToProcess);

    int y = myHistoryBuffer.getLineCount() + scrollOrigin;
    if (y < 0) { // it seems that lower bound of scrolling can get out of sync with history buffer lines count
      y = 0; // to avoid exception we start with the first line in this case
    }
    myHistoryBuffer.processLines(y, linesFromHistory, consumer, y);

    if (linesFromHistory < maximalLinesToProcess) {
      // we can show lines from screen buffer
      myScreenBuffer.processLines(0, maximalLinesToProcess - linesFromHistory, consumer, -linesFromHistory);
    }
  }

  public void clearHistory() {
    modify(() -> {
      int lineCount = myHistoryBuffer.getLineCount();
      myHistoryBuffer.clearAll();
      if (lineCount > 0) {
        fireHistoryBufferLineCountChanged();
      }
    });
    fireModelChangeEvent();
  }

  void moveScreenLinesToHistory() {
    myLock.lock();
    try {
      myScreenBuffer.removeBottomEmptyLines(myScreenBuffer.getLineCount() - 1, myScreenBuffer.getLineCount());
      int movedToHistoryLineCount = myScreenBuffer.moveTopLinesTo(myScreenBuffer.getLineCount(), myHistoryBuffer);
      if (myHistoryBuffer.getLineCount() > 0) {
        myHistoryBuffer.getLine(myHistoryBuffer.getLineCount() - 1).setWrapped(false);
      }
      if (movedToHistoryLineCount > 0) {
        fireHistoryBufferLineCountChanged();
      }
    }
    finally {
      myLock.unlock();
    }
  }

  private void fireHistoryBufferLineCountChanged() {
    for (TerminalHistoryBufferListener historyBufferListener : myHistoryBufferListeners) {
      historyBufferListener.historyBufferLineCountChanged();
    }
  }

  @NotNull
  LinesBuffer getHistoryBufferOrBackup() {
    return myUsingAlternateBuffer ? myHistoryBufferBackup : myHistoryBuffer;
  }

  @NotNull
  LinesBuffer getScreenBufferOrBackup() {
    return myUsingAlternateBuffer ? myScreenBufferBackup : myScreenBuffer;
  }

  public int findScreenLineIndex(@NotNull TerminalLine line) {
    return myScreenBuffer.findLineIndex(line);
  }

  public void clearTypeAheadPredictions() {
    myScreenBuffer.clearTypeAheadPredictions();
    myHistoryBuffer.clearTypeAheadPredictions();
    fireModelChangeEvent();
  }

  @Nullable TextProcessing getTextProcessing() {
    return myTextProcessing;
  }
}
