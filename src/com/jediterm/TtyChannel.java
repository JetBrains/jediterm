/**
 *
 */
package com.jediterm;

import java.awt.Dimension;
import java.io.IOException;

public class TtyChannel {
  private TtyConnector myTtyConnector;

  char[] buf = new char[1024];

  int offset = 0;

  int length = 0;

  int serial;

  public TtyChannel(final TtyConnector ttyConnector) {
    this.myTtyConnector = ttyConnector;
    serial = 0;
  }

  public char getChar() throws IOException {
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
    length = myTtyConnector.read(buf, offset, buf.length);
    serial++;

    if (length <= 0) {
      length = 0;
      throw new IOException("Connection lost.");
    }
  }

  public void pushChar(final char b) throws IOException {
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
    char tmp;
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
    myTtyConnector.write(bytes);
  }

  public void postResize(final Dimension termSize, final Dimension pixelSize) {
    myTtyConnector.resize(termSize, pixelSize);
  }

  public void pushBackBuffer(final char[] bytes, final int len) throws IOException {
    for (int i = len - 1; i >= 0; i--) {
      pushChar(bytes[i]);
    }
  }

  public boolean isConnected() {
    return myTtyConnector.isConnected();
  }

  public void sendString(String string) throws IOException {
    myTtyConnector.write(string);
  }
}