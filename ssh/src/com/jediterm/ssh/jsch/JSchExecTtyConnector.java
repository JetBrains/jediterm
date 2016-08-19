package com.jediterm.ssh.jsch;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class JSchExecTtyConnector extends JSchTtyConnector<ChannelExec> {

  public JSchExecTtyConnector() {
  }

  public JSchExecTtyConnector(String host, String user, String password) {
    super(host, DEFAULT_PORT, user, password);
  }

  public JSchExecTtyConnector(String host, int port, String user, String password) {
    super(host, port, user, password);
  }

  @Override
  protected ChannelExec openChannel(Session session) throws JSchException {
    return (ChannelExec) session.openChannel("exec");
  }

  @Override
  protected void configureChannelShell(ChannelExec channel) {
    String lang = System.getenv().get("LANG");
    channel.setEnv("LANG", lang != null ? lang : "en_US.UTF-8");
    channel.setPtyType("xterm");
  }

  @Override
  protected void setPtySize(ChannelExec channel, int col, int row, int wp, int hp) {
    channel.setPtySize(col, row, wp, hp);
  }

}
