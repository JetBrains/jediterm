package com.jediterm.terminal;

import com.jediterm.core.typeahead.TerminalTypeAheadManager;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.emulator.Emulator;
import com.jediterm.terminal.emulator.JediEmulator;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;


/**
 * Runs terminal emulator. Manages threads to send response.
 *
 * @author traff
 */
public class TerminalStarter implements TerminalOutputStream {
  private static final Logger LOG = LoggerFactory.getLogger(TerminalStarter.class);

  private final Emulator myEmulator;

  private final Terminal myTerminal;

  private final TtyConnector myTtyConnector;

  private final TerminalTypeAheadManager myTypeAheadManager;

  private final ScheduledExecutorService myEmulatorExecutor = Executors.newSingleThreadScheduledExecutor();

  public TerminalStarter(final Terminal terminal, final TtyConnector ttyConnector, TerminalDataStream dataStream, TerminalTypeAheadManager typeAheadManager) {
    myTtyConnector = ttyConnector;
    myTerminal = terminal;
    myTerminal.setTerminalOutput(this);
    myEmulator = createEmulator(dataStream, terminal);
    myTypeAheadManager = typeAheadManager;
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

  public void postResize(@NotNull TermSize termSize, @NotNull RequestOrigin origin) {
    execute(() -> {
      resize(myEmulator, myTerminal, myTtyConnector, termSize, origin, (millisDelay, runnable) -> {
        myEmulatorExecutor.schedule(runnable, millisDelay, TimeUnit.MILLISECONDS);
      });
    });
  }

  /**
   * Resizes terminal and tty connector, should be called on a pooled thread.
   */
  public static void resize(@NotNull Emulator emulator,
                            @NotNull Terminal terminal,
                            @NotNull TtyConnector ttyConnector,
                            @NotNull TermSize newTermSize,
                            @NotNull RequestOrigin origin,
                            @NotNull BiConsumer<Long, Runnable> taskScheduler) {
    CompletableFuture<?> promptUpdated = ((JediEmulator)emulator).getPromptUpdatedAfterResizeFuture(taskScheduler);
    terminal.resize(newTermSize, origin, promptUpdated);
    ttyConnector.resize(newTermSize);
  }

  @Override
  public void sendBytes(final byte[] bytes) {
    sendBytes(bytes, false);
  }

  @Override
  public void sendBytes(final byte[] bytes, boolean userInput) {
    execute(() -> {
      try {
        if (userInput) {
          TerminalTypeAheadManager.TypeAheadEvent.fromByteArray(bytes).forEach(myTypeAheadManager::onKeyEvent);
        }
        myTtyConnector.write(bytes);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Override
  public void sendString(final String string) {
    sendString(string, false);
  }

  @Override
  public void sendString(final String string, boolean userInput) {
    execute(() -> {
      try {
        if (userInput) {
          TerminalTypeAheadManager.TypeAheadEvent.fromString(string).forEach(myTypeAheadManager::onKeyEvent);
        }

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
