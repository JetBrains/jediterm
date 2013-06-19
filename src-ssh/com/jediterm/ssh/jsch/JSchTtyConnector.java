/**
 *
 */
package com.jediterm.ssh.jsch;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jediterm.terminal.Questioner;
import com.jediterm.terminal.TtyConnector;
import org.apache.log4j.Logger;

import java.awt.*;
import java.io.*;

public class JSchTtyConnector implements TtyConnector {
  public static final Logger LOG = Logger.getLogger(JSchTtyConnector.class);

  private InputStream in = null;
  private OutputStream out = null;
  private Session session;
  private ChannelShell channel;
  private int port = 22;

  private String user = null;
  private String host = null;
  private String password = null;

  private Dimension pendingTermSize;
  private Dimension pendingPixelSize;
  private InputStreamReader inReader;
  private OutputStreamWriter outWriter;


  public JSchTtyConnector() {

  }

  public JSchTtyConnector(String host, String user, String password) {
    this.host = host;
    this.user = user;
    this.password = password;
  }

  public void resize(Dimension termSize, Dimension pixelSize) {
    pendingTermSize = termSize;
    pendingPixelSize = pixelSize;
    if (channel != null) resizeImmediately();
  }

  private void resizeImmediately() {
    if (pendingTermSize != null && pendingPixelSize != null) {
      channel.setPtySize(pendingTermSize.width, pendingTermSize.height, pendingPixelSize.width, pendingPixelSize.height);
      pendingTermSize = null;
      pendingPixelSize = null;
    }
  }


  public void close() {
    if (session != null) {
      session.disconnect();
      session = null;
      channel = null;
      in = null;
      out = null;
    }
  }

  public boolean init(Questioner q) {

    getAuthDetails(q);

    try {
      session = connectSession(q);
      channel = (ChannelShell)session.openChannel("shell");
      in = channel.getInputStream();
      out = channel.getOutputStream();
      inReader = new InputStreamReader(in, "utf-8");
      channel.connect();
      resizeImmediately();
      return true;
    }
    catch (final IOException e) {
      q.showMessage(e.getMessage());
      LOG.error("Error opening channel", e);
      return false;
    }
    catch (final JSchException e) {
      q.showMessage(e.getMessage());
      LOG.error("Error opening session or channel", e);
      return false;
    }
  }

  private Session connectSession(Questioner questioner) throws JSchException {
    JSch jsch = new JSch();

    Session session = jsch.getSession(user, host, port);

    final QuestionerUserInfo ui = new QuestionerUserInfo(questioner);
    if (password != null) {
      session.setPassword(password);
      ui.setPassword(password);
    }
    session.setUserInfo(ui);

    final java.util.Properties config = new java.util.Properties();
    config.put("compression.s2c", "zlib,none");
    config.put("compression.c2s", "zlib,none");
    configureSession(session, config);
    session.setTimeout(5000);
    session.connect();
    session.setTimeout(0);

    return session;
  }

  protected void configureSession(Session session, final java.util.Properties config) {
    session.setConfig(config);
  }

  private void getAuthDetails(Questioner q) {
    while (true) {
      if (host == null) {
        host = q.questionVisible("host:", "localhost");
      }
      if (host == null || host.length() == 0) {
        continue;
      }
      if (host.indexOf(':') != -1) {
        final String portString = host.substring(host.indexOf(':') + 1);
        try {
          port = Integer.parseInt(portString);
        }
        catch (final NumberFormatException eee) {
          q.showMessage("Could not parse port : " + portString);
          continue;
        }
        host = host.substring(0, host.indexOf(':'));
      }

      if (user == null) {
        user = q.questionVisible("user:", System.getProperty("user.name").toLowerCase());
      }
      if (host == null || host.length() == 0) {
        continue;
      }
      break;
    }
  }

  public String getName() {
    return "ConnectRunnable";
  }

  @Override
  public int read(char[] buf, int offset, int length) throws IOException {
    return inReader.read(buf, offset, length);
  }

  public int read(byte[] buf, int offset, int length) throws IOException {
    return in.read(buf, offset, length);
  }

  public void write(byte[] bytes) throws IOException {
    out.write(bytes);
    out.flush();
  }

  @Override
  public boolean isConnected() {
    return channel.isConnected();
  }

  @Override
  public void write(String string) throws IOException {
    write(string.getBytes("utf-8")); //TODO: fix
  }
}
