package com.jediterm.util;

import com.jediterm.terminal.Util;
import com.jediterm.terminal.display.BackBuffer;
import junit.framework.Assert;

/**
 * @author traff
 */
public class BackBufferUtil {
  public static void checkTextBufferByLines(BackBuffer backBuffer) {
    String[] lines = backBuffer.getLines().split("\n", 1000);
    String[] textBufferLines = backBuffer.getTextBufferLines().split("\n", 1000);

    for (int i = 0; i < lines.length; i++) {
      if (i < textBufferLines.length) {
        String line = Util.trimTrailing(lines[i]);

        if (!line.equals(Util.trimTrailing(textBufferLines[i]))) {
          throw new IllegalStateException("Lines " + i + " dont equal:\n" + line + "\n" + textBufferLines[i]);
        }
      }
      else {
        if (lines[i].trim().length() > 0) {
          throw new IllegalStateException("Extra line " + i + ":\n" + lines[i]);
        }
      }
    }
  }

  public static void assertBuffersEquals(BackBuffer backBuffer) {
    String[] lines = backBuffer.getLines().split("\n");
    String[] textBufferLines = backBuffer.getTextBufferLines().split("\n");

    StringBuilder newText = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      if (i < textBufferLines.length) {
        newText.append(textBufferLines[i].length() < lines[i].length() ? lines[i].substring(0, textBufferLines[i].length()) : lines[i]);
        newText.append("\n");
      }
      else {
        if (lines[i].trim().length() > 0) {
          newText.append(lines[i]);
          newText.append("\n");
        }
      }
    }

    Assert.assertEquals(newText.toString(), backBuffer.getTextBufferLines() + "\n");
  }
}
