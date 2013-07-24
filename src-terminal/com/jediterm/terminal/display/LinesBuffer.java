package com.jediterm.terminal.display;

import com.google.common.collect.Lists;
import com.jediterm.terminal.StyledTextConsumer;
import com.jediterm.terminal.TextStyle;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Holds styled characters lines
 */
public class LinesBuffer {
  private static final Logger LOG = Logger.getLogger(LinesBuffer.class);

  private static final int BUFFER_MAX_LINES = 1000;

  private ArrayList<TerminalLine> myLines = Lists.newArrayList();

  public LinesBuffer() {
  }

  public synchronized String getLines() {
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


  public synchronized void addNewLine(@NotNull TextStyle style, @NotNull CharBuffer characters) {
    addNewLine(new TerminalLine.TextEntry(style, characters));
  }


  private synchronized void addNewLine(@NotNull TerminalLine.TextEntry entry) {
    addLine(new TerminalLine(entry));
  }

  private synchronized void addLine(@NotNull TerminalLine line) {
    if (myLines.size() > BUFFER_MAX_LINES) {
      removeTopLines(1);
    }

    myLines.add(line);
  }

  public synchronized int getLineCount() {
    return myLines.size();
  }

  public synchronized void removeTopLines(int count) {
    myLines = Lists.newArrayList(myLines.subList(count, myLines.size()));
  }

  public String getLineText(int row) {
    TerminalLine line = getLine(row);

    return line.getText();
  }

  public synchronized void insertLines(int y, int count, int lastLine) {
    LinesBuffer tail = new LinesBuffer();

    if (lastLine < getLineCount() - 1) {
      moveBottomLinesTo(getLineCount() - lastLine - 1, tail);
    }

    LinesBuffer head = new LinesBuffer();
    if (y > 0) {
      moveTopLinesTo(y, head);
    }

    for (int i = 0; i < count; i++) {
      head.addNewLine(TextStyle.EMPTY, CharBuffer.EMPTY);
    }

    head.moveBottomLinesTo(head.getLineCount(), this);

    removeBottomLines(count);

    tail.moveTopLinesTo(tail.getLineCount(), this);
  }

  public synchronized LinesBuffer deleteLines(int y, int count, int lastLine) {
    LinesBuffer tail = new LinesBuffer();

    if (lastLine < getLineCount() - 1) {
      moveBottomLinesTo(getLineCount() - lastLine - 1, tail);
    }

    LinesBuffer head = new LinesBuffer();
    if (y > 0) {
      moveTopLinesTo(y, head);
    }

    int toRemove = Math.min(count, getLineCount());

    LinesBuffer removed = new LinesBuffer();
    moveTopLinesTo(toRemove, removed);

    head.moveBottomLinesTo(head.getLineCount(), this);

    for (int i = 0; i < toRemove; i++) {
      addNewLine(TextStyle.EMPTY, CharBuffer.EMPTY);
    }

    tail.moveTopLinesTo(tail.getLineCount(), this);

    return removed;
  }

  public synchronized void writeString(int x, int y, String str, @NotNull TextStyle style) {
    TerminalLine line = getLine(y);

    line.writeString(x, str, style);
  }

  public synchronized void clearLines(int startRow, int endRow) {
    for (int i = startRow; i <= endRow; i++) {
      if (i < myLines.size()) {
        TerminalLine line = myLines.get(i);
        line.clear();
      }
      else {
        break;
      }
    }
  }

  public synchronized void clearAll() {
    myLines.clear();
  }

  public synchronized void deleteCharacters(int x, int y, int count) {
    TerminalLine line = getLine(y);
    line.deleteCharacters(x, count);
  }

  public synchronized void insertBlankCharacters(final int x, final int y, final int count, final int maxLen) {
    TerminalLine line = getLine(y);
    line.insertBlankCharacters(x, count, maxLen);
  }

  public synchronized void clearArea(int leftX, int topY, int rightX, int bottomY, @NotNull TextStyle style) {
    for (int y = topY; y < bottomY; y++) {
      TerminalLine line = getLine(y);
      line.clearArea(leftX, rightX, style);
    }
  }


  interface TextEntryProcessor {
    /**
     * @return true to remove entry
     */
    void process(int x, int y, @NotNull TerminalLine.TextEntry entry);
  }

  public synchronized void processLines(final int yStart,
                                        final int yCount,
                                        @NotNull final StyledTextConsumer consumer,
                                        final int startRow) {
    iterateLines(yStart, yCount, new TextEntryProcessor() {
      @Override
      public void process(int x, int y, @NotNull TerminalLine.TextEntry entry) {
        consumer.consume(x, y, entry.getStyle(), entry.getText(), startRow);
      }
    }, startRow);
  }

  public synchronized void processLines(final int yStart, final int yCount, @NotNull final StyledTextConsumer consumer) {
    processLines(yStart, yCount, consumer, -getLineCount());
  }

  public synchronized void iterateLines(final int firstLine, final int count, @NotNull TextEntryProcessor processor, int startRow) {
    int y = startRow-1;

    int x;

    for (TerminalLine line : myLines) {
      y++;
      if (y < firstLine) {
        continue;
      }

      x = 0;

      Iterator<TerminalLine.TextEntry> it = line.entriesIterator();

      while (it.hasNext()) {
        TerminalLine.TextEntry textEntry = it.next();

        if (y >= firstLine + count) {
          break;
        }

        processor.process(x, y, textEntry);

        x += textEntry.getText().length();
      }
    }
  }

  public synchronized void moveTopLinesTo(int count, final @NotNull LinesBuffer buffer) {
    count = Math.min(count, getLineCount());
    buffer.addLines(myLines.subList(0, count));
    removeTopLines(count);
  }

  public synchronized void addLines(@NotNull List<TerminalLine> lines) {
    myLines.addAll(lines);
  }

  @NotNull
  public synchronized TerminalLine getLine(int row) {
    if (row >= getLineCount()) {
      for (int i = getLineCount(); i <= row; i++) {
        addLine(TerminalLine.createEmpty());
      }
    }

    return myLines.get(row);
  }

  public synchronized void moveBottomLinesTo(int count, final @NotNull LinesBuffer buffer) {
    count = Math.min(count, getLineCount());
    buffer.addLinesFirst(myLines.subList(getLineCount() - count, getLineCount()));

    removeBottomLines(count);
  }

  private synchronized void addLinesFirst(@NotNull List<TerminalLine> lines) {
    List<TerminalLine> list = Lists.newArrayList(lines);
    list.addAll(myLines);
    myLines = Lists.newArrayList(list);
  }

  private synchronized void removeBottomLines(int count) {
    myLines = Lists.newArrayList(myLines.subList(0, getLineCount() - count));
  }
}
