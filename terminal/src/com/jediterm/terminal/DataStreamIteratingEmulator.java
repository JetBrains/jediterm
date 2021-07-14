package com.jediterm.terminal;

import com.jediterm.terminal.emulator.Emulator;
import com.jediterm.terminal.model.TerminalTypeAheadManager;

import java.io.IOException;

/**
 * @author traff
 */
public abstract class DataStreamIteratingEmulator implements Emulator {
  protected final TerminalDataStream myDataStream;
  protected final Terminal myTerminal;
  protected final TerminalTypeAheadManager myTypeAheadManager;

  private boolean myEof = false;

  public DataStreamIteratingEmulator(TerminalDataStream dataStream, Terminal terminal, TerminalTypeAheadManager typeAheadManager) {
    myDataStream = dataStream;
    myTerminal = terminal;
    myTypeAheadManager = typeAheadManager;
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
      if (myDataStream instanceof ArrayTerminalDataStream) { // TODO: more permanent solution, probably change to the interface.
        ArrayTerminalDataStream terminalDataStream = (ArrayTerminalDataStream) myDataStream;

        terminalDataStream.startRecording();

        char b = myDataStream.getChar();
        processChar(b, myTerminal);

        myTypeAheadManager.onTerminalData(terminalDataStream.stopRecording());
      } else {
        char b = myDataStream.getChar();
        processChar(b, myTerminal);
      }
    }
    catch (TerminalDataStream.EOF e) {
      myEof = true;
    }
  }

  protected abstract void processChar(char ch, Terminal terminal) throws IOException;
}
