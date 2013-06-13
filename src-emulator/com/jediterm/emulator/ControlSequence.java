/**
 *
 */
package com.jediterm.emulator;

import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.ArrayList;

public class ControlSequence {
  private int argc;

  private int[] argv;

  private TerminalMode[] modeTable;

  private char finalChar;

  private static TerminalMode[] NORMAL_MODES = {

  };

  private static TerminalMode[] QUESTION_MARK_MODES = {TerminalMode.Null,
    TerminalMode.CursorKey, TerminalMode.ANSI,
    TerminalMode.WideColumn, TerminalMode.SmoothScroll, TerminalMode.ReverseScreen,
    TerminalMode.RelativeOrigin, TerminalMode.WrapAround, TerminalMode.AutoRepeat,
    TerminalMode.Interlace};

  private ArrayList<Character> unhandledChars;

  ControlSequence(final TerminalDataStream channel) throws IOException {
    argv = new int[10];
    argc = 0;
    modeTable = NORMAL_MODES;
    readControlSequence(channel);
  }

  private void readControlSequence(final TerminalDataStream channel) throws IOException {
    argc = 0;
    // Read integer arguments
    int digit = 0;
    int seenDigit = 0;
    int pos = -1;

    while (true) {
      final char b = channel.getChar();
      pos++;
      if (b == '?' && pos == 0) {
        modeTable = QUESTION_MARK_MODES;
      }
      else if (b == ';') {
        if (digit > 0) {
          argc++;
          argv[argc] = 0;
          digit = 0;
        }
      }
      else if ('0' <= b && b <= '9') {
        argv[argc] = argv[argc] * 10 + b - '0';
        digit++;
        seenDigit = 1;
        continue;
      }
      else if (':' <= b && b <= '?') {
        addUnhandled(b);
      }
      else if (0x40 <= b && b <= 0x7E) {
        finalChar = b;
        break;
      }
      else {
        addUnhandled(b);
      }
    }
    argc += seenDigit;
  }

  private void addUnhandled(final char b) {
    if (unhandledChars == null) {
      unhandledChars = Lists.newArrayList();
    }
    unhandledChars.add(b);
  }

  public boolean pushBackReordered(final TerminalDataStream channel) throws IOException {
    if (unhandledChars == null) return false;
    final char[] bytes = new char[1024]; // can't be more than the whole buffer...
    int i = 0;
    for (final char b : unhandledChars) {
      bytes[i++] = b;
    }
    bytes[i++] = (byte)CharacterUtils.ESC;
    bytes[i++] = (byte)'[';

    if (modeTable == QUESTION_MARK_MODES) {
      bytes[i++] = (byte)'?';
    }
    for (int argi = 0; argi < argc; argi++) {
      if (argi != 0) bytes[i++] = ';';
      String s = Integer.toString(argv[argi]);
      for (int j = 0; j < s.length(); j++) {
        bytes[i++] = s.charAt(j);
      }
    }
    bytes[i++] = finalChar;
    channel.pushBackBuffer(bytes, i);
    return true;
  }

  int getCount() {
    return argc;
  }

  final int getArg(final int index, final int def) {
    if (index >= argc) {
      return def;
    }
    return argv[index];
  }

  public final void appendToBuffer(final StringBuffer sb) {
    sb.append("ESC[");
    if (modeTable == QUESTION_MARK_MODES) {
      sb.append("?");
    }

    String sep = "";
    for (int i = 0; i < argc; i++) {
      sb.append(sep);
      sb.append(argv[i]);
      sep = ";";
    }
    sb.append(finalChar);

    if (unhandledChars != null) {
      sb.append(" Unhandled:");
      CharacterUtils.CharacterType last = CharacterUtils.CharacterType.NONE;
      for (final char b : unhandledChars) {
        last = CharacterUtils.appendChar(sb, last, (char)b);
      }
    }
  }

  public char getFinalChar() {
    return finalChar;
  }

  public TerminalMode[] getModeTable() {
    return modeTable;
  }
}