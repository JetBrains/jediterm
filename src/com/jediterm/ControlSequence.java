/**
 *
 */
package com.jediterm;

import java.io.IOException;
import java.util.ArrayList;

import com.google.common.collect.Lists;
import com.jediterm.CharacterUtils.CharacterType;

public class ControlSequence {
  private int argc;

  private int[] argv;

  private TerminalMode[] modeTable;

  private char finalChar;

  private int startInBuf;

  private int lengthInBuf;

  private int bufferVersion;

  private static TerminalMode[] normalModes = {

  };

  private static TerminalMode[] questionMarkModes = {TerminalMode.Null,
    TerminalMode.CursorKey, TerminalMode.ANSI,
    TerminalMode.WideColumn, TerminalMode.SmoothScroll, TerminalMode.ReverseScreen,
    TerminalMode.RelativeOrigin, TerminalMode.WrapAround, TerminalMode.AutoRepeat,
    TerminalMode.Interlace};

  private ArrayList<Character> unhandledChars;

  ControlSequence(final TtyChannel channel) throws IOException {
    argv = new int[10];
    argc = 0;
    modeTable = normalModes;
    readControlSequence(channel);
  }

  private void readControlSequence(final TtyChannel channel) throws IOException {
    argc = 0;
    // Read integer arguments
    int digit = 0;
    int seenDigit = 0;
    int pos = -1;

    bufferVersion = channel.serial;
    startInBuf = channel.offset;

    while (true) {
      final char b = channel.getChar();
      pos++;
      if (b == '?' && pos == 0) {
        modeTable = questionMarkModes;
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
    if (bufferVersion == channel.serial) {
      lengthInBuf = channel.offset - startInBuf;
    }
    else {
      lengthInBuf = -1;
    }
    argc += seenDigit;
  }

  private void addUnhandled(final char b) {
    if (unhandledChars == null) {
      unhandledChars = Lists.newArrayList();
    }
    unhandledChars.add(b);
  }

  public boolean pushBackReordered(final TtyChannel channel) throws IOException {
    if (unhandledChars == null) return false;
    final char[] bytes = new char[1024]; // can't be more than the whole buffer...
    int i = 0;
    for (final char b : unhandledChars) {
      bytes[i++] = b;
    }
    bytes[i++] = (byte)CharacterUtils.ESC;
    bytes[i++] = (byte)'[';

    if (modeTable == questionMarkModes) {
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
    if (modeTable == questionMarkModes) {
      sb.append("?");
    }

    String sep = "";
    for (int i = 0; i < argc; i++) {
      sb.append(sep);
      sb.append(argv[i]);
      sep = ";";
    }
    sb.append((char)finalChar);

    if (unhandledChars != null) {
      sb.append(" Unhandled:");
      CharacterType last = CharacterType.NONE;
      for (final char b : unhandledChars) {
        last = CharacterUtils.appendChar(sb, last, (char)b);
      }
    }
  }

  public final void appendActualBytesRead(final StringBuffer sb,
                                          final TtyChannel buffer) {
    if (lengthInBuf == -1) {
      sb.append("TermIOBuffer filled in reading");
    }
    else if (bufferVersion != buffer.serial) {
      sb.append("TermIOBuffer filled after reading");
    }
    else {
      buffer.appendBuf(sb, startInBuf, lengthInBuf);
    }
  }

  public char getFinalChar() {
    return finalChar;
  }

  public TerminalMode[] getModeTable() {
    return modeTable;
  }
}