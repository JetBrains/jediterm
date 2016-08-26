package com.jediterm.util;

import java.io.*;
import java.util.Scanner;

/**
 * @author traff
 */
public class FileUtil {
  public static char[] loadFileText(java.io.File file, String encoding) throws IOException {
    InputStream stream = new FileInputStream(file);
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    Reader reader = encoding == null ? new InputStreamReader(stream) : new InputStreamReader(stream, encoding);
    try {
      return loadText(reader, (int)file.length());
    }
    finally {
      reader.close();
    }
  }

  public static char[] loadText(Reader reader, int length) throws IOException {
    char[] chars = new char[length];
    int count = 0;
    while (count < chars.length) {
      int n = reader.read(chars, count, chars.length - count);
      if (n <= 0) break;
      count += n;
    }
    if (count == chars.length) {
      return chars;
    }
    else {
      char[] newChars = new char[count];
      System.arraycopy(chars, 0, newChars, 0, count);
      return newChars;
    }
  }

  public static String loadFileLines(File file) throws FileNotFoundException {
    Scanner s = new Scanner(file);
    StringBuilder sb = new StringBuilder();

    while (s.hasNextLine()) {
      sb.append(s.nextLine()).append("\n");
    }

    s.close();
    return sb.toString();
  }
}
