/* -*-mode:java; c-basic-offset:2; -*- */
/* JCTerm
 * Copyright (C) 2002 ymnk, JCraft,Inc.
 *  
 * Written by: 2002 ymnk<ymnk@jcaft.com>
 *   
 *   
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 * 
 * You should have received a copy of the GNU Library General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package com.jediterm.emulator;

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
  final protected TtyChannel myTtyChannel;

  private final ExecutorService myEmulatorExecutor = Executors.newFixedThreadPool(1);

  public TerminalStarter(final Terminal terminal, final TtyChannel channel) {
    myTtyChannel = channel;
    myTerminal = terminal;
    myEmulator = new Emulator(myTtyChannel, this, terminal);
  }

  private void execute(Runnable runnable) {
    myEmulatorExecutor.execute(runnable);
  }

  public void start() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        myEmulator.processNextChar();
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

  public byte[] getCode(final int key) {
    return CharacterUtils.getCode(key);
  }

  public void postResize(final Dimension dimension, final RequestOrigin origin) {
    Dimension pixelSize;
    synchronized (myTerminal) {
      pixelSize = myTerminal.resize(dimension, origin);
    }
    myTtyChannel.postResize(dimension, pixelSize);
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
}
