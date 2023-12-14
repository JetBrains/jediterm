package com.jediterm.terminal.model;

import com.jediterm.terminal.StyledTextConsumer;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.TerminalLine.TextEntry;
import com.jediterm.terminal.model.hyperlinks.TextProcessing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds styled characters lines
 */
public class LinesBuffer {
  private static final Logger LOG = LoggerFactory.getLogger(LinesBuffer.class);

  public static final int DEFAULT_MAX_LINES_COUNT = 5000;

  // negative number means no limit
  private int myBufferMaxLinesCount = DEFAULT_MAX_LINES_COUNT;

  private ArrayList<TerminalLine> myLines = new ArrayList<>();

  @Nullable
  private final TextProcessing myTextProcessing;

  public LinesBuffer(@Nullable TextProcessing textProcessing) {
    myTextProcessing = textProcessing;
  }

  public LinesBuffer(int bufferMaxLinesCount, @Nullable TextProcessing textProcessing) {
    myBufferMaxLinesCount = bufferMaxLinesCount;
    myTextProcessing = textProcessing;
  }

  public String getLines() {
    final StringBuilder sb = new StringBuilder();

    boolean first = true;

    for (TerminalLine line : myLines) {
      if (!first) {
        sb.append("\n");
      }

      sb.append(line.getText());
      first = false;
    }

    return sb.toString();
  }

  public @NotNull List<String> getLineTexts() {
    return getLineTexts(0, getLineCount());
  }

  @SuppressWarnings("SameParameterValue")
  @NotNull List<String> getLineTexts(int from, int to) {
    List<String> lines = new ArrayList<>();
    for (int i = from; i < Math.min(to, getLineCount()); i++) {
      lines.add(myLines.get(i).getText());
    }
    return lines;
  }

  public void addNewLine(@NotNull TextStyle style, @NotNull CharBuffer characters) {
    addNewLine(new TerminalLine.TextEntry(style, characters));
  }


  private void addNewLine(@NotNull TerminalLine.TextEntry entry) {
    addLine(new TerminalLine(entry));
  }

  private void addLine(@NotNull TerminalLine line) {
    if (myBufferMaxLinesCount > 0 && myLines.size() >= myBufferMaxLinesCount) {
      removeTopLines(1);
    }

    myLines.add(line);
  }

  public int getLineCount() {
    return myLines.size();
  }

  public void removeTopLines(int count) {
    if (count >= myLines.size()) { // remove all lines
      myLines = new ArrayList<>();
    } else {
      myLines = new ArrayList<>(myLines.subList(count, myLines.size()));
    }
  }

  public String getLineText(int row) {
    TerminalLine line = getLine(row);

    return line.getText();
  }

  public void insertLines(int y, int count, int lastLine, @NotNull TextEntry filler) {
    LinesBuffer tail = new LinesBuffer(myTextProcessing);

    if (lastLine < getLineCount() - 1) {
      moveBottomLinesTo(getLineCount() - lastLine - 1, tail);
    }

    LinesBuffer head = new LinesBuffer(myTextProcessing);
    if (y > 0) {
      moveTopLinesTo(y, head);
    }

    for (int i = 0; i < count; i++) {
      head.addNewLine(filler);
    }

    head.moveBottomLinesTo(head.getLineCount(), this);

    removeBottomLines(count);

    tail.moveTopLinesTo(tail.getLineCount(), this);
  }

  public LinesBuffer deleteLines(int y, int count, int lastLine, @NotNull TextEntry filler) {
    LinesBuffer tail = new LinesBuffer(myTextProcessing);

    if (lastLine < getLineCount() - 1) {
      moveBottomLinesTo(getLineCount() - lastLine - 1, tail);
    }

    LinesBuffer head = new LinesBuffer(myTextProcessing);
    if (y > 0) {
      moveTopLinesTo(y, head);
    }

    int toRemove = Math.min(count, getLineCount());

    LinesBuffer removed = new LinesBuffer(myTextProcessing);
    moveTopLinesTo(toRemove, removed);

    head.moveBottomLinesTo(head.getLineCount(), this);

    for (int i = 0; i < toRemove; i++) {
      addNewLine(filler);
    }

    tail.moveTopLinesTo(tail.getLineCount(), this);

    return removed;
  }

