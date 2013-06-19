package com.jediterm.pty;

import com.jediterm.terminal.ProcessTtyConnector;
import jpty.WinSize;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * @author traff
 */
public class PtyProcessTtyConnector extends ProcessTtyConnector {
  private PtyProcess myProcess;

  public PtyProcessTtyConnector(PtyProcess process, Charset charset) {
    super(process, charset);

    myProcess = process;
  }

  @Override
  public void close() {
    myProcess.destroy();
  }

  @Override
  protected void resizeImmediately() {

    if (getPendingTermSize() != null && getPendingPixelSize() != null) {
      try {
        myProcess.getPty().setWinSize(
          new WinSize(getPendingTermSize().width, getPendingTermSize().height, getPendingPixelSize().width, getPendingPixelSize().height));
      }
      catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  @Override
  public boolean isConnected() {
    return !myProcess.isFinished();
  }
}
