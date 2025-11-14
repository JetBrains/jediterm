package com.jediterm.terminal;

import com.jediterm.core.input.KeyEvent;
import com.jediterm.core.typeahead.TerminalTypeAheadManager;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.emulator.Emulator;
import com.jediterm.terminal.emulator.JediEmulator;
import com.jediterm.terminal.model.JediTerminal;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
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

  private final JediTerminal myTerminal;

  private final TtyConnector myTtyConnector;

  private final TerminalTypeAheadManager myTypeAheadManager;
  private final ScheduledExecutorService mySingleThreadScheduledExecutor;
  private volatile boolean myStopped = false;
  private volatile ScheduledFuture<?> myScheduledTtyConnectorResizeFuture;
  private volatile boolean myIsLastSentByteEscape = false;

  public TerminalStarter(@NotNull JediTerminal terminal,
                         @NotNull TtyConnector ttyConnector,
                         @NotNull TerminalDataStream dataStream,
                         @NotNull TerminalTypeAheadManager typeAheadManager,
                         @NotNull TerminalExecutorServiceManager executorServiceManager) {
    myTtyConnector = ttyConnector;
    myTerminal = terminal;
    myTerminal.setTerminalOutput(this);
    myEmulator = createEmulator(dataStream, terminal);
    myTypeAheadManager = typeAheadManager;
    mySingleThreadScheduledExecutor = executorServiceManager.getSingleThreadScheduledExecutor();
  }

  protected JediEmulator createEmulator(TerminalDataStream dataStream, Terminal terminal) {
    return new JediEmulator(dataStream, terminal);
  }

  private void execute(Runnable runnable) {
    if (!mySingleThreadScheduledExecutor.isShutdown()) {
      mySingleThreadScheduledExecutor.execute(runnable);
    }
  }

  public @NotNull TtyConnector getTtyConnector() {
    return myTtyConnector;
  }

  public @NotNull Terminal getTerminal() {
    return myTerminal;
  }

  public void start() {
    runUnderThreadName("TerminalEmulator-" + myTtyConnector.getName(), this::doStartEmulator);
  }

  private void doStartEmulator() {
    try {
      while ((!Thread.currentThread().isInterrupted() && !myStopped) && myEmulator.hasNext()) {
        myEmulator.next();
      }
    }
    catch (InterruptedIOException e) {
      LOG.debug("Terminal I/0 has been interrupted");
    }
    catch (Exception e) {
      if (myTtyConnector.isConnected()) {
        throw new RuntimeException("Uncaught exception in terminal emulator thread", e);
      }
    }
    finally {
      myTerminal.disconnected();
    }
  }

  public void requestEmulatorStop() {
    myStopped = true;
  }

  private static void runUnderThreadName(@NotNull String threadName, @NotNull Runnable runnable) {
    Thread currentThread = Thread.currentThread();
    String oldThreadName = currentThread.getName();
    if (threadName.equals(oldThreadName)) {
      runnable.run();
    }
    else {
      currentThread.setName(threadName);
      try {
        runnable.run();
      }
      finally {
        currentThread.setName(oldThreadName);
      }
    }
  }

  /**
   * @deprecated use {@link JediTerminal#getCodeForKey(int, int)} instead
   */
  @Deprecated
  public byte[] getCode(final int key, final int modifiers) {
    return myTerminal.getCodeForKey(key, modifiers);
  }

  public void postResize(@NotNull TermSize termSize, @NotNull RequestOrigin origin) {
    execute(() -> {
      myTerminal.resize(termSize, origin);
      scheduleTtyConnectorResize(termSize);
    });
  }

  /**
   * Schedule sending a resize to a process. When using primary screen buffer + scroll-back buffer,
   * resize shouldn't be sent to the process immediately to reduce probability of concurrent resizes.
   * Because sending resize to the process may lead to full screen buffer update,
   * e.g. it happens with ConPTY. The update should be applied to the screen buffer having
   * the exact same size as it had when resize was posted. Otherwise, some lines from the screen buffer
   * could escape to the scroll-back buffer and stuck there.
   */
  private void scheduleTtyConnectorResize(@NotNull TermSize termSize) {
    ScheduledFuture<?> scheduledTtyConnectorResizeFuture = myScheduledTtyConnectorResizeFuture;
    if (scheduledTtyConnectorResizeFuture != null) {
      scheduledTtyConnectorResizeFuture.cancel(false);
    }
    long mergeDelay = myTerminal.getTerminalTextBuffer().isUsingAlternateBuffer() ?
            100 /* not necessary, but let's avoid unnecessary work in case of a series of resize events */ :
            500 /* hopefully, the process will send the screen buffer update within the delay */;
    //noinspection CodeBlock2Expr
    myScheduledTtyConnectorResizeFuture = mySingleThreadScheduledExecutor.schedule(() -> {
      myTtyConnector.resize(termSize);
    }, mergeDelay, TimeUnit.MILLISECONDS);
  }

  /**
   * @deprecated use {@link Terminal#resize(TermSize, RequestOrigin)} and {@link TtyConnector#resize(TermSize)} independently.
   * Resizes terminal and tty connector, should be called on a pooled thread.
   */
  @SuppressWarnings("unused")
  @Deprecated(forRemoval = true)
  public static void resize(@NotNull Emulator emulator,
                            @NotNull Terminal terminal,
                            @NotNull TtyConnector ttyConnector,
                            @NotNull TermSize newTermSize,
                            @NotNull RequestOrigin origin,
                            @NotNull BiConsumer<Long, Runnable> taskScheduler) {
    terminal.resize(newTermSize, origin);
    ttyConnector.resize(newTermSize);
  }

  @Override
  public void sendBytes(byte @NotNull [] bytes, boolean userInput) {
    int length = bytes.length;
    if (length > 0) {
      myIsLastSentByteEscape = bytes[length - 1] == KeyEvent.VK_ESCAPE;
    }
    execute(() -> {
      try {
        if (userInput) {
          TerminalTypeAheadManager.TypeAheadEvent.fromByteArray(bytes).forEach(myTypeAheadManager::onKeyEvent);
        }
        myTtyConnector.write(bytes);
      }
      catch (IOException e) {
        logWriteError(e);
      }
    });
  }

  @Override
  public void sendString(@NotNull String string, boolean userInput) {
    int length = string.length();
    if (length > 0) {
      myIsLastSentByteEscape = string.charAt(length - 1) == KeyEvent.VK_ESCAPE;
    }
    execute(() -> {
      try {
        if (userInput) {
          TerminalTypeAheadManager.TypeAheadEvent.fromString(string).forEach(myTypeAheadManager::onKeyEvent);
        }

        myTtyConnector.write(string);
      }
      catch (IOException e) {
        logWriteError(e);
      }
    });
  }

  private void logWriteError(@NotNull IOException e) {
    LOG.info("Cannot write to TtyConnector " + myTtyConnector.getClass().getName() + ", connected: " + myTtyConnector.isConnected(), e);
  }

  public void close() {
    execute(() -> {
      try {
        myTtyConnector.close();
      }
      catch (Exception e) {
        LOG.error("Error closing terminal", e);
      }
    });
  }

  public boolean isLastSentByteEscape() {
    return myIsLastSentByteEscape;
  }
}