  public void writeString(int x, int y, CharBuffer str, @NotNull TextStyle style) {
    TerminalLine line = getLine(y);

    line.writeString(x, str, style);

    if (myTextProcessing != null) {
      myTextProcessing.processHyperlinks(this, line);
    }
  }

  public void clearLines(int startRow, int endRow, @NotNull TextEntry filler) {
    for (int i = startRow; i <= endRow; i++) {
      getLine(i).clear(filler);
    }
  }

  // used for reset, style not needed here (reset as well)
  public void clearAll() {
    myLines.clear();
  }

  public void deleteCharacters(int x, int y, int count, @NotNull TextStyle style) {
    TerminalLine line = getLine(y);
    line.deleteCharacters(x, count, style);
  }

  public void insertBlankCharacters(final int x, final int y, final int count, final int maxLen, @NotNull TextStyle style) {
    TerminalLine line = getLine(y);
    line.insertBlankCharacters(x, count, maxLen, style);
  }

  public void clearArea(int leftX, int topY, int rightX, int bottomY, @NotNull TextStyle style) {
    for (int y = topY; y < bottomY; y++) {
      TerminalLine line = getLine(y);
      line.clearArea(leftX, rightX, style);
    }
  }

  public void processLines(final int yStart, final int yCount, @NotNull final StyledTextConsumer consumer) {
    processLines(yStart, yCount, consumer, -getLineCount());
  }

  public void processLines(final int firstLine,
                           final int count,
                           @NotNull final StyledTextConsumer consumer,
                           final int startRow) {
    if (firstLine < 0) {
      throw new IllegalArgumentException("firstLine=" + firstLine + ", should be >0");
    }
    for (int y = firstLine; y < Math.min(firstLine + count, myLines.size()); y++) {
      myLines.get(y).process(y, consumer, startRow);
    }
  }

  int moveTopLinesTo(int count, final @NotNull LinesBuffer buffer) {
    if (count < 0) {
      throw new AssertionError("Moving negative line count: " + count);
    }
    count = Math.min(count, getLineCount());
    buffer.addLines(myLines.subList(0, count));
    removeTopLines(count);
    return count;
  }

  public void addLines(@NotNull List<TerminalLine> lines) {
    if (myBufferMaxLinesCount > 0) {
      // adding more lines than max size
      if (lines.size() >= myBufferMaxLinesCount) {
        int index = lines.size() - myBufferMaxLinesCount;
        myLines = new ArrayList<>(lines.subList(index, lines.size()));
        return;
      }

      int count = myLines.size() + lines.size();
      if (count >= myBufferMaxLinesCount) {
        removeTopLines(count - myBufferMaxLinesCount);
      }
    }

    myLines.addAll(lines);
  }

  public @NotNull TerminalLine getLine(int row) {
    if (row < 0) {
      LOG.error("Negative line number: " + row);
      return TerminalLine.createEmpty();
    }

    for (int i = getLineCount(); i <= row; i++) {
      addLine(TerminalLine.createEmpty());
    }

    return myLines.get(row);
  }

  public void moveBottomLinesTo(int count, @NotNull LinesBuffer buffer) {
    count = Math.min(count, getLineCount());
    buffer.addLinesFirst(myLines.subList(getLineCount() - count, getLineCount()));

    removeBottomLines(count);
  }

  private void addLinesFirst(@NotNull List<TerminalLine> lines) {
    List<TerminalLine> list = new ArrayList<>(lines);
    list.addAll(myLines);
    myLines = new ArrayList<>(list);
  }

  private void removeBottomLines(int count) {
    myLines = new ArrayList<>(myLines.subList(0, getLineCount() - count));
  }

  public int removeBottomEmptyLines(int bottomMostLineInd, int maxCount) {
    int removedCount = 0;
    int ind = bottomMostLineInd;
    while (removedCount < maxCount && ind >= 0 && (ind >= myLines.size() || myLines.get(ind).isNulOrEmpty())) {
      if (ind < myLines.size()) {
        myLines.remove(ind);
      }
      ind--;
      removedCount++;
    }
    return removedCount;
  }

  int findLineIndex(@NotNull TerminalLine line) {
    return myLines.indexOf(line);
  }

  public void clearTypeAheadPredictions() {
    for (TerminalLine line : myLines) {
      line.myTypeAheadLine = null;
    }
  }
}
