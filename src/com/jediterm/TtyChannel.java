/**
 *
 */
package com.jediterm;

import java.awt.Dimension;
import java.io.IOException;

public class TtyChannel {
  private Tty tty;

  byte[] buf = new byte[1024];

  int offset = 0;

  int length = 0;

  int serial;

  public TtyChannel(final Tty tty) {
    this.tty = tty;
    serial = 0;
  }

  public byte getChar() throws IOException {
    if (length == 0) {
      fillBuf();
    }
    length--;

    return buf[offset++];
  }

  public void appendBuf(final StringBuffer sb, final int begin, final int length) {
    CharacterUtils.appendBuf(sb, buf, begin, length);
  }

  private void fillBuf() throws IOException {
    offset = 0;
    length = tty.read(buf, offset, buf.length);
    serial++;

    if (length <= 0) {
      length = 0;
      throw new IOException("Connection lost.");
    }
  }

  public void pushChar(final byte b) throws IOException {
    if (offset == 0) {
      // Pushed back too many... shift it up to the end.
      offset = buf.length - length;
      System.arraycopy(buf, 0, buf, offset, length);
    }

    length++;
    buf[--offset] = b;
  }

  int advanceThroughASCII(int toLineEnd) throws IOException {
    if (length == 0) {
      fillBuf();
    }

    int len = toLineEnd > length ? length : toLineEnd;

    final int origLen = len;
    byte tmp;
    while (len > 0) {
      tmp = buf[offset++];
      if (0x20 <= tmp && tmp <= 0x7f) {
        length--;
        len--;
        continue;
      }
      offset--;
      break;
    }
    return origLen - len;
  }

  public void sendBytes(final byte[] bytes) throws IOException {
    tty.write(bytes);
  }

  public void postResize(final Dimension termSize, final Dimension pixelSize) {
    tty.resize(termSize, pixelSize);
  }

  public void pushBackBuffer(final byte[] bytes, final int len) throws IOException {
    for (int i = len - 1; i >= 0; i--) {
      pushChar(bytes[i]);
    }
  }

  public boolean isConnected() {
    return tty.isConnected();
  }
}