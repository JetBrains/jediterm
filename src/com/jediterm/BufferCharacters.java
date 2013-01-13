package com.jediterm;

import com.google.common.collect.Lists;

import java.util.Iterator;
import java.util.List;

/**
 * @author traff
 */
public class BufferCharacters implements Iterable<Character> {
  private final char[] myBuf;
  private final int myStart;
  private final int myLen;

  public BufferCharacters(char[] buf, int start, int len) {
    myBuf = buf;
    myStart = start;
    myLen = len;
  }

  @Override
  public Iterator<Character> iterator() {
    return new Iterator<Character>() {
      private int myCurPosition = myStart;

      @Override
      public boolean hasNext() {
        return myCurPosition < myBuf.length && myCurPosition < myStart + myLen;
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

  public int getLen() {
    return myLen;
  }

  public static BufferCharacters adapt(Iterable<Character> characters) {
    if (characters instanceof BufferCharacters) {
      return (BufferCharacters)characters;
    }
    else {
      List<Character> charList = Lists.newArrayList(characters);
      char[] buf = new char[charList.size()];
      int i = 0;
      for (Character ch : charList) {
        buf[i++] = ch;
      }
      return new BufferCharacters(buf, 0, buf.length);
    }
  }
}
