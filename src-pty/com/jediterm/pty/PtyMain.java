package com.jediterm.pty;

import com.jediterm.emulator.ui.AbstractTerminalFrame;
import com.jediterm.emulator.ui.SwingJediTerminal;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.nio.charset.Charset;

/**
 * @author traff
 */
public class PtyMain extends AbstractTerminalFrame {
  @Override
  public void openSession(SwingJediTerminal terminal) {
    terminal.setTtyConnector(new PtyProcessTtyConnector(new PtyProcess("/bin/bash", new String[]{"/bin/bash"}), Charset.defaultCharset()));
    terminal.start();
  }

  public static void main(final String[] arg) {
    BasicConfigurator.configure();
    Logger.getRootLogger().setLevel(Level.DEBUG);
    new PtyMain();
  }
}
