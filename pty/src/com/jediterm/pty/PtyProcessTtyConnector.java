package com.jediterm.pty;

import com.jediterm.terminal.ProcessTtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;

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
  protected void resizeImmediately() {
    if (getPendingTermSize() != null && getPendingPixelSize() != null) {
      myProcess.setWinSize(
          new WinSize(getPendingTermSize().width, getPendingTermSize().height, getPendingPixelSize().width, getPendingPixelSize().height));
    }
  }

  @Override
  public boolean isConnected() {
    return myProcess.isRunning();
  }

  @Override
  public String getName() {
    return "Local";
  }
}
