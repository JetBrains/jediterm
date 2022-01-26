package com.jediterm.ssh;

import com.jediterm.ssh.jsch.JSchShellTtyConnector;
import com.jediterm.ssh.jsch.JSchTtyConnector;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.AbstractTerminalFrame;

/**
 * @author traff
 */
public class SshMain extends AbstractTerminalFrame {

  public static void main(final String[] arg) {
    new SshMain();
  }

  @Override
  public TtyConnector createTtyConnector() {
    return new JSchShellTtyConnector();
  }

}
