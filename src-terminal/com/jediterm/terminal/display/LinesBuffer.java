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


  private void addNewLine(@NotNull TerminalLine.TextEntry entry) {
    addLine(new TerminalLine(entry));
  }

  private void addLine(@NotNull TerminalLine line) {
    if (myLines.size() > BUFFER_MAX_LINES) {
      removeTopLines(1);
    }

    myLines.add(line);
  }

  public int getLineCount() {
    return myLines.size();
  }

  public void removeTopLines(int count) {
    myLines = Lists.newArrayList(myLines.subList(count, myLines.size()));
  }

  public String getLineText(int row) {
    TerminalLine line = getLine(row);
    if (line != null) {
      return line.getText();
    }
    else {
      return "";
    }
  }

  public void insertLines(int y, int num) {
    LinesBuffer head = new LinesBuffer();
    if (y > 0) {
      moveTopLinesTo(y - 1, head);
    }
    for (int i = 0; i < num; i++) {
      head.addNewLine(TextStyle.EMPTY, CharBuffer.EMPTY);
    }

    head.moveBottomLinesTo(head.getLineCount(), this);
  }

  public void writeString(int x, int y, String str, @NotNull TextStyle style) {
    if (y >= getLineCount()) {
      for (int i = getLineCount(); i <= y; i++) {
        addLine(TerminalLine.createEmpty());
      }
    }

    TerminalLine line = getLine(y);

    line.writeString(x, str, style);
  }

  public void clearLines(int startRow, int endRow) {
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

  interface TextEntryProcessor {
    /**
     * @return true to remove entry
     */
    void process(int x, int y, @NotNull TerminalLine.TextEntry entry);
  }

  public synchronized void processLines(final int firstLine, final int count, @NotNull final StyledTextConsumer consumer) {
    final int linesShift = getLineCount();
    iterateLines(getLineCount() + firstLine, count, new TextEntryProcessor() {
      @Override
      public void process(int x, int y, @NotNull TerminalLine.TextEntry entry) {
        consumer.consume(x, y - linesShift, entry.getStyle(), entry.getText(), -getLineCount()); //  TODO: first line as a parameter
      }
    });
  }

  public synchronized void iterateLines(final int firstLine, final int count, @NotNull TextEntryProcessor processor) {
    int y = -1;

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

  public void moveTopLinesTo(int count, final @NotNull LinesBuffer buffer) {
    count = Math.min(count, getLineCount());
    buffer.addLines(myLines.subList(0, count));
    removeTopLines(count);
  }

  public void addLines(@NotNull List<TerminalLine> lines) {
    myLines.addAll(lines);
  }

  public TerminalLine getLine(int row) {
    return myLines.get(row);
  }

  public void moveBottomLinesTo(int count, final @NotNull LinesBuffer buffer) {
    count = Math.min(count, getLineCount());
    buffer.addLinesFirst(myLines.subList(getLineCount() - count, getLineCount()));

    removeBottomLines(count);
  }

  private void addLinesFirst(@NotNull List<TerminalLine> lines) {
    List<TerminalLine> list = Lists.newArrayList(lines);
    list.addAll(myLines);
    myLines = Lists.newArrayList(list);
  }

  private void removeBottomLines(int count) {
    myLines = Lists.newArrayList(myLines.subList(0, getLineCount() - count));
  }
}
