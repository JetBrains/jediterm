package com.jediterm.util;

import com.jediterm.terminal.ArrayTerminalDataStream;
import com.jediterm.terminal.TerminalDataStream;

import java.io.File;
import java.io.IOException;

/**
 * @author traff
 */
public class FileLoadableTerminalDataStream implements TerminalDataStream {
  private ArrayTerminalDataStream myDataStream;

  public void load(File file) throws IOException {
    myDataStream = new ArrayTerminalDataStream(FileUtil.loadFileText(file, "UTF-8"));
  }

  @Override
  public char getChar() throws IOException {
    return myDataStream.getChar();
  }

  @Override
  public void pushChar(char c) throws IOException {
    myDataStream.pushChar(c);
  }

  @Override
  public String readNonControlCharacters(int maxChars) throws IOException {
    return myDataStream.readNonControlCharacters(maxChars);
  }

  @Override
  public void pushBackBuffer(char[] bytes, int length) throws IOException {
    myDataStream.pushBackBuffer(bytes, length);
  }
}
