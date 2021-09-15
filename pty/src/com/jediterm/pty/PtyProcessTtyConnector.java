package com.jediterm.pty;

import com.jediterm.terminal.ProcessTtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Dimension;
import java.nio.charset.Charset;
import java.util.List;

/**
 * @author traff
 */
public class PtyProcessTtyConnector extends ProcessTtyConnector {
  private final PtyProcess myProcess;

  public PtyProcessTtyConnector(@NotNull PtyProcess process, @NotNull Charset charset) {
    this(process, charset, null);
  }

  public PtyProcessTtyConnector(@NotNull PtyProcess process, @NotNull Charset charset, @Nullable List<String> commandLine) {
    super(process, charset, commandLine);
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
