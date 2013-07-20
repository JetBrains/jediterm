package com.jediterm.pty;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jediterm.terminal.LoggingTtyConnector;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.AbstractTerminalFrame;
import com.jediterm.terminal.ui.UIUtil;
import com.pty4j.Pty;
import com.pty4j.PtyProcess;
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
  @Override
  public TtyConnector createTtyConnector() {
    Map<String, String> envs = Maps.newHashMap(System.getenv());
    envs.put("TERM", "xterm");
    String[] command = UIUtil.isMac ? new String[]{"/bin/bash", "--login"} : new String[]{"/bin/bash"};
    try {
      PtyProcess process = Pty.exec(command, toStringArray(envs), new File("/Users/traff"));

      return new PtyProcessTtyConnector(process, Charset.forName("UTF-8"));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String[] toStringArray(Map<String, String> environment) {
    List<String> list = Lists.transform(Lists.newArrayList(environment.entrySet()), new Function<Map.Entry<String, String>, String>() {
      @Override
      public String apply(Map.Entry<String, String> entry) {
        return entry.getKey() + "=" + entry.getValue();
      }
    });
    return list.toArray(new String[list.size()]);
  }

  public static void main(final String[] arg) {
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
  }
}
