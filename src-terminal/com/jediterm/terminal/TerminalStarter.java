package com.jediterm.terminal;

import com.jediterm.terminal.emulator.Emulator;
import com.jediterm.terminal.emulator.JediEmulator;
import org.apache.log4j.Logger;

import java.awt.*;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Runs terminal emulator. Manages threads to send response.
 *
 * @author traff
 */
public class TerminalStarter implements TerminalOutputStream {
  private static final Logger LOG = Logger.getLogger(TerminalStarter.class);

  private final Emulator myEmulator;

  private final Terminal myTerminal;
  private final TtyChannel myTtyChannel;

  private final TtyConnector myTtyConnector;

  private final ExecutorService myEmulatorExecutor = Executors.newSingleThreadExecutor();

  public TerminalStarter(final Terminal terminal, final TtyConnector ttyConnector) {
    myTtyConnector = ttyConnector;
    myTtyChannel = createTtyChannel();
    myTerminal = terminal;
    myTerminal.setTerminalOutput(this);
    myEmulator = createEmulator(myTtyChannel, this, terminal);
  }

  protected JediEmulator createEmulator(TtyChannel channel, TerminalOutputStream stream, Terminal terminal) {
    return new JediEmulator(channel, stream, terminal);
  }

  private TtyChannel createTtyChannel() {
    return new TtyChannel(myTtyConnector); //TODO: streams can be moved to ttyChanel, so encoding change
    //can be implemented - just recreate channel and that's it
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
      if (!myTtyChannel.isConnected()) {
        myTerminal.disconnected();
        return;
      }
      LOG.error("Caught exception in terminal thread", e);
    }
  }

  public byte[] getCode(final int key, final int modifiers) {
    return myTerminal.getCodeForKey(key, modifiers);
  }

  public void postResize(final Dimension dimension, final RequestOrigin origin) {
    execute(new Runnable() {
      @Override
      public void run() {
        final Dimension pixelSize;
        synchronized (myTerminal) {
          pixelSize = myTerminal.resize(dimension, origin);
        }

        myTtyChannel.postResize(dimension, pixelSize);
      }
    });
  }

  @Override
  public void sendBytes(final byte[] bytes) {
    execute(new Runnable() {
      @Override
      public void run() {
        try {
          myTtyChannel.sendBytes(bytes);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  @Override
  public void sendString(final String string) {
    execute(new Runnable() {
      @Override
      public void run() {
        try {
          myTtyChannel.sendString(string);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  public void close() {
    execute(new Runnable() {
      @Override
      public void run() {
        try {
          myTtyConnector.close();
        }
        catch (Exception e) {
          LOG.error("Error closing terminal", e);
        }
        finally {
          myEmulatorExecutor.shutdown();
        }
      }
    });
  }
}
