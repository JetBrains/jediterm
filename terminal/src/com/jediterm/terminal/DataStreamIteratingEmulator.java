package com.jediterm.terminal;

import com.jediterm.terminal.emulator.Emulator;

import java.io.IOException;

/**
 * @author traff
 */
public abstract class DataStreamIteratingEmulator implements Emulator {
  protected final TerminalDataStream myDataStream;
  protected final Terminal myTerminal;

  private boolean myEof = false;

  public DataStreamIteratingEmulator(TerminalDataStream dataStream, Terminal terminal) {
    myDataStream = dataStream;
    myTerminal = terminal;
  }

  @Override
  public boolean hasNext() {
    return !myEof;
  }

  @Override
  public void resetEof() {
    myEof = false;
  }

  @Override
  public void next() throws IOException {
    try {
      TypeAheadTerminalDataStream terminalDataStream = null;
      if (myDataStream instanceof TypeAheadTerminalDataStream) {
        terminalDataStream = (TypeAheadTerminalDataStream) myDataStream;
        terminalDataStream.startRecordingReadChars();
      }

      char b = myDataStream.getChar();
      processChar(b, myTerminal);

      if (terminalDataStream != null) {
        String readChars = terminalDataStream.stopRecodingReadCharsAndGet();
        terminalDataStream.getTypeAheadManager().onTerminalData(readChars);
      }
    }
    catch (TerminalDataStream.EOF e) {
      myEof = true;
    }
  }

  protected abstract void processChar(char ch, Terminal terminal) throws IOException;
}
