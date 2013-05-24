/**
 *
 */
package com.jediterm.emulator;

import java.awt.*;
import java.io.IOException;

public class TtyChannel {
  private TtyConnector myTtyConnector;

  char[] myBuf = new char[1024];

  int myOffset = 0;

  int myLength = 0;

  int mySerial;

  public TtyChannel(final TtyConnector ttyConnector) {
    myTtyConnector = ttyConnector;
    mySerial = 0;
  }

  public char getChar() throws IOException {
    if (myLength == 0) {
      fillBuf();
    }
    myLength--;

    return myBuf[myOffset++];
  }

  public void appendBuf(final StringBuffer sb, final int begin, final int length) {
    CharacterUtils.appendBuf(sb, myBuf, begin, length);
  }

  private void fillBuf() throws IOException {
    myOffset = 0;
    myLength = myTtyConnector.read(myBuf, myOffset, myBuf.length);
    mySerial++;

    if (myLength <= 0) {
      myLength = 0;
      throw new IOException("Connection lost.");
    }
  }

  public void pushChar(final char b) throws IOException {
    if (myOffset == 0) {
      // Pushed back too many... shift it up to the end.
      myOffset = myBuf.length - myLength;
      System.arraycopy(myBuf, 0, myBuf, myOffset, myLength);
    }

    myLength++;
    myBuf[--myOffset] = b;
  }

  int advanceThroughASCII(int toLineEnd) throws IOException {
    if (myLength == 0) {
      fillBuf();
    }

    int len = toLineEnd > myLength ? myLength : toLineEnd;

    final int origLen = len;
    char tmp;
    while (len > 0) {
      tmp = myBuf[myOffset++];
      if (0x20 <= tmp) { //stop when we reach control chars
        myLength--;
        len--;
        continue;
      }
      myOffset--;
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