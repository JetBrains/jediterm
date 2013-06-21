package com.jediterm.terminal;

import com.google.common.base.Ascii;

import java.util.HashMap;
import java.util.Map;

import static java.awt.event.KeyEvent.*;

public class CharacterUtils {

  public static final int ESC = Ascii.ESC;
  public static final int DEL = Ascii.DEL;

  private CharacterUtils() {
  }

  private static final String[] NONPRINTING_NAMES = {"NUL", "SOH", "STX", "ETX", "EOT", "ENQ",
    "ACK", "BEL", "BS", "TAB", "LF", "VT", "FF", "CR", "S0", "S1",
    "DLE", "DC1", "DC2", "DC3", "DC4", "NAK", "SYN", "ETB", "CAN",
    "EM", "SUB", "ESC", "FS", "GS", "RS", "US"};

  public static byte[] VT102_RESPONSE = makeCode(ESC, '[', '?', '6', 'c');

  private static final Map<Integer, byte[]> CODES = new HashMap<Integer, byte[]>();
  static {
    putCode(VK_ENTER, Ascii.CR);
    putCode(VK_UP, ESC, 'O', 'A');
    putCode(VK_DOWN, ESC, 'O', 'B');
    putCode(VK_RIGHT, ESC, 'O', 'C');
    putCode(VK_LEFT, ESC, 'O', 'D');
    putCode(VK_F1, ESC, 'O', 'P');
    putCode(VK_F2, ESC, 'O', 'Q');
    putCode(VK_F3, ESC, 'O', 'R');
    putCode(VK_F4, ESC, 'O', 'S');
    putCode(VK_F5, ESC, 'O', 't');
    putCode(VK_F6, ESC, 'O', 'u');
    putCode(VK_F7, ESC, 'O', 'v');
    putCode(VK_F8, ESC, 'O', 'I');
    putCode(VK_F9, ESC, 'O', 'w');
    putCode(VK_F10, ESC, 'O', 'x');
    putCode(VK_HOME, ESC, '[', 'H');
    putCode(VK_END, ESC, '[', 'F');
    //putCode(VK_PAGE_UP, ESC, '[', '5', '~');  don't work
    //putCode(VK_PAGE_DOWN, ESC, '[', '6', '~');
  }

  public static String getNonControlCharacters(int maxChars, char[] buf, int offset, int charsLength) {
    int len = maxChars > charsLength ? charsLength : maxChars;

    final int origLen = len;
    char tmp;
    while (len > 0) {
      tmp = buf[offset++];
      if (0x20 <= tmp) { //stop when we reach control chars
        len--;
        continue;
      }
      offset--;
      break;
    }

    int length = origLen - len;

    return new String(buf, offset - length, length);
  }

  public enum CharacterType {
    NONPRINTING,
    PRINTING,
    NONASCII, NONE
  }

  public static CharacterType appendChar(final StringBuilder sb, final CharacterType last, final char c) {
    if (c <= 0x1F) {
      sb.append(' ');
      sb.append(NONPRINTING_NAMES[c]);
      return CharacterType.NONPRINTING;
    }
    else if (c == DEL) {
      sb.append(" DEL");
      return CharacterType.NONPRINTING;
    }
    else if (c > 0x1F && c <= 0x7E) {
      if (last != CharacterType.PRINTING) sb.append(' ');
      sb.append(c);
      return CharacterType.PRINTING;
    }
    else {
      sb.append(" 0x").append(Integer.toHexString(c));
      return CharacterType.NONASCII;
    }
  }

  public static void appendBuf(final StringBuilder sb, final char[] bs, final int begin, final int length) {
    CharacterType last = CharacterType.NONPRINTING;
    final int end = begin + length;
    for (int i = begin; i < end; i++) {
      final char c = (char)bs[i];
      last = appendChar(sb, last, c);
    }
  }

  static void putCode(final int code, final int... bytesAsInt) {
    CODES.put(code, makeCode(bytesAsInt));
  }

  private static byte[] makeCode(final int... bytesAsInt) {
    final byte[] bytes = new byte[bytesAsInt.length];
    int i = 0;
    for (final int byteAsInt : bytesAsInt) {
      bytes[i] = (byte)byteAsInt;
      i++;
    }
    return bytes;
  }



  static public byte[] getCode(final int key) {
    return CODES.get(key);
  }
}
