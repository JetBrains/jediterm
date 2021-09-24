package com.jediterm.terminal.emulator;

import com.google.common.base.Ascii;
import com.google.common.collect.Lists;
import com.jediterm.terminal.TerminalDataStream;
import com.jediterm.terminal.util.CharUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * @author traff
 */
final class SystemCommandSequence {
  private final List<Object> myArgs = Lists.newArrayList();

  private final StringBuilder mySequenceString = new StringBuilder();

  public SystemCommandSequence(TerminalDataStream dataStream) throws IOException {
    readSystemCommandSequence(dataStream);
  }

  private void readSystemCommandSequence(TerminalDataStream stream) throws IOException {
    boolean isNumber = true;
    int number = 0;
    StringBuilder string = new StringBuilder();

    while (true) {
      final char b = stream.getChar();
      mySequenceString.append(b);

      if (b == ';' || isEnd(b)) {
        if (isTwoBytesEnd(b)) {
          string.delete(string.length() - 1, string.length());
        }
        if (isNumber) {
          myArgs.add(number);
        }
        else {
          myArgs.add(string.toString());
        }
        if (isEnd(b)) {
          break;
        }
        isNumber = true;
        number = 0;
        string = new StringBuilder();
      }
      else if (isNumber) {
        if ('0' <= b && b <= '9') {
          number = number * 10 + b - '0';
        }
        else {
          isNumber = false;
        }
        string.append(b);
      }
      else {
        string.append(b);
      }
    }
  }

  private boolean isEnd(char b) {
    return b == Ascii.BEL || b == 0x9c || isTwoBytesEnd(b);
  }

  private boolean isTwoBytesEnd(char ch) {
    int len = mySequenceString.length();
    return len >= 2 && mySequenceString.charAt(len - 2) == Ascii.ESC && ch == '\\';
  }

  public @Nullable String getStringAt(int i) {
    if (i>=myArgs.size()) {
      return null;
    }
    Object val = myArgs.get(i);
    return val instanceof String ? (String)val : null;
  }

  public int getIntAt(int position, int defaultValue) {
    if (position < myArgs.size()) {
      Object val = myArgs.get(position);
      if (val instanceof Integer) {
        return (Integer) val;
      }
    }
    return defaultValue;
  }

  public @NotNull String format(@NotNull String body) {
    return (char)Ascii.ESC + "]" + body + getTerminator();
  }

  @Override
  public String toString() {
    return CharUtils.toHumanReadableText(mySequenceString.toString());
  }

  /**
   * <a href="https://invisible-island.net/xterm/ctlseqs/ctlseqs.html">
   * XTerm accepts either BEL or ST for terminating OSC
   * sequences, and when returning information, uses the same
   * terminator used in a query. </a>
   */
  private @NotNull String getTerminator() {
    int lastInd = mySequenceString.length() - 1;
    if (isTwoBytesEnd(mySequenceString.charAt(lastInd))) {
      return mySequenceString.substring(lastInd - 1);
    }
    return mySequenceString.substring(lastInd);
  }
}
