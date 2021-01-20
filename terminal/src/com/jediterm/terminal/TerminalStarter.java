package com.jediterm.terminal;

import com.jediterm.terminal.emulator.Emulator;
import com.jediterm.terminal.emulator.JediEmulator;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;


/**
 * Runs terminal emulator. Manages threads to send response.
 *
 * @author traff
 */
public class TerminalStarter implements TerminalOutputStream {
  private static final Logger LOG = Logger.getLogger(TerminalStarter.class);

  private final Emulator myEmulator;

  private final Terminal myTerminal;

  private final TtyConnector myTtyConnector;

  private final ScheduledExecutorService myEmulatorExecutor = Executors.newSingleThreadScheduledExecutor();

  public TerminalStarter(final Terminal terminal, final TtyConnector ttyConnector, TerminalDataStream dataStream) {
    myTtyConnector = ttyConnector;
    myTerminal = terminal;
    myTerminal.setTerminalOutput(this);
    myEmulator = createEmulator(dataStream, terminal);
  }

  protected JediEmulator createEmulator(TerminalDataStream dataStream, Terminal terminal) {
    return new JediEmulator(dataStream, terminal);
  }

  private void execute(Runnable runnable) {
    if (!myEmulatorExecutor.isShutdown()) {
      myEmulatorExecutor.execute(runnable);
    }
  }

  public void start() {
    try {
      while (!Thread.currentThread().isInterrupted() && myEmulator.hasNext()) {
        myEmulator.next();
      }
    }
    catch (final InterruptedIOException e) {
      LOG.info("Terminal exiting");
    }
    catch (final Exception e) {
      if (!myTtyConnector.isConnected()) {
        myTerminal.disconnected();
        return;
      }
      LOG.error("Caught exception in terminal thread", e);
    }
  }

  public byte[] getCode(final int key, final int modifiers) {
    return myTerminal.getCodeForKey(key, modifiers);
  }

  public void postResize(@NotNull Dimension dimension, @NotNull RequestOrigin origin) {
    execute(() -> {
      resize(dimension, origin);
    });
  }

  /**
   * Resizes terminal and tty connector, should be called on a pooled thread.
   */
  protected void resize(@NotNull Dimension newTermSize, @NotNull RequestOrigin origin) {
    CompletableFuture<?> promptUpdated = ((JediEmulator)myEmulator).getPromptUpdatedAfterResizeFuture(myEmulatorExecutor);
    myTerminal.resize(newTermSize, origin, promptUpdated);
    myTtyConnector.resize(newTermSize);
  }

  @Override
  public void sendBytes(final byte[] bytes) {
    execute(() -> {
      try {
        myTtyConnector.write(bytes);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Override
  public void sendString(final String string) {
    execute(() -> {
      try {
        myTtyConnector.write(string);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  public void close() {
    execute(() -> {
      try {
        myTtyConnector.close();
      }
      catch (Exception e) {
        LOG.error("Error closing terminal", e);
      }
      finally {
        myEmulatorExecutor.shutdown();
      }
    });
  }
}
