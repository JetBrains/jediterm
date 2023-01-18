package com.jediterm.util;

import com.jediterm.terminal.StyledTextConsumerAdapter;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class ArrayBasedTextConsumer extends StyledTextConsumerAdapter {
  private char[][] myBuf;

  public ArrayBasedTextConsumer(int h, int w) {
    myBuf = new char[h][w];
    for (int i = 0; i<myBuf.length; i++) {
      for (int j = 0; j < myBuf[i].length; j++) {
        myBuf[i][j] = ' ';
      }
    }
  }

  @Override
  public void consume(int x, int y, @NotNull TextStyle style, @NotNull CharBuffer characters, int startRow) {
    for (int i = 0; i<characters.length(); i++) {
      myBuf[y - startRow][x + i] = characters.charAt(i);
    }
  }

  public String getLines() {
    StringBuilder res = new StringBuilder();
    for (int i = 0; i<myBuf.length; i++) {
      for (int j = 0; j < myBuf[i].length; j++) {
        res.append(myBuf[i][j]);
      }
      res.append('\n');
    }
    return res.toString();
  }
}
