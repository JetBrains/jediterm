package com.jediterm.pty;

import com.jediterm.terminal.ProcessTtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

/**
 * @author traff
 */
public class PtyProcessTtyConnector extends ProcessTtyConnector {
  private final PtyProcess myProcess;

  public PtyProcessTtyConnector(@NotNull PtyProcess process, @NotNull Charset charset) {
    super(process, charset);
    myProcess = process;
  }

  @Override
  protected void resizeImmediately() {
    if (getPendingTermSize() != null) {
      myProcess.setWinSize(new WinSize(getPendingTermSize().width, getPendingTermSize().height));
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
