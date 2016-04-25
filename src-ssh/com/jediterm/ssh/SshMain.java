package com.jediterm.ssh;

import com.jediterm.ssh.jsch.JSchShellTtyConnector;
import com.jediterm.ssh.jsch.JSchTtyConnector;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.AbstractTerminalFrame;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * @author traff
 */
public class SshMain extends AbstractTerminalFrame {

  public static void main(final String[] arg) {
    BasicConfigurator.configure();
    Logger.getRootLogger().setLevel(Level.INFO);
    new SshMain();
  }

  @Override
  public TtyConnector createTtyConnector() {
    return new JSchShellTtyConnector();
  }

}
