package com.jediterm.pty;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jediterm.terminal.LoggingTtyConnector;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.ui.AbstractTerminalFrame;
import com.jediterm.terminal.ui.UIUtil;
import com.pty4j.PtyProcess;
import com.sun.jna.Platform;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author traff
 */
public class PtyMain extends AbstractTerminalFrame {
  public static String[] args;
  @Override
  public TtyConnector createTtyConnector() {
    try {
      Map<String, String> envs = Maps.newHashMap(System.getenv());
      envs.put("TERM", "xterm");
      String[] command = PtyMain.args.clone();
      if (PtyMain.args.length == 0) {
        if (UIUtil.isWindows) {
          command = new String[]{"cmd.exe"};
        } else {
          command = new String[]{"/bin/bash", "--login"};          
        }
      }

      PtyProcess process = PtyProcess.exec(command, envs, null);

      return new LoggingPtyProcessTtyConnector(process, Charset.forName("UTF-8"));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static void main(final String[] arg) {
    PtyMain.args = arg;
    BasicConfigurator.configure();
    Logger.getRootLogger().setLevel(Level.INFO);
    new PtyMain();
  }


  public static class LoggingPtyProcessTtyConnector extends PtyProcessTtyConnector implements LoggingTtyConnector {
    private List<char[]> myDataChunks = Lists.newArrayList();

    public LoggingPtyProcessTtyConnector(PtyProcess process, Charset charset) {
      super(process, charset);
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
      int len = super.read(buf, offset, length);
      if (len > 0) {
        char[] arr = Arrays.copyOfRange(buf, offset, len);
        myDataChunks.add(arr);
      }
      return len;
    }

    public List<char[]> getChunks() {
      return Lists.newArrayList(myDataChunks);
    }

    @Override
    public void write(String string) throws IOException {
      LOG.debug("Writing in OutputStream : " + string);
      super.write(string);
    }

    @Override
    public void write(byte[] bytes) throws IOException {
      LOG.debug("Writing in OutputStream : " + Arrays.toString(bytes) + " " + new String(bytes));
      super.write(bytes);
    }
  }
}
