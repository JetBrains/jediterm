package com.jediterm.pty;

import com.jediterm.terminal.ProcessTtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
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
  public void resize(@NotNull Dimension termWinSize) {
    if (isConnected()) {
      myProcess.setWinSize(new WinSize(termWinSize.width, termWinSize.height));
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
