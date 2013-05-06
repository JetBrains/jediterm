package com.jediterm.emulator.ui;

import com.jediterm.emulator.Emulator;
import org.apache.log4j.Logger;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;

public class ConnectedKeyHandler implements KeyListener {
  private static Logger logger = Logger.getLogger(ConnectedKeyHandler.class);
  private final Emulator myEmulator;

  public ConnectedKeyHandler(Emulator emu) {
    myEmulator = emu;
  }

  public void keyPressed(final KeyEvent e) {
    try {
      final int keycode = e.getKeyCode();
      final byte[] code = myEmulator.getCode(keycode);
      if (code != null) {
        myEmulator.sendBytes(code);
      }
      else {
        final char keychar = e.getKeyChar();
        final byte[] obuffer = new byte[1];
        if ((keychar & 0xff00) == 0) {
          obuffer[0] = (byte)e.getKeyChar();
          myEmulator.sendBytes(obuffer);
        }
      }
    }
    catch (final IOException ex) {
      logger.error("Error sending key to emulator", ex);
    }
  }

  public void keyTyped(final KeyEvent e) {
    final char keychar = e.getKeyChar();
    if ((keychar & 0xff00) != 0) {
      final char[] foo = new char[1];
      foo[0] = keychar;
      try {
        myEmulator.sendString(new String(foo));
      }
      catch (final IOException ex) {
        logger.error("Error sending key to emulator", ex);
      }
    }
  }

  //Ignore releases
  public void keyReleased(KeyEvent e) {
  }
}
