package com.jediterm.terminal.emulator;

import com.jediterm.core.util.Ascii;
import com.jediterm.terminal.TerminalDataStream;
import com.jediterm.terminal.util.CharUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author traff
 */
final class SystemCommandSequence {

  private static final char ST = 0x9c;

  private final List<String> myArgs;
  private final StringBuilder mySequence = new StringBuilder();

  public SystemCommandSequence(@NotNull TerminalDataStream stream) throws IOException {
    StringBuilder argBuilder = new StringBuilder();
    boolean end = false;
    List<String> args = new ArrayList<>();
    while (!end) {
      char ch = stream.getChar();
      mySequence.append(ch);
      end = isEnd();
      if (ch == ';' || end) {
        if (end && isTwoBytesEnd()) {
          argBuilder.deleteCharAt(argBuilder.length() - 1);
        }
        args.add(argBuilder.toString());
        argBuilder.setLength(0);
      }
      else {
        argBuilder.append(ch);
      }
    }
    myArgs = List.copyOf(args);
  }

  private boolean isEnd() {
    int len = mySequence.length();
    if (len > 0) {
      char ch = mySequence.charAt(len - 1);
      return ch == Ascii.BEL || ch == ST || isTwoBytesEnd();
    }
    return false;
  }

  private boolean isTwoBytesEnd() {
    int len = mySequence.length();
    return len > 1 && mySequence.charAt(len - 2) == Ascii.ESC && mySequence.charAt(len - 1) == '\\';
  }

  public @Nullable String getStringAt(int i) {
    return i < myArgs.size() ? myArgs.get(i) : null;
  }

  public @NotNull List<String> getArgs() {
    return myArgs;
  }

  public int getIntAt(int position, int defaultValue) {
    if (position < myArgs.size()) {
      return parseArg(myArgs.get(position), defaultValue);
    }
    return defaultValue;
  }

  private int parseArg(@NotNull String arg, int defaultValue) {
    if (!arg.isEmpty() && Character.isDigit(arg.charAt(arg.length() - 1))) {
      // check isDigit to reduce amount of expensive NumberFormatException
      try {
        return Integer.parseInt(arg);
      }
      catch (NumberFormatException ignored) {
      }
    }
    return defaultValue;
  }

  public @NotNull String format(@NotNull String body) {
    return (char)Ascii.ESC + "]" + body + getTerminator();
  }

  @Override
  public String toString() {
    return CharUtils.toHumanReadableText(mySequence.toString());
  }

  /**
   * <a href="https://invisible-island.net/xterm/ctlseqs/ctlseqs.html">
   * XTerm accepts either BEL or ST for terminating OSC
   * sequences, and when returning information, uses the same
   * terminator used in a query. </a>
   */
  private @NotNull String getTerminator() {
    int len = mySequence.length();
    if (isTwoBytesEnd()) {
      return mySequence.substring(len - 2);
    }
    return mySequence.substring(len - 1);
  }
}
