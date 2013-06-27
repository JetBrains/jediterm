package com.jediterm.terminal.display;

import com.jediterm.terminal.TextStyle;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * @author traff
 */
public class TerminalLine {
  private Deque<TextEntry> myTextEntries = new ArrayDeque<TextEntry>();

  private int myLength;

  public TerminalLine(@NotNull TextEntry entry) {
    myTextEntries.add(entry);
    myLength = entry.getLength();
  }

  public static TerminalLine createEmpty() {
    return new TerminalLine(TextEntry.EMPTY);
  }


  public String getText() {
    final StringBuilder sb = new StringBuilder();

    for (TerminalLine.TextEntry textEntry : myTextEntries) {
      sb.append(textEntry.getText());
    }

    return sb.toString();
  }

  public Iterator<TextEntry> entriesIterator() {
    return myTextEntries.iterator();
  }

  public void clear() {
    myTextEntries.clear();
    myLength = 0;
  }

  public void writeString(int x, @NotNull String str, @NotNull TextStyle style) {
    if (myTextEntries.size() == 1 && myTextEntries.getFirst().getLength() == 0) {
      myTextEntries.removeFirst(); //Remove empty element      
    }
    
    if (x >= myLength) {
      if (x - myLength > 0) {
        myTextEntries.add(new TextEntry(TextStyle.EMPTY, new CharBuffer(' ', x - myLength)));
      }
      myTextEntries.add(new TextEntry(style, new CharBuffer(str)));
      myLength = x + str.length();
    }
    else {
      myLength = Math.max(myLength, x + str.length());
      myTextEntries = merge(x, str, style, myTextEntries, myLength);
    }
  }

  private static Deque<TextEntry> merge(int x, String str, @NotNull TextStyle style, @NotNull Deque<TextEntry> entries, int lineLength) {
    char[] buf = new char[lineLength];
    TextStyle[] styles = new TextStyle[lineLength];

    int p = 0;
    for (TextEntry entry : entries) {
      for (int i = 0; i < entry.getLength(); i++) {
        buf[p + i] = entry.getText().charAt(i);
        styles[p + i] = entry.getStyle();
      }
      p += entry.getLength();
    }

    for (int i = 0; i < str.length(); i++) {
      buf[i + x] = str.charAt(i);
      styles[i + x] = style;
    }

    return collectFromBuffer(buf, styles);
  }

  private static Deque<TextEntry> collectFromBuffer(@NotNull char[] buf, @NotNull TextStyle[] styles) {
    Deque<TextEntry> result = new ArrayDeque<TextEntry>();

    TextStyle curStyle = styles[0];
    int start = 0;

    for (int i = 1; i < buf.length; i++) {
      if (styles[i] != curStyle) {
        result.add(new TextEntry(curStyle, new CharBuffer(buf, start, i - start)));
        curStyle = styles[i];
        start = i;
      }
    }

    result.add(new TextEntry(curStyle, new CharBuffer(buf, start, buf.length - start)));

    return result;
  }

  static class TextEntry {
    public static final TextEntry EMPTY = new TextEntry(TextStyle.EMPTY, CharBuffer.EMPTY);

    private final TextStyle myStyle;
    private final CharBuffer myText;

    public TextEntry(@NotNull TextStyle style, @NotNull CharBuffer text) {
      myStyle = style;
      myText = text.clone();
    }

    public TextStyle getStyle() {
      return myStyle;
    }

    public CharBuffer getText() {
      return myText;
    }

    public int getLength() {
      return myText.getLength();
    }
  }
}
