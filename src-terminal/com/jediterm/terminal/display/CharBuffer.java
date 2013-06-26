package com.jediterm.terminal.display;

import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * @author traff
 */
public class CharBuffer implements Iterable<Character>, CharSequence {
  
  public final static CharBuffer EMPTY = new CharBuffer(new char[0], 0, 0);
  
  private final char[] myBuf;
  private final int myStart;
  private final int myLength;

  public CharBuffer(char[] buf, int start, int length) {
    myBuf = buf;
    myStart = start;
    myLength = length;
  }

  public CharBuffer(char c, int count) {
    myBuf = new char[count];
    Arrays.fill(myBuf, c);
    myStart = 0;
    myLength = count;
  }

  public CharBuffer(String str) {
    this(str.toCharArray(), 0, str.length());
  }

  @Override
  public Iterator<Character> iterator() {
    return new Iterator<Character>() {
      private int myCurPosition = myStart;

      @Override
      public boolean hasNext() {
        return myCurPosition < myBuf.length && myCurPosition < myStart + myLength;
      }

      @Override
      public Character next() {
        return myBuf[myCurPosition];
      }

      @Override
      public void remove() {
        throw new IllegalStateException("Can't remove from buffer");
      }
    };
  }

  public char[] getBuf() {
    return myBuf;
  }

  public int getStart() {
    return myStart;
  }

  public int getLength() {
    return myLength;
  }

  public static CharBuffer adapt(Iterable<Character> characters) {
    if (characters instanceof CharBuffer) {
      return (CharBuffer)characters;
    }
    else {
      List<Character> charList = Lists.newArrayList(characters);
      char[] buf = new char[charList.size()];
      int i = 0;
      for (Character ch : charList) {
        buf[i++] = ch;
      }
      return new CharBuffer(buf, 0, buf.length);
    }
  }

  @Override
  public int length() {
    return myLength;
  }

  @Override
  public char charAt(int index) {
    return myBuf[myStart + index];
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    return new CharBuffer(myBuf, myStart + start, end - start);
  }

  @Override
  public String toString() {
    return new String(myBuf, myStart, myLength);
  }

  public CharBuffer clone() {
    char[] newBuf = Arrays.copyOfRange(myBuf, myStart, myStart + myLength);

    return new CharBuffer(newBuf, 0, myLength);
  }
}
