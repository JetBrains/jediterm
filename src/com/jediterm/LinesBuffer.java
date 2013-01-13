package com.jediterm;

import java.util.Iterator;
import java.util.List;

import com.google.common.collect.Lists;
import org.apache.log4j.Logger;

/**
 * Holds characters data.
 */
public class LinesBuffer implements StyledTextConsumer {
  private static final Logger logger = Logger.getLogger(LinesBuffer.class);

  private int myTotalLines = 0;
  private List<TextEntry> myTextEntries = Lists.newArrayList();

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
    myTextEntries.add(0, new TextEntry(isNewLine, style, characters));

    if (isNewLine) {
      myTotalLines++;
    }
  }

  public synchronized void addToBuffer(TextStyle style, CharBuffer characters, boolean isNewLine) {
    myTextEntries.add(new TextEntry(isNewLine, style, characters));

    if (isNewLine) {
      myTotalLines++;
    }
  }

  public int getLineCount() {
    return myTotalLines;
  }


  interface TextEntryProcessor {
    /**
     * @return true to remove entry
     */
    boolean process(int x, int y, TextEntry entry);
  }

  public synchronized void processLines(final int firstLine, final int count, final StyledTextConsumer consumer) {
    iterateLines(myTotalLines + firstLine, count, new TextEntryProcessor() {
      @Override
      public boolean process(int x, int y, TextEntry entry) {
        consumer.consume(x, y-myTotalLines, entry.getStyle(), entry.getText());
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


  public void moveTopLinesTo(int count, final LinesBuffer buffer) {
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

    for (TextEntryWithPosition entry: Lists.reverse(entries)) {
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
