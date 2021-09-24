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
    StringBuilder argBuilder = new StringBuilder();
    boolean end = false;
    while (!end) {
      final char ch = stream.getChar();
      mySequenceString.append(ch);
      end = isEnd(ch);
      if (ch == ';' || end) {
        if (end && isTwoBytesEnd(ch)) {
          argBuilder.deleteCharAt(argBuilder.length() - 1);
        }
        String arg = argBuilder.toString();
        myArgs.add(parseArg(arg));
        argBuilder.setLength(0);
      }
      else {
        argBuilder.append(ch);
      }
    }
  }

  private @NotNull Object parseArg(@NotNull String arg) {
    if (isNumber(arg)) {
      // use Integer.parseInt on numbers only to avoid excessive NumberFormatException
      try {
        return Integer.parseInt(arg);
      }
      catch (NumberFormatException ignored) {
      }
    }
    return arg;
  }

  private static boolean isNumber(@NotNull String str) {
    for (int i = 0; i < str.length(); i++) {
      if (!Character.isDigit(str.charAt(i))) {
        return false;
      }
    }
    return !str.isEmpty();
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
