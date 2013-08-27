package com.jediterm.terminal.display;

import com.jediterm.terminal.TextStyle;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author traff
 */
public class TerminalLine {
  
  private TextEntries myTextEntries = new TextEntries();
  private boolean wrapped = false;

  public TerminalLine(@NotNull TextEntry entry) {
    myTextEntries.add(entry);
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

  public boolean isWrapped() {
    return wrapped;
  }

  public void setWrapped(boolean wrapped) {
    this.wrapped = wrapped;
  }

  public Iterator<TextEntry> entriesIterator() {
    return myTextEntries.iterator();
  }

  public void clear() {
    myTextEntries.clear();
  }

  public void writeString(int x, @NotNull String str, @NotNull TextStyle style) {
    writeCharacters(x, style, new CharBuffer(str));
  }

  private void writeCharacters(int x, @NotNull TextStyle style, @NotNull CharBuffer characters) {
    int len = myTextEntries.length();

    if (x >= len) {
      if (x - len > 0) {
        myTextEntries.add(new TextEntry(TextStyle.EMPTY, new CharBuffer(' ', x - len)));
      }
      
      myTextEntries.add(new TextEntry(style, characters));
    }
    else {
      len = Math.max(len, x + characters.length());
      myTextEntries = merge(x, characters, style, myTextEntries, len);
    }
  }

  private static TextEntries merge(int x, @NotNull CharBuffer str, @NotNull TextStyle style, @NotNull TextEntries entries, int lineLength) {
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

  private static TextEntries collectFromBuffer(@NotNull char[] buf, @NotNull TextStyle[] styles) {
    TextEntries result = new TextEntries();

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

  public void deleteCharacters(int x) {
    deleteCharacters(x, myTextEntries.length() - x);
  }

  public void deleteCharacters(int x, int count) {
    int p = 0;
    TextEntries newEntries = new TextEntries();
    
    for (TextEntry entry : myTextEntries) {
      if (count == 0) {
        newEntries.add(entry);
        continue;
      }
      int len = entry.getLength();
      if (p + len <= x) {
        p += len;
        newEntries.add(entry);
        continue;
      }
      int dx = x - p; //>=0
      if (dx>0) {
        //part of entry before x
        newEntries.add(new TextEntry(entry.getStyle(), entry.getText().subBuffer(0, dx)));
        p = x;
      }
      if (dx + count < len) {
        //part that left after deleting count 
        newEntries.add(new TextEntry(entry.getStyle(), entry.getText().subBuffer(dx + count, len - (dx + count))));
        count = 0;
      }
      else {
        count -= (len-dx);
        p = x;
      }
    }

    myTextEntries = newEntries;
  }

  public void insertBlankCharacters(int x, int count, int maxLen) {
    int len = myTextEntries.length();
    len = Math.min(len + count, maxLen);

    char[] buf = new char[len];
    TextStyle[] styles = new TextStyle[len];

    int p = 0;
    for (TextEntry entry : myTextEntries) {
      for (int i = 0; i < entry.getLength() && p < len; i++) {
        if (p == x) {
          for(int j = 0; j < count; j++) {
            buf[p] = ' ';
            styles[p] = TextStyle.EMPTY;
            p++;
          }
        }
        if (p < len) {
          buf[p] = entry.getText().charAt(i);
          styles[p] = entry.getStyle();
          p++;
        }
      }
      if (p >= len) {
        break;
      }
    }

    myTextEntries = collectFromBuffer(buf, styles);
  }

  public void clearArea(int leftX, int rightX, @NotNull TextStyle style) {
    writeCharacters(leftX, style, new CharBuffer(' ', Math.min(myTextEntries.length(), rightX) - leftX));
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

  private static class TextEntries implements Iterable<TextEntry> {
    private Deque<TextEntry> myTextEntries = new ArrayDeque<TextEntry>();

    private int myLength = 0;
    
    public void add(TextEntry entry) {
      myTextEntries.add(entry);
      myLength+=entry.getLength();
    }
    
    private Collection<TextEntry> entries() {
      return Collections.unmodifiableCollection(myTextEntries);
    }

    public Iterator<TextEntry> iterator() {
      return entries().iterator();
    }

    public int length() {
      return myLength;
    }

    public void clear() {
      myTextEntries.clear();
      myLength = 0;
    }
  }
}
