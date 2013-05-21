package com.jediterm.ssh;

import com.jediterm.emulator.ui.AbstractTerminalFrame;
import com.jediterm.emulator.ui.SwingJediTerminal;
import com.jediterm.ssh.jsch.JSchTtyConnector;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * @author traff
 */
public class SshMain extends AbstractTerminalFrame {

  @Override
  public void openSession(SwingJediTerminal terminal) {
    if (!terminal.isSessionRunning()) {
      terminal.setTtyConnector(new JSchTtyConnector());
      terminal.start();
    }
  }

  public static void main(final String[] arg) {
    BasicConfigurator.configure();
    Logger.getRootLogger().setLevel(Level.INFO);
    new SshMain();
  }
}
