package com.jediterm.emulator.display;

import com.google.common.collect.Lists;
import com.jediterm.emulator.StyledTextConsumer;
import com.jediterm.emulator.TextStyle;
import org.apache.log4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Holds characters data.
 */
public class LinesBuffer implements StyledTextConsumer {
  private static final Logger logger = Logger.getLogger(LinesBuffer.class);

  private static final int BUFFER_MAX_SIZE = 10000;

  private int myTotalLines = 0;
  private Deque<TextEntry> myTextEntries = new ArrayDeque<TextEntry>();

  public LinesBuffer() {
  }

  public synchronized String getLines() {
    final StringBuilder sb = new StringBuilder();

    for (TextEntry textEntry : myTextEntries) {
      if (textEntry.isNewLine()) {
        sb.append("\n");
      }
      sb.append(textEntry.getText());
    }

    return sb.toString();
  }

  @Override
  public void consume(int x, int y, TextStyle style, CharBuffer characters) {
    addToBuffer(style, characters, x == 0);
  }

  public synchronized void addToBufferFirst(TextStyle style, CharBuffer characters, boolean isNewLine) {
    myTextEntries.addFirst(new TextEntry(isNewLine, style, characters));

    if (isNewLine) {
      myTotalLines++;
    }
  }

  public synchronized void addToBuffer(TextStyle style, CharBuffer characters, boolean isNewLine) {
    if (myTextEntries.size() > BUFFER_MAX_SIZE) {
      removeTopLines(1);
    }
    myTextEntries.add(new TextEntry(isNewLine, style, characters));

    if (isNewLine) {
      myTotalLines++;
    }
  }

  public int getLineCount() {
    return myTotalLines;
  }

  public void removeTopLines(int count) {
    removeLines(0, count);
  }

  public void removeLines(int from, int to) {
    iterateLines(from, to, new TextEntryProcessor() {
      @Override
      public boolean process(int x, int y, TextEntry entry) {
        return true;
      }
    });
  }


  interface TextEntryProcessor {
    /**
     * @return true to remove entry
     */
    boolean process(int x, int y, TextEntry entry);
  }

  public synchronized void processLines(final int firstLine, final int count, final StyledTextConsumer consumer) {
    final int lines = myTotalLines;
    iterateLines(myTotalLines + firstLine, count, new TextEntryProcessor() {
      @Override
      public boolean process(int x, int y, TextEntry entry) {
        consumer.consume(x, y - lines, entry.getStyle(), entry.getText());
        return false;
      }
    });
  }

  public synchronized void iterateLines(final int firstLine, final int count, TextEntryProcessor processor) {
    int y = -1;
    int x = 0;
    Iterator<TextEntry> it = myTextEntries.iterator();
    while (it.hasNext()) {
      TextEntry textEntry = it.next();

      if (textEntry.isNewLine()) {
        y++;
        x = 0;
      }

      if (y >= firstLine + count) {
        break;
      }

      if (y >= firstLine) {
        if (processor.process(x, y, textEntry)) {
          it.remove();
          if (textEntry.isNewLine()) {
            myTotalLines--;
          }
        }
      }

      x += textEntry.getText().length();
    }
  }


  public void moveTopLinesTo(final int count, final LinesBuffer buffer) {
    iterateLines(0, count, new TextEntryProcessor() {
      @Override
      public boolean process(int x, int y, TextEntry entry) {
        buffer.consume(x, y, entry.getStyle(), entry.getText());
        return true;
      }
    });
  }

  private static class TextEntryWithPosition {
    int x;
    int y;
    TextEntry myEntry;

    private TextEntryWithPosition(int x, int y, TextEntry entry) {
      this.x = x;
      this.y = y;
      myEntry = entry;
    }
  }

  public void moveBottomLinesTo(int count, final LinesBuffer buffer) {
    final List<TextEntryWithPosition> entries = Lists.newArrayList();
    iterateLines(myTotalLines - count, count, new TextEntryProcessor() {
      @Override
      public boolean process(int x, int y, TextEntry entry) {
        entries.add(new TextEntryWithPosition(x, y, entry));
        return true;
      }
    });

    for (TextEntryWithPosition entry : Lists.reverse(entries)) {
      buffer.addToBufferFirst(entry.myEntry.getStyle(), entry.myEntry.getText(), entry.x == 0);
    }
  }

  private static class TextEntry {
    private final boolean myNewLine;
    private final TextStyle myStyle;
    private final CharBuffer myText;

    private TextEntry(boolean isNewLine, TextStyle style, CharBuffer text) {
      myNewLine = isNewLine;
      myStyle = style;
      myText = text.clone();
    }

    private boolean isNewLine() {
      return myNewLine;
    }

    private TextStyle getStyle() {
      return myStyle;
    }

    private CharBuffer getText() {
      return myText;
    }
  }
}
