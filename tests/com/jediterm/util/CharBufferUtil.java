package com.jediterm.util;

import com.jediterm.terminal.model.CharBuffer;

/**
 * @author traff
 */
public class CharBufferUtil {
  public static CharBuffer create(String str) {
    char[] buf = new char[str.length()];
    for (int i = 0; i<str.length(); i++) {
      buf[i] = str.charAt(i);
    }
    return new CharBuffer(buf, 0, buf.length);
  }
}
