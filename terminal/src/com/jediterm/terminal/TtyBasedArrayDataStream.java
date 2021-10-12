package com.jediterm.terminal;

import com.jediterm.terminal.util.CharUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Takes data from and sends it back to TTY input and output streams via {@link TtyConnector}
 */
public class TtyBasedArrayDataStream extends ArrayTerminalDataStream {
  private final TtyConnector myTtyConnector;
  private final List<Runnable> myOnBeforeBlockingWaitListeners = new LinkedList<>();

  public TtyBasedArrayDataStream(final TtyConnector ttyConnector) {
    super(new char[1024], 0, 0);
    myTtyConnector = ttyConnector;
  }

  private void fillBuf() throws IOException {
    myOffset = 0;

    if (!myTtyConnector.ready()) {
      for (Runnable listener: myOnBeforeBlockingWaitListeners) {
        listener.run();
      }
    }
    myLength = myTtyConnector.read(myBuf, myOffset, myBuf.length);

    if (myLength <= 0) {
      myLength = 0;
      throw new EOF();
    }
  }

  public char getChar() throws IOException {
    if (myLength == 0) {
      fillBuf();
    }
    return super.getChar();
  }

  public String readNonControlCharacters(int maxChars) throws IOException {
    if (myLength == 0) {
      fillBuf();
    }

    return super.readNonControlCharacters(maxChars);
  }

  @Override
  public String toString() {
    return CharUtils.toHumanReadableText(new String(myBuf, myOffset, myLength));
  }

  public void addOnBeforeBlockingWaitListener(@NotNull Runnable listener) {
    myOnBeforeBlockingWaitListeners.add(listener);
  }
}
