package com.jediterm.terminal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Takes data from and sends it back to TTY input and output streams via {@link TtyConnector}
 */
public class TtyBasedArrayDataStream extends ArrayTerminalDataStream {
  private final TtyConnector myTtyConnector;
  private final @Nullable Runnable myOnBeforeBlockingWait;

  public TtyBasedArrayDataStream(final TtyConnector ttyConnector, final @Nullable Runnable onBeforeBlockingWait) {
    super(new char[1024], 0, 0);
    myTtyConnector = ttyConnector;
    myOnBeforeBlockingWait = onBeforeBlockingWait;
  }

  public TtyBasedArrayDataStream(final TtyConnector ttyConnector) {
    super(new char[1024], 0, 0);
    myTtyConnector = ttyConnector;
    myOnBeforeBlockingWait = null;
  }

  private void fillBuf() throws IOException {
    myOffset = 0;

    if (!myTtyConnector.ready() && myOnBeforeBlockingWait != null) {
      myOnBeforeBlockingWait.run();
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
    return getDebugText();
  }

  private @NotNull String getDebugText() {
    String s = new String(myBuf, myOffset, myLength);
    return s.replace("\u001b", "ESC").replace("\n", "\\n").replace("\r", "\\r").replace("\u0007", "BEL").replace(" ", "<S>");
  }
}
