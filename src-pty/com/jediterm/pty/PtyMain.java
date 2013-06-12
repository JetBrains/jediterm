package com.jediterm.pty;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jediterm.emulator.ui.AbstractTerminalFrame;
import com.jediterm.emulator.ui.SwingJediTerminal;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author traff
 */
public class PtyMain extends AbstractTerminalFrame {
  @Override
  public void openSession(final SwingJediTerminal terminal) {
    Map<String,String> envs = Maps.newHashMap(System.getenv());
    envs.put("TERM", "vt100");
    terminal.setTtyConnectorAndInitEmulator(
      new LoggingPtyProcessTtyConnector(new PtyProcess("/bin/bash", new String[]{"/bin/bash"}, envs), Charset.defaultCharset()));
    terminal.start();
  }

  public static void main(final String[] arg) {
    BasicConfigurator.configure();
    Logger.getRootLogger().setLevel(Level.DEBUG);
    new PtyMain();
  }


  public static class LoggingPtyProcessTtyConnector extends PtyProcessTtyConnector {
    private List<char[]> myDataChunks = Lists.newArrayList();

    public LoggingPtyProcessTtyConnector(PtyProcess process, Charset charset) {
      super(process, charset);
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
      int len =  super.read(buf, offset, length);
      if (len>0) {
        char[] arr = Arrays.copyOfRange(buf, offset, len);
        myDataChunks.add(arr);
      }
      return len;
    }

    public List<char[]> getChunks() {
      return myDataChunks;
    }
  }
}
