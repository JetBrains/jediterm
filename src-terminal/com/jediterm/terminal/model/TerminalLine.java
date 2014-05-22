package com.jediterm.terminal.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jediterm.terminal.CharacterUtils;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author traff
 */
public class TerminalLine {

  private TextEntries myTextEntries = new TextEntries();
  private boolean myWrapped = false;

  public TerminalLine() {
  }

  public TerminalLine(@NotNull TextEntry entry) {
    myTextEntries.add(entry);
  }

  public static TerminalLine createEmpty() {
    return new TerminalLine();
  }

  public String getText() {
    final StringBuilder sb = new StringBuilder();

    for (TerminalLine.TextEntry textEntry : Lists.newArrayList(myTextEntries)) {
      sb.append(textEntry.getText());
    }

    return sb.toString();
  }

  public boolean isWrapped() {
    return myWrapped;
  }

  public void setWrapped(boolean wrapped) {
    myWrapped = wrapped;
  }

  public void clear() {
    myTextEntries.clear();
    setWrapped(false);
  }

  public void writeString(int x, @NotNull String str, @NotNull TextStyle style) {
    writeCharacters(x, style, new CharBuffer(str));
  }

  private void writeCharacters(int x, @NotNull TextStyle style, @NotNull CharBuffer characters) {
    int len = myTextEntries.length();

    if (x >= len) {
      if (x - len > 0) {
        myTextEntries.add(new TextEntry(TextStyle.EMPTY, new CharBuffer(CharacterUtils.EMPTY_CHAR, x - len)));
      }

      myTextEntries.add(new TextEntry(style, characters));
    } else {
      len = Math.max(len, x + characters.length());
      myTextEntries = merge(x, characters, style, myTextEntries, len);
    }
  }

  private static TextEntries merge(int x, @NotNull CharBuffer str, @NotNull TextStyle style, @NotNull TextEntries entries, int lineLength) {
    Pair<char[], TextStyle[]> pair = toBuf(entries, lineLength);

    for (int i = 0; i < str.length(); i++) {
      pair.first[i + x] = str.charAt(i);
      pair.second[i + x] = style;
    }

    return collectFromBuffer(pair.first, pair.second);
  }

  private static Pair<char[], TextStyle[]> toBuf(TextEntries entries, int lineLength) {
    Pair<char[], TextStyle[]> pair = Pair.create(new char[lineLength], new TextStyle[lineLength]);


    int p = 0;
    for (TextEntry entry : entries) {
      for (int i = 0; i < entry.getLength(); i++) {
        pair.first[p + i] = entry.getText().charAt(i);
        pair.second[p + i] = entry.getStyle();
      }
      p += entry.getLength();
    }
    return pair;
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
    // delete to the end of line : line is no more wrapped
    setWrapped(false);
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
      if (dx > 0) {
        //part of entry before x
        newEntries.add(new TextEntry(entry.getStyle(), entry.getText().subBuffer(0, dx)));
        p = x;
      }
      if (dx + count < len) {
        //part that left after deleting count 
        newEntries.add(new TextEntry(entry.getStyle(), entry.getText().subBuffer(dx + count, len - (dx + count))));
        count = 0;
      } else {
        count -= (len - dx);
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
          for (int j = 0; j < count; j++) {
            buf[p] = CharacterUtils.EMPTY_CHAR;
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
    if (leftX < myTextEntries.length()) {
      writeCharacters(leftX, style, new CharBuffer(CharacterUtils.EMPTY_CHAR, Math.min(myTextEntries.length(), rightX) - leftX));
    }
  }

  @Nullable
  public TextStyle getStyleAt(int x) {
    int i = 0;

    for (TextEntry te: Lists.newArrayList(myTextEntries)) {
      if (x>=i && x< i + te.getLength()) {
        return te.getStyle();
      }
      i+=te.getLength();
    }

    return null;
  }

  public void process(int y, LinesBuffer.TextEntryProcessor processor) {
    int x = 0;
    for (TextEntry te: Lists.newArrayList(myTextEntries)) {
      processor.process(x, y, te);
      x+=te.getLength();
    }
  }

  static class TextEntry {
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
    private ArrayList<TextEntry> myTextEntries = new ArrayList<TextEntry>();

    private int myLength = 0;

    public void add(TextEntry entry) {
      myTextEntries.add(entry);
      myLength += entry.getLength();
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
