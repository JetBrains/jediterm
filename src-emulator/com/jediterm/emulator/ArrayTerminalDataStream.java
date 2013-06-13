package com.jediterm.emulator;

import java.io.IOException;

/**
 * @author traff
 */
public class ArrayTerminalDataStream implements TerminalDataStream {
  protected final char[] myBuf;
  protected int myOffset;
  protected int myLength;

  public ArrayTerminalDataStream(char[] buf, int offset, int length) {
    myBuf = buf;
    myOffset = offset;
    myLength = length;
  }

  public ArrayTerminalDataStream(char[] buf) {
    this(buf, 0, buf.length);
  }

  public char getChar() throws IOException {
    if (myLength == 0) {
      throw new DisconnectedException();
    }

    myLength--;

    return myBuf[myOffset++];
  }

  public void pushChar(final char b) throws DisconnectedException {
    if (myOffset == 0) {
      // Pushed back too many... shift it up to the end.
      myOffset = myBuf.length - myLength;
      System.arraycopy(myBuf, 0, myBuf, myOffset, myLength);
    }

    myLength++;
    myBuf[--myOffset] = b;
  }

  public CharacterUtils.CharArraySlice advanceThroughASCII(int maxChars) throws IOException {
    CharacterUtils.CharArraySlice charArraySlice = CharacterUtils.getNonControlCharacters(maxChars, myBuf, myOffset, myLength);

    myOffset += charArraySlice.length;
    myLength -= charArraySlice.length;

    return charArraySlice;
  }

  public void pushBackBuffer(final char[] bytes, final int len) throws DisconnectedException {
    for (int i = len - 1; i >= 0; i--) {
      pushChar(bytes[i]);
    }
  }

  @Override
  public void sendBytes(byte[] response) throws IOException {
  }
}
