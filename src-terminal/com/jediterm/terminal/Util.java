package com.jediterm.terminal;

import java.lang.reflect.Array;
import java.util.BitSet;

// In Java 5, the java.util.Arrays class has no copyOf() members...
public class Util {
  @SuppressWarnings("unchecked")
  public static <T> T[] copyOf(T[] original, int newLength) {
    Class<T> type = (Class<T>)original.getClass().getComponentType();
    T[] newArr = (T[])Array.newInstance(type, newLength);

    System.arraycopy(original, 0, newArr, 0, Math.min(original.length, newLength));

    return newArr;
  }

  public static int[] copyOf(int[] original, int newLength) {
    int[] newArr = new int[newLength];

    System.arraycopy(original, 0, newArr, 0, Math.min(original.length, newLength));

    return newArr;
  }

  public static char[] copyOf(char[] original, int newLength) {
    char[] newArr = new char[newLength];

    System.arraycopy(original, 0, newArr, 0, Math.min(original.length, newLength));

    return newArr;
  }

  public static void bitsetCopy(BitSet src, int srcOffset, BitSet dest, int destOffset, int length) {
    for (int i = 0; i < length; i++) {
      dest.set(destOffset + i, src.get(srcOffset + i));
    }
  }

  public static String trimTrailing(String string) {
    int index = string.length() - 1;
    while (index >= 0 && Character.isWhitespace(string.charAt(index))) index--;
    return string.substring(0, index + 1);
  }
}
