package com.jediterm.terminal.ui;

import com.jediterm.terminal.TerminalStarter;
import org.apache.log4j.Logger;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class TerminalEmulatorKeyHandler implements KeyListener {
  private static Logger logger = Logger.getLogger(TerminalEmulatorKeyHandler.class);
  private final TerminalStarter myTerminalStarter;

  public TerminalEmulatorKeyHandler(TerminalStarter terminalStarter) {
    myTerminalStarter = terminalStarter;
  }

  public void keyPressed(final KeyEvent e) {
    try {
      final int keycode = e.getKeyCode();
      
      final byte[] code = myTerminalStarter.getCode(keycode);
      if (code != null) {
        myTerminalStarter.sendBytes(code);
      }
      else {
        final char keychar = e.getKeyChar();
        final byte[] obuffer = new byte[1];
        if ((keychar & 0xff00) == 0) {
          obuffer[0] = (byte)e.getKeyChar();
          myTerminalStarter.sendBytes(obuffer);
        }
      }
    }
    catch (final Exception ex) {
      logger.error("Error sending key to emulator", ex);
    }
  }

  public void keyTyped(final KeyEvent e) {
    final char keychar = e.getKeyChar();
    if ((keychar & 0xff00) != 0) {
      final char[] foo = new char[1];
      foo[0] = keychar;
      try {
        myTerminalStarter.sendString(new String(foo));
      }
      catch (final RuntimeException ex) {
        logger.error("Error sending key to emulator", ex);
      }
    }
  }

  //Ignore releases
  public void keyReleased(KeyEvent e) {
  }
}
