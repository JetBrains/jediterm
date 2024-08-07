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

  private final LinesStorage myLines;

  @Nullable
  private final TextProcessing myTextProcessing;

  public LinesBuffer(@Nullable TextProcessing textProcessing) {
    this(-1, textProcessing);
  }

  public LinesBuffer(int bufferMaxLinesCount, @Nullable TextProcessing textProcessing) {
    myLines = new CyclicBufferLinesStorage(bufferMaxLinesCount);
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

  void addLine(@NotNull TerminalLine line) {
    myLines.addToBottom(line);
  }

  public int getLineCount() {
    return myLines.getSize();
  }

  public void removeTopLines(int count) {
    LinesStorageKt.removeFromTop(myLines, count);
  }

  public String getLineText(int row) {
    TerminalLine line = getLine(row);

    return line.getText();
  }

  public void insertLines(int y, int count, int lastLine, @NotNull TextEntry filler) {
    int tailLinesCount = getLineCount() - lastLine - 1;
    List<TerminalLine> tail = tailLinesCount > 0 ? LinesStorageKt.removeFromBottom(myLines, tailLinesCount) : List.of();

    List<TerminalLine> head = y > 0 ? LinesStorageKt.removeFromTop(myLines, y) : List.of();

    for (int i = 0; i < count; i++) {
      myLines.addToTop(new TerminalLine(filler));
    }
    LinesStorageKt.addAllToTop(myLines, head);
    LinesStorageKt.removeFromBottom(myLines, count);
    LinesStorageKt.addAllToBottom(myLines, tail);
  }

  public @NotNull List<TerminalLine> deleteLines(int y, int count, int lastLine, @NotNull TextEntry filler) {
    int tailLinesCount = getLineCount() - lastLine - 1;
    List<TerminalLine> tail = tailLinesCount > 0 ? LinesStorageKt.removeFromBottom(myLines, tailLinesCount) : List.of();

    List<TerminalLine> head = y > 0 ? LinesStorageKt.removeFromTop(myLines, y) : List.of();

    List<TerminalLine> removed = LinesStorageKt.removeFromTop(myLines, count);
    LinesStorageKt.addAllToTop(myLines, head);

    for (int i = 0; i < removed.size(); i++) {
      addNewLine(filler);
    }

    LinesStorageKt.addAllToBottom(myLines, tail);
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
    for (int y = firstLine; y < Math.min(firstLine + count, myLines.getSize()); y++) {
      myLines.get(y).process(y, consumer, startRow);
    }
  }

  int moveTopLinesTo(int count, final @NotNull LinesBuffer buffer) {
    if (count < 0) {
      throw new AssertionError("Moving negative line count: " + count);
    }
    List<TerminalLine> lines = LinesStorageKt.removeFromTop(myLines, count);
    buffer.addLines(lines);
    return lines.size();
  }

  public void addLines(@NotNull List<TerminalLine> lines) {
    LinesStorageKt.addAllToBottom(myLines, lines);
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
    List<TerminalLine> lines = LinesStorageKt.removeFromBottom(myLines, count);
    buffer.addLinesFirst(lines);
  }

  private void addLinesFirst(@NotNull List<TerminalLine> lines) {
    LinesStorageKt.addAllToTop(myLines, lines);
  }

  private void removeBottomLines(int count) {
    LinesStorageKt.removeFromBottom(myLines, count);
  }

  public int removeBottomEmptyLines(int maxCount) {
    int removedCount = 0;
    int ind = getLineCount() - 1;
    while (removedCount < maxCount && ind >= 0 && myLines.get(ind).isNulOrEmpty()) {
      ind--;
      removedCount++;
    }
    LinesStorageKt.removeFromBottom(myLines, removedCount);
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
